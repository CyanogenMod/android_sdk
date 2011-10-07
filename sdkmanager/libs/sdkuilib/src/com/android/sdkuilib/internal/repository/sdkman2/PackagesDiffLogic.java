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

package com.android.sdkuilib.internal.repository.sdkman2;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.repository.IPackageVersion;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.PlatformPackage;
import com.android.sdklib.internal.repository.PlatformToolPackage;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdklib.internal.repository.ToolPackage;
import com.android.sdklib.internal.repository.Package.UpdateInfo;
import com.android.sdkuilib.internal.repository.UpdaterData;
import com.android.sdkuilib.internal.repository.sdkman2.PkgItem.PkgState;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class that separates the logic of package management from the UI
 * so that we can test it using head-less unit tests.
 */
class PackagesDiffLogic {
    private final PackageLoader mPackageLoader;
    private final UpdaterData mUpdaterData;
    private boolean mFirstLoadComplete = true;

    public PackagesDiffLogic(UpdaterData updaterData) {
        mUpdaterData = updaterData;
        mPackageLoader = new PackageLoader(updaterData);
    }

    public PackageLoader getPackageLoader() {
        return mPackageLoader;
    }

    /**
     * Removes all the internal state and resets the object.
     * Useful for testing.
     */
    public void clear() {
        mFirstLoadComplete = true;
        mOpApi.clear();
        mOpSource.clear();
    }

    /** Return mFirstLoadComplete and resets it to false.
     * All following calls will returns false. */
    public boolean isFirstLoadComplete() {
        boolean b = mFirstLoadComplete;
        mFirstLoadComplete = false;
        return b;
    }

    /**
     * Mark all new and update PkgItems as checked.
     * <p/>
     * Try to be smart and check whether any platform is installed.
     * The heuristic is:
     * <ul>
     * <li> For extras with no platform dependency, or for tools & platform-tools,
     *          just select new and updates.
     * <li> For anything that depends on a platform:
     * <li> Always select the top platform and all its packages.
     * <li> If some platform is partially installed, selected anything new/update for it.
     * </ul>
     */
    public void checkNewUpdateItems(boolean selectNew, boolean selectUpdates) {
        int maxApi = 0;
        Set<Integer> installedPlatforms = new HashSet<Integer>();
        Map<Integer, List<PkgItem>> platformItems = new HashMap<Integer, List<PkgItem>>();

        // sort items in platforms... directly deal with items with no platform
        for (PkgItem item : getAllPkgItems(true /*byApi*/, true /*bySource*/)) {
            if (!item.hasCompatibleArchive()) {
                // Ignore items that have no archive compatible with the current platform.
                continue;
            }

            // Get the main package's API level. We don't need to look at the updates
            // since by definition they should target the same API level.
            int api = 0;
            Package p = item.getMainPackage();
            if (p instanceof IPackageVersion) {
                api = ((IPackageVersion) p).getVersion().getApiLevel();
            }

            if (api > 0) {
                maxApi = Math.max(maxApi, api);

                // keep track of what platform is currently installed and its items
                if (item.getState() == PkgState.INSTALLED) {
                    installedPlatforms.add(api);
                }
                List<PkgItem> items = platformItems.get(api);
                if (items == null) {
                    platformItems.put(api, items = new ArrayList<PkgItem>());
                }
                items.add(item);
            } else {
                // not a plaform package...
                if ((selectNew && item.getState() == PkgState.NEW) ||
                        (selectUpdates && item.hasUpdatePkg())) {
                    item.setChecked(true);
                }
            }
        }

        // If there are some platforms installed. Pickup anything new in them.
        for (Integer api : installedPlatforms) {
            List<PkgItem> items = platformItems.get(api);
            if (items != null) {
                for (PkgItem item : items) {
                    if ((selectNew && item.getState() == PkgState.NEW) ||
                            (selectUpdates && item.hasUpdatePkg())) {
                        item.setChecked(true);
                    }
                }
            }
        }

        // Whether we have platforms installed or not, select everything from the top platform.
        if (maxApi > 0) {
            List<PkgItem> items = platformItems.get(maxApi);
            if (items != null) {
                for (PkgItem item : items) {
                    if ((selectNew && item.getState() == PkgState.NEW) ||
                            (selectUpdates && item.hasUpdatePkg())) {
                        item.setChecked(true);
                    }
                }
            }
        }
    }

