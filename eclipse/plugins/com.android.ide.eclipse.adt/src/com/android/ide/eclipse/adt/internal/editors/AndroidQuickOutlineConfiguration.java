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

package com.android.ide.eclipse.adt.internal.editors;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;
import static com.android.ide.common.layout.LayoutConstants.ATTR_NAME;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.wst.xml.ui.internal.contentoutline.JFaceNodeLabelProvider;
import org.eclipse.wst.xml.ui.internal.quickoutline.XMLQuickOutlineConfiguration;
import org.w3c.dom.Element;

/**
 * Custom version of {@link XMLQuickOutlineConfiguration} which adds in icons and
 * details such as id or name, to the labels.
 */
@SuppressWarnings("restriction")
public class AndroidQuickOutlineConfiguration extends XMLQuickOutlineConfiguration {
    public AndroidQuickOutlineConfiguration() {
    }

    @Override
    public ILabelProvider getLabelProvider() {
        return new JFaceNodeLabelProvider() {
            @Override
            public Image getImage(Object element) {
                if (element instanceof Element) {
                    Element e = (Element) element;
                    String tagName = e.getTagName();
                    IconFactory factory = IconFactory.getInstance();
                    Image img = factory.getIcon(tagName);
                    if (img != null) {
                        return img;
                    }
                }
                return super.getImage(element);
            }

            @Override
            public String getText(Object element) {
                String text = super.getText(element);
                if (element instanceof Element) {
                    Element e = (Element) element;
                    String id = e.getAttributeNS(ANDROID_URI, ATTR_ID);
                    if (id == null || id.length() == 0) {
                        id = e.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    }
                    if (id == null || id.length() == 0) {
                        id = e.getAttribute(ATTR_NAME);
                    }
                    if (id != null && id.length() > 0) {
                        return text + ": " + id; //$NON-NLS-1$
                    }
                }
                return text;
            }
        };
    }

}
