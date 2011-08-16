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


package com.android.ide.common.api;

import com.android.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * A Client Rules Engine is a set of methods that {@link IViewRule}s can use to
 * access the client public API of the Rules Engine.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
public interface IClientRulesEngine {

    /**
     * Returns the FQCN for which the rule was loaded.
     *
     * @return the fully qualified name of the rule
     */
    String getFqcn();

    /**
     * Prints a debug line in the Eclipse console using the ADT formatter.
     *
     * @param msg A String format message.
     * @param params Optional parameters for the message.
     */
    void debugPrintf(String msg, Object...params);

    /**
     * Loads and returns an {@link IViewRule} for the given FQCN.
     *
     * @param fqcn A non-null, non-empty FQCN for the rule to load.
     * @return The rule that best matches the given FQCN according to the
     *   inheritance chain. Rules are cached and requesting the same FQCN twice
     *   is fast and will return the same rule instance.
     */
    IViewRule loadRule(String fqcn);

    /**
     * Returns the metadata associated with the given fully qualified class name.
     *
     * @param fqcn a fully qualified class name for an Android view class
     * @return the metadata associated with the given fully qualified class name.
     */
    IViewMetadata getMetadata(String fqcn);

    /**
     * Displays the given message string in an alert dialog with an "OK" button.
     *
     * @param message the message to be shown
     */
    void displayAlert(String message);

    /**
     * Displays a simple input alert dialog with an OK and Cancel buttons.
     *
     * @param message The message to display in the alert dialog.
     * @param value The initial value to display in the input field. Can be null.
     * @param filter An optional filter to validate the input. Specify null (or
     *            a validator which always returns true) if you do not want
     *            input validation.
     * @return Null if canceled by the user. Otherwise the possibly-empty input string.
     * @null Return value is null if dialog was canceled by the user.
     */
    @Nullable
    String displayInput(String message, @Nullable String value, @Nullable IValidator filter);

    /**
     * Returns the minimum API level that the current Android project is targeting.
     *
     * @return the minimum API level to be supported, or -1 if it cannot be determined
     */
    int getMinApiLevel();

    /**
     * Returns a resource name validator for the current project
     *
     * @return an {@link IValidator} for validating new resource name in the current
     *         project
     */
    IValidator getResourceValidator();

    /**
     * Displays an input dialog where the user can enter an Android reference value
     *
     * @param currentValue the current reference to select
     * @return the reference selected by the user, or null
     */
    String displayReferenceInput(String currentValue);

    /**
     * Displays an input dialog where the user can enter an Android resource name of the
     * given resource type ("id", "string", "drawable", and so on.)
     *
     * @param currentValue the current reference to select
     * @param resourceTypeName resource type, such as "id", "string", and so on (never
     *            null)
     * @return the margins selected by the user in the same order as the input arguments,
     *         or null
     */
    String displayResourceInput(String resourceTypeName, String currentValue);

    /**
     * Displays an input dialog tailored for editing margin properties.
     *
     * @param all The current, initial value display for "all" margins (applied to all
     *            sides)
     * @param left The current, initial value to display for the "left" margin
     * @param right The current, initial value to display for the "right" margin
     * @param top The current, initial value to display for the "top" margin
     * @param bottom The current, initial value to display for the "bottom" margin
     * @return an array of length 5 containing the user entered values for the all, left,
     *         right, top and bottom margins respectively
     */
    String[] displayMarginInput(String all, String left, String right, String top, String bottom);

    /**
     * Displays an input dialog tailored for inputing the source of an {@code <include>}
     * layout tag. This is similar to {@link #displayResourceInput} for resource type
     * "layout", but should also attempt to filter out layout resources that cannot be
     * included from the current context (because it would result in a cyclic dependency).
     *
     * @return the layout resource to include
     */
    String displayIncludeSourceInput();

    /**
     * Displays an input dialog tailored for inputing the source of a {@code <fragment>}
     * layout tag.
     *
     * @return the fully qualified class name of the fragment activity
     */
    String displayFragmentSourceInput();

    /**
     * Select the given nodes
     *
     * @param nodes the nodes to be selected, never null
     */
    void select(Collection<INode> nodes);

    /**
     * Triggers a redraw
     */
    void redraw();

    /**
     * Triggers a layout refresh and redraw
     */
    void layout();

    /**
     * Converts a pixel to a dp (device independent pixel) for the current screen density
     *
     * @param px the pixel dimension
     * @return the corresponding dp dimension
     */
    public int pxToDp(int px);

    /**
     * Converts a device independent pixel to a screen pixel for the current screen density
     *
     * @param dp the device independent pixel dimension
     * @return the corresponding pixel dimension
     */
    public int dpToPx(int dp);

    /**
     * Converts an IDE screen pixel distance to the corresponding layout distance. This
     * can be used to draw annotations on the graphics object that should be unaffected by
     * the zoom, or handle mouse events within a certain pixel distance regardless of the
     * screen zoom.
     *
     * @param pixels the size in IDE screen pixels
     * @return the corresponding pixel distance in the layout coordinate system
     */
    public int screenToLayout(int pixels);

    /**
     * Measure the preferred or actual ("wrap_content") size of the given nodes.
     *
     * @param parent the parent whose children should be measured
     * @param filter a filter to change attributes in the process of measuring, for
     *            example forcing the layout_width to wrap_content or the layout_weight to
     *            unset
     * @return the corresponding bounds of the nodes
     */
    Map<INode, Rect> measureChildren(INode parent, AttributeFilter filter);

    /**
     * The {@link AttributeFilter} allows a client of
     * {@link IClientRulesEngine#measureChildren} to modify the actual XML values of the
     * nodes being rendered, for example to force width and height values to wrap_content
     * when measuring preferred size.
     */
    public interface AttributeFilter {
        /**
         * Returns the attribute value for the given node and attribute name. This filter
         * allows a client to adjust the attribute values that a node presents to the
         * layout library.
         * <p>
         * Return "" to unset an attribute. Return null to return the unfiltered value.
         *
         * @param node the node for which the attribute value should be returned
         * @param namespace the attribute namespace
         * @param localName the attribute local name
         * @return an override value, or null to return the unfiltered value
         */
        String getAttribute(INode node, String namespace, String localName);
    }

    /**
     * Given a UI root node and a potential XML node name, returns the first available id
     * that matches the pattern "prefix%d".
     * <p/>
     * TabWidget is a special case and the method will always return "@android:id/tabs".
     *
     * @param fqcn The fully qualified class name of the view to generate a unique id for
     * @return A suitable generated id in the attribute form needed by the XML id tag
     *         (e.g. "@+id/something")
     */
    public String getUniqueId(String fqcn);

}