    /**
     * Mark all PkgItems as not checked.
     */
    public void uncheckAllItems() {
        for (PkgItem item : getAllPkgItems(true /*byApi*/, true /*bySource*/)) {
            item.setChecked(false);
        }
    }

    /**
     * An update operation, customized to either sort by API or sort by source.
     */
    abstract class UpdateOp {
        private final Set<SdkSource> mVisitedSources = new HashSet<SdkSource>();
        protected final List<PkgCategory> mCategories = new ArrayList<PkgCategory>();

        /** Removes all internal state. */
        public void clear() {
            mVisitedSources.clear();
            mCategories.clear();
        }

        /** Retrieve the sorted category list. */
        public List<PkgCategory> getCategories() {
            return mCategories;
        }

        /** Retrieve the category key for the given package, either local or remote. */
        public abstract Object getCategoryKey(Package pkg);

        /** Modified {@code currentCategories} to add default categories. */
        public abstract void addDefaultCategories();

        /** Creates the category for the given key and returns it. */
        public abstract PkgCategory createCategory(Object catKey);

        /** Sorts the category list (but not the items within the categories.) */
        public abstract void sortCategoryList();

        /** Called after items of a given category have changed. Used to sort the
         * items and/or adjust the category name. */
        public abstract void postCategoryItemsChanged();

        /** Add the new package or merge it as an update or does nothing if this package
         * is already part of the category items.
         * Returns true if the category item list has changed. */
        public abstract boolean mergeNewPackage(Package newPackage, PkgCategory cat);

        public void updateStart() {
            mVisitedSources.clear();

            // Note that default categories are created after the unused ones so that
            // the callback can decide whether they should be marked as unused or not.
            for (PkgCategory cat : mCategories) {
                cat.setUnused(true);
            }

            addDefaultCategories();
        }

        public boolean updateSourcePackages(SdkSource source, Package[] newPackages) {
            if (newPackages.length > 0) {
                mVisitedSources.add(source);
            }
            if (source == null) {
                return processLocals(this, newPackages);
            } else {
                return processSource(this, source, newPackages);
            }
        }

        public boolean updateEnd() {
            boolean hasChanged = false;

            // Remove unused categories
            synchronized (mCategories) {
                for (Iterator<PkgCategory> catIt = mCategories.iterator(); catIt.hasNext(); ) {
                    PkgCategory cat = catIt.next();
                    if (cat.isUnused()) {
                        catIt.remove();
                        hasChanged  = true;
                        continue;
                    }

                    // Remove all *remote* items which obsolete source we have not been visited.
                    // This detects packages which have disappeared from a remote source during an
                    // update and removes from the current list.
                    // Locally installed item are never removed.
                    for (Iterator<PkgItem> itemIt = cat.getItems().iterator();
                            itemIt.hasNext(); ) {
                        PkgItem item = itemIt.next();
                        if (item.getState() == PkgState.NEW &&
                                !mVisitedSources.contains(item.getSource())) {
                            itemIt.remove();
                            hasChanged  = true;
                        }
                    }
                }
            }
            return hasChanged;
        }

    }

    private final UpdateOpApi    mOpApi    = new UpdateOpApi();
    private final UpdateOpSource mOpSource = new UpdateOpSource();

    public List<PkgCategory> getCategories(boolean displayIsSortByApi) {
        return displayIsSortByApi ? mOpApi.getCategories() : mOpSource.getCategories();
    }

    public List<PkgItem> getAllPkgItems(boolean byApi, boolean bySource) {
        List<PkgItem> items = new ArrayList<PkgItem>();

        if (byApi) {
            List<PkgCategory> cats = getCategories(true /*displayIsSortByApi*/);
            synchronized (cats) {
                for (PkgCategory cat : cats) {
                    items.addAll(cat.getItems());
                }
            }
        }

        if (bySource) {
            List<PkgCategory> cats = getCategories(false /*displayIsSortByApi*/);
            synchronized (cats) {
                for (PkgCategory cat : cats) {
                    items.addAll(cat.getItems());
                }
            }
        }

        return items;
    }

