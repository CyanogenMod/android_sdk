/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.LintConstants.DOT_XML;
import static com.android.tools.lint.detector.api.LintUtils.assertionsEnabled;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.LintUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.io.Files;
import com.google.common.primitives.UnsignedBytes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Database of common typos / misspellings.
 */
public class TypoLookup {
    private static final TypoLookup NONE = new TypoLookup();

    /** String separating misspellings and suggested replacements in the text file */
    private static final String WORD_SEPARATOR = "->";  //$NON-NLS-1$

    /** Relative path to the typos database file within the Lint installation */
    private static final String XML_FILE_PATH = "tools/support/typos-%1$s.txt"; //$NON-NLS-1$
    private static final String FILE_HEADER = "Typo database used by Android lint\000";
    private static final int BINARY_FORMAT_VERSION = 2;
    private static final boolean DEBUG_FORCE_REGENERATE_BINARY = false;
    private static final boolean DEBUG_SEARCH = false;
    private static final boolean WRITE_STATS = false;
    /** Default size to reserve for each API entry when creating byte buffer to build up data */
    private static final int BYTES_PER_ENTRY = 28;

    private final LintClient mClient;
    private final File mXmlFile;
    private final File mBinaryFile;
    private byte[] mData;
    private int[] mIndices;
    private int mWordCount;

    private static WeakHashMap<String, TypoLookup> sInstanceMap =
            new WeakHashMap<String, TypoLookup>();

    /**
     * Returns an instance of the Typo database for the given locale
     *
     * @param client the client to associate with this database - used only for
     *            logging. The database object may be shared among repeated
     *            invocations, and in that case client used will be the one
     *            originally passed in. In other words, this parameter may be
     *            ignored if the client created is not new.
     * @param locale the locale to look up a typo database for (should be a
     *         language code (ISO 639-1, two lowercase character names)
     * @return a (possibly shared) instance of the typo database, or null if its
     *         data can't be found
     */
    @Nullable
    public static TypoLookup get(@NonNull LintClient client, @NonNull String locale) {
        synchronized (TypoLookup.class) {
            TypoLookup db = sInstanceMap.get(locale);
            if (db == null) {
                String path = String.format(XML_FILE_PATH, locale);
                File file = client.findResource(path);
                if (file == null) {
                    // AOSP build environment?
                    String build = System.getenv("ANDROID_BUILD_TOP");   //$NON-NLS-1$
                    if (build != null) {
                        file = new File(build, ("sdk/files/" //$NON-NLS-1$
                                    + path.substring(path.lastIndexOf('/') + 1))
                                      .replace('/', File.separatorChar));
                    }
                }

                if (file == null || !file.exists()) {
                    db = NONE;
                } else {
                    db = get(client, file);
                }
                sInstanceMap.put(locale, db);
            }

            if (db == NONE) {
                return null;
            } else {
                return db;
            }
        }
    }

    /**
     * Returns an instance of the typo database
     *
     * @param client the client to associate with this database - used only for
     *            logging
     * @param xmlFile the XML file containing configuration data to use for this
     *            database
     * @return a (possibly shared) instance of the typo database, or null
     *         if its data can't be found
     */
    public static TypoLookup get(LintClient client, File xmlFile) {
        if (!xmlFile.exists()) {
            client.log(null, "The typo database file %1$s does not exist", xmlFile);
            return null;
        }

        String name = xmlFile.getName();
        if (LintUtils.endsWith(name, DOT_XML)) {
            name = name.substring(0, name.length() - DOT_XML.length());
        }
        File cacheDir = client.getCacheDir(true/*create*/);
        if (cacheDir == null) {
            cacheDir = xmlFile.getParentFile();
        }

        File binaryData = new File(cacheDir, name
                // Incorporate version number in the filename to avoid upgrade filename
                // conflicts on Windows (such as issue #26663)
                + "-" + BINARY_FORMAT_VERSION + ".bin"); //$NON-NLS-1$ //$NON-NLS-2$

        if (DEBUG_FORCE_REGENERATE_BINARY) {
            System.err.println("\nTemporarily regenerating binary data unconditionally \nfrom "
                    + xmlFile + "\nto " + binaryData);
            if (!createCache(client, xmlFile, binaryData)) {
                return null;
            }
        } else if (!binaryData.exists() || binaryData.lastModified() < xmlFile.lastModified()) {
            if (!createCache(client, xmlFile, binaryData)) {
                return null;
            }
        }

        if (!binaryData.exists()) {
            client.log(null, "The typo database file %1$s does not exist", binaryData);
            return null;
        }

        return new TypoLookup(client, xmlFile, binaryData);
    }

