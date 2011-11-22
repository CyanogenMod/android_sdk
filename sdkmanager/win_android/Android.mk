# Copyright 2011 The Android Open Source Project
#
# Android.mk for sdkmanager/win_android


LOCAL_PATH := $(call my-dir)

# find_java static library for host
# ========================================================

include $(CLEAR_VARS)

LOCAL_MODULE := libfindjava
LOCAL_SRC_FILES := find_java.cpp utils.cpp

LOCAL_CFLAGS += -Wall -Wno-unused-parameter
LOCAL_CFLAGS += -D_XOPEN_SOURCE -D_GNU_SOURCE -DSH_HISTORY -DUSE_MINGW

include $(BUILD_HOST_STATIC_LIBRARY)


# "win_android.exe", a host replacement for "android.bat". For the Windows SDK only.
# ========================================================

include $(CLEAR_VARS)

ifeq ($(HOST_OS),windows)

LOCAL_SRC_FILES := \
	win_android.cpp

LOCAL_MODULE := win_android
LOCAL_STATIC_LIBRARIES := libfindjava

LOCAL_CFLAGS += -Wall -Wno-unused-parameter
LOCAL_CFLAGS += -D_XOPEN_SOURCE -D_GNU_SOURCE -DSH_HISTORY -DUSE_MINGW

LOCAL_MODULE_TAGS := optional

# Locate windres executable
WINDRES := windres
ifneq ($(USE_MINGW),)
  # When building the Windows resources under Linux, use the MinGW one
  WINDRES := i586-mingw32msvc-windres
endif

# Link the Windows icon file as well into the executable, based on the technique
# used in external/qemu/Makefile.android.  The variables need to have different
# names to not interfere with the ones from qemu/Makefile.android.
#
INTERMEDIATE         := $(call intermediates-dir-for,EXECUTABLES,$(LOCAL_MODULE),true)
WIN_ANDROID_ICON_OBJ  := win_android_icon.o
WIN_ANDROID_ICON_PATH := $(LOCAL_PATH)/images
$(WIN_ANDROID_ICON_PATH)/$(WIN_ANDROID_ICON_OBJ): $(WIN_ANDROID_ICON_PATH)/android_icon.rc
	$(WINDRES) $< -I $(WIN_ANDROID_ICON_PATH) -o $@

# seems to be the only way to add an object file that was not generated from
# a C/C++/Java source file to our build system. and very unfortunately,
# $(TOPDIR)/$(LOCALPATH) will always be prepended to this value, which forces
# us to put the object file in the source directory...
#
LOCAL_PREBUILT_OBJ_FILES += images/$(WIN_ANDROID_ICON_OBJ)

include $(BUILD_HOST_EXECUTABLE)

$(call dist-for-goals,droid,$(LOCAL_BUILT_MODULE))

endif


