LOCAL_PATH := $(call my-dir)

ifdef SUPPORT_GPROOF
include $(CLEAR_VARS)
LOCAL_MODULE    := gsso
LOCAL_SRC_FILES := libgs.so
include $(PREBUILT_SHARED_LIBRARY)
endif

include $(CLEAR_VARS)

# Use the MuPDF root injected by Android.mk
MY_ROOT := $(MUPDF_ROOT)
MY_ROOT_INCLUDES := $(MUPDF_ROOT)

ifeq ($(TARGET_ARCH),arm)
LOCAL_CFLAGS += -DARCH_ARM -DARCH_THUMB -DARCH_ARM_CAN_LOAD_UNALIGNED
ifdef NDK_PROFILER
LOCAL_CFLAGS += -pg -DNDK_PROFILER
endif
endif
ifdef SUPPORT_GPROOF
LOCAL_CFLAGS += -DSUPPORT_GPROOF
endif
LOCAL_CFLAGS += -DAA_BITS=8
LOCAL_CFLAGS += -DFZ_ENABLE_ICC=0 -D_LARGEFILE_SOURCE=1 -D_FILE_OFFSET_BITS=64 -D_POSIX_C_SOURCE=200112L -Dfseeko=fseek -Dftello=ftell
LOCAL_CFLAGS += -DHAVE_ANDROID
LOCAL_CFLAGS += -DTOFU_NOTO -DTOFU_CJK -DTOFU_CJK_EXT -DTOFU_CJK_LANG -DTOFU_SIL
ifdef MEMENTO
LOCAL_CFLAGS += -DMEMENTO -DMEMENTO_LEAKONLY
endif
ifdef SSL_BUILD
LOCAL_CFLAGS += -DHAVE_OPENSSL
endif

LOCAL_C_INCLUDES := \
    $(MY_ROOT_INCLUDES)/thirdparty/jbig2dec \
    $(MY_ROOT_INCLUDES)/thirdparty/openjpeg/src/lib/openjp2 \
    $(MY_ROOT_INCLUDES)/thirdparty/libjpeg \
    $(MY_ROOT_INCLUDES)/thirdparty/mujs \
    $(MY_ROOT_INCLUDES)/thirdparty/zlib \
    $(MY_ROOT_INCLUDES)/thirdparty/freetype/include \
    $(MY_ROOT_INCLUDES)/thirdparty/lcms2/include \
    $(MY_ROOT_INCLUDES)/thirdparty/brotli/c/include \
    $(MY_ROOT_INCLUDES)/thirdparty/gumbo-parser/src \
    $(MY_ROOT_INCLUDES)/thirdparty/jpegxr/Software \
    $(MY_ROOT_INCLUDES)/thirdparty/harfbuzz/src \
    $(MY_ROOT_INCLUDES)/thirdparty/extract/include \
    $(MY_ROOT_INCLUDES)/thirdparty/extract/src \
    $(MY_ROOT_INCLUDES)/source/fitz \
    $(MY_ROOT_INCLUDES)/source/pdf \
    $(MY_ROOT_INCLUDES)/source/xps \
    $(MY_ROOT_INCLUDES)/source/cbz \
    $(MY_ROOT_INCLUDES)/source/svg \
    $(MY_ROOT_INCLUDES)/source/html \
    $(MY_ROOT_INCLUDES)/source/img \
    $(MY_ROOT_INCLUDES)/source/tiff \
    $(MY_ROOT_INCLUDES)/scripts/freetype \
    $(MY_ROOT_INCLUDES)/scripts/libjpeg \
    $(MY_ROOT_INCLUDES)/scripts/openjpeg \
    $(MY_ROOT_INCLUDES)/generated \
    $(MY_ROOT_INCLUDES)/resources \
    $(MY_ROOT_INCLUDES)/source/helpers/pkcs7 \
    $(MY_ROOT_INCLUDES)/include \
    $(MY_ROOT_INCLUDES)
ifdef V8_BUILD
LOCAL_C_INCLUDES += ../../thirdparty/$(V8)/include
endif
ifdef SSL_BUILD
LOCAL_C_INCLUDES += $(MY_ROOT_INCLUDES)/thirdparty/openssl/include
endif

LOCAL_MODULE    := mupdfcore
LOCAL_SRC_FILES := \
	$(wildcard $(MY_ROOT)/source/fitz/*.c) \
	$(wildcard $(MY_ROOT)/source/pdf/*.c) \
	$(wildcard $(MY_ROOT)/source/xps/*.c) \
	$(wildcard $(MY_ROOT)/source/cbz/*.c) \
	$(wildcard $(MY_ROOT)/source/svg/*.c) \
	$(wildcard $(MY_ROOT)/source/html/*.c) \
	$(wildcard $(MY_ROOT)/generated/resources/fonts/urw/*.c) \
	$(wildcard $(MY_ROOT)/generated/resources/hyphen/*.c)
LOCAL_SRC_FILES := $(filter-out $(MY_ROOT)/source/fitz/barcode.c,$(LOCAL_SRC_FILES))
# MuPDF JS: the current tree keeps pdf-js.c in source/pdf/
# We rely on the wildcard above to include it and build with thirdparty/mujs.

ifdef SSL_BUILD
LOCAL_SRC_FILES += $(MY_ROOT)/source/helpers/pkcs7/pkcs7-openssl.c
endif

ifdef SUPPORT_GPROOF
LOCAL_SHARED_LIBRARIES := gsso
endif
LOCAL_LDLIBS    := -lm -llog -ljnigraphics

# Paths above are now absolute relative to jni; no prefixing needed

include $(BUILD_STATIC_LIBRARY)
