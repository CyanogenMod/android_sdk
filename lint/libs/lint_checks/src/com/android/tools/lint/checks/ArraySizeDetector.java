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
import static com.android.tools.lint.detector.api.LintConstants.TAG_ARRAY;
import static com.android.tools.lint.detector.api.LintConstants.TAG_INTEGER_ARRAY;
import static com.android.tools.lint.detector.api.LintConstants.TAG_STRING_ARRAY;

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
import com.android.util.Pair;

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
import java.util.Set;

/**
 * Checks for arrays with inconsistent item counts
 */
public class ArraySizeDetector extends ResourceXmlDetector {

    /** Are there differences in how many array elements are declared? */
    public static final Issue INCONSISTENT = Issue.create(
            "InconsistentArrays", //$NON-NLS-1$
            "Checks for inconsistencies in the number of elements in arrays",
            "When an array is translated in a different locale, it should normally have " +
            "the same number of elements as the original array. When adding or removing " +
            "elements to an array, it is easy to forget to update all the locales, and this " +
            "lint warning finds inconsistencies like these.\n" +
            "\n" +
            "Note however that there may be cases where you really want to declare a " +
            "different number of array items in each configuration (for example where " +
            "the array represents available options, and those options differ for " +
            "different layout orientations and so on), so use your own judgement to " +
            "decide if this is really an error.\n" +
            "\n" +
            "You can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            ArraySizeDetector.class,
            Scope.ALL_RESOURCES_SCOPE);

    private Map<File, Pair<String, Integer>> mFileToArrayCount;

    /** Constructs a new {@link ArraySizeDetector} */
    public ArraySizeDetector() {
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
                TAG_ARRAY,
                TAG_STRING_ARRAY,
                TAG_INTEGER_ARRAY
        });
    }

    @Override
    public void beforeCheckProject(Context context) {
        mFileToArrayCount = new HashMap<File, Pair<String,Integer>>(30);
    }

    @Override
    public void afterCheckProject(Context context) {
        // Check that all arrays for the same name have the same number of translations

        Set<String> alreadyReported = new HashSet<String>();
        Map<String, Integer> countMap = new HashMap<String, Integer>();
        Map<String, File> fileMap = new HashMap<String, File>();

        // Process the file in sorted file order to ensure stable output
        List<File> keys = new ArrayList<File>(mFileToArrayCount.keySet());
        Collections.sort(keys);

        for (File file : keys) {
            Pair<String, Integer> pair = mFileToArrayCount.get(file);
            String name = pair.getFirst();
            if (alreadyReported.contains(name)) {
                continue;
            }
            Integer count = pair.getSecond();

            Integer current = countMap.get(name);
            if (current == null) {
                countMap.put(name, count);
                fileMap.put(name, file);
            } else if (!count.equals(current)) {
                //Location location = getLocation(language, parentFolderToLanguage);
                String thisName = file.getParentFile().getName() + File.separator + file.getName();
                File otherFile = fileMap.get(name);
                Location location = new Location(otherFile, null, null);
                location.setSecondary(new Location(file, null, null));
                String otherName = otherFile.getParentFile().getName() + File.separator
                        + otherFile.getName();
                context.client.report(context, INCONSISTENT, location,
                    String.format(
                     "Array %1$s has an inconsistent number of items (%2$d in %3$s, %4$d in %5$s)",
                     name, count, thisName, current, otherName), null);
                alreadyReported.add(name);
            }
        }

        mFileToArrayCount = null;
    }

    @Override
    public void visitElement(Context context, Element element) {
        Attr attribute = element.getAttributeNode(ATTR_NAME);
        if (attribute == null || attribute.getValue().length() == 0) {
            context.client.report(context, INCONSISTENT, context.getLocation(element),
                String.format("Missing name attribute in %1$s declaration", element.getTagName()),
                null);
        } else {
            String name = attribute.getValue();
            int childCount = LintUtils.getChildCount(element);
            mFileToArrayCount.put(context.file, Pair.of(name, childCount));
        }
    }
}
