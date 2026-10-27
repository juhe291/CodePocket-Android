LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := codepocket_analysis
LOCAL_SRC_FILES := analysis_process.c
LOCAL_CFLAGS := -std=c11 -Wall -Wextra -D_GNU_SOURCE
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)
