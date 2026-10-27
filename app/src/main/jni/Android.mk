LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := codepocket_analysis
LOCAL_SRC_FILES := analysis_process.c
LOCAL_CFLAGS := -std=c11 -Wall -Wextra -D_GNU_SOURCE
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := codepocket_execute
LOCAL_SRC_FILES := execution_process.c terminal_relay.c
LOCAL_CFLAGS := -std=c11 -Wall -Wextra -D_GNU_SOURCE
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := codepocket_cpp_runner
LOCAL_MODULE_FILENAME := codepocket_cpp_runner
LOCAL_SRC_FILES := cpp_runner.c
LOCAL_CFLAGS := -std=c11 -Wall -Wextra -D_GNU_SOURCE
LOCAL_LDLIBS := -ldl
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_EXECUTABLE)
