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

package com.android.ide.eclipse.adt.internal.editors.layout.refactoring;

import static com.android.ide.common.layout.LayoutConstants.FQCN_GESTURE_OVERLAY_VIEW;
import static com.android.ide.common.layout.LayoutConstants.FQCN_LINEAR_LAYOUT;
import static com.android.ide.common.layout.LayoutConstants.FQCN_RADIO_BUTTON;
import static com.android.ide.common.layout.LayoutConstants.GESTURE_OVERLAY_VIEW;
import static com.android.ide.common.layout.LayoutConstants.RADIO_GROUP;
import static com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors.VIEW_INCLUDE;
import static com.android.sdklib.SdkConstants.CLASS_VIEW;
import static com.android.sdklib.SdkConstants.CLASS_VIEWGROUP;
import static com.android.sdklib.SdkConstants.FN_FRAMEWORK_LIBRARY;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.PaletteMetadataDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.ViewMetadataRepository;
import com.android.ide.eclipse.adt.internal.resources.ResourceNameValidator;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.util.Pair;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.ResolvedBinaryType;
import org.eclipse.jdt.internal.core.ResolvedSourceType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@SuppressWarnings("restriction") // JDT model access for custom-view class lookup
class WrapInWizard extends VisualRefactoringWizard {
    private static final String SEPARATOR_LABEL =
        "----------------------------------------"; //$NON-NLS-1$

    public WrapInWizard(WrapInRefactoring ref, LayoutEditor editor) {
        super(ref, editor);
        setDefaultPageTitle("Wrap in Container");
    }

    @Override
    protected void addUserInputPages() {
        WrapInRefactoring ref = (WrapInRefactoring) getRefactoring();
        String oldType = ref.getOldType();
        addPage(new InputPage(mEditor.getProject(), oldType));
    }

    /** Wizard page which inputs parameters for the {@link WrapInRefactoring} operation */
    private static class InputPage extends VisualRefactoringInputPage {
        private final IProject mProject;
        private final String mOldType;
        private Text mIdText;
        private Combo mTypeCombo;
        private List<Pair<String, ViewElementDescriptor>> mClassNames;

        public InputPage(IProject project, String oldType) {
            super("WrapInInputPage");  //$NON-NLS-1$
            mProject = project;
            mOldType = oldType;
        }

        public void createControl(Composite parent) {
            Composite composite = new Composite(parent, SWT.NONE);
            composite.setLayout(new GridLayout(2, false));

            Label typeLabel = new Label(composite, SWT.NONE);
            typeLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
            typeLabel.setText("Type of Container:");

            mTypeCombo = new Combo(composite, SWT.READ_ONLY);
            mTypeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
            mTypeCombo.addSelectionListener(mSelectionValidateListener);

            Label idLabel = new Label(composite, SWT.NONE);
            idLabel.setText("New Layout Id:");
            idLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));

            mIdText = new Text(composite, SWT.BORDER);
            mIdText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
            mIdText.addModifyListener(mModifyValidateListener);

            Set<String> exclude = Collections.singleton(VIEW_INCLUDE);
            mClassNames = addLayouts(mProject, mOldType, mTypeCombo, exclude, true);
            mTypeCombo.select(0);

            setControl(composite);
            validatePage();

