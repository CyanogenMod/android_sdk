# Makefile to build the Windows SDK Tools under linux.
#
# This makefile is included by development/build/tools/windows_sdk.mk
# to device which tools we want to build from the sdk.git project.

WIN_SDK_TARGETS := \
	avdlauncher \
	emulator \
	emulator-arm \
	emulator-x86 \
	mksdcard \
	sdklauncher

# Add OpenGLES emulation host libraries if needed.
ifeq (true,$(BUILD_EMULATOR_OPENGL))
WIN_SDK_TARGETS += \
	libOpenglRender \
	libGLES_CM_translator \
	libGLES_V2_translator \
	libEGL_translator
endif

