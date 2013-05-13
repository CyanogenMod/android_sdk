/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.wizards.exportgradle;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.google.common.base.Joiner;
import com.ibm.icu.text.MessageFormat;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Displays a wizard page that lets the user choose the projects for which to create Gradle build
 * files.
 * <p>
 * Based on {@link org.eclipse.ant.internal.ui.datatransfer.AntBuildfileExportPage}
 */
public class GradleExportPage extends WizardPage {
    private static final String NEWLINE = System.getProperty("line.separator"); //$NON-NLS-1$

    private static final Comparator<IJavaProject> PROJECT_COMPARATOR =
            new Comparator<IJavaProject>() {
        @Override
        public int compare(IJavaProject o1, IJavaProject o2) {
            return o1.getProject().getName().compareTo(o2.getProject().getName());
        }
    };

    private CheckboxTableViewer mTableViewer;
    private List<IJavaProject> mSelectedJavaProjects = new ArrayList<IJavaProject>();

    public GradleExportPage() {
        super("GradleExportPage"); //$NON-NLS-1$
        setPageComplete(false);
        setTitle(ExportMessages.PageTitle);
        setDescription(ExportMessages.PageDescription);
    }

    @Override
    public void createControl(Composite parent) {
        initializeDialogUnits(parent);

        Composite workArea = new Composite(parent, SWT.NONE);
        setControl(workArea);

        workArea.setLayout(new GridLayout());
        workArea.setLayoutData(new GridData(GridData.FILL_BOTH
                | GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL));

        Label title = new Label(workArea, SWT.NONE);
        title.setText(ExportMessages.SelectProjects);

        Composite listComposite = new Composite(workArea, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        layout.marginWidth = 0;
        layout.makeColumnsEqualWidth = false;
        listComposite.setLayout(layout);

        listComposite.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL
                | GridData.GRAB_VERTICAL | GridData.FILL_BOTH));

        Table table = new Table(listComposite,
                SWT.CHECK | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        mTableViewer = new CheckboxTableViewer(table);
        table.setLayout(new TableLayout());
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        data.heightHint = 300;
        table.setLayoutData(data);
        mTableViewer.setContentProvider(new WorkbenchContentProvider() {
            @Override
            public Object[] getElements(Object element) {
                if (element instanceof IJavaProject[]) {
                    return (IJavaProject[]) element;
                }
                return null;
            }
        });
        mTableViewer.setLabelProvider(new WorkbenchLabelProvider());
        mTableViewer.addCheckStateListener(new ICheckStateListener() {
            @Override
            public void checkStateChanged(CheckStateChangedEvent event) {
                if (event.getChecked()) {
                    mSelectedJavaProjects.add((IJavaProject) event.getElement());
                } else {
                    mSelectedJavaProjects.remove(event.getElement());
                }
                updateEnablement();
            }
        });

        initializeProjects();
        createSelectionButtons(listComposite);
        setControl(workArea);
        updateEnablement();
        Dialog.applyDialogFont(parent);
    }

