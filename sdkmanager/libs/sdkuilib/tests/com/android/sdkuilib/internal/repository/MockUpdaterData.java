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

package com.android.sdkuilib.internal.repository;

import com.android.sdklib.NullSdkLog;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.ArchiveInstaller;
import com.android.sdklib.internal.repository.ArchiveReplacement;
import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskFactory;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.MockEmptySdkManager;
import com.android.sdklib.internal.repository.NullTaskMonitor;
import com.android.sdklib.mock.MockLog;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;

import org.eclipse.swt.graphics.Image;

import java.util.ArrayList;
import java.util.List;

/** A mock UpdaterData that simply records what would have been installed. */
public class MockUpdaterData extends UpdaterData {

    public final static String SDK_PATH = "/tmp/SDK";

    private final List<ArchiveReplacement> mInstalled = new ArrayList<ArchiveReplacement>();

    public MockUpdaterData() {
        super(SDK_PATH, new MockLog());

        setTaskFactory(new MockTaskFactory());
        setImageFactory(new NullImageFactory());
    }

    /** Gives access to the internal {@link #installArchives(List, int)}. */
    public void _installArchives(List<ArchiveInfo> result) {
        installArchives(result, 0/*flags*/);
    }

    public ArchiveReplacement[] getInstalled() {
        return mInstalled.toArray(new ArchiveReplacement[mInstalled.size()]);
    }

    @Override
    protected void initSdk() {
        setSdkManager(new MockEmptySdkManager(SDK_PATH));
    }

    @Override
    public void reloadSdk() {
        // bypass original implementation
    }

    /** Returns a mock installer that simply records what would have been installed. */
    @Override
    protected ArchiveInstaller createArchiveInstaler() {
        return new ArchiveInstaller() {
            @Override
            public boolean install(
                    ArchiveReplacement archiveInfo,
                    String osSdkRoot,
                    boolean forceHttp,
                    SdkManager sdkManager,
                    ITaskMonitor monitor) {
                mInstalled.add(archiveInfo);
                return true;
            }
        };
    }

    //------------

    private class MockTaskFactory implements ITaskFactory {
        public void start(String title, ITask task) {
            start(title, null /*parentMonitor*/, task);
        }

        public void start(String title, ITaskMonitor parentMonitor, ITask task) {
            new MockTask(task);
        }
    }

    //------------

    private static class MockTask extends NullTaskMonitor {
        public MockTask(ITask task) {
            super(new NullSdkLog());
            task.run(this);
        }
    }

    //------------

    private static class NullImageFactory extends ImageFactory {
        public NullImageFactory() {
            // pass
            super(null /*display*/);
        }

        @Override
        public Image getImageByName(String imageName) {
            return null;
        }

        @Override
        public Image getImageForObject(Object object) {
            return null;
        }

        @Override
        public void dispose() {
            // pass
        }

    }
}
