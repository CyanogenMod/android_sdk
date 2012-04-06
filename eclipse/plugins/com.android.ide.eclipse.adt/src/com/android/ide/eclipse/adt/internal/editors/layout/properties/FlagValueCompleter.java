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
package com.android.ide.eclipse.adt.internal.editors.layout.properties;

import com.android.ide.eclipse.adt.AdtUtils;

import org.eclipse.jface.fieldassist.ContentProposal;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalProvider;

import java.util.ArrayList;
import java.util.List;

/** Resource value completion for the given property */
class FlagValueCompleter implements IContentProposalProvider {
    protected final XmlProperty mProperty;
    private String[] mValues;

    FlagValueCompleter(XmlProperty property, String[] values) {
        mProperty = property;
        mValues = values;
    }

    @Override
    public IContentProposal[] getProposals(String contents, int position) {
        List<IContentProposal> proposals = new ArrayList<IContentProposal>(mValues.length);
        String prefix = contents;
        int flagStart = prefix.lastIndexOf('|');
        String prepend = null;
        if (flagStart != -1) {
            prepend = prefix.substring(0, flagStart + 1);
            prefix = prefix.substring(flagStart + 1).trim();
        }

        boolean exactMatch = false;
        for (String value : mValues) {
            if (prefix.equals(value)) {
                exactMatch = true;
                proposals.add(new ContentProposal(contents));

                break;
            }
        }

        if (exactMatch) {
            prepend = contents + '|';
            prefix = "";
        }

        for (String value : mValues) {
            if (AdtUtils.startsWithIgnoreCase(value, prefix)) {
                if (prepend != null && prepend.contains(value)) {
                    continue;
                }
                String match;
                if (prepend != null) {
                    match = prepend + value;
                } else {
                    match = value;
                }
                proposals.add(new ContentProposal(match));
            }
        }

        return proposals.toArray(new IContentProposal[proposals.size()]);
    }
}