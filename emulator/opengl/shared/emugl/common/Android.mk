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
$(call emugl-import,libemugl_common libemugl_gtest)
$(call emugl-end-module)

$(call emugl-begin-host-executable,emugl64_common_host_unittests)
LOCAL_SRC_FILES := $(host_commonSources)
$(call emugl-import,lib64emugl_common lib64emugl_gtest)
$(call emugl-end-module)
