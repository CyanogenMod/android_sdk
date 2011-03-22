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


import com.android.sdklib.ISdkLog;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;
import com.android.sdkuilib.internal.tasks.ProgressView;
import com.android.sdkuilib.internal.tasks.ProgressViewFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

/**
 * This is the private implementation of the UpdateWindow
 * for the second version of the SDK Manager.
 * <p/>
 * This window features only one embedded page, the combined installed+available package list.
 */
public class UpdaterWindowImpl2 extends UpdaterWindowImpl {

    private ProgressBar mProgressBar;
    private Label mStatusText;
    private ImgDisabledButton mButtonStop;
    private ToggleButton mButtonDetails;

    /**
     * Creates a new window. Caller must call open(), which will block.
     *
     * @param parentShell Parent shell.
     * @param sdkLog Logger. Cannot be null.
     * @param osSdkRoot The OS path to the SDK root.
     */
    public UpdaterWindowImpl2(Shell parentShell, ISdkLog sdkLog, String osSdkRoot) {
        super(parentShell, sdkLog, osSdkRoot);
    }

    /**
     * @wbp.parser.entryPoint
     */
    @Override
    public void open() {
        super.open();
    }

    @Override
    protected void createContents() {
        mShell.setText("Android SDK Manager");

        Composite root = new Composite(mShell, SWT.NONE);
        GridLayout gl = new GridLayout(1, false);
        //gl.marginHeight = gl.marginRight = 0;
        root.setLayout(gl);
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

        createPagesRoot(root);
        getPagesRootComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        Composite composite1 = new Composite(mShell, SWT.NONE);
        composite1.setLayout(new GridLayout(1, false));
        composite1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        mProgressBar = new ProgressBar(composite1, SWT.NONE);
        mProgressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        mStatusText = new Label(composite1, SWT.NONE);
        mStatusText.setText("Status Placeholder");  //$NON-NLS-1$ placeholder
        mStatusText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        Composite composite2 = new Composite(mShell, SWT.NONE);
        composite2.setLayout(new GridLayout(2, false));

        mButtonStop = new ImgDisabledButton(composite2, SWT.NONE,
                getImage("stop_enabled_16.png"),   //$NON-NLS-1$
                getImage("stop_disabled_16.png"));   //$NON-NLS-1$
        mButtonStop.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                onStopSelected();
            }
        });

        mButtonDetails = new ToggleButton(composite2, SWT.NONE,
                getImage("collapsed_16.png"),   //$NON-NLS-1$
                getImage("expanded_16.png"));   //$NON-NLS-1$
        mButtonDetails.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                onToggleDetails();
            }
        });
    }

    private Image getImage(String filename) {
        UpdaterData updaterData = getUpdaterData();
        if (updaterData != null) {
            ImageFactory imgFactory = updaterData.getImageFactory();
            if (imgFactory != null) {
                return imgFactory.getImageByName(filename);
            }
        }
        return null;
    }


    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$

    // --- Public API -----------

    // --- Internals & UI Callbacks -----------

    @Override
    protected boolean postCreateContent() {
        // Override the base task factory with the new one
        UpdaterData updaterData = getUpdaterData();
        ProgressViewFactory factory = new ProgressViewFactory();
        factory.setProgressView(new ProgressView(
                mStatusText, mProgressBar, mButtonStop));
        updaterData.setTaskFactory(factory);

        return super.postCreateContent();
    }

    @Override
    protected void createPages() {
        PackagesPage pkgPage = new PackagesPage(getPagesRootComposite(), getUpdaterData());
        addPage(pkgPage, "Packages List");
        addExtraPages();
    }

    private void onToggleDetails() {
        mButtonDetails.setState(1 - mButtonDetails.getState());
    }

    private void onStopSelected() {
        // TODO
    }

    // End of hiding from SWT Designer
    //$hide<<$

    // -----

    /**
     * A label that can display 2 images depending on its internal state.
     * This acts as a button by firing the {@link SWT#Selection} listener.
     */
    private static class ToggleButton extends CLabel {
        private Image[] mImage = new Image[2];
        private boolean mMouseIn;
        private int mState = 0;


        public ToggleButton(Composite parent, int style, Image image1, Image image2) {
            super(parent, style);
            mImage[0] = image1;
            mImage[1] = image2;
            updateImage();

            addMouseListener(new MouseListener() {
                public void mouseDown(MouseEvent e) {
                    // pass
                }

                public void mouseUp(MouseEvent e) {
                    // We select on mouse-up, as it should be properly done since this is the
                    // only way a user can cancel a button click by moving out of the button.
                    if (mMouseIn && e.button == 1) {
                        notifyListeners(SWT.Selection, new Event());
                    }
                }

                public void mouseDoubleClick(MouseEvent e) {
                    if (mMouseIn && e.button == 1) {
                        notifyListeners(SWT.DefaultSelection, new Event());
                    }
                }
            });

            addMouseTrackListener(new MouseTrackListener() {
                public void mouseExit(MouseEvent e) {
                    if (mMouseIn) {
                        mMouseIn = false;
                        redraw();
                    }
                }

                public void mouseEnter(MouseEvent e) {
                    if (!mMouseIn) {
                        mMouseIn = true;
                        redraw();
                    }
                }

                public void mouseHover(MouseEvent e) {
                    // pass
                }
            });
        }

        @Override
        public int getStyle() {
            int style = super.getStyle();
            if (mMouseIn) {
                style |= SWT.SHADOW_IN;
            }
            return style;
        }

        /**
         * Sets current state.
         * @param state A value 0 or 1.
         */
        public void setState(int state) {
            assert state == 0 || state == 1;
            mState = state;
            updateImage();
            redraw();
        }

        /**
         * Returns the current state
         * @return Returns the current state, either 0 or 1.
         */
        public int getState() {
            return mState;
        }

        protected void updateImage() {
            setImage(mImage[getState()]);
        }
    }

    /**
     * A label that can display 2 images depending on its enabled/disabled state.
     * This acts as a button by firing the {@link SWT#Selection} listener.
     */
    private static class ImgDisabledButton extends ToggleButton {
        public ImgDisabledButton(Composite parent, int style,
                Image imageEnabled, Image imageDisabled) {
            super(parent, style, imageEnabled, imageDisabled);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            updateImage();
            redraw();
        }

        @Override
        public void setState(int state) {
            throw new UnsupportedOperationException(); // not available for this type of button
        }

        @Override
        public int getState() {
            return (isDisposed() || !isEnabled()) ? 1 : 0;
        }
    }
}
