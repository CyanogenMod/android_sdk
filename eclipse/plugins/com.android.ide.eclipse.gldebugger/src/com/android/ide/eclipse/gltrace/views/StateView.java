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

package com.android.ide.eclipse.gltrace.views;

import com.android.ide.eclipse.gltrace.editors.GLFunctionTraceViewer;
import com.android.ide.eclipse.gltrace.editors.StateViewPage;

import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.IPage;
import org.eclipse.ui.part.MessagePage;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.PageBookView;

/**
 * The StateView shows the GL state for the current active {@link GLFunctionTraceViewer}.
 * It behaves like the Eclipse Outline View: Each active editor provides the GL state content
 * to show via a {@link StateViewPage}. This class simply acts as a stack view showing the
 * state corresponding to whichever editor is active.
 */
public class StateView extends PageBookView {
    public static final String ID = "com.android.ide.eclipse.gltrace.views.State"; //$NON-NLS-1$

    @Override
    protected IPage createDefaultPage(PageBook book) {
        MessagePage page = new MessagePage();
        initPage(page);
        page.createControl(book);
        page.setMessage("Open (or select) a GL Trace file to view the GL State.");
        return page;
    }

    @Override
    protected PageRec doCreatePage(IWorkbenchPart part) {
        if (!(part instanceof GLFunctionTraceViewer)) {
            return null;
        }

        GLFunctionTraceViewer viewer = (GLFunctionTraceViewer) part;
        StateViewPage page = viewer.getStateViewPage();
        initPage(page);
        page.createControl(getPageBook());

        return new PageRec(part, page);
    }

    @Override
    protected void doDestroyPage(IWorkbenchPart part, PageRec pageRecord) {
        StateViewPage v = (StateViewPage) pageRecord.page;
        v.dispose();
        pageRecord.dispose();
    }

    @Override
    protected IWorkbenchPart getBootstrapPart() {
        IWorkbenchPage page = getSite().getPage();
        if (page != null) {
            return page.getActiveEditor();
        }

        return null;
    }

    @Override
    protected boolean isImportant(IWorkbenchPart part) {
        return part instanceof GLFunctionTraceViewer;
    }

    @Override
    public void partBroughtToTop(IWorkbenchPart part) {
        partActivated(part);
    }
}