    private static boolean createCache(LintClient client, File xmlFile, File binaryData) {
        long begin = 0;
        if (WRITE_STATS) {
            begin = System.currentTimeMillis();
        }

        // Read in data
        List<String> lines;
        try {
            lines = Files.readLines(xmlFile, Charsets.UTF_8);
        } catch (IOException e) {
            client.log(e, "Can't read typo database file");
            return false;
        }

        if (WRITE_STATS) {
            long end = System.currentTimeMillis();
            System.out.println("Reading data structures took " + (end - begin) + " ms)");
        }

        try {
            writeDatabase(binaryData, lines);
            return true;
        } catch (IOException ioe) {
            client.log(ioe, "Can't write typo cache file");
        }

        return false;
    }

    /** Use one of the {@link #get} factory methods instead */
    private TypoLookup(
            @NonNull LintClient client,
            @NonNull File xmlFile,
            @Nullable File binaryFile) {
        mClient = client;
        mXmlFile = xmlFile;
        mBinaryFile = binaryFile;

        if (binaryFile != null) {
            readData();
        }
    }

    private TypoLookup() {
        mClient = null;
        mXmlFile = null;
        mBinaryFile = null;
    }

    private void readData() {
        if (!mBinaryFile.exists()) {
            mClient.log(null, "%1$s does not exist", mBinaryFile);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            MappedByteBuffer buffer = Files.map(mBinaryFile, MapMode.READ_ONLY);
            assert buffer.order() == ByteOrder.BIG_ENDIAN;

            // First skip the header
            byte[] expectedHeader = FILE_HEADER.getBytes(Charsets.US_ASCII);
            buffer.rewind();
            for (int offset = 0; offset < expectedHeader.length; offset++) {
                if (expectedHeader[offset] != buffer.get()) {
                    mClient.log(null, "Incorrect file header: not an typo database cache " +
                            "file, or a corrupt cache file");
                    return;
                }
            }

            // Read in the format number
            if (buffer.get() != BINARY_FORMAT_VERSION) {
                // Force regeneration of new binary data with up to date format
                if (createCache(mClient, mXmlFile, mBinaryFile)) {
                    readData(); // Recurse
                }

                return;
            }

            mWordCount = buffer.getInt();

            // Read in the word table indices;
            int count = mWordCount;
            int[] offsets = new int[count];

            // Another idea: I can just store the DELTAS in the file (and add them up
            // when reading back in) such that it takes just ONE byte instead of four!

            for (int i = 0; i < count; i++) {
                offsets[i] = buffer.getInt();
            }

            // No need to read in the rest -- we'll just keep the whole byte array in memory
            // TODO: Make this code smarter/more efficient.
            int size = buffer.limit();
            byte[] b = new byte[size];
            buffer.rewind();
            buffer.get(b);
            mData = b;
            mIndices = offsets;

            // TODO: We only need to keep the data portion here since we've initialized
            // the offset array separately.
            // TODO: Investigate (profile) accessing the byte buffer directly instead of
            // accessing a byte array.
        } catch (IOException e) {
            mClient.log(e, null);
        }
        if (WRITE_STATS) {
            long end = System.currentTimeMillis();
            System.out.println("\nRead typo database in " + (end - start)
                    + " milliseconds.");
            System.out.println("Size of data table: " + mData.length + " bytes ("
                    + Integer.toString(mData.length/1024) + "k)\n");
        }
    }

