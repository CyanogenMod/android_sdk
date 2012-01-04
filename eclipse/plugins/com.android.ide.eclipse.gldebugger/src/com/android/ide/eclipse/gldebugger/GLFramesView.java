/*
 ** Copyright 2011, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

package com.android.ide.eclipse.gldebugger;

import com.android.ide.eclipse.gldebugger.DebuggerMessage.Message;
import com.android.ide.eclipse.gldebugger.DebuggerMessage.Message.Function;
import com.android.ide.eclipse.gldebugger.DebuggerMessage.Message.Prop;
import com.android.ide.eclipse.gldebugger.DebuggerMessage.Message.Type;
import com.android.sdklib.util.SparseArray;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.part.ViewPart;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.ByteOrder;

public class GLFramesView extends ViewPart implements Runnable {
    public static final ByteOrder TARGET_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private boolean mRunning = false;
    private Thread mThread;

    MessageQueue messageQueue;
    SparseArray<DebugContext> debugContexts = new SparseArray<DebugContext>();

    TabFolder tabFolder;
    TabItem tabItemText, tabItemImage, tabItemBreakpointOption;
    TabItem tabItemShaderEditor, tabContextViewer;

    private ListViewer mViewer; // ListViewer / TableViewer
    private Scale mFrameScale; // scale max cannot overlap min, so max is array size
    private Spinner mFrameNumberspinner;
    private TreeViewer mContextViewer;
    private BreakpointOption mBreakpointOption;
    private ShaderEditor mShaderEditor;
    Canvas canvas;
    private Text mText;
    private Action mActionConnect; // connect / disconnect

    private Action mActionAutoScroll;
    private Action mActionFilter;
    Action actionPort;

    private Action mActionContext; // for toggling contexts
    DebugContext current = null;

    private Point mOrigin = new Point(0, 0); // for smooth scrolling canvas
    private String[] mTextFilters = null;

    private static class ViewContentProvider extends LabelProvider implements IStructuredContentProvider {
        private Frame mFrame = null;

        @Override
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
            mFrame = (Frame) newInput;
        }

        @Override
        public void dispose() {
        }

        @Override
        public Object[] getElements(Object parent) {
            return mFrame.get().toArray();
        }

        @Override
        public String getText(Object obj) {
            MessageData msgData = (MessageData) obj;
            return msgData.text;
        }

        @Override
        public Image getImage(Object obj) {
            MessageData msgData = (MessageData) obj;
            return msgData.getImage();
        }
    }

    private class Filter extends ViewerFilter {
        @Override
        public boolean select(Viewer viewer, Object parentElement,
                Object element) {
            MessageData msgData = (MessageData) element;
            if (null == mTextFilters)
                return true;
            for (int i = 0; i < mTextFilters.length; i++)
                if (msgData.text.contains(mTextFilters[i]))
                    return true;
            return false;
        }
    }

    private void createLeftPane(Composite parent) {
        Composite composite = new Composite(parent, 0);

        GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 3;
        composite.setLayout(gridLayout);
        composite.setLayoutData(new GridData(GridData.FILL_BOTH));

        // Frame: -------|slider|--------- [ Spinner ]
        Label l = new Label(composite, SWT.NONE);
        l.setText("Frame:");

        mFrameScale = new Scale(composite, SWT.BORDER | SWT.HORIZONTAL);
        mFrameScale.setMinimum(0);
        mFrameScale.setMaximum(1);
        mFrameScale.setSelection(0);
        mFrameScale.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (current == null) {
                    return;
                }
                int selectedFrame = mFrameScale.getSelection();
                mFrameNumberspinner.setSelection(selectedFrame);
                selectFrame(selectedFrame);
            }
        });

        GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
        mFrameScale.setLayoutData(gridData);

        mFrameNumberspinner = new Spinner(composite, SWT.BORDER);
        mFrameNumberspinner.setMinimum(0);
        mFrameNumberspinner.setMaximum(1);
        mFrameNumberspinner.setSelection(0);
        mFrameNumberspinner.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (current == null) {
                    return;
                }
                int selectedFrame = mFrameNumberspinner.getSelection();
                mFrameScale.setSelection(selectedFrame);
                selectFrame(selectedFrame);
            }
        });

        mViewer = new ListViewer(composite, SWT.DEFAULT);
        mViewer.getList().setFont(new Font(mViewer.getList().getDisplay(),
                "Courier", 10, SWT.BOLD));
        ViewContentProvider contentProvider = new ViewContentProvider();
        mViewer.setContentProvider(contentProvider);
        mViewer.setLabelProvider(contentProvider);
        mViewer.setFilters(new ViewerFilter[] {
                new Filter()
        });

        gridData = new GridData(GridData.FILL_BOTH);
        gridData.horizontalSpan = 3;
        mViewer.getControl().setLayoutData(gridData);
    }

    private void selectFrame(int frameNumber) {
        if (frameNumber == current.frameCount()) {
            return; // scale maximum cannot overlap minimum
        }

        Frame frame = current.getFrame(frameNumber);
        mViewer.setInput(frame);
    }

    @Override
    public void createPartControl(Composite parent) {
        createLeftPane(parent);

        tabFolder = new TabFolder(parent, SWT.BORDER);

        mText = new Text(tabFolder, SWT.NO_BACKGROUND | SWT.READ_ONLY
                | SWT.V_SCROLL | SWT.H_SCROLL);

        tabItemText = new TabItem(tabFolder, SWT.NONE);
        tabItemText.setText("Text");
        tabItemText.setControl(mText);

        canvas = new Canvas(tabFolder, SWT.NO_BACKGROUND | SWT.NO_REDRAW_RESIZE
                | SWT.V_SCROLL | SWT.H_SCROLL);
        tabItemImage = new TabItem(tabFolder, SWT.NONE);
        tabItemImage.setText("Image");
        tabItemImage.setControl(canvas);

        mBreakpointOption = new BreakpointOption(this, tabFolder);
        tabItemBreakpointOption = new TabItem(tabFolder, SWT.NONE);
        tabItemBreakpointOption.setText("Breakpoint Option");
        tabItemBreakpointOption.setControl(mBreakpointOption);

        mShaderEditor = new ShaderEditor(this, tabFolder);
        tabItemShaderEditor = new TabItem(tabFolder, SWT.NONE);
        tabItemShaderEditor.setText("Shader Editor");
        tabItemShaderEditor.setControl(mShaderEditor);

        mContextViewer = new TreeViewer(tabFolder);
        ContextViewProvider contextViewProvider = new ContextViewProvider(this);
        mContextViewer.addSelectionChangedListener(contextViewProvider);
        mContextViewer.setContentProvider(contextViewProvider);
        mContextViewer.setLabelProvider(contextViewProvider);
        tabContextViewer = new TabItem(tabFolder, SWT.NONE);
        tabContextViewer.setText("Context Viewer");
        tabContextViewer.setControl(mContextViewer.getTree());

        final ScrollBar hBar = canvas.getHorizontalBar();
        hBar.addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(Event e) {
                if (null == canvas.getBackgroundImage())
                    return;
                Image image = canvas.getBackgroundImage();
                int hSelection = hBar.getSelection();
                int destX = -hSelection - mOrigin.x;
                Rectangle rect = image.getBounds();
                canvas.scroll(destX, 0, 0, 0, rect.width, rect.height, false);
                mOrigin.x = -hSelection;
            }
        });
        final ScrollBar vBar = canvas.getVerticalBar();
        vBar.addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(Event e) {
                if (null == canvas.getBackgroundImage())
                    return;
                Image image = canvas.getBackgroundImage();
                int vSelection = vBar.getSelection();
                int destY = -vSelection - mOrigin.y;
                Rectangle rect = image.getBounds();
                canvas.scroll(0, destY, 0, 0, rect.width, rect.height, false);
                mOrigin.y = -vSelection;
            }
        });
        canvas.addListener(SWT.Resize, new Listener() {
            @Override
            public void handleEvent(Event e) {
                if (null == canvas.getBackgroundImage())
                    return;
                Image image = canvas.getBackgroundImage();
                Rectangle rect = image.getBounds();
                Rectangle client = canvas.getClientArea();
                hBar.setMaximum(rect.width);
                vBar.setMaximum(rect.height);
                hBar.setThumb(Math.min(rect.width, client.width));
                vBar.setThumb(Math.min(rect.height, client.height));
                int hPage = rect.width - client.width;
                int vPage = rect.height - client.height;
                int hSelection = hBar.getSelection();
                int vSelection = vBar.getSelection();
                if (hSelection >= hPage) {
                    if (hPage <= 0)
                        hSelection = 0;
                    mOrigin.x = -hSelection;
                }
                if (vSelection >= vPage) {
                    if (vPage <= 0)
                        vSelection = 0;
                    mOrigin.y = -vSelection;
                }
                canvas.redraw();
            }
        });
        canvas.addListener(SWT.Paint, new Listener() {
            @Override
            public void handleEvent(Event e) {
                if (null == canvas.getBackgroundImage())
                    return;
                Image image = canvas.getBackgroundImage();
                GC gc = e.gc;
                gc.drawImage(image, mOrigin.x, mOrigin.y);
                Rectangle rect = image.getBounds();
                Rectangle client = canvas.getClientArea();
                int marginWidth = client.width - rect.width;
                if (marginWidth > 0) {
                    gc.fillRectangle(rect.width, 0, marginWidth, client.height);
                }
                int marginHeight = client.height - rect.height;
                if (marginHeight > 0) {
                    gc.fillRectangle(0, rect.height, client.width, marginHeight);
                }
            }
        });

        hookContextMenu();
        hookSelectionChanged();
        contributeToActionBars();

        messageQueue = new MessageQueue(this, new ProcessMessage[] {
                mBreakpointOption, mShaderEditor
        });
    }

    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#PopupMenu");
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                GLFramesView.this.fillContextMenu(manager);
            }
        });
        Menu menu = menuMgr.createContextMenu(mViewer.getControl());
        mViewer.getControl().setMenu(menu);
        getSite().registerContextMenu(menuMgr, mViewer);
    }

    private void contributeToActionBars() {
        IActionBars bars = getViewSite().getActionBars();
        fillLocalToolBar(bars.getToolBarManager());
    }

    private void fillContextMenu(IMenuManager manager) {
        // Other plug-ins can contribute there actions here
        manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
    }

    private void fillLocalToolBar(final IToolBarManager manager) {
        mActionConnect = new Action("Connect", Action.AS_PUSH_BUTTON) {
            @Override
            public void run() {
                if (!mRunning)
                    changeContext(null); // viewer will switch to newest context
                connectDisconnect();
            }
        };
        manager.add(mActionConnect);

        manager.add(new Action("Open File", Action.AS_PUSH_BUTTON) {
            @Override
            public void run() {
                if (!mRunning) {
                    changeContext(null); // viewer will switch to newest context
                    openFile();
                }
            }
        });

        final Shell shell = this.getViewSite().getShell();
        mActionAutoScroll = new Action("Auto Scroll", Action.AS_CHECK_BOX) {
            @Override
            public void run() {
            }
        };
        mActionAutoScroll.setChecked(true);
        manager.add(mActionAutoScroll);

        mActionFilter = new Action("*", Action.AS_DROP_DOWN_MENU) {
            @Override
            public void run() {
                InputDialog dialog = new InputDialog(
                        shell, "Contains Filter",
                        "case sensitive substring or *",
                        mActionFilter.getText(), null);
                if (Window.OK == dialog.open()) {
                    mActionFilter.setText(dialog.getValue());
                    manager.update(true);
                    mTextFilters = dialog.getValue().split("\\|");
                    if (mTextFilters.length == 1 && mTextFilters[0].equals("*"))
                        mTextFilters = null;
                    mViewer.refresh();
                }

            }
        };
        manager.add(mActionFilter);

        manager.add(new Action("CaptureDraw", Action.AS_DROP_DOWN_MENU) {
            @Override
            public void run() {
                int contextId = 0;
                if (current != null)
                    contextId = current.contextId;
                InputDialog inputDialog = new InputDialog(shell,
                        "Capture glDrawArrays/Elements",
                        "Enter number of glDrawArrays/Elements to glReadPixels for "
                                + "context 0x" + Integer.toHexString(contextId) +
                                "\n(0x0 is any context)", "9001", null);
                if (inputDialog.open() != Window.OK)
                    return;
                Message.Builder builder = Message.newBuilder();
                builder.setContextId(contextId);
                builder.setType(Type.Response);
                builder.setExpectResponse(false);
                builder.setFunction(Function.SETPROP);
                builder.setProp(Prop.CaptureDraw);
                builder.setArg0(Integer.parseInt(inputDialog.getValue()));
                messageQueue.addCommand(builder.build());
            }
        });

        manager.add(new Action("CaptureSwap", Action.AS_DROP_DOWN_MENU) {
            @Override
            public void run() {
                int contextId = 0;
                if (current != null)
                    contextId = current.contextId;
                InputDialog inputDialog = new InputDialog(shell,
                        "Capture eglSwapBuffers",
                        "Enter number of eglSwapBuffers to glReadPixels for "
                                + "context 0x" + Integer.toHexString(contextId) +
                                "\n(0x0 is any context)", "9001", null);
                if (inputDialog.open() != Window.OK)
                    return;
                Message.Builder builder = Message.newBuilder();
                builder.setContextId(contextId);
                builder.setType(Type.Response);
                builder.setExpectResponse(false);
                builder.setFunction(Function.SETPROP);
                builder.setProp(Prop.CaptureSwap);
                builder.setArg0(Integer.parseInt(inputDialog.getValue()));
                messageQueue.addCommand(builder.build());
            }
        });

        manager.add(new Action("SYSTEM_TIME_THREAD", Action.AS_DROP_DOWN_MENU) {
            @Override
            public void run() {
                final String[] timeModes = {
                        "SYSTEM_TIME_REALTIME", "SYSTEM_TIME_MONOTONIC", "SYSTEM_TIME_PROCESS",
                        "SYSTEM_TIME_THREAD"
                };
                int i = java.util.Arrays.asList(timeModes).indexOf(this.getText());
                i = (i + 1) % timeModes.length;
                Message.Builder builder = Message.newBuilder();
                builder.setContextId(0); // FIXME: proper context id
                builder.setType(Type.Response);
                builder.setExpectResponse(false);
                builder.setFunction(Message.Function.SETPROP);
                builder.setProp(Prop.TimeMode);
                builder.setArg0(i);
                messageQueue.addCommand(builder.build());
                this.setText(timeModes[i]);
                manager.update(true);
            }
        });

        mActionContext = new Action("Context: 0x", Action.AS_DROP_DOWN_MENU) {
            @Override
            public void run() {
                if (debugContexts.size() < 2)
                    return;
                final String idStr = this.getText().substring(
                                          "Context: 0x".length());
                if (idStr.length() == 0)
                    return;
                final int contextId = Integer.parseInt(idStr, 16);
                int index = debugContexts.indexOfKey(contextId);
                index = (index + 1) % debugContexts.size();
                changeContext(debugContexts.valueAt(index));
            }
        };
        manager.add(mActionContext);

        actionPort = new Action("5039", Action.AS_DROP_DOWN_MENU) {
            @Override
            public void run() {
                InputDialog dialog = new InputDialog(shell, "Port", "Debugger port",
                        actionPort.getText(), null);
                if (Window.OK == dialog.open()) {
                    actionPort.setText(dialog.getValue());
                    manager.update(true);
                }
            }
        };
        manager.add(actionPort);

        manager.add(new Action("CodeGen Frame", Action.AS_PUSH_BUTTON) {
            @Override
            public void run() {
                if (current != null) {
                    new CodeGen().codeGenFrame((Frame) mViewer.getInput());
                    // need to reload current frame
                    mViewer.setInput(current.getFrame(mFrameScale.getSelection()));
                }
            }
        });

        manager.add(new Action("CodeGen Frames", Action.AS_PUSH_BUTTON) {
            @Override
            public void run() {
                if (current != null) {
                    new CodeGen().codeGenFrames(current, mFrameScale.getSelection() + 1,
                            getSite().getShell());
                    // need to reload current frame
                    mViewer.setInput(current.getFrame(mFrameScale.getSelection()));
                }
            }
        });
    }

    private void openFile() {
        FileDialog dialog = new FileDialog(getSite().getShell(), SWT.OPEN);
        dialog.setText("Open");
        dialog.setFilterExtensions(new String[] {
                "*.gles2dbg"
        });
        String filePath = dialog.open();
        if (filePath == null)
            return;
        FileInputStream file = null;
        try {
            file = new FileInputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        mRunning = true;
        messageQueue.start(TARGET_BYTE_ORDER, file);
        mThread = new Thread(this);
        mThread.start();
        mActionConnect.setText("Disconnect");
        getViewSite().getActionBars().getToolBarManager().update(true);
    }

    private void connectDisconnect() {
        if (!mRunning) {
            mRunning = true;
            messageQueue.start(TARGET_BYTE_ORDER, null);
            mThread = new Thread(this);
            mThread.start();
            mActionConnect.setText("Disconnect");
        } else {
            mRunning = false;
            messageQueue.stop();
            mActionConnect.setText("Connect");
        }
        this.getSite().getShell().getDisplay().syncExec(new Runnable() {
            @Override
            public void run() {
                getViewSite().getActionBars().getToolBarManager().update(true);
            }
        });
    }

    void messageDataSelected(final MessageData msgData) {
        if (null == msgData)
            return;
        if (mFrameScale.getSelection() == mFrameScale.getMaximum())
            return; // scale max cannot overlap min, so max is array size
        final Frame frame = current.getFrame(mFrameScale.getSelection());
        final Context context = frame.computeContext(msgData);
        mContextViewer.setInput(context);
        if (msgData.getImage() != null) {
            canvas.setBackgroundImage(msgData.getImage());
            tabFolder.setSelection(tabItemImage);
            canvas.redraw();
        } else if (null != msgData.shader) {
            mText.setText(msgData.shader);
            tabFolder.setSelection(tabItemText);
        } else if (null != msgData.attribs) {
            StringBuilder builder = new StringBuilder();
            final int maxAttrib = msgData.msg.getArg7();
            for (int i = 0; i < msgData.attribs[0].length / 4; i++) {
                if (msgData.indices != null) {
                    builder.append(msgData.indices[i] & 0xffff);
                    builder.append(": ");
                }
                for (int j = 0; j < maxAttrib; j++) {
                    for (int k = 0; k < 4; k++)
                        builder.append(String.format("%.3g ", msgData.attribs[j][i * 4 + k]));
                    if (j < maxAttrib - 1)
                        builder.append("|| ");
                }
                builder.append('\n');
            }
            mText.setText(builder.toString());
            tabFolder.setSelection(tabItemText);
        }
    }

    private void hookSelectionChanged() {
        mViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                StructuredSelection selection = (StructuredSelection) event
                        .getSelection();
                if (null == selection)
                    return;
                MessageData msgData = (MessageData) selection.getFirstElement();
                messageDataSelected(msgData);
            }
        });
    }

    public void showError(final Exception e) {
        mViewer.getControl().getDisplay().syncExec(new Runnable() {
            @Override
            public void run() {
                MessageDialog.openError(mViewer.getControl().getShell(),
                        "GL ES 2.0 Debugger Client", e.getMessage());
            }
        });
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    @Override
    public void setFocus() {
        mViewer.getControl().setFocus();
    }

    @Override
    public void run() {
        int newMessages = 0;

        boolean shaderEditorUpdate = false;
        while (mRunning) {
            final Message oriMsg = messageQueue.removeCompleteMessage(0);
            if (oriMsg == null && !messageQueue.isRunning())
                break;
            if (newMessages > 60 || (newMessages > 0 && null == oriMsg)) {
                newMessages = 0;
                if (current != null && current.uiUpdate)
                    getSite().getShell().getDisplay().syncExec(new Runnable() {
                        @Override
                        public void run() {
                            if (mFrameScale.getSelection() == current.frameCount() - 1 ||
                                    mFrameScale.getSelection() == current.frameCount() - 2)
                            {
                                mViewer.refresh(false);
                                if (mActionAutoScroll.isChecked())
                                    mViewer.getList().setSelection(
                                            mViewer.getList().getItemCount() - 1);
                            }
                            setMaxFrameCount(current.frameCount());
                        }
                    });
                current.uiUpdate = false;

                if (shaderEditorUpdate)
                    this.getSite().getShell().getDisplay().syncExec(new Runnable() {
                        @Override
                        public void run() {
                            mShaderEditor.updateUI();
                        }
                    });
                shaderEditorUpdate = false;
            }
            if (null == oriMsg) {
                try {
                    Thread.sleep(1);
                    continue;
                } catch (InterruptedException e) {
                    showError(e);
                }
            }
            DebugContext debugContext = debugContexts.get(oriMsg.getContextId());
            if (debugContext == null) {
                debugContext = new DebugContext(oriMsg.getContextId());
                debugContexts.put(oriMsg.getContextId(), debugContext);
            }
            debugContext.processMessage(oriMsg);
            shaderEditorUpdate |= debugContext.currentContext.serverShader.uiUpdate;
            debugContext.currentContext.serverShader.uiUpdate = false;
            if (current == null && debugContext.frameCount() > 0)
                changeContext(debugContext);
            newMessages++;
        }
        if (mRunning)
            connectDisconnect(); // error occurred, disconnect
    }

    private void setMaxFrameCount(int frameCount) {
        mFrameScale.setMaximum(frameCount);
        mFrameNumberspinner.setMaximum(frameCount);
    }

    private void setSelectedFrame(int frameNumber) {
        mFrameScale.setSelection(frameNumber);
        mFrameNumberspinner.setSelection(frameNumber);
    }

    /** can be called from non-UI thread */
    void changeContext(final DebugContext newContext) {
        getSite().getShell().getDisplay().syncExec(new Runnable() {
            @Override
            public void run() {
                current = newContext;
                if (current != null) {
                    setMaxFrameCount(current.frameCount());

                    int frame = Math.min(mFrameScale.getSelection(), current.frameCount() - 1);
                    if (frame < 0) {
                        frame = 0;
                    }
                    setSelectedFrame(frame);
                    mViewer.setInput(current.getFrame(frame));
                    mActionContext.setText("Context: 0x" + Integer.toHexString(current.contextId));
                } else {
                    setMaxFrameCount(1);
                    setSelectedFrame(0);

                    mViewer.setInput(null);
                    mActionContext.setText("Context: 0x");
                }
                mShaderEditor.updateUI();
                getViewSite().getActionBars().getToolBarManager().update(true);
            }
        });
    }
}
