LOCAL_PATH := $(call my-dir)
TOP_LOCAL_PATH := $(LOCAL_PATH)

# Point to the actual MuPDF checkout (../../../../mupdf from jni/)
# Use an absolute path so nested makefiles can rely on it.
MUPDF_ROOT := $(abspath $(LOCAL_PATH)/../../../../mupdf)

include $(MUPDF_ROOT)/Makelists

# Enable OpenSSL-backed signature support when the MuPDF checkout provides
# prebuilt OpenSSL libs. CI clones upstream MuPDF (no OpenSSL payload), so we
# auto-disable there to keep Android CI green while preserving the feature for
# local/F-Droid builds that stage OpenSSL under thirdparty/openssl/.
SSL_BUILD := 1
ifeq ($(wildcard $(MUPDF_ROOT)/thirdparty/openssl/android/$(TARGET_ARCH_ABI)/lib/libcrypto.a),)
SSL_BUILD :=
endif

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
	../../common/pp_core_cache.c \
	../../common/pp_core_doc.c \
	../../common/pp_core_cookie.c \
	../../common/pp_core_alloc.c \
	../../common/pp_core_widgets.c \
	../../common/pp_core_text.c \
	../../common/pp_core_render.c \
	../../common/pp_core_alerts.c \
	../../common/pp_core_export.c \
	../../common/pp_core_pdf_annots.c \
	../../common/pp_core_pdf_annots_ink.c \
	../../common/pp_core_pdf_annots_list.c \
	../../common/pp_core_pdf_annots_edit.c \
	../../common/pp_core_pdf_annots_compat.c \
	../../common/pp_core_pdf_annots_freetext.c \
	../../common/pp_core_pdf_annots_freetext_ds.c \
	../../common/pp_core_pdf_annots_freetext_ops.c \
	../../common/pp_core_pdf_annots_freetext_appearance.c \
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
	widgets.c
ifdef SSL_BUILD
LOCAL_SRC_FILES += widgets_signature.c
endif
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
