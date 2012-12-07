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
#
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under,src/main/java)

LOCAL_JAR_MANIFEST := manifest.txt

# IMPORTANT: if you add a new dependency here, please make sure
# to also check the following files:
#   testutils/manifest.txt
LOCAL_JAVA_LIBRARIES := \
	common \
	junit \
	guava-tools

LOCAL_MODULE := sdktestutils

LOCAL_MODULE_TAGS := optional

include $(BUILD_HOST_JAVA_LIBRARY)

# Build tests

include $(CLEAR_VARS)

# Only compile source java files in this lib.
LOCAL_SRC_FILES := $(call all-java-files-under, src/main/java)

LOCAL_MODULE := sdktestutils-tests
LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := common sdktestutils junit

include $(BUILD_HOST_JAVA_LIBRARY)