    /** See the {@link #readData()} for documentation on the data format. */
    private static void writeDatabase(File file, List<String> lines) throws IOException {
        /*
         * 1. A file header, which is the exact contents of {@link FILE_HEADER} encoded
         *     as ASCII characters. The purpose of the header is to identify what the file
         *     is for, for anyone attempting to open the file.
         * 2. A file version number. If the binary file does not match the reader's expected
         *     version, it can ignore it (and regenerate the cache from XML).
         */

        // Drop comments etc
        List<String> words = new ArrayList<String>(lines.size());
        for (String line : lines) {
            if (!line.isEmpty() && Character.isLetter(line.charAt(0))) {
                int end = line.indexOf(WORD_SEPARATOR);
                if (end == -1) {
                    end = line.trim().length();
                }
                String typo = line.substring(0, end);
                String replacements = line.substring(end + WORD_SEPARATOR.length()).trim();
                String combined = typo + (char) 0 + replacements;

                words.add(combined);
            }
        }

        byte[][] wordArrays = new byte[words.size()][];
        for (int i = 0, n = words.size(); i < n; i++) {
            String word = words.get(i);
            wordArrays[i] = word.getBytes(Charsets.UTF_8);
        }
        // Sort words, using our own comparator to ensure that it matches the
        // binary search in getTypos()
        Comparator<byte[]> comparator = new Comparator<byte[]>() {
            @Override
            public int compare(byte[] o1, byte[] o2) {
                return TypoLookup.compare(o1, 0, (byte) 0, o2, 0, o2.length);
            }
        };
        Arrays.sort(wordArrays, comparator);

        int entryCount = wordArrays.length;
        int capacity = entryCount * BYTES_PER_ENTRY;
        ByteBuffer buffer = ByteBuffer.allocate(capacity);
        buffer.order(ByteOrder.BIG_ENDIAN);
        //  1. A file header, which is the exact contents of {@link FILE_HEADER} encoded
        //      as ASCII characters. The purpose of the header is to identify what the file
        //      is for, for anyone attempting to open the file.
        buffer.put(FILE_HEADER.getBytes(Charsets.US_ASCII));

        //  2. A file version number. If the binary file does not match the reader's expected
        //      version, it can ignore it (and regenerate the cache from XML).
        buffer.put((byte) BINARY_FORMAT_VERSION);

        //  3. The number of words [1 int]
        buffer.putInt(entryCount);

        //  4. Word offset table (one integer per word, pointing to the byte offset in the
        //       file (relative to the beginning of the file) where each word begins.
        //       The words are always sorted alphabetically.
        int wordOffsetTable = buffer.position();

        // Reserve enough room for the offset table here: we will backfill it with pointers
        // as we're writing out the data structures below
        for (int i = 0, n = entryCount; i < n; i++) {
            buffer.putInt(0);
        }

        int nextEntry = buffer.position();
        int nextOffset = wordOffsetTable;

        // 7. Word entry table. Each word entry consists of the word, followed by the byte 0
        //      as a terminator, followed by a comma separated list of suggestions (which
        //      may be empty), or a final 0.
        for (int i = 0; i < entryCount; i++) {
            byte[] word = wordArrays[i];
            buffer.position(nextOffset);
            buffer.putInt(nextEntry);
            nextOffset = buffer.position();
            buffer.position(nextEntry);

            buffer.put(word); // already embeds 0 to separate typo from words
            buffer.put((byte) 0);

            nextEntry = buffer.position();
        }

        int size = buffer.position();
        assert size <= buffer.limit();
        buffer.mark();

        if (WRITE_STATS) {
            System.out.println("Wrote " + words.size() + " word entries");
            System.out.print("Actual binary size: " + size + " bytes");
            System.out.println(String.format(" (%.1fM)", size/(1024*1024.f)));

            System.out.println("Allocated size: " + (entryCount * BYTES_PER_ENTRY) + " bytes");
            System.out.println("Required bytes per entry: " + (size/ entryCount) + " bytes");
        }

        // Now dump this out as a file
        // There's probably an API to do this more efficiently; TODO: Look into this.
        byte[] b = new byte[size];
        buffer.rewind();
        buffer.get(b);
        FileOutputStream output = Files.newOutputStreamSupplier(file).getOutput();
        output.write(b);
        output.close();
    }

    // For debugging only
    private String dumpEntry(int offset) {
        if (DEBUG_SEARCH) {
            StringBuilder sb = new StringBuilder();
            for (int i = offset; i < mData.length; i++) {
                if (mData[i] == 0) {
                    break;
                }
                char c = (char) UnsignedBytes.toInt(mData[i]);
                sb.append(c);
            }

            return sb.toString();
        } else {
            return "<disabled>"; //$NON-NLS-1$
        }
    }

    /** Comparison function: *only* used for ASCII strings */
    private static int compare(byte[] data, int offset, byte terminator, CharSequence s,
            int begin, int end) {
        int i = offset;
        int j = begin;
        for (; j < end; i++, j++) {
            byte b = data[i];
            char c = s.charAt(j);
            byte cb = (byte) c;
            int delta = b - cb;
            if (delta != 0) {
                cb = (byte) Character.toLowerCase(c);
                delta = b - cb;
                if (delta != 0) {
                    return delta;
                }
            }
        }

        return data[i] - terminator;
    }