            mTypeCombo.setFocus();
        }

        @Override
        protected boolean validatePage() {
            boolean ok = true;

            String id = mIdText.getText().trim();

            if (id.length() == 0) {
                setErrorMessage("ID required");
                ok = false;
            } else {
                // ...but if you do, it has to be valid!
                ResourceNameValidator validator = ResourceNameValidator.create(false, mProject,
                        ResourceType.ID);
                String message = validator.isValid(id);
                if (message != null) {
                    setErrorMessage(message);
                    ok = false;
                }
            }

            int selectionIndex = mTypeCombo.getSelectionIndex();
            String type = selectionIndex != -1 ? mClassNames.get(selectionIndex).getFirst() : null;
            if (type == null) {
                setErrorMessage("Select a container type");
                ok = false; // The user has chosen a separator
            }

            if (ok) {
                setErrorMessage(null);

                // Record state
                WrapInRefactoring refactoring =
                    (WrapInRefactoring) getRefactoring();
                refactoring.setId(id);
                refactoring.setType(type);

                ViewElementDescriptor descriptor = mClassNames.get(selectionIndex).getSecond();
                if (descriptor instanceof PaletteMetadataDescriptor) {
                    PaletteMetadataDescriptor paletteDescriptor =
                        (PaletteMetadataDescriptor) descriptor;
                    String initializedAttributes = paletteDescriptor.getInitializedAttributes();
                    refactoring.setInitializedAttributes(initializedAttributes);
                } else {
                    refactoring.setInitializedAttributes(null);
                }
            }

            setPageComplete(ok);
            return ok;
        }
    }

    static List<Pair<String, ViewElementDescriptor>> addLayouts(IProject project,
            String oldType, Combo combo,
            Set<String> exclude, boolean addGestureOverlay) {
        List<Pair<String, ViewElementDescriptor>> classNames =
            new ArrayList<Pair<String, ViewElementDescriptor>>();

        if (oldType != null && oldType.equals(FQCN_RADIO_BUTTON)) {
            combo.add(RADIO_GROUP);
            // NOT a fully qualified name since android widgets do not include the package
            classNames.add(Pair.of(RADIO_GROUP, (ViewElementDescriptor) null));

            combo.add(SEPARATOR_LABEL);
            classNames.add(Pair.<String,ViewElementDescriptor>of(null, null));
        }

        Pair<List<String>,List<String>> result = findViews(project, true);
        List<String> customViews = result.getFirst();
        List<String> thirdPartyViews = result.getSecond();
        if (customViews.size() > 0) {
            for (String view : customViews) {
                combo.add(view);
                classNames.add(Pair.of(view, (ViewElementDescriptor) null));
            }
            combo.add(SEPARATOR_LABEL);
            classNames.add(Pair.<String,ViewElementDescriptor>of(null, null));
        }

        // Populate type combo
        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk != null) {
            IAndroidTarget target = currentSdk.getTarget(project);
            if (target != null) {
                AndroidTargetData targetData = currentSdk.getTargetData(target);
                if (targetData != null) {
                    ViewMetadataRepository repository = ViewMetadataRepository.get();
                    List<Pair<String,List<ViewElementDescriptor>>> entries =
                        repository.getPaletteEntries(targetData, false, true);
                    // Find the layout category - it contains LinearLayout
                    List<ViewElementDescriptor> layoutDescriptors = null;

                    search: for (Pair<String,List<ViewElementDescriptor>> pair : entries) {
                        List<ViewElementDescriptor> list = pair.getSecond();
                        for (ViewElementDescriptor d : list) {
                            if (d.getFullClassName().equals(FQCN_LINEAR_LAYOUT)) {
                                // Found - use this list
                                layoutDescriptors = list;
                                break search;
                            }
                        }
                    }
                    if (layoutDescriptors != null) {
                        for (ViewElementDescriptor d : layoutDescriptors) {
                            String className = d.getFullClassName();
                            if (exclude == null || !exclude.contains(className)) {
                                combo.add(d.getUiName());
                                classNames.add(Pair.of(className, d));
                            }
                        }

                        // SWT does not support separators in combo boxes
                        combo.add(SEPARATOR_LABEL);
                        classNames.add(null);

                        if (thirdPartyViews.size() > 0) {
                            for (String view : thirdPartyViews) {
                                combo.add(view);
                                classNames.add(Pair.of(view, (ViewElementDescriptor) null));
                            }
                            combo.add(SEPARATOR_LABEL);
                            classNames.add(null);
                        }

                        if (addGestureOverlay) {
                            combo.add(GESTURE_OVERLAY_VIEW);
                            classNames.add(Pair.<String, ViewElementDescriptor> of(
                                    FQCN_GESTURE_OVERLAY_VIEW, null));

                            combo.add(SEPARATOR_LABEL);
                            classNames.add(Pair.<String,ViewElementDescriptor>of(null, null));
                        }
                    }

                    // Now add ALL known layout descriptors in case the user has
                    // a special case
                    layoutDescriptors =
                        targetData.getLayoutDescriptors().getLayoutDescriptors();

                    for (ViewElementDescriptor d : layoutDescriptors) {
                        String className = d.getFullClassName();
                        if (exclude == null || !exclude.equals(className)) {
                            combo.add(d.getUiName());
                            classNames.add(Pair.of(className, d));
                        }
                    }
                }
            }
        } else {
            combo.add("SDK not initialized");
            classNames.add(Pair.<String,ViewElementDescriptor>of(null, null));
        }

        return classNames;
    }

    /**
     * Returns a pair of view lists - the custom views and the 3rd-party views
     *
     * @param project the Android project
     * @param layoutsOnly if true, only search for layouts
     * @return a pair of lists, the first containing custom views and the second
     *         containing 3rd party views
     */
    public static Pair<List<String>,List<String>> findViews(IProject project, boolean layoutsOnly) {
        final List<String> customViews = new ArrayList<String>();
        final List<String> thirdPartyViews = new ArrayList<String>();

        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) throws CoreException {
                Object element = match.getElement();

                if (element instanceof ResolvedBinaryType) {
                    ResolvedBinaryType bt = (ResolvedBinaryType) element;
                    IPackageFragment fragment = bt.getPackageFragment();
                    IPath path = fragment.getPath();
                    String last = path.lastSegment();
                    // Filter out android.jar stuff
                    if (last.equals(FN_FRAMEWORK_LIBRARY)) {
                        return;
                    }
                    String fqn = bt.getFullyQualifiedName();
                    thirdPartyViews.add(fqn);
                } else if (element instanceof ResolvedSourceType) {
                    ResolvedSourceType type = (ResolvedSourceType) element;
                    String fqn = type.getFullyQualifiedName();
                    // User custom view
                    customViews.add(fqn);
                }
            }
        };
        try {
            IJavaProject javaProject = (IJavaProject) project.getNature(JavaCore.NATURE_ID);
            if (javaProject != null) {
                String className = layoutsOnly ? CLASS_VIEWGROUP : CLASS_VIEW;
                IType activityType = javaProject.findType(className);
                if (activityType != null) {
                    IJavaSearchScope scope = SearchEngine.createHierarchyScope(activityType);
                    SearchParticipant[] participants = new SearchParticipant[] {
                        SearchEngine.getDefaultSearchParticipant()
                    };
                    int matchRule = SearchPattern.R_PATTERN_MATCH | SearchPattern.R_CASE_SENSITIVE;
                    SearchPattern pattern = SearchPattern.createPattern("*",
                            IJavaSearchConstants.CLASS, IJavaSearchConstants.DECLARATIONS,
                            matchRule);
                    SearchEngine engine = new SearchEngine();
                    engine.search(pattern, participants, scope, requestor,
                            new NullProgressMonitor());
                }
            }
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
        }

        return Pair.of(customViews, thirdPartyViews);
    }
}
