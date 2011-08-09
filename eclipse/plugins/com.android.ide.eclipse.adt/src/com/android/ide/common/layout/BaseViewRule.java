/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ide.common.layout;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WIDTH;
import static com.android.ide.common.layout.LayoutConstants.ATTR_TEXT;
import static com.android.ide.common.layout.LayoutConstants.ID_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.NEW_ID_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.VALUE_FILL_PARENT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_MATCH_PARENT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_WRAP_CONTENT;

import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IAttributeInfo;
import com.android.ide.common.api.IAttributeInfo.Format;
import com.android.ide.common.api.IClientRulesEngine;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.IGraphics;
import com.android.ide.common.api.IMenuCallback;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.IValidator;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.InsertType;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;
import com.android.ide.common.api.RuleAction;
import com.android.ide.common.api.RuleAction.ActionProvider;
import com.android.ide.common.api.RuleAction.ChoiceProvider;
import com.android.ide.common.api.RuleAction.Choices;
import com.android.ide.common.api.SegmentType;
import com.android.util.Pair;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Common IViewRule processing to all view and layout classes.
 */
public class BaseViewRule implements IViewRule {
    // Strings used as internal ids, group ids and prefixes for actions
    private static final String FALSE_ID = "false"; //$NON-NLS-1$
    private static final String TRUE_ID = "true"; //$NON-NLS-1$
    private static final String PROP_PREFIX = "@prop@"; //$NON-NLS-1$
    private static final String CLEAR_ID = "clear"; //$NON-NLS-1$
    private static final String PROPERTIES_ID = "properties"; //$NON-NLS-1$
    private static final String EDIT_TEXT_ID = "edittext"; //$NON-NLS-1$
    private static final String EDIT_ID_ID = "editid"; //$NON-NLS-1$
    private static final String WIDTH_ID = "layout_width"; //$NON-NLS-1$
    private static final String HEIGHT_ID = "layout_height"; //$NON-NLS-1$
    private static final String ZCUSTOM = "zcustom"; //$NON-NLS-1$

    protected IClientRulesEngine mRulesEngine;

    // Cache of attributes. Key is FQCN of a node mixed with its view hierarchy
    // parent. Values are a custom map as needed by getContextMenu.
    private Map<String, Map<String, Prop>> mAttributesMap =
        new HashMap<String, Map<String, Prop>>();

    public boolean onInitialize(String fqcn, IClientRulesEngine engine) {
        this.mRulesEngine = engine;

        // This base rule can handle any class so we don't need to filter on
        // FQCN. Derived classes should do so if they can handle some
        // subclasses.

        // If onInitialize returns false, it means it can't handle the given
        // FQCN and will be unloaded.

        return true;
    }

    public void onDispose() {
        // Nothing to dispose.
    }

    public String getDisplayName() {
        // Default is to not override the selection display name.
        return null;
    }

    /**
     * Returns the {@link IClientRulesEngine} associated with this {@link IViewRule}
     *
     * @return the {@link IClientRulesEngine} associated with this {@link IViewRule}
     */
    public IClientRulesEngine getRulesEngine() {
        return mRulesEngine;
    }

    // === Context Menu ===

