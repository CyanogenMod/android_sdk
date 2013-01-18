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

import com.android.SdkConstants;
import com.android.hierarchyviewerlib.device.IHvDevice;
import com.android.hierarchyviewerlib.models.ViewNode;
import com.android.hierarchyviewerlib.models.ViewNode.Property;
import com.android.utils.SdkUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DevicePropertyEditingSupport {
    public enum PropertyType {
        INTEGER,
        INTEGER_OR_CONSTANT,
        ENUM,
    };

    private static final List<IDevicePropertyEditor> sDevicePropertyEditors = Arrays.asList(
                new LayoutPropertyEditor(),
                new PaddingPropertyEditor()
            );

    public boolean canEdit(Property p) {
        return getPropertyEditorFor(p) != null;
    }

    private IDevicePropertyEditor getPropertyEditorFor(Property p) {
        for (IDevicePropertyEditor pe: sDevicePropertyEditors) {
            if (pe.canEdit(p)) {
                return pe;
            }
        }

        return null;
    }

    public PropertyType getPropertyType(Property p) {
        return getPropertyEditorFor(p).getType(p);
    }

    public String[] getPropertyRange(Property p) {
        return getPropertyEditorFor(p).getPropertyRange(p);
    }

    public boolean setValue(Collection<Property> properties, Property p, Object newValue,
            ViewNode viewNode, IHvDevice device) {
        return getPropertyEditorFor(p).setValue(properties, p, newValue, viewNode, device);
    }

    private static String stripCategoryPrefix(String name) {
        return name.substring(name.indexOf(':') + 1);
    }

    private interface IDevicePropertyEditor {
        boolean canEdit(Property p);
        PropertyType getType(Property p);
        String[] getPropertyRange(Property p);
        boolean setValue(Collection<Property> properties, Property p, Object newValue,
                ViewNode viewNode, IHvDevice device);
    }

    private static class LayoutPropertyEditor implements IDevicePropertyEditor {
        private static final Set<String> sLayoutPropertiesWithStringValues =
                ImmutableSet.of(SdkConstants.ATTR_LAYOUT_WIDTH,
                        SdkConstants.ATTR_LAYOUT_HEIGHT,
                        SdkConstants.ATTR_LAYOUT_GRAVITY);

        private static final int MATCH_PARENT = -1;
        private static final int FILL_PARENT = -1;
        private static final int WRAP_CONTENT = -2;

        private enum LayoutGravity {
            top(0x30),
            bottom(0x50),
            left(0x03),
            right(0x05),
            center_vertical(0x10),
            fill_vertical(0x70),
            center_horizontal(0x01),
            fill_horizontal(0x07),
            center(0x11),
            fill(0x77),
            clip_vertical(0x80),
            clip_horizontal(0x08),
            start(0x00800003),
            end(0x00800005);

            private final int mValue;

            private LayoutGravity(int v) {
                mValue = v;
            }
        }

        /**
         * Returns true if this is a layout property with either a known string value, or an
         * integer value.
         */
        @Override
        public boolean canEdit(Property p) {
            String name = stripCategoryPrefix(p.name);
            if (!name.startsWith(SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX)) {
                return false;
            }

            if (sLayoutPropertiesWithStringValues.contains(name)) {
                return true;
            }

            try {
                SdkUtils.parseLocalizedInt(p.value);
                return true;
            } catch (ParseException e) {
                return false;
            }
        }

        @Override
        public PropertyType getType(Property p) {
            String name = stripCategoryPrefix(p.name);
            if (sLayoutPropertiesWithStringValues.contains(name)) {
                return PropertyType.INTEGER_OR_CONSTANT;
            } else {
                return PropertyType.INTEGER;
            }
        }

        @Override
        public String[] getPropertyRange(Property p) {
            return new String[0];
        }

        @Override
        public boolean setValue(Collection<Property> properties, Property p, Object newValue,
                ViewNode viewNode, IHvDevice device) {
            String name = stripCategoryPrefix(p.name);

            // nothing to do if same as current value
            if (p.value.equals(newValue)) {
                return false;
            }

            int value = -1;
            String textValue = null;

            if (SdkConstants.ATTR_LAYOUT_GRAVITY.equals(name)) {
                value = 0;
                StringBuilder sb = new StringBuilder(20);
                for (String attr: Splitter.on('|').split((String) newValue)) {
                    LayoutGravity g;
                    try {
                        g = LayoutGravity.valueOf(attr);
                    } catch (IllegalArgumentException e) {
                        // ignore this gravity attribute
                        continue;
                    }

                    value |= g.mValue;

                    if (sb.length() > 0) {
                        sb.append('|');
                    }
                    sb.append(g.name());
                }
                textValue = sb.toString();
            } else if (SdkConstants.ATTR_LAYOUT_HEIGHT.equals(name)
                    || SdkConstants.ATTR_LAYOUT_WIDTH.equals(name)) {
                // newValue is of type string, but its contents may be a named constant or a integer
                String s = (String) newValue;
                if (s.equalsIgnoreCase(SdkConstants.VALUE_MATCH_PARENT)) {
                    textValue = SdkConstants.VALUE_MATCH_PARENT;
                    value = MATCH_PARENT;
                } else if (s.equalsIgnoreCase(SdkConstants.VALUE_FILL_PARENT)) {
                    textValue = SdkConstants.VALUE_FILL_PARENT;
                    value = FILL_PARENT;
                } else if (s.equalsIgnoreCase(SdkConstants.VALUE_WRAP_CONTENT)) {
                    textValue = SdkConstants.VALUE_WRAP_CONTENT;
                    value = WRAP_CONTENT;
                }
            }

            if (textValue == null) {
                try {
                    value = Integer.parseInt((String) newValue);
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            // attempt to set the value on the device
            name = name.substring(SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX.length());
            if (device.setLayoutParameter(viewNode.window, viewNode, name, value)) {
                p.value = textValue != null ? textValue : (String) newValue;
            }

            return true;
        }
    }

    private static class PaddingPropertyEditor implements IDevicePropertyEditor {
        // These names should match the field names used for padding in the Framework's View class
        private static final String PADDING_LEFT = "mPaddingLeft";      //$NON-NLS-1$
        private static final String PADDING_RIGHT = "mPaddingRight";    //$NON-NLS-1$
        private static final String PADDING_TOP = "mPaddingTop";        //$NON-NLS-1$
        private static final String PADDING_BOTTOM = "mPaddingBottom";  //$NON-NLS-1$

        private static final Set<String> sPaddingProperties = ImmutableSet.of(
                PADDING_LEFT, PADDING_RIGHT, PADDING_TOP, PADDING_BOTTOM);

        @Override
        public boolean canEdit(Property p) {
            return sPaddingProperties.contains(stripCategoryPrefix(p.name));
        }

        @Override
        public PropertyType getType(Property p) {
            return PropertyType.INTEGER;
        }

        @Override
        public String[] getPropertyRange(Property p) {
            return new String[0];
        }

        /**
         * Set padding: Since the only view method is setPadding(l, t, r, b), we need access
         * to all 4 padding's to update any particular one.
         */
        @Override
        public boolean setValue(Collection<Property> properties, Property prop, Object newValue,
                ViewNode viewNode, IHvDevice device) {
            int v;
            try {
                v = Integer.parseInt((String) newValue);
            } catch (NumberFormatException e) {
                return false;
            }

            int pLeft = 0;
            int pRight = 0;
            int pTop = 0;
            int pBottom = 0;

            String propName = stripCategoryPrefix(prop.name);
            for (Property p: properties) {
                String name = stripCategoryPrefix(p.name);
                if (!sPaddingProperties.contains(name)) {
                    continue;
                }

                if (name.equals(PADDING_LEFT)) {
                    pLeft = propName.equals(PADDING_LEFT) ?
                            v : SdkUtils.parseLocalizedInt(p.value, 0);
                } else if (name.equals(PADDING_RIGHT)) {
                    pRight = propName.equals(PADDING_RIGHT) ?
                            v : SdkUtils.parseLocalizedInt(p.value, 0);
                } else if (name.equals(PADDING_TOP)) {
                    pTop = propName.equals(PADDING_TOP) ?
                            v : SdkUtils.parseLocalizedInt(p.value, 0);
                } else if (name.equals(PADDING_BOTTOM)) {
                    pBottom = propName.equals(PADDING_BOTTOM) ?
                            v : SdkUtils.parseLocalizedInt(p.value, 0);
                }
            }

            // invoke setPadding() on the device
            device.invokeViewMethod(viewNode.window, viewNode, "setPadding", Arrays.asList(
                    Integer.valueOf(pLeft),
                    Integer.valueOf(pTop),
                    Integer.valueOf(pRight),
                    Integer.valueOf(pBottom)
            ));

            // update the value set in the property (to avoid reading all properties back from
            // the device)
            prop.value = Integer.toString(v);
            return true;
        }
    }
}