    /**
     * Creates select all/deselect all buttons.
     */
    private void createSelectionButtons(Composite composite) {
        Composite buttonsComposite = new Composite(composite, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        buttonsComposite.setLayout(layout);

        buttonsComposite.setLayoutData(new GridData(
                GridData.VERTICAL_ALIGN_BEGINNING));

        Button selectAll = new Button(buttonsComposite, SWT.PUSH);
        selectAll.setText(ExportMessages.SelectAll);
        selectAll.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                for (int i = 0; i < mTableViewer.getTable().getItemCount(); i++) {
                    mSelectedJavaProjects.add((IJavaProject) mTableViewer.getElementAt(i));
                }
                mTableViewer.setAllChecked(true);
                updateEnablement();
            }
        });
        setButtonLayoutData(selectAll);

        Button deselectAll = new Button(buttonsComposite, SWT.PUSH);
        deselectAll.setText(ExportMessages.DeselectAll);
        deselectAll.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                mSelectedJavaProjects.clear();
                mTableViewer.setAllChecked(false);
                updateEnablement();
            }
        });
        setButtonLayoutData(deselectAll);
    }

    /**
     * Populates the list with all the eligible projects in the workspace.
     */
    private void initializeProjects() {
        IWorkspaceRoot rootWorkspace = ResourcesPlugin.getWorkspace().getRoot();
        IJavaModel javaModel = JavaCore.create(rootWorkspace);
        IJavaProject[] javaProjects;
        try {
            javaProjects = javaModel.getJavaProjects();
        } catch (JavaModelException e) {
            javaProjects = new IJavaProject[0];
        }
        mTableViewer.setInput(javaProjects);
        // Check any necessary projects
        if (mSelectedJavaProjects != null) {
            mTableViewer.setCheckedElements(mSelectedJavaProjects.toArray(
                    new IJavaProject[mSelectedJavaProjects.size()]));
        }
    }

    /**
     * Enables/disables the finish button on the wizard and displays error messages as needed.
     */
    private void updateEnablement() {
        boolean complete = true;
        if (mSelectedJavaProjects.size() == 0) {
            setErrorMessage(ExportMessages.NoProjectsError);
            complete = false;
        }
        List<String> cyclicProjects;
        try {
            cyclicProjects = getCyclicProjects(getProjects(false));
            if (cyclicProjects.size() > 0) {
                setErrorMessage(MessageFormat.format(ExportMessages.CyclicProjectsError,
                        Joiner.on(", ").join(cyclicProjects))); //$NON-NLS-1$
                complete = false;
            }
        } catch (CoreException e) {}
        if (complete) {
            setErrorMessage(null);
        }
        setPageComplete(complete);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            mTableViewer.getTable().setFocus();
        }
    }

    /**
     * Converts Eclipse Java projects to Gradle build files. Displays error dialogs.
     */
    public boolean generateBuildfiles() {
        setErrorMessage(null);
        final List<String> projectNames = new ArrayList<String>();
        final Set<IJavaProject> projects;
        try {
            projects = getProjects(true);
            if (projects.size() == 0) {
                return false;
            }
        } catch (JavaModelException e) {
            AdtPlugin.log(e, null);
            setErrorMessage(MessageFormat.format(
                    ExportMessages.ExportFailedError, e.toString()));
            return false;
        }
        IRunnableWithProgress runnable = new IRunnableWithProgress() {
            @Override
            public void run(IProgressMonitor pm) throws InterruptedException {
                SubMonitor localmonitor = SubMonitor.convert(pm, ExportMessages.StatusMessage,
                        projects.size());
                Exception problem = null;
                try {
                    projectNames.addAll(BuildFileCreator.createBuildFiles(projects, getShell(),
                            localmonitor.newChild(projects.size())));
                } catch (JavaModelException e) {
                    problem = e;
                } catch (IOException e) {
                    problem = e;
                } catch (CoreException e) {
                    problem = e;
                }

                if (problem != null) {
                    AdtPlugin.log(problem, null);
                    setErrorMessage(MessageFormat.format(ExportMessages.ExportFailedError,
                            problem.toString()));
                }
            }
        };

        try {
            getContainer().run(false, false, runnable);
        } catch (InvocationTargetException e) {
            AdtPlugin.log(e, null);
            return false;
        } catch (InterruptedException e) {
            AdtPlugin.log(e, null);
            return false;
        }
        if (getErrorMessage() != null) {
            return false;
        }
        return true;
    }

    /**
     * Get projects to write buildfiles for. Opens confirmation dialog.
     *
     * @param displayConfirmation if set to true a dialog prompts for
     *            confirmation before overwriting files
     * @return set of project names
     */
    private Set<IJavaProject> getProjects(boolean displayConfirmation) throws JavaModelException {
        // collect all projects to create buildfiles for
        Set<IJavaProject> projects = new TreeSet<IJavaProject>(PROJECT_COMPARATOR);
        for (IJavaProject javaProject : mSelectedJavaProjects) {
            projects.addAll(getClasspathProjectsRecursive(javaProject));
            projects.add(javaProject);
        }

        // confirm overwrite
        List<String> confirmOverwrite = getConfirmOverwriteSet(projects);
        if (displayConfirmation && confirmOverwrite.size() > 0) {
            String message = ExportMessages.ConfirmOverwrite + NEWLINE +
                Joiner.on(NEWLINE).join(confirmOverwrite);
            if (!MessageDialog.openQuestion(getShell(),
                    ExportMessages.ConfirmOverwriteTitle, message)) {
                return new TreeSet<IJavaProject>(PROJECT_COMPARATOR);
            }
        }
        return projects;
    }

    /**
     * Returns given projects that have cyclic dependencies.
     *
     * @param javaProjects list of IJavaProject objects
     * @return set of project names
     */
    private List<String> getCyclicProjects(Set<IJavaProject> projects) throws CoreException {

        List<String> cyclicProjects = new ArrayList<String>();
        for (IJavaProject javaProject : projects) {
            if (hasCyclicDependency(javaProject)) {
                cyclicProjects.add(javaProject.getProject().getName());
            }
        }
        return cyclicProjects;
    }

    /**
     * Check if given project has a cyclic dependency.
     * <p>
     * See {@link org.eclipse.jdt.core.tests.model.ClasspathTests.numberOfCycleMarkers}
     */
    public static boolean hasCyclicDependency(IJavaProject javaProject)
            throws CoreException {
        IMarker[] markers = javaProject.getProject().findMarkers(
                IJavaModelMarker.BUILDPATH_PROBLEM_MARKER, false,
                IResource.DEPTH_ONE);
        for (int i = 0; i < markers.length; i++) {
            IMarker marker = markers[i];
            String cycleAttr = (String) marker
                    .getAttribute(IJavaModelMarker.CYCLE_DETECTED);
            if (cycleAttr != null && cycleAttr.equals("true")) { //$NON-NLS-1$
                return true;
            }
        }
        return false;
    }

    /**
     * Get list of projects which have already a buildfile.
     *
     * @param javaProjects list of IJavaProject objects
     * @return set of project names
     */
    private List<String> getConfirmOverwriteSet(Set<IJavaProject> javaProjects)
    {
        List<String> result = new ArrayList<String>(javaProjects.size());
        for (IJavaProject project : javaProjects) {
            String projectRoot = project.getResource().getLocation().toString();
            if (new File(projectRoot, "build.gradle").exists()) {
                result.add(project.getProject().getName());
            }
        }
        return result;
    }

    /**
     * Get Java project from resource.
     */
    private static IJavaProject getJavaProjectByName(String name) {
        try {
            IProject project = ResourcesPlugin.getWorkspace().getRoot()
                    .getProject(name);
            if (project.exists()) {
                return JavaCore.create(project);
            }
        } catch (IllegalArgumentException iae) {
        }
        return null;
    }

    /**
     * Get Java project for given root.
     */
    private static IJavaProject getJavaProject(String root) {
        IPath path = new Path(root);
        if (path.segmentCount() == 1) {
            return getJavaProjectByName(root);
        }
        IResource resource = ResourcesPlugin.getWorkspace().getRoot()
                .findMember(path);
        if (resource != null && resource.getType() == IResource.PROJECT) {
            if (resource.exists()) {
                return (IJavaProject) JavaCore.create(resource);
            }
        }
        return null;
    }

    /**
     * Get for given project all directly and indirectly dependent projects.
     *
     * @return set of IJavaProject objects
     */
    private static List<IJavaProject> getClasspathProjectsRecursive(IJavaProject project)
            throws JavaModelException {
        LinkedList<IJavaProject> result = new LinkedList<IJavaProject>();
        getClasspathProjectsRecursive(project, result);
        return result;
    }

    private static void getClasspathProjectsRecursive(IJavaProject project,
            LinkedList<IJavaProject> result) throws JavaModelException {
        List<IJavaProject> projects = new ArrayList<IJavaProject>();
        IClasspathEntry entries[] = project.getRawClasspath();
        for (IClasspathEntry classpathEntry : entries) {
            if (classpathEntry.getContentKind() == IPackageFragmentRoot.K_SOURCE
                    && classpathEntry.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
                // found required project on build path
                String subProjectRoot = classpathEntry.getPath().toString();
                IJavaProject subProject = getJavaProject(subProjectRoot);
                // is project available in workspace
                if (subProject != null) {
                    projects.add(subProject);
                }
            }
        }
        for (IJavaProject javaProject : projects) {
            if (!result.contains(javaProject)) {
                result.addFirst(javaProject);
                getClasspathProjectsRecursive(javaProject, result); // recursion
            }
        }
    }
}