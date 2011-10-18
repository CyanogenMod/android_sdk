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

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks for incomplete translations - e.g. keys that are only present in some
 * locales but not all.
 * <p>
 * TODO: Check that {@code <string-array>} declarations all have the same number of elements!
 */
public class TranslationDetector extends ResourceXmlDetector {
    private static final String TAG_STRING = "string";              //$NON-NLS-1$
    private static final String TAG_STRING_ARRAY = "string-array";  //$NON-NLS-1$
    private static final String ATTR_NAME = "name";                 //$NON-NLS-1$
    private static final String ATTR_TRANSLATABLE = "translatable"; //$NON-NLS-1$

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "IncompleteTranslation", //$NON-NLS-1$
            "Checks for incomplete translations where not all strings are translated",
            "If an application has more than one locale, then all the strings declared in " +
            "one language should also be translated in all other languages.",
            CATEGORY_CORRECTNESS, 8, Severity.ERROR);

    private Set<String> mNames;
    private Map<File, Set<String>> mFileToNames;

    /** Constructs a new {@link TranslationDetector} */
    public TranslationDetector() {
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Issue[] getIssues() {
        return new Issue[] { ISSUE };
    }

    @Override
    public Speed getSpeed() {
        return Speed.NORMAL;
    }

    @Override
    public Scope getScope() {
        return Scope.RESOURCES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(new String[] {
                TAG_STRING,
                TAG_STRING_ARRAY
        });
    }

    @Override
    public void beforeCheckProject(Context context) {
        mFileToNames = new HashMap<File, Set<String>>();
    }

    @Override
    public void beforeCheckFile(Context context) {
        mNames = new HashSet<String>();
    }

    @Override
    public void afterCheckFile(Context context) {
        // Store this layout's set of ids for full project analysis in afterCheckProject
        mFileToNames.put(context.file, mNames);

        mNames = null;
    }

    @Override
    public void afterCheckProject(Context context) {
        // NOTE - this will look for the presence of translation strings.
        // If you create a resource folder but don't actually place a file in it
        // we won't detect that, but it seems like a smaller problem.

        checkTranslations(context);

        mFileToNames = null;
    }

    private void checkTranslations(Context context) {
        // Only one file defining strings? If so, no problems.
        Set<File> files = mFileToNames.keySet();
        if (files.size() == 1) {
            return;
        }

        Set<File> parentFolders = new HashSet<File>();
        for (File file : files) {
            parentFolders.add(file.getParentFile());
        }
        if (parentFolders.size() == 1) {
            // Only one language - no problems.
            return;
        }

        Pattern languagePattern = Pattern.compile("^[a-z]{2}$"); //$NON-NLS-1$
        Pattern regionPattern = Pattern.compile("^r([A-Z]{2})$"); //$NON-NLS-1$

        Map<File, String> parentFolderToLanguage = new HashMap<File, String>();
        for (File parent : parentFolders) {
            String name = parent.getName();

            // Look up the language for this folder.

            String[] segments = name.split("-"); //$NON-NLS-1$

            // TODO: To get an accurate answer, this should later do a
            //   FolderConfiguration.getConfig(String[] folderSegments)
            // to obtain a FolderConfiguration, then call
            // getLanguageQualifier() on it, and if not null, call getValue() to get the
            // actual language value.
            // However, we don't have ide_common on the build path for lint, so for now
            // use a simple guess about what constitutes a language qualifier here:

            String language = null;
            for (String segment : segments) {
                // Language
                if (language == null && segment.length() == 2
                        && languagePattern.matcher(segment).matches()) {
                    language = segment;
                }

                // Add in region
                if (language != null && segment.length() == 3
                        && regionPattern.matcher(segment).matches()) {
                    language = language + '-' + segment;
                    break;
                }
            }

            if (language == null) {
                // res/strings.xml etc
                language = "Default";
            }

            parentFolderToLanguage.put(parent, language);
        }

        int languageCount = parentFolderToLanguage.values().size();
        if (languageCount <= 1) {
            // At most one language -- no problems.
            return;
        }

        // Merge together the various files building up the translations for each language
        Map<String, Set<String>> languageToStrings =
                new HashMap<String, Set<String>>(languageCount);
        Set<String> allStrings = new HashSet<String>(200);
        for (File file : files) {
            String language = parentFolderToLanguage.get(file.getParentFile());
            assert language != null : file.getParent();
            Set<String> fileStrings = mFileToNames.get(file);

            Set<String> languageStrings = languageToStrings.get(language);
            if (languageStrings == null) {
                // We don't need a copy; we're done with the string tables now so we
                // can modify them
                languageToStrings.put(language, fileStrings);
            } else {
                languageStrings.addAll(fileStrings);
            }
            allStrings.addAll(fileStrings);
        }

        // See if all languages define the same number of strings as the union
        // of all the strings; if they do, there is no problem.
        int stringCount = allStrings.size();
        for (Map.Entry<String, Set<String>> entry : languageToStrings.entrySet()) {
            Set<String> strings = entry.getValue();
            if (stringCount != strings.size()) {
                String language = entry.getKey();

                // Found a discrepancy in the count for different languages.
                // Produce a full report.
                Set<String> difference = difference(allStrings, strings);
                List<String> sorted = new ArrayList<String>(difference);
                Collections.sort(sorted);

                Location location = null;
                for (Entry<File, String> e : parentFolderToLanguage.entrySet()) {
                    if (e.getValue().equals(language)) {
                        // Use the location of the parent folder for this language
                        location = new Location(e.getKey(), null, null);
                        break;
                    }
                }
                context.toolContext.report(ISSUE, location,
                    String.format("Language %1$s is missing translations for the names %2$s",
                        language, formatList(sorted, 4)));
            }
        }
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        HashSet<String> copy = new HashSet<String>(a);
        copy.removeAll(b);
        return copy;
    }

    static String formatList(List<String> strings, int maxItems) {
        StringBuilder sb = new StringBuilder(20 * strings.size());

        for (int i = 0, n = strings.size(); i < n; i++) {
            if (sb.length() > 0) {
                sb.append(", "); //$NON-NLS-1$
            }
            sb.append(strings.get(i));

            if (i == maxItems - 1 && n > maxItems) {
                sb.append(String.format("... (%1$d more)", n - i - 1));
                break;
            }
        }

        return sb.toString();
    }

    @Override
    public void visitElement(Context context, Element element) {
        Attr attribute = element.getAttributeNode(ATTR_NAME);
        if (attribute == null || attribute.getValue().length() == 0) {
            context.toolContext.report(ISSUE, context.getLocation(element),
                    "Missing name attribute in <string> declaration");
        } else {
            String name = attribute.getValue();

            Attr translatable = element.getAttributeNode(ATTR_TRANSLATABLE);
            if (translatable != null && !Boolean.valueOf(translatable.getValue())) {
                return;
            }

            // TODO: Consider string-arrays as well?

            // Check for duplicate name definitions? No, because there can be
            // additional customizations like product=
            //if (mNames.contains(name)) {
            //    context.toolContext.report(ISSUE, context.getLocation(attribute),
            //        String.format("Duplicate name %1$s, already defined earlier in this file",
            //            name));
            //}

            mNames.add(name);

            // TBD: Also make sure that the strings are not empty or placeholders?
        }
    }
}
