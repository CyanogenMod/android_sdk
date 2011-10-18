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
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Checks for duplicate ids within a layout and within an included layout
 */
public class DuplicateIdDetector extends LayoutDetector {
    private Set<String> mIds;
    private Map<File, Set<String>> mFileToIds;
    private Map<File, List<String>> mIncludes;

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "DuplicateIds", //$NON-NLS-1$
            "Checks for duplicate ids within a single layout or within an include hierarchy",
            "It's okay for two independent layouts to use the same ids. However, within " +
            "a single layout (including the case where two separate layouts are fused " +
            "together with an include tag) the ids should be unique such that the" +
            "Activity#findViewById() method can work predictably.",
            CATEGORY_LAYOUT, 7, Severity.WARNING);

    /** Constructs a duplicate id check */
    public DuplicateIdDetector() {
    };


    @Override
    public Issue[] getIssues() {
        return new Issue[] { ISSUE };
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.MENU;
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Scope getScope() {
        // TODO: Split this detector in half, since single-layout duplicates can be checked
        // quickly.
        return Scope.RESOURCES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(INCLUDE);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mIds = new HashSet<String>();
    }

    @Override
    public void afterCheckFile(Context context) {
        // Store this layout's set of ids for full project analysis in afterCheckProject
        mFileToIds.put(context.file, mIds);

        mIds = null;
    }

    @Override
    public void beforeCheckProject(Context context) {
        mFileToIds = new HashMap<File, Set<String>>();
        mIncludes = new HashMap<File, List<String>>();
    }

    @Override
    public void afterCheckProject(Context context) {
        // Look for duplicates
        if (mIncludes.size() > 0) {
            // Traverse all the include chains and ensure that there are no duplicates
            // across.
            // First perform a topological sort such such
            checkForIncludeDuplicates(context);
        }

        mFileToIds = null;
        mIncludes = null;
    }

    @Override
    public void visitElement(Context context, Element element) {
        // Record include graph such that we can look for inter-layout duplicates after the
        // project has been fully checked

        String layout = element.getAttribute(ATTR_LAYOUT); // NOTE: Not in android: namespace
        if (layout.startsWith(VALUE_LAYOUT_PREFIX)) { // Ignore @android:layout/ layouts
            layout = layout.substring(VALUE_LAYOUT_PREFIX.length());

            List<String> to = mIncludes.get(context.file);
            if (to == null) {
                to = new ArrayList<String>();
                mIncludes.put(context.file, to);
            }
            to.add(layout);
        }
    }

    private void checkForIncludeDuplicates(Context context) {
        // Consider this scenario:
        //     first/foo.xml: include @layout/second
        //     first-land/foo.xml: define @+id/foo
        //     second-land/bar.xml define @+id/bar
        //     second-port/bar.xml define @+id/foo
        // Here there's no problem, because even though @layout/first includes @layout/second,
        // the only duplicate is "foo" which appears only in the combination first-land and
        // second-port which won't be matched up together.
        // In this analysis we won't go that far; we'll just look at the OVERALL set of
        // includes. In other words, we'll consider the set of ids defined by "first" to
        // be {"foo"}, and the set of ids defined by "second" to be {"foo","bar"}, and
        // so there is a potential conflict.

        // Map from layout resource name (instead of file) to referenced layouts.
        // Note: Unlike mIncludes, this merges all the configurations for a single layout
        Map<String, Set<String>> resourceToLayouts =
                new HashMap<String, Set<String>>(mIncludes.size());
        Map<String, Set<String>> resourceToIds =
                new HashMap<String, Set<String>>(mIncludes.size());

        for (Entry<File, List<String>> entry : mIncludes.entrySet()) {
            File file = entry.getKey();
            String from = getLayoutName(file);

            // Merge include lists
            List<String> layouts = entry.getValue();
            Set<String> set = resourceToLayouts.get(from);
            if (set == null) {
                resourceToLayouts.put(from, new HashSet<String>(layouts));
            } else {
                set.addAll(layouts);
            }
        }

        // Merge id maps
        for (Entry<File, Set<String>> entry : mFileToIds.entrySet()) {
            File file = entry.getKey();
            String from = getLayoutName(file);
            Set<String> ids = entry.getValue();
            if (ids != null) {
                Set<String> set = resourceToIds.get(from);
                if (set == null) {
                    // I might be able to just reuse the set instance here instead of duplicating
                    resourceToIds.put(from, new HashSet<String>(ids));
                } else {
                    set.addAll(ids);
                }
            }
        }

        // Set of layouts that are included from somewhere else. We will use
        Set<String> included = new HashSet<String>();
        for (Set<String> s : resourceToLayouts.values()) {
            included.addAll(s);
        }

        // Compute the set of layouts which include some other layouts, but which are not
        // included themselves, meaning they are the "roots" to start searching through
        // include chains from
        Set<String> entryPoints = new HashSet<String>(resourceToLayouts.keySet());
        entryPoints.removeAll(included);

        // Perform DFS on the include graph and look for a cycle; if we find one, produce
        // a chain of includes on the way back to show to the user
        HashMap<String, Set<String>> mergedIds = new HashMap<String, Set<String>>();
        Set<String> visiting = new HashSet<String>();
        for (String from : entryPoints) {
            visiting.clear();
            getMergedIds(context, from, visiting, resourceToLayouts, resourceToIds, mergedIds);
        }
    }

    private String getLayoutName(File file) {
        String name = file.getName();
        int dotIndex = name.indexOf('.');
        if (dotIndex != -1) {
            name = name.substring(0, dotIndex);
        }
        return name;
    }

    /**
     * Computes the complete set of ids in a layout (including included layouts,
     * transitively) and emits warnings when it detects that there is a
     * duplication
     */
    private Set<String> getMergedIds(
            Context context,
            String from,
            Set<String> visiting,
            Map<String, Set<String>> resourceToLayouts,
            Map<String, Set<String>> resourceToIds,
            Map<String, Set<String>> mergedIds) {

        Set<String> merged = mergedIds.get(from);
        if (merged == null) {
            visiting.add(from);

            Set<String> currentIds = resourceToIds.get(from);
            if (currentIds != null && currentIds.size() > 0) {
                merged = new HashSet<String>(currentIds);
            } else {
                merged = new HashSet<String>();
            }
            Set<String> includes = resourceToLayouts.get(from);
            if (includes != null && includes.size() > 0) {
                for (String include : includes) {
                    if (!visiting.contains(include)) {
                        Set<String> otherIds = getMergedIds(context, include, visiting,
                                resourceToLayouts, resourceToIds, mergedIds);
                        // Look for overlap
                        for (String id : otherIds) {
                            if (merged.contains(id)) {
                                // Find the (first) file among the various configuration variations
                                // which defines the id
                                File first = null;
                                File second = null;
                                for (Map.Entry<File, Set<String>> entry : mFileToIds.entrySet()) {
                                    File file = entry.getKey();
                                    String name = getLayoutName(file);
                                    if (name.equals(from)) {
                                        Set<String> fileIds = entry.getValue();
                                        if (fileIds.contains(id)) {
                                            first = file;
                                        }
                                    }
                                    if (name.equals(include)) {
                                        Set<String> fileIds = entry.getValue();
                                        if (fileIds.contains(id)) {
                                            second = file;
                                        }
                                    }
                                }
                                if (first == null) {
                                    for (Map.Entry<File, List<String>> entry
                                            : mIncludes.entrySet()) {
                                        File file = entry.getKey();
                                        String name = getLayoutName(file);
                                        if (name.equals(from)) {
                                            first = file;
                                        }
                                    }
                                }

                                String includer = second != null ? second.getName() : include;
                                List<String> chain = new ArrayList<String>();
                                chain.add(from);
                                findOrigin(chain, from, id, new HashSet<String>(),
                                        resourceToLayouts, resourceToIds);
                                String msg = null;
                                if (chain.size() > 2) { // < 2: it's a directly include & obvious
                                    StringBuilder sb = new StringBuilder();
                                    for (String layout : chain) {
                                        if (sb.length() > 0) {
                                            sb.append(" => ");
                                        }
                                        sb.append(layout);
                                    }
                                    msg = String.format(
                                            "Duplicate id %1$s, already defined in layout %2$s which is included in this layout (%3$s)",
                                            id, includer, sb.toString());
                                } else {
                                    msg = String.format(
                                            "Duplicate id %1$s, already defined in layout %2$s which is included in this layout",
                                            id, includer);
                                }

                                Location location = new Location(first, null, null);
                                if (second != null) {
                                    // Also record the secondary location
                                    location.setSecondary(new Location(second, null, null));
                                }
                                context.toolContext.report(ISSUE, location, msg);
                            } else {
                                merged.add(id);
                            }
                        }
                    }
                }
            }
            mergedIds.put(from, merged);
            visiting.remove(from);
        }

        return merged;
    }

    /**
     * Compute the include chain which provided id into this layout. We could
     * have tracked this while we were already performing a depth first search,
     * but we're choosing to be faster before we know there's an error and take
     * ore time to produce diagnostics if an actual error is found.
     */
    private boolean findOrigin(
            List<String> chain,
            String from,
            String id,
            Set<String> visiting,
            Map<String, Set<String>> resourceToLayouts,
            Map<String, Set<String>> resourceToIds) {
        visiting.add(from);

        Set<String> includes = resourceToLayouts.get(from);
        if (includes != null && includes.size() > 0) {
            for (String include : includes) {
                if (visiting.contains(include)) {
                    return false;
                }

                Set<String> ids = resourceToIds.get(include);
                if (ids != null && ids.contains(id)) {
                    chain.add(include);
                    return true;
                }

                if (findOrigin(chain, include, id, visiting, resourceToLayouts, resourceToIds)) {
                    chain.add(include);
                    return true;
                }
            }
        }

        visiting.remove(from);

        return false;
    }

    @Override
    public void visitAttribute(Context context, Attr attribute) {
        assert attribute.getLocalName().equals(ATTR_ID);
        String id = attribute.getValue();
        if (mIds.contains(id)) {
            context.toolContext.report(ISSUE, context.getLocation(attribute),
                    String.format("Duplicate id %1$s, already defined earlier in this layout",
                            id));
        } else if (id.startsWith("@+id/")) { //$NON-NLS-1$
            mIds.add(id);
        }
    }
}
