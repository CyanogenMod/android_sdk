/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.resources;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor.ATTRIBUTE_ICON_FILENAME;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.ITEM_TAG;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.NAME_ATTR;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.STYLE_ELEMENT;
import static com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData.DESCRIPTOR_LAYOUT;

import com.android.annotations.VisibleForTesting;
import com.android.ide.eclipse.adt.internal.editors.AndroidContentAssist;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.IDescriptorProvider;
import com.android.ide.eclipse.adt.internal.editors.descriptors.SeparatorAttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiAttributeNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;

import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Content Assist Processor for /res/values and /res/drawable XML files
 * <p>
 * Further enhancements:
 * <ul>
 *   <li> Complete prefixes in the style element itself for the name attribute
 *   <li> Complete parent names
 * </ul>
 */
@VisibleForTesting
public class ResourcesContentAssist extends AndroidContentAssist {

    /**
     * Constructor for ResourcesContentAssist
     */
    public ResourcesContentAssist() {
        super(AndroidTargetData.DESCRIPTOR_RESOURCES);
    }

    @Override
    protected void computeAttributeValues(List<ICompletionProposal> proposals, int offset,
            String parentTagName, String attributeName, Node node, String wordPrefix,
            boolean skipEndTag, int replaceLength) {
        super.computeAttributeValues(proposals, offset, parentTagName, attributeName, node,
                wordPrefix, skipEndTag, replaceLength);

        if (parentTagName.equals(ITEM_TAG) && NAME_ATTR.equals(attributeName)) {

            // Special case: the user is code completing inside
            //    <style><item name="^"/></style>
            // In this case, ALL attributes are valid so we need to synthesize
            // a choice list from all the layout descriptors

            // Add in android: as a completion item?
            if (startsWith(ANDROID_NS_NAME_PREFIX, wordPrefix)) {
                proposals.add(new CompletionProposal(ANDROID_NS_NAME_PREFIX,
                        offset - wordPrefix.length(), // replacementOffset
                        wordPrefix.length() + replaceLength, // replacementLength
                        ANDROID_NS_NAME_PREFIX.length(), // cursorPosition
                        IconFactory.getInstance().getIcon(ATTRIBUTE_ICON_FILENAME),
                        null, null, null));
            }


            String attributePrefix = wordPrefix;
            if (startsWith(attributePrefix, ANDROID_NS_NAME_PREFIX)) {
                attributePrefix = attributePrefix.substring(ANDROID_NS_NAME_PREFIX.length());
            }

            AndroidTargetData data = mEditor.getTargetData();
            if (data != null) {
                IDescriptorProvider descriptorProvider =
                    data.getDescriptorProvider(
                            AndroidTargetData.DESCRIPTOR_LAYOUT);
                if (descriptorProvider != null) {
                    ElementDescriptor[] rootElementDescriptors =
                        descriptorProvider.getRootElementDescriptors();
                    Map<String, AttributeDescriptor> matches =
                        new HashMap<String, AttributeDescriptor>(180);
                    for (ElementDescriptor elementDesc : rootElementDescriptors) {
                        for (AttributeDescriptor desc : elementDesc.getAttributes()) {
                            if (desc instanceof SeparatorAttributeDescriptor) {
                                continue;
                            }
                            String name = desc.getXmlLocalName();
                            if (startsWith(name, attributePrefix)) {
                                matches.put(name, desc);
                            }
                        }
                    }

                    List<AttributeDescriptor> sorted =
                        new ArrayList<AttributeDescriptor>(matches.size());
                    sorted.addAll(matches.values());
                    Collections.sort(sorted);
                    char needTag = 0;
                    addMatchingProposals(proposals, sorted.toArray(), offset, node, wordPrefix,
                            needTag, true /* isAttribute */, false /* isNew */,
                            skipEndTag /* skipEndTag */, replaceLength);
                    return;
                }
            }
        }
    }

    @Override
    protected void computeTextValues(List<ICompletionProposal> proposals, int offset,
            Node parentNode, Node currentNode, UiElementNode uiParent,
            String prefix) {
        super.computeTextValues(proposals, offset, parentNode, currentNode, uiParent,
                prefix);

        if (parentNode.getNodeName().equals(ITEM_TAG) &&
            parentNode.getParentNode() != null &&
            STYLE_ELEMENT.equals(parentNode.getParentNode().getNodeName())) {

            // Special case: the user is code completing inside
            //    <style><item name="android:foo"/>|</style>
            // In this case, we need to find the right AttributeDescriptor
            // for the given attribute and offer its values

            AndroidTargetData data = mEditor.getTargetData();
            if (data != null) {
                IDescriptorProvider descriptorProvider =
                    data.getDescriptorProvider(DESCRIPTOR_LAYOUT);
                if (descriptorProvider != null) {

                    Element element = (Element) parentNode;
                    String attrName = element.getAttribute(NAME_ATTR);
                    int pos = attrName.indexOf(':');
                    if (pos >= 0) {
                        attrName = attrName.substring(pos + 1);
                    }

                    // Search for an attribute match
                    ElementDescriptor[] rootElementDescriptors =
                        descriptorProvider.getRootElementDescriptors();
                    for (ElementDescriptor elementDesc : rootElementDescriptors) {
                        for (AttributeDescriptor desc : elementDesc.getAttributes()) {
                            if (desc.getXmlLocalName().equals(attrName)) {
                                // Make a ui parent node such that we can attach our
                                // newfound attribute node to something (the code we delegate
                                // to for looking up attribute completions will look at the
                                // parent node and ask for its editor etc.)
                                if (uiParent == null) {
                                    DocumentDescriptor documentDescriptor =
                                        data.getLayoutDescriptors().getDescriptor();
                                    uiParent = documentDescriptor.createUiNode();
                                    uiParent.setEditor(mEditor);
                                }

                                UiAttributeNode currAttrNode = desc.createUiNode(uiParent);
                                AttribInfo attrInfo = new AttribInfo();
                                Object[] values = getAttributeValueChoices(currAttrNode, attrInfo,
                                        prefix);
                                char needTag = attrInfo.needTag;
                                if (attrInfo.correctedPrefix != null) {
                                    prefix = attrInfo.correctedPrefix;
                                }
                                boolean isAttribute = true;
                                boolean isNew = false;
                                int replaceLength = computeTextReplaceLength(currentNode, offset);
                                addMatchingProposals(proposals, values, offset, currentNode,
                                        prefix, needTag, isAttribute, isNew,
                                        false /* skipEndTag */, replaceLength);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
