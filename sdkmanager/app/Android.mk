# Copyright 2007 The Android Open Source Project
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

LOCAL_SRC_FILES := $(call all-java-files-under, src/main/java)
LOCAL_JAVA_RESOURCE_DIRS := src/main/java

LOCAL_JAR_MANIFEST := etc/manifest.txt

# IMPORTANT: if you add a new dependency here, please make sure
# to also check the following files:
#   sdkmanager/app/etc/manifest.txt
#   sdkmanager/app/etc/android.bat
# (Note that we don't reference swt.jar in these files since
#  it is dynamically added by android.bat/.sh based on whether the
#  current VM is 32 or 64 bit.)
LOCAL_JAVA_LIBRARIES := \
	common \
	guava-tools \
	sdklib \
	sdkuilib \
	swt \
	org.eclipse.jface_3.6.2.M20110210-1200 \
	org.eclipse.equinox.common_3.6.0.v20100503 \
	org.eclipse.core.commands_3.6.0.I20100512-1500

LOCAL_MODULE := sdkmanager
LOCAL_MODULE_TAGS := optional

include $(BUILD_HOST_JAVA_LIBRARY)

# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))


# ----- TESTS ------
# Copyright (C) 2011 The Android Open Source Project

include $(CLEAR_VARS)

# Only compile source java files in this lib.
LOCAL_SRC_FILES := $(call all-java-files-under, src/test/java)

LOCAL_MODULE := sdkmanager-tests
LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := \
    guava-tools \
    httpclient-4.1.1 \
    httpcore-4.1 \
    httpmime-4.1.1 \
    junit \
    sdkmanager \
    sdklib

include $(BUILD_HOST_JAVA_LIBRARY)

