# Copyright 2011 The Android Open Source Project
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

# The manifest-merger code has moved to tools/base/manifmerger.
# The rule below uses the prebuilt manifmerger.jar if found.
# If not found, it tries to built it using the tools/base sources.
#
# TIP: to regenerate a new prebuilt, simply remove it and rebuilt it:
# $ rm prebuilts/devtools/manifestmerger.jar
# $ make manifmerger
# $ cd prebuilts/devtools && git commit -a ...

LOCAL_MODULE := manifmerger
LOCAL_MODULE_TAGS := optional
LOCAL_JAVA_LIBRARIES := common sdklib

# Note: we need a path relative to this module's path for LOCAL_PREBUILT_JAVA_LIBRARIES
# and we need a TOPDIR "absolute" path for the make rule. The "../.." part isn't pretty
# but allows us to satisfy both requirements.
mm_rel_jar := ../../prebuilts/devtools/$(LOCAL_MODULE)/$(LOCAL_MODULE)$(COMMON_JAVA_PACKAGE_SUFFIX)
mm_abs_jar := $(LOCAL_PATH)/$(mm_rel_jar)

# Tools/base project name
mm_project_name := manifmerger

# Build the jar if it doesn't exist
ifeq (,$(wildcard $(mm_abs_jar)))
$(info # Building $(mm_project_name) prebuilt from tools/base using gradle)
ifeq (,$(wildcard $(TOPDIR)tools/base))
$(error Missing tools/base directory. You probably need to run 'repo init -g all,-notdefault,tools' or similar)
endif
$(mm_abs_jar):
	$(hide)cd $(TOPDIR)tools/base && \
	./gradlew :$(mm_project_name):jar && \
	src=`./gradlew :$(mm_project_name):properties | \
	     awk 'BEGIN { N=""; V="" } \
	          /^archivesBaseName:/ { N=$$2 } \
	          /^version:/ { V=$$2 } \
	          END { print N "-" V ".jar" }'` && \
	cd ../.. && \
	mkdir -p $(dir $(mm_abs_jar)) && \
	cp $(TOPDIR)tools/base/$(mm_project_name)/build/libs/$$src $(mm_abs_jar)
endif

LOCAL_PREBUILT_JAVA_LIBRARIES := $(mm_rel_jar)

include $(BUILD_HOST_PREBUILT)