    public void updateStart() {
        mOpApi.updateStart();
        mOpSource.updateStart();
    }

    public boolean updateSourcePackages(
            boolean displayIsSortByApi,
            SdkSource source,
            Package[] newPackages) {

        boolean apiListChanged = mOpApi.updateSourcePackages(source, newPackages);
        boolean sourceListChanged = mOpSource.updateSourcePackages(source, newPackages);
        return displayIsSortByApi ? apiListChanged : sourceListChanged;
    }

    public boolean updateEnd(boolean displayIsSortByApi) {
        boolean apiListChanged = mOpApi.updateEnd();
        boolean sourceListChanged = mOpSource.updateEnd();
        return displayIsSortByApi ? apiListChanged : sourceListChanged;
    }

    /** Process all local packages. Returns true if something changed. */
    private boolean processLocals(UpdateOp op, Package[] packages) {
        boolean hasChanged = false;
        Set<Package> newPackages = new HashSet<Package>(Arrays.asList(packages));
        Set<Package> unusedPackages = new HashSet<Package>(newPackages);

        assert newPackages.size() == packages.length;

        // Upgrade NEW items to INSTALLED for any local package we already know about.
        // We can't just change the state of the NEW item to INSTALLED, we also need its
        // installed package/archive information and so we swap them in-place in the items list.

        for (PkgCategory cat : op.getCategories()) {
            List<PkgItem> items = cat.getItems();
            for (int i = 0; i < items.size(); i++) {
                PkgItem item = items.get(i);

                if (item.hasUpdatePkg()) {
                    Package newPkg = setContainsLocalPackage(newPackages, item.getUpdatePkg());
                    if (newPkg != null) {
                        // This item has an update package that is now installed.
                        PkgItem installed = new PkgItem(newPkg, PkgState.INSTALLED);
                        removePackageFromSet(unusedPackages, newPkg);
                        item.removeUpdate();
                        items.add(installed);
                        cat.setUnused(false);
                        hasChanged = true;
                    }
                }

                Package newPkg = setContainsLocalPackage(newPackages, item.getMainPackage());
                if (newPkg != null) {
                    removePackageFromSet(unusedPackages, newPkg);
                    if (item.getState() == PkgState.NEW) {
                        // This item has a main package that is now installed.
                        replace(items, i, new PkgItem(newPkg, PkgState.INSTALLED));
                        cat.setUnused(false);
                        hasChanged = true;
                    }
                }
            }
        }

        // Remove INSTALLED items if their package isn't listed anymore in locals
        for (PkgCategory cat : op.getCategories()) {
            List<PkgItem> items = cat.getItems();
            for (int i = 0; i < items.size(); i++) {
                PkgItem item = items.get(i);

                if (item.getState() == PkgState.INSTALLED) {
                    Package newPkg = setContainsLocalPackage(newPackages, item.getMainPackage());
                    if (newPkg == null) {
                        items.remove(i--);
                        hasChanged = true;
                    }
                }
            }
        }

        // Create new 'installed' items for any local package we haven't processed yet
        for (Package newPackage : unusedPackages) {
            Object catKey = op.getCategoryKey(newPackage);
            PkgCategory cat = findCurrentCategory(op.getCategories(), catKey);

            if (cat == null) {
                // This is a new category. Create it and add it to the list.
                cat = op.createCategory(catKey);
                op.getCategories().add(cat);
                op.sortCategoryList();
            }

            cat.getItems().add(new PkgItem(newPackage, PkgState.INSTALLED));
            cat.setUnused(false);
            hasChanged = true;
        }

        if (hasChanged) {
            op.postCategoryItemsChanged();
        }

        return hasChanged;
    }

