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

import static com.android.tools.lint.detector.api.LintConstants.ANDROID_URI;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;

import java.util.Arrays;
import java.util.Collection;

/**
 * Check which looks for likely typos in various places.
 */
public class TypoDetector extends ResourceXmlDetector {
    private static final String XMLNS_ANDROID = "xmlns:android";    //$NON-NLS-1$
    private static final String XMLNS_A = "xmlns:a";                //$NON-NLS-1$

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "NamespaceTypo", //$NON-NLS-1$
            "Looks for misspellings in namespace declarations",

            "Accidental misspellings in namespace declarations can lead to some very " +
            "obscure error messages. This check looks for potential misspellings to " +
            "help track these down.",
            Category.CORRECTNESS,
            8,
            Severity.WARNING,
            TypoDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    /** Constructs a new {@link TypoDetector} */
    public TypoDetector() {
    }

    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(XMLNS_ANDROID, XMLNS_A);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (!value.equals(ANDROID_URI)) {
            if (attribute.getName().equals(XMLNS_A)) {
                // For the "android" prefix we always assume that the namespace prefix
                // should be our expected prefix, but for the "a" prefix we make sure
                // that it's at least "close"; if you're bound it to something completely
                // different, don't complain.
                if (LintUtils.editDistance(ANDROID_URI, value) > 4) {
                    return;
                }
            }

            if (value.equalsIgnoreCase(ANDROID_URI)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        String.format("URI is case sensitive: was \"%1$s\", expected \"%2$s\"",
                                value, ANDROID_URI), null);
            } else {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        String.format("Unexpected namespace URI bound to the \"android\" " +
                                "prefix, was %1$s, expected %2$s", value, ANDROID_URI), null);
            }
        }
    }
}
