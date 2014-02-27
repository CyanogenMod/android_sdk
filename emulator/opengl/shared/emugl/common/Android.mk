# This build script corresponds to a library containing many definitions
# common to both the guest and the host. They relate to
#
LOCAL_PATH := $(call my-dir)

### emugl_common host library ###########################################

commonSources := \
        lazy_instance.cpp \
        smart_ptr.cpp \
        sockets.cpp \
        thread_store.cpp \

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
    lazy_instance_unittest.cpp \
    mutex_unittest.cpp \
    smart_ptr_unittest.cpp \
    thread_store_unittest.cpp \

$(call emugl-begin-host-executable,emugl_common_host_unittests)
LOCAL_SRC_FILES := $(host_commonSources)
$(call emugl-import,libemugl_common libemugl_gtest)
$(call emugl-end-module)

$(call emugl-begin-host-executable,emugl64_common_host_unittests)
LOCAL_SRC_FILES := $(host_commonSources)
$(call emugl-import,lib64emugl_common lib64emugl_gtest)
$(call emugl-end-module)