    /**
     * Replaces the item at {@code index} in {@code list} with the new {@code obj} element.
     * This uses {@link ArrayList#set(int, Object)} if possible, remove+add otherwise.
     *
     * @return The old item at the same index position.
     * @throws IndexOutOfBoundsException if index out of range (index < 0 || index >= size()).
     */
    private <T> T replace(List<T> list, int index, T obj) {
        if (list instanceof ArrayList<?>) {
            return ((ArrayList<T>) list).set(index, obj);
        } else {
            T old = list.remove(index);
            list.add(index, obj);
            return old;
        }
    }

    /**
     * Checks whether the {@code newPackages} set contains a package that is the
     * same as {@code pkgToFind}.
     * This is based on Package being the same from an install point of view rather than
     * pure object equality.
     * @return The matching package from the {@code newPackages} set or null if not found.
     */
    private Package setContainsLocalPackage(Collection<Package> newPackages, Package pkgToFind) {
        // Most of the time, local packages don't have the exact same hash code
        // as new ones since the objects are similar but not exactly the same,
        // for example their installed OS path cannot match (by definition) so
        // their hash code do not match when used with Set.contains().

        for (Package newPkg : newPackages) {
            // Two packages are the same if they are compatible types,
            // do not update each other and have the same revision number.
            if (pkgToFind.canBeUpdatedBy(newPkg) == UpdateInfo.NOT_UPDATE &&
                    newPkg.getRevision() == pkgToFind.getRevision()) {
                return newPkg;
            }
        }

        return null;
    }

    /**
     * Removes the given package from the set.
     * This is based on Package being the same from an install point of view rather than
     * pure object equality.
     */
    private void removePackageFromSet(Collection<Package> packages, Package pkgToFind) {
        // First try to remove the package based on its hash code. This can fail
        // for a variety of reasons, as explained in setContainsLocalPackage().
        if (packages.remove(pkgToFind)) {
            return;
        }

        for (Package pkg : packages) {
            // Two packages are the same if they are compatible types,
            // or not updates of each other and have the same revision number.
            if (pkgToFind.canBeUpdatedBy(pkg) == UpdateInfo.NOT_UPDATE &&
                    pkg.getRevision() == pkgToFind.getRevision()) {
                packages.remove(pkg);
                // Implementation detail: we can get away with using Collection.remove()
                // whilst in the for iterator because we return right away (otherwise the
                // iterator would complain the collection just changed.)
                return;
            }
        }
    }

    /**
     * Removes any package from the set that is equal or lesser than {@code pkgToFind}.
     * This is based on Package being the same from an install point of view rather than
     * pure object equality.
     * </p>
     * This is a slight variation on {@link #removePackageFromSet(Collection, Package)}
     * where we remove from the set any package that is similar to {@code pkgToFind}
     * and has either the same revision number or a <em>lesser</em> revision number.
     * An example of this use-case is there's an installed local package in rev 5
     * (that is the pkgToFind) and there's a remote package in rev 3 (in the package list),
     * in which case we 'forget' the rev 3 package even exists.
     */
    private void removePackageOrLesserFromSet(Collection<Package> packages, Package pkgToFind) {
        for (Iterator<Package> it = packages.iterator(); it.hasNext(); ) {
            Package pkg = it.next();

            // Two packages are the same if they are compatible types,
            // or not updates of each other and have the same revision number.
            if (pkgToFind.canBeUpdatedBy(pkg) == UpdateInfo.NOT_UPDATE &&
                    pkg.getRevision() <= pkgToFind.getRevision()) {
                it.remove();
            }
        }
    }

