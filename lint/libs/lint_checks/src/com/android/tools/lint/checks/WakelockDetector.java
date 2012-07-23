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

package com.android.tools.lint.checks;
import static com.android.tools.lint.detector.api.LintConstants.ANDROID_APP_ACTIVITY;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.List;

/**
 * Checks for problems with wakelocks (such as failing to release them)
 * which can lead to unnecessary battery usage.
 */
public class WakelockDetector extends Detector implements ClassScanner {

    /** Problems using wakelocks */
    public static final Issue ISSUE = Issue.create(
        "Wakelock", //$NON-NLS-1$
        "Looks for problems with wakelock usage",

        "Failing to release a wakelock properly can keep the Android device in " +
        "a high power mode, which reduces battery life. There are several causes " +
        "of this, such as releasing the wake lock in onDestroy() instead of in " +
        "onPause(), failing to call release() in all possible code paths after " +
        "an acquire(), and so on.\n" +
        "\n" +
        "NOTE: If you are using the lock just to keep the screen on, you should " +
        "strongly consider using FLAG_KEEP_SCREEN_ON instead. This window flag " +
        "will be correctly managed by the platform as the user moves between " +
        "applications and doesn't require a special permission. See " +
        "http://developer.android.com/reference/android/view/WindowManager.LayoutParams.html#FLAG_KEEP_SCREEN_ON.",

        Category.PERFORMANCE,
        9,
        Severity.WARNING,
        WakelockDetector.class,
        Scope.CLASS_FILE_SCOPE);

    private static final String WAKELOCK_OWNER = "android/os/PowerManager$WakeLock"; //$NON-NLS-1$
    private static final String RELEASE_METHOD = "release"; //$NON-NLS-1$
    private static final String ACQUIRE_METHOD = "acquire"; //$NON-NLS-1$

    /** Constructs a new {@link WakelockDetector} */
    public WakelockDetector() {
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mHasAcquire && !mHasRelease && context.getDriver().getPhase() == 1) {
            // Gather positions of the acquire calls
            context.getDriver().requestRepeat(this, Scope.CLASS_FILE_SCOPE);
        }
    }

    // ---- Implements ClassScanner ----

    /** Whether any {@code acquire()} calls have been encountered */
    private boolean mHasAcquire;

    /** Whether any {@code release()} calls have been encountered */
    private boolean mHasRelease;

    @Override
    @Nullable
    public List<String> getApplicableCallNames() {
        return Arrays.asList(ACQUIRE_METHOD, RELEASE_METHOD);
    }

    @Override
    public void checkCall(@NonNull ClassContext context, @NonNull ClassNode classNode,
            @NonNull MethodNode method, @NonNull MethodInsnNode call) {
        if (call.owner.equals(WAKELOCK_OWNER)) {
            String name = call.name;
            if (name.equals(ACQUIRE_METHOD)) {
                mHasAcquire = true;

                if (context.getDriver().getPhase() == 2) {
                    assert !mHasRelease;
                    context.report(ISSUE, method, context.getLocation(call),
                        "Found a wakelock acquire() but no release() calls anywhere",
                        null);
                } else {
                    assert context.getDriver().getPhase() == 1;
                    // Perform flow analysis in this method to see if we're
                    // performing an acquire/release block, where there are code paths
                    // between the acquire and release which can result in the
                    // release call not getting reached.
                    // TODO: Implement this.
                }
            } else if (name.equals(RELEASE_METHOD)) {
                mHasRelease = true;

                // See if the release is happening in an onDestroy method, in an
                // activity.
                if ("onDestroy".equals(method.name) //$NON-NLS-1$
                        && context.getDriver().isSubclassOf(
                                classNode, ANDROID_APP_ACTIVITY)) {
                    context.report(ISSUE, method, context.getLocation(call),
                        "Wakelocks should be released in onPause, not onDestroy",
                        null);
                }
            }
        }
    }
}