    /** Comparison function used for general UTF-8 encoded strings */
    private static int compare(byte[] data, int offset, byte terminator, byte[] s,
            int begin, int end) {
        int i = offset;
        int j = begin;
        for (; j < end; i++, j++) {
            byte b = data[i];
            byte cb = s[j];
            int delta = b - cb;
            if (delta != 0) {
                if (cb >= 'A' && cb <= 'Z') { // TODO: Generalize lower-casing here
                    cb -= 'A' - 'a';
                    delta = b - cb;
                    if (delta != 0) {
                        return delta;
                    }
                } else {
                    return delta;
                }
            }

            if (b == terminator || cb == terminator) {
                return delta;
            }
        }

        return data[i] - terminator;
    }

    /**
     * Look up whether this word is a typo, and if so, return one or more likely
     * meanings
     *
     * @param text the string containing the word
     * @param begin the index of the first character in the word
     * @param end the index of the first character after the word
     * @return an iterable of replacement strings if the word represents a typo,
     *         and null otherwise
     */
    @Nullable
    public Iterable<String> getTypos(@NonNull CharSequence text, int begin, int end) {
        assert end <= text.length();

        if (assertionsEnabled()) {
            for (int i = begin; i < end; i++) {
                char c = text.charAt(i);
                if (c >= 128) {
                    assert false : "Call the UTF-8 version of this method instead";
                    return null;
                }
            }
        }

        int low = 0;
        int high = mWordCount - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int offset = mIndices[middle];

            if (DEBUG_SEARCH) {
                System.out.println("Comparing string " + text +" with entry at " + offset
                        + ": " + dumpEntry(offset));
            }

            // Compare the word at the given index.
            int compare = compare(mData, offset, (byte) 0, text, begin, end);
            if (compare == 0) {
                offset = mIndices[middle];

                // Make sure there is a case match; we only want to allow
                // matching capitalized words to capitalized typos or uncapitalized typos
                //  (e.g. "Teh" and "teh" to "the"), but not uncapitalized words to capitalized
                // typos (e.g. "enlish" to "Enlish").
                for (int i = begin; i < end; i++) {
                    byte b = mData[offset++];
                    char c = text.charAt(i);
                    byte cb = (byte) c;
                    if (b != cb && i > begin) {
                        return null;
                    }
                }

                assert mData[offset] == 0;
                offset++;
                int replacementEnd = offset;
                while (mData[replacementEnd] != 0) {
                    replacementEnd++;
                }
                String replacements = new String(mData, offset, replacementEnd - offset,
                        Charsets.UTF_8);
                return Splitter.on(',').omitEmptyStrings().trimResults().split(replacements);
            }

            if (compare < 0) {
                low = middle + 1;
            } else if (compare > 0) {
                high = middle - 1;
            } else {
                assert false; // compare == 0 already handled above
                return null;
            }
        }

        return null;
    }

    /**
     * Look up whether this word is a typo, and if so, return one or more likely
     * meanings
     *
     * @param utf8Text the string containing the word, encoded as UTF-8
     * @param begin the index of the first character in the word
     * @param end the index of the first character after the word
     * @return an iterable of replacement strings if the word represents a typo,
     *         and null otherwise
     */
    @Nullable
    public Iterable<String> getTypos(@NonNull byte[] utf8Text, int begin, int end) {
        assert end <= utf8Text.length;

        int low = 0;
        int high = mWordCount - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int offset = mIndices[middle];

            if (DEBUG_SEARCH) {
                System.out.println("Comparing string " + utf8Text +" with entry at " + offset
                        + ": " + dumpEntry(offset));
            }

            // Compare the word at the given index.
            int compare = compare(mData, offset, (byte) 0, utf8Text, begin, end);
            if (compare == 0) {
                offset = mIndices[middle];

                // Make sure there is a case match; we only want to allow
                // matching capitalized words to capitalized typos or uncapitalized typos
                //  (e.g. "Teh" and "teh" to "the"), but not uncapitalized words to capitalized
                // typos (e.g. "enlish" to "Enlish").
                for (int i = begin; i < end; i++) {
                    byte b = mData[offset++];
                    byte cb = utf8Text[i];
                    if (b != cb && i > begin) {
                        return null;
                    }
                }

                assert mData[offset] == 0;
                offset++;
                int replacementEnd = offset;
                while (mData[replacementEnd] != 0) {
                    replacementEnd++;
                }
                String replacements = new String(mData, offset, replacementEnd - offset,
                        Charsets.UTF_8);
                return Splitter.on(',').omitEmptyStrings().trimResults().split(replacements);
            }

            if (compare < 0) {
                low = middle + 1;
            } else if (compare > 0) {
                high = middle - 1;
            } else {
                assert false; // compare == 0 already handled above
                return null;
            }
        }

        return null;
    }
}
