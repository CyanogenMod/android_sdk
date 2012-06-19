/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static com.android.tools.lint.detector.api.LintConstants.TAG_STRING;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.base.Splitter;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Check which looks for likely typos in Strings.
 * <p>
 * TODO:
 * <ul>
 * <li> Add check of Java String literals too!
 * <li> Add support for other languages
 * </ul>
 */
public class TypoDetector extends ResourceXmlDetector {
    private TypoLookup mLookup;
    private boolean mInitialized;

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "Typos", //$NON-NLS-1$
            "Looks for typos in messages",

            "This check looks through the string definitions, and if it finds any words " +
            "that look like likely misspellings, they are flagged.",
            Category.MESSAGES,
            7,
            Severity.WARNING,
            TypoDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    private boolean mIgnore;

    /** Constructs a new detector */
    public TypoDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (!mInitialized) {
            mInitialized = true;
            mLookup = TypoLookup.get(context.getClient());
        }

        if (mLookup == null) {
            mIgnore = true;
        } else {
            String parent = context.file.getParentFile().getName();
            if (parent.equals("values")) { //$NON-NLS-1$
                mIgnore = false;
            } else {
                mIgnore = true;
                // Is this an English language file?
                for (String qualifier : Splitter.on('-').split(parent)) {
                    if ("en".equals(qualifier)) { //$NON-NLS-1$
                        mIgnore = false;
                        break;
                    }
                }
            }
        }
    }

    @Override
    public @NonNull Speed getSpeed() {
        return Speed.NORMAL;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_STRING);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mIgnore) {
            return;
        }

        visit(context, element);
    }

    private void visit(XmlContext context, Node node) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            // TODO: Figure out how to deal with entities
            check(context, node, node.getTextContent());
        } else {
            NodeList children = node.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                visit(context, children.item(i));
            }
        }
    }

    private void check(XmlContext context, Node node, String text) {
        int max = text.length();
        int index = 0;
        while (index < max) {
            while (index < max && !Character.isLetter(text.charAt(index))) {
                index++;
            }
            if (index == max) {
                return;
            }
            int begin = index;
            index++;
            while (index < max && Character.isLetter(text.charAt(index))) {
                index++;
            }
            int end = index;
            Iterable<String> replacements = mLookup.getTypos(text, begin, end);
            if (replacements != null) {
                String word = text.substring(begin, end);

                String first = null;
                String message;
                Iterator<String> iterator = replacements.iterator();
                if (iterator.hasNext()) {
                    boolean isCapitalized = Character.isUpperCase(word.charAt(0));
                    StringBuilder sb = new StringBuilder();
                    for (String replacement : replacements) {
                        if (first == null) {
                            first = replacement;
                        }
                        if (sb.length() > 0) {
                            sb.append(" or ");
                        }
                        sb.append('"');
                        if (isCapitalized) {
                            sb.append(Character.toUpperCase(replacement.charAt(0))
                                    + replacement.substring(1));
                        } else {
                            sb.append(replacement);
                        }
                        sb.append('"');
                    }

                    if (first != null && first.equalsIgnoreCase(word)) {
                        message = String.format(
                                "\"%1$s\" is usually capitalized as \"%2$s\"",
                                word, first);
                    } else {
                        message = String.format(
                                "\"%1$s\" is a common misspelling; did you mean %2$s ?",
                                word, sb.toString());
                    }
                } else {
                    message = String.format("\"%1$s\" is a common misspelling", word);
                }

                context.report(ISSUE, node, context.getLocation(node, begin, end), message, null);
            }

            index = end + 1;
        }
    }

    /** Returns the suggested replacements, if any, for the given typo. The error
     * message <b>must</b> be one supplied by lint.
     *
     * @param errorMessage the error message
     * @return a list of replacement words suggested by the error message
     */
    @Nullable
    public static List<String> getSuggestions(@NonNull String errorMessage) {
        // The words are all in quotes; the first word is the misspelling,
        // the other words are the suggested replacements
        List<String> words = new ArrayList<String>();
        // Skip the typo
        int index = errorMessage.indexOf('"');
        index = errorMessage.indexOf('"', index + 1);
        index++;

        while (true) {
            index = errorMessage.indexOf('"', index);
            if (index == -1) {
                break;
            }
            index++;
            int start = index;
            index = errorMessage.indexOf('"', index);
            if (index == -1) {
                index = errorMessage.length();
            }
            words.add(errorMessage.substring(start, index));
            index++;
        }

        return words;
    }

    /**
     * Returns the typo word in the error message from this detector
     *
     * @param errorMessage the error message produced earlier by this detector
     * @return the typo
     */
    @Nullable
    public static String getTypo(@NonNull String errorMessage) {
        // The words are all in quotes
        int index = errorMessage.indexOf('"');
        int start = index + 1;
        index = errorMessage.indexOf('"', start);
        if (index != -1) {
            return errorMessage.substring(start, index);
        }

        return null;
    }
}
