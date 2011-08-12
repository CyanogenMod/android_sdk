# Copyright 2011 The Android Open Source Project
#
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_JAVA_RESOURCE_DIRS := src

LOCAL_JAR_MANIFEST := etc/manifest.txt

# TODO figure out what to keep here and remove obsolete stuff
#
# IMPORTANT: if you add a new dependency here, please make sure
# to also check the following files:
#   sdkmanager/app/etc/manifest.txt
#   sdkmanager/app/etc/android.bat
# (Note that we don't reference swt.jar in these files since
#  it is dynamically added by android.bat/.sh based on whether the
#  current VM is 32 or 64 bit.)
#LOCAL_JAVA_LIBRARIES := \
#	androidprefs \
#	sdklib \
#	sdkuilib \
#	swt \
#	org.eclipse.jface_3.4.2.M20090107-0800 \
#	org.eclipse.equinox.common_3.4.0.v20080421-2006 \
#	org.eclipse.core.commands_3.4.0.I20080509-2000

LOCAL_MODULE := sdklauncher_exp
LOCAL_MODULE_TAGS := optional

include $(BUILD_HOST_JAVA_LIBRARY)

# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))

