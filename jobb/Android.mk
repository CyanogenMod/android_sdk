# Copyright (C) 2010 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# The jobb code has moved to tools/base/jobb.
# The rule below uses the prebuilt jobb.jar if found.

LOCAL_MODULE := jobb
LOCAL_MODULE_TAGS := debug
LOCAL_JAVA_LIBRARIES := fat32lib

LOCAL_PREBUILT_JAVA_LIBRARIES := \
	../../prebuilts/devtools/$(LOCAL_MODULE)/$(LOCAL_MODULE)$(COMMON_JAVA_PACKAGE_SUFFIX)

include $(BUILD_HOST_PREBUILT)

