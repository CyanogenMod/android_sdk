# Copyright 2007 The Android Open Source Project
#
SDKMANAGERAPP_LOCAL_DIR := $(call my-dir)

# Build all sub-directories
include $(call all-makefiles-under,$(SDKMANAGERAPP_LOCAL_DIR))
