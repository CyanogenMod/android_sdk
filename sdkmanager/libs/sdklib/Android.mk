#
# Copyright (C) 2008 The Android Open Source Project
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

# The sdklib code has moved to tools/base/sdklib.
# The rule below uses the prebuilt sdklib.jar if found.
#
# If you want to run the tests, cd to tools/base
# and run ./gradlew :sdklib:test

LOCAL_MODULE := sdklib
LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := \
        common \
        commons-codec-1.4 \
        commons-compress-1.0 \
        commons-logging-1.1.1 \
        dvlib \
        guava-tools \
        httpclient-4.1.1 \
        httpcore-4.1 \
        httpmime-4.1.1 \
        mkidentity-prebuilt \
        layoutlib_api

LOCAL_PREBUILT_JAVA_LIBRARIES := \
	../../../../prebuilts/devtools/$(LOCAL_MODULE)$(COMMON_JAVA_PACKAGE_SUFFIX)

include $(BUILD_HOST_PREBUILT)

