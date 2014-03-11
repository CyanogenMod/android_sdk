# This contains common definitions used to define a host module
# to link GoogleTest with the EmuGL test programs.
#
# This is used instead of including external/gtest/Android.mk to
# be able to build both the 32-bit and 64-bit binaries while
# building a 32-bit only SDK (sdk-eng, sdk_x86-eng, sdk_mips-eng).

LOCAL_PATH := external/gtest

$(call emugl-begin-host-static-library,libemugl_gtest)
LOCAL_SRC_FILES := \
    src/gtest-all.cc \
    src/gtest_main.cc
LOCAL_CFLAGS += -O0
LOCAL_CPP_EXTENSION := .cc
$(call emugl-export,C_INCLUDES,$(LOCAL_PATH)/include)
$(call emugl-export,LDLIBS,-lpthread)
$(call emugl-end-module)

$(call emugl-begin-host-static-library,lib64emugl_gtest)
LOCAL_SRC_FILES := \
    src/gtest-all.cc \
    src/gtest_main.cc
LOCAL_CFLAGS += -O0
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_CPP_EXTENSION := .cc
$(call emugl-export,C_INCLUDES,$(LOCAL_PATH)/include)
$(call emugl-export,CFLAGS,-m64)
$(call emugl-export,LDLIBS,-lpthread -m64)
$(call emugl-end-module)
