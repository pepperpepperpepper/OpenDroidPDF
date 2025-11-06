LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

MY_ROOT := $(MUPDF_ROOT)
MY_ROOT_INCLUDES := $(MUPDF_ROOT)

LOCAL_MODULE := mupdfthirdparty
LOCAL_CPP_EXTENSION := .cc

# Aggregate include paths from Makelists definitions
LOCAL_C_INCLUDES += $(patsubst -I%,$(MY_ROOT_INCLUDES)/%,$(filter -I%,$(FREETYPE_CFLAGS) $(FREETYPE_BUILD_CFLAGS)))
LOCAL_C_INCLUDES += $(patsubst -I%,$(MY_ROOT_INCLUDES)/%,$(filter -I%,$(GUMBO_CFLAGS) $(GUMBO_BUILD_CFLAGS)))
LOCAL_C_INCLUDES += $(patsubst -I%,$(MY_ROOT_INCLUDES)/%,$(filter -I%,$(HARFBUZZ_CFLAGS) $(HARFBUZZ_BUILD_CFLAGS)))
LOCAL_C_INCLUDES += $(patsubst -I%,$(MY_ROOT_INCLUDES)/%,$(filter -I%,$(JBIG2DEC_CFLAGS) $(JBIG2DEC_BUILD_CFLAGS)))
LOCAL_C_INCLUDES += $(patsubst -I%,$(MY_ROOT_INCLUDES)/%,$(filter -I%,$(LCMS2_CFLAGS) $(LCMS2_BUILD_CFLAGS)))
LOCAL_C_INCLUDES += $(patsubst -I%,$(MY_ROOT_INCLUDES)/%,$(filter -I%,$(LIBJPEG_CFLAGS) $(LIBJPEG_BUILD_CFLAGS)))
LOCAL_C_INCLUDES += $(patsubst -I%,$(MY_ROOT_INCLUDES)/%,$(filter -I%,$(MUJS_CFLAGS) $(MUJS_BUILD_CFLAGS)))
LOCAL_C_INCLUDES += $(patsubst -I%,$(MY_ROOT_INCLUDES)/%,$(filter -I%,$(OPENJPEG_CFLAGS) $(OPENJPEG_BUILD_CFLAGS)))
LOCAL_C_INCLUDES += $(patsubst -I%,$(MY_ROOT_INCLUDES)/%,$(filter -I%,$(BROTLI_CFLAGS) $(BROTLI_BUILD_CFLAGS)))
LOCAL_C_INCLUDES += $(patsubst -I%,$(MY_ROOT_INCLUDES)/%,$(filter -I%,$(ZLIB_CFLAGS) $(ZLIB_BUILD_CFLAGS)))
LOCAL_C_INCLUDES += $(patsubst -I%,$(MY_ROOT_INCLUDES)/%,$(filter -I%,$(EXTRACT_CFLAGS) $(EXTRACT_BUILD_CFLAGS)))

LOCAL_CFLAGS += $(filter-out -I%, $(FREETYPE_CFLAGS) $(FREETYPE_BUILD_CFLAGS))
LOCAL_CFLAGS += $(filter-out -I%, $(GUMBO_CFLAGS) $(GUMBO_BUILD_CFLAGS))
LOCAL_CPPFLAGS += $(filter-out -I%, $(HARFBUZZ_CFLAGS) $(HARFBUZZ_BUILD_CFLAGS))
LOCAL_CFLAGS += $(filter-out -I%, $(JBIG2DEC_CFLAGS) $(JBIG2DEC_BUILD_CFLAGS))
LOCAL_CFLAGS += $(filter-out -I%, $(LCMS2_CFLAGS) $(LCMS2_BUILD_CFLAGS))
LOCAL_CFLAGS += $(filter-out -I%, $(LIBJPEG_CFLAGS) $(LIBJPEG_BUILD_CFLAGS))
LOCAL_CFLAGS += $(filter-out -I%, $(MUJS_CFLAGS) $(MUJS_BUILD_CFLAGS))
LOCAL_CFLAGS += $(filter-out -I%, $(OPENJPEG_CFLAGS) $(OPENJPEG_BUILD_CFLAGS))
LOCAL_CFLAGS += $(filter-out -I%, $(BROTLI_CFLAGS) $(BROTLI_BUILD_CFLAGS))
LOCAL_CFLAGS += $(filter-out -I%, $(ZLIB_CFLAGS) $(ZLIB_BUILD_CFLAGS))
LOCAL_CFLAGS += $(filter-out -I%, $(EXTRACT_CFLAGS) $(EXTRACT_BUILD_CFLAGS))

ifdef NDK_PROFILER
LOCAL_CFLAGS += -pg -DNDK_PROFILER -O2
LOCAL_CPPFLAGS += -pg -DNDK_PROFILER -O2
endif

ifdef MEMENTO
LOCAL_CFLAGS += -DMEMENTO -DMEMENTO_LEAKONLY
LOCAL_CPPFLAGS += -DMEMENTO -DMEMENTO_LEAKONLY
endif

LOCAL_SRC_FILES := \
	$(patsubst %,$(MY_ROOT)/%,$(MUJS_SRC)) \
	$(patsubst %,$(MY_ROOT)/%,$(JBIG2DEC_SRC)) \
	$(patsubst %,$(MY_ROOT)/%,$(OPENJPEG_SRC)) \
	$(patsubst %,$(MY_ROOT)/%,$(LIBJPEG_SRC)) \
	$(patsubst %,$(MY_ROOT)/%,$(ZLIB_SRC)) \
	$(patsubst %,$(MY_ROOT)/%,$(FREETYPE_SRC)) \
	$(patsubst %,$(MY_ROOT)/%,$(LCMS2_SRC)) \
	$(patsubst %,$(MY_ROOT)/%,$(BROTLI_SRC)) \
	$(patsubst %,$(MY_ROOT)/%,$(GUMBO_SRC)) \
	$(patsubst %,$(MY_ROOT)/%,$(HARFBUZZ_SRC)) \
	$(patsubst %,$(MY_ROOT)/%,$(EXTRACT_SRC))

ifneq ($(wildcard $(MY_ROOT)/thirdparty/jpegxr/Software/algo.c),)
LOCAL_C_INCLUDES += $(patsubst -I%,$(MY_ROOT_INCLUDES)/%,$(filter -I%,$(JPEGXR_CFLAGS) $(JPEGXR_BUILD_CFLAGS)))
LOCAL_CFLAGS += $(filter-out -I%, $(JPEGXR_CFLAGS) $(JPEGXR_BUILD_CFLAGS))
LOCAL_SRC_FILES += $(patsubst %,$(MY_ROOT)/%,$(JPEGXR_SRC))
endif

include $(BUILD_STATIC_LIBRARY)