    /** Process all remote packages. Returns true if something changed. */
    private boolean processSource(UpdateOp op, SdkSource source, Package[] packages) {
        boolean hasChanged = false;
        // Note: unusedPackages must respect the original packages order. It can't be a set.
        List<Package> unusedPackages = new ArrayList<Package>(Arrays.asList(packages));
        Set<Package> newPackages = new HashSet<Package>(unusedPackages);

        assert source != null;
        assert newPackages.size() == packages.length;

        // Remove any items or updates that are no longer in the source's packages
        for (PkgCategory cat : op.getCategories()) {
            List<PkgItem> items = cat.getItems();
            for (int i = 0; i < items.size(); i++) {
                PkgItem item = items.get(i);

                if (!isSourceCompatible(item, source)) {
                    continue;
                }

                // Try to prune current items that are no longer on the remote site.
                // Installed items have been dealt with the local source, so only
                // change new items here.
                if (item.getState() == PkgState.NEW) {
                    Package newPkg = setContainsLocalPackage(newPackages, item.getMainPackage());
                    if (newPkg == null) {
                        // This package is no longer part of the source.
                        items.remove(i--);
                        hasChanged = true;
                        continue;
                    }
                }

                cat.setUnused(false);
                removePackageOrLesserFromSet(unusedPackages, item.getMainPackage());

                if (item.hasUpdatePkg()) {
                    Package newPkg = setContainsLocalPackage(newPackages, item.getUpdatePkg());
                    if (newPkg != null) {
                        removePackageFromSet(unusedPackages, newPkg);
                    } else {
                        // This update is no longer part of the source
                        item.removeUpdate();
                        hasChanged = true;
                    }
                }
            }
        }

        // Add any new unknown packages
        for (Package newPackage : unusedPackages) {
            Object catKey = op.getCategoryKey(newPackage);
            PkgCategory cat = findCurrentCategory(op.getCategories(), catKey);

            if (cat == null) {
                // This is a new category. Create it and add it to the list.
                cat = op.createCategory(catKey);
                op.getCategories().add(cat);
                op.sortCategoryList();
            }

            // Add the new package or merge it as an update
            hasChanged |= op.mergeNewPackage(newPackage, cat);
        }

        if (hasChanged) {
            op.postCategoryItemsChanged();
        }

        return hasChanged;
    }

    private boolean isSourceCompatible(PkgItem currentItem, SdkSource newItemSource) {
        SdkSource currentSource = currentItem.getSource();

        // Only process items matching the current source.
        if (currentSource == newItemSource) {
            // Object identity, so definitely the same source. Accept it.
            return true;

        } else if (currentSource != null && newItemSource != null &&
                !currentSource.getClass().equals(newItemSource.getClass())) {
            // Both sources don't have the same type (e.g. sdk repository versus add-on repository)
            return false;

        } else if (currentSource != null && currentSource.equals(newItemSource)) {
            // Same source. Accept it.
            return true;

        } else if (currentSource == null && currentItem.getState() == PkgState.INSTALLED) {
            // Accept it.
            // If a locally installed item has no source, it probably has been
            // manually installed. In this case just match any remote source.
            return true;

        } else if (currentSource != null && currentSource.getUrl().startsWith("file://")) {
            // Heuristic: Probably a manual local install. Accept it.
            return true;

        } else {
            // Reject the source mismatch. The idea is that if two remote repositories
            // have similar packages, we don't want to merge them together and have
            // one hide the other. This is a design error from the repository owners
            // and we want the case to be blatant so that we can get it fixed.

            if (currentSource != null && newItemSource != null) {
                try {
                    URL url1 = new URL(currentSource.getUrl());
                    URL url2 = new URL(newItemSource.getUrl());

                    // Make an exception if both URLs have the same host name & domain name.
                    if (url1.sameFile(url2) || url1.getHost().equals(url2.getHost())) {
                        return true;
                    }
                } catch (Exception ignore) {
                    // Ignore MalformedURLException or other exceptions
                }
            }

            return false;
        }
    }

    private PkgCategory findCurrentCategory(
            List<PkgCategory> currentCategories,
            Object categoryKey) {
        for (PkgCategory cat : currentCategories) {
            if (cat.getKey().equals(categoryKey)) {
                return cat;
            }
        }
        return null;
    }

    /**
     * {@link UpdateOp} describing the Sort-by-API operation.
     */
    private class UpdateOpApi extends UpdateOp {
        @Override
        public Object getCategoryKey(Package pkg) {
            // Sort by API

            if (pkg instanceof IPackageVersion) {
                return ((IPackageVersion) pkg).getVersion().getApiLevel();

            } else if (pkg instanceof ToolPackage || pkg instanceof PlatformToolPackage) {
                return PkgCategoryApi.KEY_TOOLS;

            } else {
                return PkgCategoryApi.KEY_EXTRA;
            }
        }

