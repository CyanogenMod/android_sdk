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

package com.android.menubar.internal;

import com.android.menubar.IMenuBarCallback;
import com.android.menubar.IMenuBarEnhancer;

import org.eclipse.swt.internal.Callback;
import org.eclipse.swt.internal.carbon.HICommand;
import org.eclipse.swt.internal.carbon.OS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;


/**
 * Implementation of IMenuBarEnhancer for MacOS Carbon SWT.
 */
public final class MenuBarEnhancerCarbon implements IMenuBarEnhancer {

    private static final int kHICommandPreferences = ('p'<<24) + ('r'<<16) + ('e'<<8) + 'f';
    private static final int kHICommandAbout       = ('a'<<24) + ('b'<<16) + ('o'<<8) + 'u';
    private static final int kHICommandServices    = ('s'<<24) + ('e'<<16) + ('r'<<8) + 'v';

    public MenuBarEnhancerCarbon() {
    }

    public MenuBarMode getMenuBarMode() {
        return MenuBarMode.MAC_OS;
    }

    public void setupMenu(
            String appName,
            Display display,
            final IMenuBarCallback callbacks) {

        // Callback target
        Object target = new Object() {
           @SuppressWarnings("unused")
           int commandProc(int nextHandler, int theEvent, int userData) {
              if (OS.GetEventKind(theEvent) == OS.kEventProcessCommand) {
                 HICommand command = new HICommand();
                 OS.GetEventParameter(
                         theEvent,
                         OS.kEventParamDirectObject,
                         OS.typeHICommand,
                         null,
                         HICommand.sizeof,
                         null,
                         command);
                 switch (command.commandID) {
                 case kHICommandPreferences:
                     callbacks.onPreferencesMenuSelected();
                   return OS.eventNotHandledErr; // TODO wrong
                 case kHICommandAbout:
                     callbacks.onAboutMenuSelected();
                     return OS.eventNotHandledErr;// TODO wrong
                 default:
                    break;
                 }
              }
              return OS.eventNotHandledErr;
           }
        };

        final Callback commandCallback= new Callback(target, "commandProc", 3); //$NON-NLS-1$
        int commandProc = commandCallback.getAddress();
        if (commandProc == 0) {
           commandCallback.dispose();
           log(callbacks, "%1$s: commandProc hook failed.", getClass().getSimpleName()); //$NON-NLS-1$
           return;  // give up
        }

        // Install event handler for commands
        int[] mask = new int[] {
           OS.kEventClassCommand, OS.kEventProcessCommand
        };
        OS.InstallEventHandler(
                OS.GetApplicationEventTarget(), commandProc, mask.length / 2, mask, 0, null);

        // create About Eclipse menu command
        int[] outMenu = new int[1];
        short[] outIndex = new short[1];
        if (OS.GetIndMenuItemWithCommandID(
                0, kHICommandPreferences, 1, outMenu, outIndex) == OS.noErr && outMenu[0] != 0) {
           int menu = outMenu[0];

           // add About menu item (which isn't present by default)
           String about = "About " + appName;
           int l = about.length();
           char buffer[] = new char[l];
           about.getChars(0, l, buffer, 0);
           int str = OS.CFStringCreateWithCharacters(OS.kCFAllocatorDefault, buffer, l);
           OS.InsertMenuItemTextWithCFString(menu, str, (short) 0, 0, kHICommandAbout);
           OS.CFRelease(str);

           // add separator between About & Preferences
           OS.InsertMenuItemTextWithCFString(menu, 0, (short) 1, OS.kMenuItemAttrSeparator, 0);

           // enable pref menu
           OS.EnableMenuCommand(menu, kHICommandPreferences);

           // disable services menu
           OS.DisableMenuCommand(menu, kHICommandServices);
        }

        // schedule disposal of callback object
        display.disposeExec(
           new Runnable() {
              public void run() {
                 commandCallback.dispose();
              }
           }
        );
    }

    private void log(IMenuBarCallback callbacks, String format, Object... args) {
        callbacks.printError(format , args);
    }

}
