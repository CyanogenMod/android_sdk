/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.layout.configuration;

import com.android.ide.eclipse.adt.internal.editors.layout.gle2.SubmenuAction;
import com.android.resources.NightMode;
import com.android.resources.ScreenOrientation;
import com.android.resources.UiMode;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ToolItem;

import java.util.List;

/**
 * Action which creates a submenu that shows the available orientations as well
 * as some related options for night mode and dock mode
 */
class OrientationMenuAction extends SubmenuAction {
    // Constants used to indicate what type of menu is being shown, such that
    // the submenus can lazily construct their contents
    private static final int MENU_NIGHTMODE = 1;
    private static final int MENU_UIMODE = 2;

    private final ConfigurationComposite mConfiguration;
    /** Type of menu; one of the constants {@link #MENU_NIGHTMODE} etc */
    private final int mType;

    OrientationMenuAction(int type, String title, ConfigurationComposite configuration) {
        super(title);
        mType = type;
        mConfiguration = configuration;
    }

    static void showMenu(ConfigurationComposite configuration, ToolItem combo) {
        MenuManager manager = new MenuManager();

        // Show toggles for all the available configurations
        State current = configuration.getSelectedDeviceState();
        Device device = configuration.getSelectedDevice();
        if (device != null) {
            List<State> states = device.getAllStates();

            if (states.size() > 1 && current != null) {
                State flip = configuration.getNextDeviceState(current);
                manager.add(new DeviceConfigAction(configuration,
                        String.format("Switch to %1$s", flip.getName()), flip, false, true));
                manager.add(new Separator());
            }

            for (State config : states) {
                manager.add(new DeviceConfigAction(configuration, config.getName(),
                        config, config == current, false));
            }
            manager.add(new Separator());
        }
        manager.add(new OrientationMenuAction(MENU_UIMODE, "UI Mode", configuration));
        manager.add(new Separator());
        manager.add(new OrientationMenuAction(MENU_NIGHTMODE, "Night Mode", configuration));

        Menu menu = manager.createContextMenu(configuration.getShell());
        Rectangle bounds = combo.getBounds();
        Point location = new Point(bounds.x, bounds.y + bounds.height);
        location = combo.getParent().toDisplay(location);
        menu.setLocation(location.x, location.y);
        menu.setVisible(true);
    }

    @Override
    protected void addMenuItems(Menu menu) {
        switch (mType) {
            case MENU_NIGHTMODE: {
                NightMode selected = mConfiguration.getSelectedNightMode();
                for (NightMode mode : NightMode.values()) {
                    boolean checked = mode == selected;
                    SelectNightModeAction action = new SelectNightModeAction(mode, checked);
                    new ActionContributionItem(action).fill(menu, -1);

                }
                break;
            }
            case MENU_UIMODE: {
                UiMode selected = mConfiguration.getSelectedUiMode();
                for (UiMode mode : UiMode.values()) {
                    boolean checked = mode == selected;
                    SelectUiModeAction action = new SelectUiModeAction(mode, checked);
                    new ActionContributionItem(action).fill(menu, -1);
                }
                break;
            }
        }
    }

    private class SelectNightModeAction extends Action {
        private final NightMode mMode;

        private SelectNightModeAction(NightMode mode, boolean checked) {
            super(mode.getLongDisplayValue(), IAction.AS_RADIO_BUTTON);
            mMode = mode;
            if (checked) {
                setChecked(true);
            }
        }

        @Override
        public void run() {
            mConfiguration.selectNightMode(mMode);
        }
    }

    private class SelectUiModeAction extends Action {
        private final UiMode mMode;

        private SelectUiModeAction(UiMode mode, boolean checked) {
            super(mode.getLongDisplayValue(), IAction.AS_RADIO_BUTTON);
            mMode = mode;
            if (checked) {
                setChecked(true);
            }
        }

        @Override
        public void run() {
            mConfiguration.selectUiMode(mMode);
        }
    }

    private static class DeviceConfigAction extends Action {
        private final ConfigurationComposite mConfiguration;
        private final State mState;

        private DeviceConfigAction(ConfigurationComposite configuration, String title,
                State state, boolean checked, boolean flip) {
            super(title, IAction.AS_RADIO_BUTTON);
            mConfiguration = configuration;
            mState = state;
            if (checked) {
                setChecked(true);
            }
            ScreenOrientation orientation = configuration.getOrientation(state);
            setImageDescriptor(configuration.getOrientationImage(orientation, flip));
        }

        @Override
        public void run() {
            mConfiguration.selectDeviceState(mState);
            mConfiguration.onDeviceConfigChange();
        }
    }
}