        @Override
        public void addDefaultCategories() {
            boolean needTools = true;
            boolean needExtras = true;

            for (PkgCategory cat : mCategories) {
                if (cat.getKey().equals(PkgCategoryApi.KEY_TOOLS)) {
                    // Mark them as no unused to prevent their removal in updateEnd().
                    cat.setUnused(false);
                    needTools = false;
                } else if (cat.getKey().equals(PkgCategoryApi.KEY_EXTRA)) {
                    cat.setUnused(false);
                    needExtras = false;
                }
            }

            // Always add the tools & extras categories, even if empty (unlikely anyway)
            if (needTools) {
                PkgCategoryApi acat = new PkgCategoryApi(
                        PkgCategoryApi.KEY_TOOLS,
                        null,
                        mUpdaterData.getImageFactory().getImageByName(PackagesPage.ICON_CAT_OTHER));
                synchronized (mCategories) {
                    mCategories.add(acat);
                }
            }

            if (needExtras) {
                PkgCategoryApi acat = new PkgCategoryApi(
                        PkgCategoryApi.KEY_EXTRA,
                        null,
                        mUpdaterData.getImageFactory().getImageByName(PackagesPage.ICON_CAT_OTHER));
                synchronized (mCategories) {
                    mCategories.add(acat);
                }
            }
        }

        @Override
        public PkgCategory createCategory(Object catKey) {
            // Create API category.
            PkgCategory cat = null;

            assert catKey instanceof Integer;
            int apiKey = ((Integer) catKey).intValue();

            // We need a label for the category.
            // If we have an API level, try to get the info from the SDK Manager.
            // If we don't (e.g. when installing a new platform that isn't yet available
            // locally in the SDK Manager), it's OK we'll try to find the first platform
            // package available.
            String platformName = null;
            if (apiKey >= 1 && apiKey != PkgCategoryApi.KEY_TOOLS) {
                for (IAndroidTarget target :
                        mUpdaterData.getSdkManager().getTargets()) {
                    if (target.isPlatform() &&
                            target.getVersion().getApiLevel() == apiKey) {
                        platformName = target.getVersionName();
                        break;
                    }
                }
            }

            cat = new PkgCategoryApi(
                    apiKey,
                    platformName,
                    mUpdaterData.getImageFactory().getImageByName(PackagesPage.ICON_CAT_PLATFORM));

            return cat;
        }

        @Override
        public boolean mergeNewPackage(Package newPackage, PkgCategory cat) {
            // First check if the new package could be an update
            // to an existing package
            for (PkgItem item : cat.getItems()) {
                if (!isSourceCompatible(item, newPackage.getParentSource())) {
                    continue;
                }

                if (item.isSameMainPackageAs(newPackage)) {
                    // Seems like this isn't really a new item after all.
                    cat.setUnused(false);
                    // Return false since we're not changing anything.
                    return false;
                } else if (item.mergeUpdate(newPackage)) {
                    // The new package is an update for the existing package
                    // and has been merged in the PkgItem as such.
                    cat.setUnused(false);
                    // Return true to indicate we changed something.
                    return true;
                }
            }

            // This is truly a new item.
            cat.getItems().add(new PkgItem(newPackage, PkgState.NEW));
            cat.setUnused(false);
            return true; // something has changed
        }

        @Override
        public void sortCategoryList() {
            // Sort the categories list.
            // We always want categories in order tools..platforms..extras.
            // For platform, we compare in descending order (o2-o1).
            // This order is achieved by having the category keys ordered as
            // needed for the sort to just do what we expect.

            synchronized (mCategories) {
                Collections.sort(mCategories, new Comparator<PkgCategory>() {
                    public int compare(PkgCategory cat1, PkgCategory cat2) {
                        assert cat1 instanceof PkgCategoryApi;
                        assert cat2 instanceof PkgCategoryApi;
                        int api1 = ((Integer) cat1.getKey()).intValue();
                        int api2 = ((Integer) cat2.getKey()).intValue();
                        return api2 - api1;
                    }
                });
            }
        }

