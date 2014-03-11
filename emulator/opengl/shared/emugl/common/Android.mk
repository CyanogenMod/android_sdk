# This build script corresponds to a library containing many definitions
# common to both the guest and the host. They relate to
#
LOCAL_PATH := $(call my-dir)

### emugl_common host library ###########################################

commonSources := \
        smart_ptr.cpp \

host_commonSources := $(commonSources)

$(call emugl-begin-host-static-library,libemugl_common)
LOCAL_SRC_FILES := $(host_commonSources)
$(call emugl-end-module)

$(call emugl-begin-host-static-library,lib64emugl_common)
LOCAL_SRC_FILES := $(host_commonSources)
$(call emugl-export,CFLAGS,-m64)
$(call emugl-end-module)


### emugl_common_unittests ##############################################

host_commonSources := \
    smart_ptr_unittest.cpp

$(call emugl-begin-host-executable,emugl_common_host_unittests)
LOCAL_SRC_FILES := $(host_commonSources)
LOCAL_C_INCLUDES += external/gtest/include
$(call emugl-export,STATIC_LIBRARIES, libemugl_common libgtest_host libgtest_main_host)
$(call emugl-end-module)

# TODO(digit): 64-bit version, once we have 64-bit gtest.
