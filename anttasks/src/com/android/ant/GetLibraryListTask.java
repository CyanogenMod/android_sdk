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

package com.android.ant;

import com.android.ant.DependencyHelper.LibraryProcessor;
import com.android.sdklib.internal.project.IPropertySource;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import java.io.File;
import java.util.List;

/**
 * Task to get the list of Library Project for the current project.
 *
 */
public class GetLibraryListTask extends Task {

    private String mLibraryFolderPathOut;


    public void setLibraryFolderPathOut(String libraryFolderPathOut) {
        mLibraryFolderPathOut = libraryFolderPathOut;
    }

    @Override
    public void execute() throws BuildException {
        execute(null);
    }

    public void execute(LibraryProcessor processor) throws BuildException {

        if (mLibraryFolderPathOut == null) {
            throw new BuildException("Missing attribute libraryFolderPathOut");
        }

        final Project antProject = getProject();

        DependencyHelper helper = new DependencyHelper(antProject.getBaseDir(),
                new IPropertySource() {
                    @Override
                    public String getProperty(String name) {
                        return antProject.getProperty(name);
                    }
                },
                true /*verbose*/);

        System.out.println("Library dependencies:");

        if (helper.getLibraryCount() > 0) {
            System.out.println("\n------------------\nOrdered libraries:");

            helper.processLibraries(processor);

            // Create a Path object of all the libraries in reverse order.
            // This is important so that compilation of libraries happens
            // in the reverse order.
            Path rootPath = new Path(antProject);

            List<File> libraries = helper.getLibraries();

            for (int i = libraries.size() - 1 ; i >= 0; i--) {
                File library = libraries.get(i);
                PathElement element = rootPath.createPathElement();
                element.setPath(library.getAbsolutePath());
            }

            antProject.addReference(mLibraryFolderPathOut, rootPath);
        } else {
            System.out.println("No Libraries");
        }
    }

    protected String getLibraryFolderPathOut() {
        return mLibraryFolderPathOut;
    }
}
