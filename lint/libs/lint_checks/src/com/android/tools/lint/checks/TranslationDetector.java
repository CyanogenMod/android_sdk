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

import static com.android.tools.lint.detector.api.LintConstants.ATTR_NAME;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_TRANSLATABLE;
import static com.android.tools.lint.detector.api.LintConstants.TAG_STRING;
import static com.android.tools.lint.detector.api.LintConstants.TAG_STRING_ARRAY;

import com.android.annotations.VisibleForTesting;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
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
 */
public class TranslationDetector extends ResourceXmlDetector {
    @VisibleForTesting
    static boolean COMPLETE_REGIONS =
            System.getenv("ANDROID_LINT_COMPLETE_REGIONS") != null; //$NON-NLS-1$

    private static final Pattern lANGUAGE_PATTERN = Pattern.compile("^[a-z]{2}$"); //$NON-NLS-1$
    private static final Pattern REGION_PATTERN = Pattern.compile("^r([A-Z]{2})$"); //$NON-NLS-1$

    /** Are all translations complete? */
    public static final Issue MISSING = Issue.create(
            "MissingTranslation", //$NON-NLS-1$
            "Checks for incomplete translations where not all strings are translated",
            "If an application has more than one locale, then all the strings declared in " +
            "one language should also be translated in all other languages.\n" +
            "\n" +
            "By default this detector allows regions of a language to just provide a " +
            "subset of the strings and fall back to the standard language strings. " +
            "You can require all regions to provide a full translation by setting the " +
            "environment variable ANDROID_LINT_COMPLETE_REGIONS.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            TranslationDetector.class,
            Scope.ALL_RESOURCES_SCOPE);

    /** Are there extra translations that are "unused" (appear only in specific languages) ? */
    public static final Issue EXTRA = Issue.create(
            "ExtraTranslation", //$NON-NLS-1$
            "Checks for translations that appear to be unused (no default language string)",
            "If a string appears in a specific language translation file, but there is " +
            "no corresponding string in the default locale, then this string is probably " +
            "unused. (It's technically possible that your application is only intended to " +
            "run in a specific locale, but it's still a good idea to provide a fallback.)",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            TranslationDetector.class,
            Scope.ALL_RESOURCES_SCOPE);

    private Set<String> mNames;
    private boolean mIgnoreFile;
    private Map<File, Set<String>> mFileToNames;

    /** Constructs a new {@link TranslationDetector} */
    public TranslationDetector() {
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Speed getSpeed() {
        return Speed.NORMAL;
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

        // Convention seen in various projects
        mIgnoreFile = context.file.getName().startsWith("donottranslate"); //$NON-NLS-1$
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

        boolean reportMissing = context.configuration.isEnabled(MISSING);
        boolean reportExtra = context.configuration.isEnabled(EXTRA);

        // res/strings.xml etc
        String defaultLanguage = "Default";

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
                        && lANGUAGE_PATTERN.matcher(segment).matches()) {
                    language = segment;
                }

                // Add in region
                if (language != null && segment.length() == 3
                        && REGION_PATTERN.matcher(segment).matches()) {
                    language = language + '-' + segment;
                    break;
                }
            }

