LOCAL_PATH := $(call my-dir)
TOP_LOCAL_PATH := $(LOCAL_PATH)

# Point to the actual MuPDF checkout (../../../../mupdf from jni/)
# Use an absolute path so nested makefiles can rely on it.
MUPDF_ROOT := $(abspath $(LOCAL_PATH)/../../../../mupdf)

include $(MUPDF_ROOT)/Makelists

SSL_BUILD := 1

ifdef NDK_PROFILER
include android-ndk-profiler.mk
endif

include $(TOP_LOCAL_PATH)/Core.mk
include $(TOP_LOCAL_PATH)/ThirdParty.mk

include $(CLEAR_VARS)

LOCAL_C_INCLUDES := \
	jni/andprof \
	$(LOCAL_PATH)/../../common \
	$(MUPDF_ROOT)/include \
	$(MUPDF_ROOT)/source/fitz \
	$(MUPDF_ROOT)/source/pdf
LOCAL_CFLAGS :=
LOCAL_MODULE    := mupdf
LOCAL_SRC_FILES := \
	../../common/pp_core.c \
	document_io.c \
	alerts.c \
	cookies.c \
	proof.c \
	render.c \
	ink.c \
	export_share.c \
	text_annot.c \
	text_selection.c \
	separations.c \
	utils.c \
	widgets.c \
	widgets_signature.c
LOCAL_WHOLE_STATIC_LIBRARIES := mupdfcore mupdfthirdparty
## Ensure ld.lld pulls all archive objects
LOCAL_LDFLAGS += -Wl,--whole-archive
LOCAL_STATIC_LIBRARIES := $(LOCAL_STATIC_LIBRARIES) mupdfcore mupdfthirdparty
LOCAL_LDFLAGS += -Wl,--no-whole-archive
ifdef NDK_PROFILER
LOCAL_CFLAGS += -pg -DNDK_PROFILER
LOCAL_STATIC_LIBRARIES += andprof
endif
ifdef SUPPORT_GPROOF
LOCAL_CFLAGS += -DSUPPORT_GPROOF
endif

LOCAL_LDLIBS    := -lm -llog -ljnigraphics
ifdef SSL_BUILD
OPENSSL_LIB_PATH := $(MUPDF_ROOT)/thirdparty/openssl/android/$(TARGET_ARCH_ABI)/lib
LOCAL_LDLIBS	+= -L$(OPENSSL_LIB_PATH) -lcrypto -lssl
endif

include $(BUILD_SHARED_LIBRARY)
