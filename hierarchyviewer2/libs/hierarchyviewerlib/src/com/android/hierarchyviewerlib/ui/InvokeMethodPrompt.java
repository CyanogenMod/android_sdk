/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.hierarchyviewerlib.ui;

import com.android.ddmlib.Log;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.device.IHvDevice;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.TreeViewModel.ITreeChangeListener;
import com.android.hierarchyviewerlib.models.ViewNode;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class InvokeMethodPrompt extends Composite implements ITreeChangeListener {
    private TreeViewModel mModel;
    private DrawableViewNode mSelectedNode;
    private Text mText;
    private static final Splitter CMD_SPLITTER = Splitter.on(CharMatcher.anyOf(", "))
                                                         .trimResults().omitEmptyStrings();

    public InvokeMethodPrompt(Composite parent) {
        super(parent, SWT.NONE);
        setLayout(new FillLayout());

        mText = new Text(this, SWT.BORDER);
        mText.addKeyListener(new KeyListener() {
            @Override
            public void keyReleased(KeyEvent ke) {
            }

            @Override
            public void keyPressed(KeyEvent ke) {
                onKeyPress(ke);
            }
        });

        mModel = TreeViewModel.getModel();
        mModel.addTreeChangeListener(this);
    }

    private void onKeyPress(KeyEvent ke) {
        if (ke.keyCode == SWT.CR) {
            String cmd = mText.getText().trim();
            if (!cmd.isEmpty()) {
                invokeViewMethod(cmd);
            }
            mText.setText("");
        }
    }

    private void invokeViewMethod(String cmd) {
        Iterator<String> segmentIterator = CMD_SPLITTER.split(cmd).iterator();

        String method = null;
        if (segmentIterator.hasNext()) {
            method = segmentIterator.next();
        } else {
            return;
        }

        List<Object> args = new ArrayList<Object>(10);
        while (segmentIterator.hasNext()) {
            String arg = segmentIterator.next();

            // check for boolean
            if (arg.equalsIgnoreCase("true")) {
                args.add(Boolean.TRUE);
                continue;
            } else if (arg.equalsIgnoreCase("false")) {
                args.add(Boolean.FALSE);
                continue;
            }

            // see if last character gives a clue regarding the argument type
            char typeSpecifier = Character.toUpperCase(arg.charAt(arg.length() - 1));
            try {
                switch (typeSpecifier) {
                    case 'L':
                        args.add(Long.valueOf(arg.substring(0, arg.length())));
                        break;
                    case 'D':
                        args.add(Double.valueOf(arg.substring(0, arg.length())));
                        break;
                    case 'F':
                        args.add(Float.valueOf(arg.substring(0, arg.length())));
                        break;
                    case 'S':
                        args.add(Short.valueOf(arg.substring(0, arg.length())));
                        break;
                    case 'B':
                        args.add(Byte.valueOf(arg.substring(0, arg.length())));
                        break;
                    default: // default to integer
                        args.add(Integer.valueOf(arg));
                        break;
                }
            } catch (NumberFormatException e) {
                Log.e("hv", "Unable to parse method argument: " + arg);
                return;
            }
        }

        HierarchyViewerDirector.getDirector().invokeMethodOnSelectedView(method, args);
    }

    @Override
    public void selectionChanged() {
        mSelectedNode = mModel.getSelection();
        refresh();
    }

    private boolean isViewUpdateEnabled(ViewNode viewNode) {
        IHvDevice device = viewNode.window.getHvDevice();
        return device != null && device.isViewUpdateEnabled();
    }

    private void refresh() {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                mText.setEnabled(mSelectedNode != null
                        && isViewUpdateEnabled(mSelectedNode.viewNode));
            }
        });
    }

    @Override
    public void treeChanged() {
        selectionChanged();
    }

    @Override
    public void viewportChanged() {
    }

    @Override
    public void zoomChanged() {
    }
}