            if (language == null) {
                language = defaultLanguage;
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

        Set<String> defaultStrings = languageToStrings.get(defaultLanguage);
        if (defaultStrings == null) {
            defaultStrings = new HashSet<String>();
        }

        // Fast check to see if there's no problem: if the default locale set is the
        // same as the all set (meaning there are no extra strings in the other languages)
        // then we can quickly determine if everything is okay by just making sure that
        // each language defines everything. If that's the case they will all have the same
        // string count.
        int stringCount = allStrings.size();
        if (stringCount == defaultStrings.size()) {
            boolean haveError = false;
            for (Map.Entry<String, Set<String>> entry : languageToStrings.entrySet()) {
                Set<String> strings = entry.getValue();
                if (stringCount != strings.size()) {
                    haveError = true;
                    break;
                }
            }
            if (!haveError) {
                return;
            }
        }

        // Do we need to resolve fallback strings for regions that only define a subset
        // of the strings in the language and fall back on the main language for the rest?
        if (!COMPLETE_REGIONS) {
            for (String l : languageToStrings.keySet()) {
                if (l.indexOf('-') != -1) {
                    // Yes, we have regions. Merge all base language string names into each region.
                    for (Map.Entry<String, Set<String>> entry : languageToStrings.entrySet()) {
                        Set<String> strings = entry.getValue();
                        if (stringCount != strings.size()) {
                            String languageRegion = entry.getKey();
                            int regionIndex = languageRegion.indexOf('-');
                            if (regionIndex != -1) {
                                String language = languageRegion.substring(0, regionIndex);
                                Set<String> fallback = languageToStrings.get(language);
                                if (fallback != null) {
                                    strings.addAll(fallback);
                                }
                            }
                        }
                    }
                    // We only need to do this once; when we see the first region we know
                    // we need to do it; once merged we can bail
                    break;
                }
            }
        }

        List<String> languages = new ArrayList<String>(languageToStrings.keySet());
        Collections.sort(languages);
        for (String language : languages) {
            Set<String> strings = languageToStrings.get(language);
            if (defaultLanguage.equals(language)) {
                continue;
            }

            // if strings.size() == stringCount, then this language is defining everything,
            // both all the default language strings and the union of all extra strings
            // defined in other languages, so there's no problem.
            if (stringCount != strings.size()) {
                if (reportMissing) {
                    Set<String> difference = LintUtils.difference(defaultStrings, strings);
                    if (difference.size() > 0) {
                        List<String> sorted = new ArrayList<String>(difference);
                        Collections.sort(sorted);
                        Location location = getLocation(language, parentFolderToLanguage);
                        context.client.report(context, MISSING, location,
                            String.format("Locale %1$s is missing translations for: %2$s",
                                language, LintUtils.formatList(sorted, 4)), null);
                    }
                }

                if (reportExtra) {
                    Set<String> difference = LintUtils.difference(strings, defaultStrings);
                    if (difference.size() > 0) {
                        List<String> sorted = new ArrayList<String>(difference);
                        Collections.sort(sorted);
                        Location location = getLocation(language, parentFolderToLanguage);
                        context.client.report(context, EXTRA, location, String.format(
                              "Locale %1$s is translating names not found in default locale: %2$s",
                              language, LintUtils.formatList(sorted, 4)), null);
                    }
                }
            }
        }
    }

    private Location getLocation(String language, Map<File, String> parentFolderToLanguage) {
        Location location = null;
        for (Entry<File, String> e : parentFolderToLanguage.entrySet()) {
            if (e.getValue().equals(language)) {
                // Use the location of the parent folder for this language
                location = new Location(e.getKey(), null, null);
                break;
            }
        }
        return location;
    }

    @Override
    public void visitElement(Context context, Element element) {
        if (mIgnoreFile) {
            return;
        }

        Attr attribute = element.getAttributeNode(ATTR_NAME);
        if (attribute == null || attribute.getValue().length() == 0) {
            context.client.report(context, MISSING, context.getLocation(element),
                    "Missing name attribute in <string> declaration", null);
        } else {
            String name = attribute.getValue();

            Attr translatable = element.getAttributeNode(ATTR_TRANSLATABLE);
            if (translatable != null && !Boolean.valueOf(translatable.getValue())) {
                return;
            }

            // Check for duplicate name definitions? No, because there can be
            // additional customizations like product=
            //if (mNames.contains(name)) {
            //    context.mClient.report(ISSUE, context.getLocation(attribute),
            //        String.format("Duplicate name %1$s, already defined earlier in this file",
            //            name));
            //}

            mNames.add(name);

            // TBD: Also make sure that the strings are not empty or placeholders?
        }
    }
}
