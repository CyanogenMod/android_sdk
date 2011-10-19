/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.eclipse.adt.internal.wizards.newproject;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.wizards.newproject.NewProjectWizardState.Mode;

import org.eclipse.jdt.ui.actions.OpenJavaPerspectiveAction;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;


/**
 * A "New Android Project" Wizard.
 * <p/>
 * Note: this class is public so that it can be accessed from unit tests.
 * It is however an internal class. Its API may change without notice.
 * It should semantically be considered as a private final class.
 * <p/>
 * Do not derive from this class.
 */
public class NewProjectWizard extends Wizard implements INewWizard {
    private static final String PROJECT_LOGO_LARGE = "icons/android-64.png"; //$NON-NLS-1$

    private NewProjectWizardState mValues;
    private ProjectNamePage mNamePage;
    private SdkSelectionPage mSdkPage;
    private SampleSelectionPage mSamplePage;
    private ApplicationInfoPage mPropertiesPage;
    private final Mode mMode;

    /** Constructs a new wizard default project wizard */
    public NewProjectWizard() {
        this(Mode.ANY);
    }

    protected NewProjectWizard(Mode mode) {
        setWindowTitle("New Android Project");
        mMode = mode;
    }

    @Override
    public void addPages() {
        mValues = new NewProjectWizardState(mMode);

        if (mMode != Mode.SAMPLE) {
            mNamePage = new ProjectNamePage(mValues);
            addPage(mNamePage);
        }

        if (mMode == Mode.TEST) {
            addPage(new TestTargetPage(mValues));
        }

        mSdkPage = new SdkSelectionPage(mValues);
        addPage(mSdkPage);

        if (mMode != Mode.TEST) {
            // Sample projects can be created when entering the new/existing wizard, or
            // the sample wizard
            mSamplePage = new SampleSelectionPage(mValues);
            addPage(mSamplePage);
        }

        if (mMode != Mode.SAMPLE) {
            // Project properties are entered in all project types except sample projects
            mPropertiesPage = new ApplicationInfoPage(mValues);
            addPage(mPropertiesPage);
        }
    }

    public void init(IWorkbench workbench, IStructuredSelection selection) {
        setHelpAvailable(false); // TODO have help
        ImageDescriptor desc = AdtPlugin.getImageDescriptor(PROJECT_LOGO_LARGE);
        setDefaultPageImageDescriptor(desc);
    }

    @Override
    public boolean performFinish() {
        NewProjectCreator creator = new NewProjectCreator(mValues, getContainer());
        if (!(creator.createAndroidProjects())) {
            return false;
        }

        // Open the default Java Perspective
        OpenJavaPerspectiveAction action = new OpenJavaPerspectiveAction();
        action.run();
        return true;
    }

    @Override
    public IWizardPage getNextPage(IWizardPage page) {
        if (page == mNamePage) {
            // Skip the test target selection page unless creating a test project
            if (mValues.mode != Mode.TEST) {
                return mSdkPage;
            }
        } else if (page == mSdkPage) {
            if (mValues.mode == Mode.SAMPLE) {
                return mSamplePage;
            } else if (mValues.mode != Mode.TEST) {
                return mPropertiesPage;
            } else {
                // Done with wizard when creating from existing or creating test projects
                return null;
            }
        } else if (page == mSamplePage) {
            // Nothing more to be entered for samples
            return null;
        }

        return super.getNextPage(page);
    }

    /**
     * Returns the package name currently set by the wizard
     *
     * @return the current package name, or null
     */
    public String getPackageName() {
        return mValues.packageName;
    }

    // TBD: Call setDialogSettings etc to store persistent state between wizard invocations.
}
