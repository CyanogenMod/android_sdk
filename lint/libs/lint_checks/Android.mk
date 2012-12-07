# Copyright 2011 The Android Open Source Project
#
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# Only compile source java files in this lib.
LOCAL_SRC_FILES := $(call all-java-files-under, src/main/java)
LOCAL_JAVA_RESOURCE_DIRS := src/main/java

# If the dependency list is changed, etc/manifest.txt
LOCAL_JAVA_LIBRARIES := \
	common \
	sdklib \
	layoutlib_api \
	lombok-ast-0.2 \
	lint_api \
	asm-tools \
	asm-tree-tools \
	asm-analysis-tools \
	guava-tools

LOCAL_MODULE := lint_checks
LOCAL_MODULE_TAGS := optional

include $(BUILD_HOST_JAVA_LIBRARY)

# Build tests
include $(CLEAR_VARS)

# Only compile source java files in this lib.
LOCAL_SRC_FILES := $(call all-java-files-under, src/test/java)

LOCAL_MODULE := lint_checks-tests
LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := common sdklib lint_api lint_checks lint junit easymock asm-tools asm-tree-tools guava-tools layoutlib_api sdktestutils

include $(BUILD_HOST_JAVA_LIBRARY)