    /**
     * Generate custom actions for the context menu: <br/>
     * - Explicit layout_width and layout_height attributes.
     * - List of all other simple toggle attributes.
     */
    public void addContextMenuActions(List<RuleAction> actions, final INode selectedNode) {
        String width = null;
        String currentWidth = selectedNode.getStringAttr(ANDROID_URI, ATTR_LAYOUT_WIDTH);

        String fillParent = getFillParentValueName();
        boolean canMatchParent = supportsMatchParent();
        if (canMatchParent && VALUE_FILL_PARENT.equals(currentWidth)) {
            currentWidth = VALUE_MATCH_PARENT;
        } else if (!canMatchParent && VALUE_MATCH_PARENT.equals(currentWidth)) {
            currentWidth = VALUE_FILL_PARENT;
        } else if (!VALUE_WRAP_CONTENT.equals(currentWidth) && !fillParent.equals(currentWidth)) {
            width = currentWidth;
        }

        String height = null;
        String currentHeight = selectedNode.getStringAttr(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (canMatchParent && VALUE_FILL_PARENT.equals(currentHeight)) {
            currentHeight = VALUE_MATCH_PARENT;
        } else if (!canMatchParent && VALUE_MATCH_PARENT.equals(currentHeight)) {
            currentHeight = VALUE_FILL_PARENT;
        } else if (!VALUE_WRAP_CONTENT.equals(currentHeight)
                && !fillParent.equals(currentHeight)) {
            height = currentHeight;
        }
        final String newWidth = width;
        final String newHeight = height;

        final IMenuCallback onChange = new IMenuCallback() {
            public void action(
                    final RuleAction action,
                    final List<? extends INode> selectedNodes,
                    final String valueId, final Boolean newValue) {
                String fullActionId = action.getId();
                boolean isProp = fullActionId.startsWith(PROP_PREFIX);
                final String actionId = isProp ?
                        fullActionId.substring(PROP_PREFIX.length()) : fullActionId;

                if (fullActionId.equals(WIDTH_ID)) {
                    final String newAttrValue = getValue(valueId, newWidth);
                    if (newAttrValue != null) {
                        for (INode node : selectedNodes) {
                            node.editXml("Change Attribute " + ATTR_LAYOUT_WIDTH,
                                    new PropertySettingNodeHandler(ANDROID_URI,
                                            ATTR_LAYOUT_WIDTH, newAttrValue));
                        }
                    }
                    return;
                } else if (fullActionId.equals(HEIGHT_ID)) {
                    // Ask the user
                    final String newAttrValue = getValue(valueId, newHeight);
                    if (newAttrValue != null) {
                        for (INode node : selectedNodes) {
                            node.editXml("Change Attribute " + ATTR_LAYOUT_HEIGHT,
                                    new PropertySettingNodeHandler(ANDROID_URI,
                                            ATTR_LAYOUT_HEIGHT, newAttrValue));
                        }
                    }
                    return;
                } else if (fullActionId.equals(EDIT_ID_ID)) {
                    // Ids must be set individually so open the id dialog for each
                    // selected node (though allow cancel to break the loop)
                    for (INode node : selectedNodes) {
                        // Strip off the @id prefix stuff
                        String oldId = node.getStringAttr(ANDROID_URI, ATTR_ID);
                        oldId = stripIdPrefix(ensureValidString(oldId));
                        IValidator validator = mRulesEngine.getResourceValidator();
                        String newId = mRulesEngine.displayInput("New Id:", oldId, validator);
                        if (newId != null && newId.trim().length() > 0) {
                            if (!newId.startsWith(NEW_ID_PREFIX)) {
                                newId = NEW_ID_PREFIX + stripIdPrefix(newId);
                            }
                            node.editXml("Change ID", new PropertySettingNodeHandler(ANDROID_URI,
                                    ATTR_ID, newId));
                        } else if (newId == null) {
                            // Cancelled
                            break;
                        }
                    }
                    return;
                } else {
                    INode firstNode = selectedNodes.get(0);
                    if (fullActionId.equals(EDIT_TEXT_ID)) {
                        String oldText = selectedNodes.size() == 1
                            ? firstNode.getStringAttr(ANDROID_URI, ATTR_TEXT)
                            : ""; //$NON-NLS-1$
                        oldText = ensureValidString(oldText);
                        String newText = mRulesEngine.displayResourceInput("string", oldText); //$NON-NLS-1$
                        if (newText != null) {
                            for (INode node : selectedNodes) {
                                node.editXml("Change Text",
                                        new PropertySettingNodeHandler(ANDROID_URI,
                                                ATTR_TEXT, newText.length() > 0 ? newText : null));
                            }
                        }
                        return;
                    } else if (isProp) {
                        String key = getPropertyMapKey(selectedNode);
                        Map<String, Prop> props = mAttributesMap.get(key);
                        final Prop prop = (props != null) ? props.get(actionId) : null;

                        if (prop != null) {
                            // For custom values (requiring an input dialog) input the
                            // value outside the undo-block
                            final String customValue = prop.isStringEdit()
                                ? inputAttributeValue(firstNode, actionId) : null;

                            for (INode n : selectedNodes) {
                                if (prop.isToggle()) {
                                    // case of toggle
                                    String value = "";                  //$NON-NLS-1$
                                    if (valueId.equals(TRUE_ID)) {
                                        value = newValue ? "true" : ""; //$NON-NLS-1$ //$NON-NLS-2$
                                    } else if (valueId.equals(FALSE_ID)) {
                                        value = newValue ? "false" : "";//$NON-NLS-1$ //$NON-NLS-2$
                                    }
                                    n.setAttribute(ANDROID_URI, actionId, value);
                                } else if (prop.isFlag()) {
                                    // case of a flag
                                    String values = "";                 //$NON-NLS-1$
                                    if (!valueId.equals(CLEAR_ID)) {
                                        values = n.getStringAttr(ANDROID_URI, actionId);
                                        Set<String> newValues = new HashSet<String>();
                                        if (values != null) {
                                            newValues.addAll(Arrays.asList(
                                                    values.split("\\|"))); //$NON-NLS-1$
                                        }
                                        if (newValue) {
                                            newValues.add(valueId);
                                        } else {
                                            newValues.remove(valueId);
                                        }
                                        values = join('|', newValues);
                                    }
                                    n.setAttribute(ANDROID_URI, actionId, values);
                                } else if (prop.isEnum()) {
                                    // case of an enum
                                    String value = "";                   //$NON-NLS-1$
                                    if (!valueId.equals(CLEAR_ID)) {
                                        value = newValue ? valueId : ""; //$NON-NLS-1$
                                    }
                                    n.setAttribute(ANDROID_URI, actionId, value);
                                } else {
                                    assert prop.isStringEdit();
                                    // We've already received the value outside the undo block
                                    if (customValue != null) {
                                        n.setAttribute(ANDROID_URI, actionId, customValue);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /**
             * Input the custom value for the given attribute. This will use the Reference
             * Chooser if it is a reference value, otherwise a plain text editor.
             */
            private String inputAttributeValue(final INode node, final String attribute) {
                String oldValue = node.getStringAttr(ANDROID_URI, attribute);
                oldValue = ensureValidString(oldValue);
                IAttributeInfo attributeInfo = node.getAttributeInfo(ANDROID_URI, attribute);
                if (attributeInfo != null
                        && IAttributeInfo.Format.REFERENCE.in(attributeInfo.getFormats())) {
                    return mRulesEngine.displayReferenceInput(oldValue);
                } else {
                    // A single resource type? If so use a resource chooser initialized
                    // to this specific type
                    /* This does not work well, because the metadata is a bit misleading:
                     * for example a Button's "text" property and a Button's "onClick" property
                     * both claim to be of type [string], but @string/ is NOT valid for
                     * onClick..
                    if (attributeInfo != null && attributeInfo.getFormats().length == 1) {
                        // Resource chooser
                        Format format = attributeInfo.getFormats()[0];
                        return mRulesEngine.displayResourceInput(format.name(), oldValue);
                    }
                    */

                    // Fallback: just edit the raw XML string
                    String message = String.format("New %1$s Value:", attribute);
                    return mRulesEngine.displayInput(message, oldValue, null);
                }
            }

            /**
             * Returns the value (which will ask the user if the value is the special
             * {@link #ZCUSTOM} marker
             */
            private String getValue(String valueId, String defaultValue) {
                if (valueId.equals(ZCUSTOM)) {
                    if (defaultValue == null) {
                        defaultValue = "";
                    }
                    String value = mRulesEngine.displayInput(
                            "Set custom layout attribute value (example: 50dp)",
                            defaultValue, null);
                    if (value != null && value.trim().length() > 0) {
                        return value.trim();
                    } else {
                        return null;
                    }
                }

                return valueId;
            }
        };

        IAttributeInfo textAttribute = selectedNode.getAttributeInfo(ANDROID_URI, ATTR_TEXT);
        if (textAttribute != null) {
            actions.add(RuleAction.createAction(EDIT_TEXT_ID, "Edit Text...", onChange,
                    null, 10, true));
        }

        actions.add(RuleAction.createAction(EDIT_ID_ID, "Edit ID...", onChange, null, 20, true));

        // Create width choice submenu
        List<Pair<String, String>> widthChoices = new ArrayList<Pair<String,String>>(4);
        widthChoices.add(Pair.of(VALUE_WRAP_CONTENT, "Wrap Content"));
        if (canMatchParent) {
            widthChoices.add(Pair.of(VALUE_MATCH_PARENT, "Match Parent"));
        } else {
            widthChoices.add(Pair.of(VALUE_FILL_PARENT, "Fill Parent"));
        }
        if (width != null) {
            widthChoices.add(Pair.of(width, width));
        }
        widthChoices.add(Pair.of(ZCUSTOM, "Other..."));
        actions.add(RuleAction.createChoices(
                WIDTH_ID, "Layout Width",
                onChange,
                null /* iconUrls */,
                currentWidth,
                null, 30,
                true, // supportsMultipleNodes
                widthChoices));

        // Create height choice submenu
        List<Pair<String, String>> heightChoices = new ArrayList<Pair<String,String>>(4);
        heightChoices.add(Pair.of(VALUE_WRAP_CONTENT, "Wrap Content"));
        if (canMatchParent) {
            heightChoices.add(Pair.of(VALUE_MATCH_PARENT, "Match Parent"));
        } else {
            heightChoices.add(Pair.of(VALUE_FILL_PARENT, "Fill Parent"));
        }
        if (height != null) {
            heightChoices.add(Pair.of(height, height));
        }
        heightChoices.add(Pair.of(ZCUSTOM, "Other..."));
        actions.add(RuleAction.createChoices(
                HEIGHT_ID, "Layout Height",
                onChange,
                null /* iconUrls */,
                currentHeight,
                null, 40,
                true,
                heightChoices));

        actions.add(RuleAction.createSeparator(45));
        RuleAction properties = RuleAction.createChoices(PROPERTIES_ID, "Properties",
                onChange /*callback*/, null /*icon*/, 50,
                true /*supportsMultipleNodes*/, new ActionProvider() {
            public List<RuleAction> getNestedActions(INode node) {
                List<RuleAction> propertyActions = createPropertyActions(node,
                        getPropertyMapKey(node), onChange);

                return propertyActions;
            }
        });

        actions.add(properties);
    }

    private static String getPropertyMapKey(INode node) {
        // Compute the key for mAttributesMap. This depends on the type of this
        // node and its parent in the view hierarchy.
        StringBuilder sb = new StringBuilder();
        sb.append(node.getFqcn());
        sb.append('_');
        INode parent = node.getParent();
        if (parent != null) {
            sb.append(parent.getFqcn());
        }
        return sb.toString();
    }

    /**
     * Creates a list of nested actions representing the property-setting
     * actions for the given selected node
     */
    private List<RuleAction> createPropertyActions(final INode selectedNode, final String key,
            final IMenuCallback onChange) {
        List<RuleAction> propertyActions = new ArrayList<RuleAction>();

        Map<String, Prop> props = mAttributesMap.get(key);
        if (props == null) {
            // Prepare the property map
            props = new HashMap<String, Prop>();
            for (IAttributeInfo attrInfo : selectedNode.getDeclaredAttributes()) {
                String id = attrInfo != null ? attrInfo.getName() : null;
                if (id == null || id.equals(ATTR_LAYOUT_WIDTH) || id.equals(ATTR_LAYOUT_HEIGHT)) {
                    // Layout width/height are already handled at the root level
                    continue;
                }
                Format[] formats = attrInfo != null ? attrInfo.getFormats() : null;
                if (formats == null) {
                    continue;
                }

                String title = prettyName(id);

                if (IAttributeInfo.Format.BOOLEAN.in(formats)) {
                    props.put(id, new Prop(title, true));
                } else if (IAttributeInfo.Format.ENUM.in(formats)) {
                    // Convert each enum into a map id=>title
                    Map<String, String> values = new HashMap<String, String>();
                    if (attrInfo != null) {
                        for (String e : attrInfo.getEnumValues()) {
                            values.put(e, prettyName(e));
                        }
                    }

                    props.put(id, new Prop(title, false, false, values));
                } else if (IAttributeInfo.Format.FLAG.in(formats)) {
                    // Convert each flag into a map id=>title
                    Map<String, String> values = new HashMap<String, String>();
                    if (attrInfo != null) {
                        for (String e : attrInfo.getFlagValues()) {
                            values.put(e, prettyName(e));
                        }
                    }

                    props.put(id, new Prop(title, false, true, values));
                } else {
                    props.put(id, new Prop(title + "...", false));
                }
            }
            mAttributesMap.put(key, props);
        }

        int nextPriority = 10;
        for (Map.Entry<String, Prop> entry : props.entrySet()) {
            String id = entry.getKey();
            Prop p = entry.getValue();
            if (p.isToggle()) {
                // Toggles are handled as a multiple-choice between true, false
                // and nothing (clear)
                String value = selectedNode.getStringAttr(ANDROID_URI, id);
                if (value != null)
                    value = value.toLowerCase();
                if ("true".equals(value)) {         //$NON-NLS-1$
                    value = TRUE_ID;
                } else if ("false".equals(value)) { //$NON-NLS-1$
                    value = FALSE_ID;
                } else {
                    value = CLEAR_ID;
                }
                Choices action = RuleAction.createChoices(PROP_PREFIX + id, p.getTitle(),
                        onChange, BOOLEAN_CHOICE_PROVIDER,
                        value,
                        null, nextPriority++,
                        true);
                propertyActions.add(action);
            } else if (p.getChoices() != null) {
                // Enum or flags. Their possible values are the multiple-choice
                // items, with an extra "clear" option to remove everything.
                String current = selectedNode.getStringAttr(ANDROID_URI, id);
                if (current == null || current.length() == 0) {
                    current = CLEAR_ID;
                }
                Choices action = RuleAction.createChoices(PROP_PREFIX + id, p.getTitle(),
                        onChange, new EnumPropertyChoiceProvider(p),
                        current,
                        null, nextPriority++,
                        true);
                propertyActions.add(action);
            } else {
                RuleAction action = RuleAction.createAction(
                        PROP_PREFIX + id,
                        p.getTitle(),
                        onChange,
                        null, nextPriority++,
                        true);
                propertyActions.add(action);
            }
        }

        // The properties are coming out of map key order which isn't right
        Collections.sort(propertyActions, new Comparator<RuleAction>() {
            public int compare(RuleAction action1, RuleAction action2) {
                return action1.getTitle().compareTo(action2.getTitle());
            }
        });
        return propertyActions;
    }

    /**
     * A {@link ChoiceProvder} which provides alternatives suitable for choosing
     * values for a boolean property: true, false, or "default".
     */
    private static ChoiceProvider BOOLEAN_CHOICE_PROVIDER = new ChoiceProvider() {
        public void addChoices(List<String> titles, List<URL> iconUrls, List<String> ids) {
            titles.add("True");
            ids.add(TRUE_ID);

            titles.add("False");
            ids.add(FALSE_ID);

            titles.add(RuleAction.SEPARATOR);
            ids.add(RuleAction.SEPARATOR);

            titles.add("Default");
            ids.add(CLEAR_ID);
        }
    };

    /**
     * A {@link ChoiceProvider} which provides the various available
     * attribute values available for a given {@link Prop} property descriptor.
     */
    private static class EnumPropertyChoiceProvider implements ChoiceProvider {
        private Prop mProperty;

        public EnumPropertyChoiceProvider(Prop property) {
            super();
            this.mProperty = property;
        }

        public void addChoices(List<String> titles, List<URL> iconUrls, List<String> ids) {
            for (Entry<String, String> entry : mProperty.getChoices().entrySet()) {
                ids.add(entry.getKey());
                titles.add(entry.getValue());
            }

            titles.add(RuleAction.SEPARATOR);
            ids.add(RuleAction.SEPARATOR);

            titles.add("Default");
            ids.add(CLEAR_ID);
        }
    }

    /**
     * Returns true if the given node is "filled" (e.g. has layout width set to match
     * parent or fill parent
     */
    protected final boolean isFilled(INode node, String attribute) {
        String value = node.getStringAttr(ANDROID_URI, attribute);
        return VALUE_MATCH_PARENT.equals(value) || VALUE_FILL_PARENT.equals(value);
    }

    /**
     * Returns fill_parent or match_parent, depending on whether the minimum supported
     * platform supports match_parent or not
     *
     * @return match_parent or fill_parent depending on which is supported by the project
     */
    protected final String getFillParentValueName() {
        return supportsMatchParent() ? VALUE_MATCH_PARENT : VALUE_FILL_PARENT;
    }

    /**
     * Returns true if the project supports match_parent instead of just fill_parent
     *
     * @return true if the project supports match_parent instead of just fill_parent
     */
    protected final boolean supportsMatchParent() {
        // fill_parent was renamed match_parent in API level 8
        return mRulesEngine.getMinApiLevel() >= 8;
    }

    /** Join strings into a single string with the given delimiter */
    static String join(char delimiter, Collection<String> strings) {
        StringBuilder sb = new StringBuilder(100);
        for (String s : strings) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(s);
        }
        return sb.toString();
    }

    static Map<String, String> concatenate(Map<String, String> pre, Map<String, String> post) {
        Map<String, String> result = new HashMap<String, String>(pre.size() + post.size());
        result.putAll(pre);
        result.putAll(post);
        return result;
    }

    // Quick utility for building up maps declaratively to minimize the diffs
    static Map<String, String> mapify(String... values) {
        Map<String, String> map = new HashMap<String, String>(values.length / 2);
        for (int i = 0; i < values.length; i += 2) {
            String key = values[i];
            if (key == null) {
                continue;
            }
            String value = values[i + 1];
            map.put(key, value);
        }

        return map;
    }

    public static String prettyName(String name) {
        if (name != null && name.length() > 0) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('_', ' ');
        }

        return name;
    }

    // ==== Selection ====

    public List<String> getSelectionHint(INode parentNode, INode childNode) {
        return null;
    }

    public void addLayoutActions(List<RuleAction> actions, INode parentNode,
            List<? extends INode> children) {
    }

    // ==== Drag'n'drop support ====

    // By default Views do not accept drag'n'drop.
    public DropFeedback onDropEnter(INode targetNode, IDragElement[] elements) {
        return null;
    }

    public DropFeedback onDropMove(INode targetNode, IDragElement[] elements,
            DropFeedback feedback, Point p) {
        return null;
    }

    public void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // ignore
    }

    public void onDropped(
            INode targetNode,
            IDragElement[] elements,
            DropFeedback feedback,
            Point p) {
        // ignore
    }

    // ==== Paste support ====

    /**
     * Most views can't accept children so there's nothing to paste on them. In
     * this case, defer the call to the parent layout and use the target node as
     * an indication of where to paste.
     */
    public void onPaste(INode targetNode, IDragElement[] elements) {
        //
        INode parent = targetNode.getParent();
        if (parent != null) {
            String parentFqcn = parent.getFqcn();
            IViewRule parentRule = mRulesEngine.loadRule(parentFqcn);

            if (parentRule instanceof BaseLayoutRule) {
                ((BaseLayoutRule) parentRule).onPasteBeforeChild(parent, targetNode, elements);
            }
        }
    }

    /**
     * Support class for the context menu code. Stores state about properties in
     * the context menu.
     */
    private static class Prop {
        private final boolean mToggle;
        private final boolean mFlag;
        private final String mTitle;
        private final Map<String, String> mChoices;

        public Prop(String title, boolean isToggle, boolean isFlag, Map<String, String> choices) {
            this.mTitle = title;
            this.mToggle = isToggle;
            this.mFlag = isFlag;
            this.mChoices = choices;
        }

        public Prop(String title, boolean isToggle) {
            this(title, isToggle, false, null);
        }

        private boolean isToggle() {
            return mToggle;
        }

        private boolean isFlag() {
            return mFlag && mChoices != null;
        }

        private boolean isEnum() {
            return !mFlag && mChoices != null;
        }

        private String getTitle() {
            return mTitle;
        }

        private Map<String, String> getChoices() {
            return mChoices;
        }

        private boolean isStringEdit() {
            return mChoices == null && !mToggle;
        }
    }

    /**
     * Returns a source attribute value which points to a sample image. This is typically
     * used to provide an initial image shown on ImageButtons, etc. There is no guarantee
     * that the source pointed to by this method actually exists.
     *
     * @return a source attribute to use for sample images, never null
     */
    protected final String getSampleImageSrc() {
        // For now, we point to the sample icon which is written into new Android projects
        // created in ADT. We could alternatively look into the project resources folder
        // and try to pick something else, or even return some builtin image resource
        // in the @android namespace.

        return "@drawable/icon"; //$NON-NLS-1$
    }

    public void onCreate(INode node, INode parent, InsertType insertType) {
    }

    public void onChildInserted(INode node, INode parent, InsertType insertType) {
    }

    public void onRemovingChildren(List<INode> deleted, INode parent) {
    }

    public static String stripIdPrefix(String id) {
        if (id == null) {
            return ""; //$NON-NLS-1$
        } else if (id.startsWith(NEW_ID_PREFIX)) {
            return id.substring(NEW_ID_PREFIX.length());
        } else if (id.startsWith(ID_PREFIX)) {
            return id.substring(ID_PREFIX.length());
        }
        return id;
    }

    private static String ensureValidString(String value) {
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        return value;
    }

    public void paintSelectionFeedback(IGraphics graphics, INode parentNode,
            List<? extends INode> childNodes) {
    }

    // ---- Resizing ----

    public DropFeedback onResizeBegin(INode child, INode parent, SegmentType horizontalEdge,
            SegmentType verticalEdge) {
        return null;
    }

    public void onResizeUpdate(DropFeedback feedback, INode child, INode parent, Rect newBounds,
            int modifierMask) {
    }

    public void onResizeEnd(DropFeedback feedback, INode child, final INode parent,
            final Rect newBounds) {
    }
}
