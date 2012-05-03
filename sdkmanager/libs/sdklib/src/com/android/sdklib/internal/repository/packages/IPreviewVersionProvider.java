/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.sdklib.internal.repository.packages;

import com.android.annotations.NonNull;


/**
 * Interface for packages that provide a {@link PreviewVersion},
 * which is a multi-part revision number (major.minor.micro) and an optional preview revision.
 */
public interface IPreviewVersionProvider {

    /**
     * Returns a {@link PreviewVersion} for this package. Never null.
     */
    public abstract @NonNull PreviewVersion getPreviewVersion();
}
