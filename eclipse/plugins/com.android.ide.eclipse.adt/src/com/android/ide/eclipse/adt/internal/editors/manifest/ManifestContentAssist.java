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

package com.android.ide.eclipse.adt.internal.editors.manifest;

import static com.android.sdklib.xml.AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION;

import com.android.annotations.VisibleForTesting;
import com.android.ide.eclipse.adt.internal.editors.AndroidContentAssist;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.util.Pair;

import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Content Assist Processor for AndroidManifest.xml
 */
@VisibleForTesting
public final class ManifestContentAssist extends AndroidContentAssist {

    /**
     * Constructor for ManifestContentAssist
     */
    public ManifestContentAssist() {
        super(AndroidTargetData.DESCRIPTOR_MANIFEST);
    }

    @Override
    protected void computeAttributeValues(List<ICompletionProposal> proposals, int offset,
            String parentTagName, String attributeName, Node node, String wordPrefix,
            boolean skipEndTag, int replaceLength) {
        if (attributeName.endsWith(':' + ATTRIBUTE_MIN_SDK_VERSION)) {
            // The user is completing the minSdkVersion attribute: it should be
            // an integer for the API version, but we'll add full Android version
            // names to make it more obvious what they're selecting

            List<Pair<String, String>> choices = new ArrayList<Pair<String, String>>();
            // Max: Look up what versions I have
            IAndroidTarget[] targets = Sdk.getCurrent().getTargets();
            Map<String, IAndroidTarget> versionMap = new HashMap<String, IAndroidTarget>();
            List<String> codeNames = new ArrayList<String>();
            int maxVersion = 1;
            for (IAndroidTarget target : targets) {
                AndroidVersion version = target.getVersion();
                int apiLevel = version.getApiLevel();
                String key;
                if (version.isPreview()) {
                    key = version.getCodename();
                    codeNames.add(key);
                    apiLevel--;
                } else {
                    key = Integer.toString(apiLevel);
                }
                if (apiLevel > maxVersion) {
                    maxVersion = apiLevel;
                }

                versionMap.put(key, target);
            }
            for (String codeName : codeNames) {
                choices.add(Pair.<String, String>of(codeName, null));
            }
            for (int i = maxVersion; i >= 1; i--) {
                IAndroidTarget target = versionMap.get(Integer.toString(i));
                String version = target != null ? target.getFullName() : null;
                choices.add(Pair.of(Integer.toString(i), version));
            }
            char needTag = 0;
            addMatchingProposals(proposals, choices.toArray(), offset, node, wordPrefix,
                    needTag, true /* isAttribute */, false /* isNew */,
                    skipEndTag /* skipEndTag */, replaceLength);
        } else {
            super.computeAttributeValues(proposals, offset, parentTagName, attributeName, node,
                    wordPrefix, skipEndTag, replaceLength);
        }
    }
}
