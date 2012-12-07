# Copyright 2011 The Android Open Source Project
#
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under,src/main/java)
LOCAL_JAVA_RESOURCE_DIRS :=

LOCAL_JAR_MANIFEST := etc/manifest.txt
LOCAL_JAVA_LIBRARIES :=  \
	common \
	sdklib
LOCAL_MODULE := manifmerger
LOCAL_MODULE_TAGS := optional

include $(BUILD_HOST_JAVA_LIBRARY)

# Build tests
include $(CLEAR_VARS)

# Only compile source java files in this lib.
LOCAL_SRC_FILES := $(call all-java-files-under, src/test/java)
LOCAL_JAVA_RESOURCE_DIRS := src/test/java

LOCAL_MODULE := manifmerger-tests
LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := manifmerger sdklib-tests junit

include $(BUILD_HOST_JAVA_LIBRARY)
