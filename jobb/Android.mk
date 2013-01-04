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
# If not found, it tries to built it using the tools/base sources.
#
# TIP: to regenerate a new prebuilt, simply remove it and rebuilt it:
# $ rm prebuilts/devtools/jobb.jar
# $ make jobb
# $ cd prebuilts/devtools && git commit -a ...

LOCAL_MODULE := jobb
LOCAL_MODULE_TAGS := debug
LOCAL_JAVA_LIBRARIES := fat32lib

# Note: we need a path relative to this module's path for LOCAL_PREBUILT_JAVA_LIBRARIES
# and we need a TOPDIR "absolute" path for the make rule. The "../.." part isn't pretty
# but allows us to satisfy both requirements.
jobb_rel_jar := ../../prebuilts/devtools/$(LOCAL_MODULE)/$(LOCAL_MODULE)$(COMMON_JAVA_PACKAGE_SUFFIX)
jobb_abs_jar := $(LOCAL_PATH)/$(jobb_rel_jar)

# Tools/base project name
jobb_project_name := jobb

# Build the jar if it doesn't exist
ifeq (,$(wildcard $(jobb_abs_jar)))
$(info # Building $(jobb_project_name) prebuilt from tools/base using gradle)
ifeq (,$(wildcard $(TOPDIR)tools/base))
$(error Missing tools/base directory. You probably need to run 'repo init -g all,-notdefault,tools' or similar)
endif
$(jobb_abs_jar):
	$(hide)cd $(TOPDIR)tools/base && \
	./gradlew :$(jobb_project_name):jar && \
	src=`./gradlew :$(jobb_project_name):properties | \
	     awk 'BEGIN { N=""; V="" } \
	          /^archivesBaseName:/ { N=$$2 } \
	          /^version:/ { V=$$2 } \
	          END { print N "-" V ".jar" }'` && \
	cd ../.. && \
	dst=$(dir $(jobb_abs_jar)) && \
	mkdir -p $$dst && \
	cp $(TOPDIR)tools/base/$(jobb_project_name)/build/libs/$$src $(jobb_abs_jar) && \
	mkdir -p $$dst/etc && \
	cp $(TOPDIR)tools/base/$(jobb_project_name)/etc/{jobb,jobb.bat} $$dst/etc/
endif

LOCAL_PREBUILT_JAVA_LIBRARIES := $(jobb_rel_jar)

include $(BUILD_HOST_PREBUILT)