        @Override
        public void postCategoryItemsChanged() {
            // Sort the items
            for (PkgCategory cat : mCategories) {
                Collections.sort(cat.getItems());

                // When sorting by API, we can't always get the platform name
                // from the package manager. In this case at the very end we
                // look for a potential platform package we can use to extract
                // the platform version name (e.g. '1.5') from the first suitable
                // platform package we can find.

                assert cat instanceof PkgCategoryApi;
                PkgCategoryApi pac = (PkgCategoryApi) cat;
                if (pac.getPlatformName() == null) {
                    // Check whether we can get the actual platform version name (e.g. "1.5")
                    // from the first Platform package we find in this category.

                    for (PkgItem item : cat.getItems()) {
                        Package p = item.getMainPackage();
                        if (p instanceof PlatformPackage) {
                            String platformName = ((PlatformPackage) p).getVersionName();
                            if (platformName != null) {
                                pac.setPlatformName(platformName);
                                break;
                            }
                        }
                    }
                }
            }

        }
    }

    /**
     * {@link UpdateOp} describing the Sort-by-Source operation.
     */
    private class UpdateOpSource extends UpdateOp {
        @Override
        public Object getCategoryKey(Package pkg) {
            // Sort by source
            SdkSource source = pkg.getParentSource();
            if (source == null) {
                return PkgCategorySource.UNKNOWN_SOURCE;
            }
            return source;
        }

        @Override
        public void addDefaultCategories() {
            for (PkgCategory cat : mCategories) {
                if (cat.getKey().equals(PkgCategorySource.UNKNOWN_SOURCE)) {
                    // Already present.
                    return;
                }
            }

            // Always add the local categories, even if empty (unlikely anyway)
            PkgCategorySource cat = new PkgCategorySource(
                    PkgCategorySource.UNKNOWN_SOURCE,
                    mUpdaterData);
            // Mark it as unused so that it can be cleared in updateEnd() if not used.
            cat.setUnused(true);
            synchronized (mCategories) {
                mCategories.add(cat);
            }
        }

        @Override
        public PkgCategory createCategory(Object catKey) {
            assert catKey instanceof SdkSource;
            PkgCategory cat = new PkgCategorySource((SdkSource) catKey, mUpdaterData);
            return cat;

        }

        @Override
        public boolean mergeNewPackage(Package newPackage, PkgCategory cat) {
            // First check if the new package could be an update
            // to an existing package
            for (PkgItem item : cat.getItems()) {
                if (item.isSameMainPackageAs(newPackage)) {
                    // Seems like this isn't really a new item after all.
                    cat.setUnused(false);
                    // Return false since we're not changing anything.
                    return false;
                } else if (item.mergeUpdate(newPackage)) {
                    // The new package is an update for the existing package
                    // and has been merged in the PkgItem as such.
                    cat.setUnused(false);
                    // Return true to indicate we changed something.
                    return true;
                }
            }

            // This is truly a new item.
            cat.getItems().add(new PkgItem(newPackage, PkgState.NEW));
            cat.setUnused(false);
            return true; // something has changed
        }

        @Override
        public void sortCategoryList() {
            // Sort the sources in ascending source name order,
            // with the local packages always first.

            synchronized (mCategories) {
                Collections.sort(mCategories, new Comparator<PkgCategory>() {
                    public int compare(PkgCategory cat1, PkgCategory cat2) {
                        assert cat1 instanceof PkgCategorySource;
                        assert cat2 instanceof PkgCategorySource;

                        SdkSource src1 = ((PkgCategorySource) cat1).getSource();
                        SdkSource src2 = ((PkgCategorySource) cat2).getSource();

                        if (src1 == src2) {
                            return 0;
                        } else if (src1 == PkgCategorySource.UNKNOWN_SOURCE) {
                            return -1;
                        } else if (src2 == PkgCategorySource.UNKNOWN_SOURCE) {
                            return 1;
                        }
                        assert src1 != null; // true because LOCAL_SOURCE==null
                        assert src2 != null;
                        return src1.toString().compareTo(src2.toString());
                    }
                });
            }
        }

        @Override
        public void postCategoryItemsChanged() {
            // Sort the items
            for (PkgCategory cat : mCategories) {
                Collections.sort(cat.getItems());
            }
        }
    }
}
