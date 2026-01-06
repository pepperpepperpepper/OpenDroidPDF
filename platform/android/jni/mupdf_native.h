#ifndef OPENDROIDPDF_MUPDF_NATIVE_H
#define OPENDROIDPDF_MUPDF_NATIVE_H

#include <jni.h>
#include <pthread.h>
#include <android/log.h>
#include <android/bitmap.h>

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <time.h>

#ifdef NDK_PROFILER
#include "prof.h"
#endif

#include "mupdf/fitz.h"
#include "mupdf/pdf.h"
#include "mupdf/pdf/annot.h"
#ifdef HAVE_OPENSSL
#include "mupdf/helpers/pkcs7-openssl.h"
#endif
#include "mupdf/ucdn.h"
#include "pdf-annot-imp.h"

typedef struct pp_pdf_alerts pp_pdf_alerts;

#define JNI_FN(A) Java_org_opendroidpdf_ ## A
#define PACKAGENAME "org/opendroidpdf"

#define LOG_TAG "libmupdf"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGT(...) __android_log_print(ANDROID_LOG_INFO,"alert",__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define MAX_SEARCH_HITS (500)
#define NUM_CACHE (3)
#define STRIKE_HEIGHT (0.375f)
#define UNDERLINE_HEIGHT (0.075f)
#define LINE_THICKNESS (0.07f)
#define INK_THICKNESS (3.0f)
#define INK_COLORr (1.0f)
#define INK_COLORg (0.0f)
#define INK_COLORb (0.0f)
#define HIGHLIGHT_COLORr (1.0f)
#define HIGHLIGHT_COLORg (1.0f)
#define HIGHLIGHT_COLORb (0.0f)
#define UNDERLINE_COLORr (0.0f)
#define UNDERLINE_COLORg (0.0f)
#define UNDERLINE_COLORb (1.0f)
#define STRIKEOUT_COLORr (1.0f)
#define STRIKEOUT_COLORg (0.0f)
#define STRIKEOUT_COLORb (0.0f)
#define TEXTANNOTICON_COLORr (0.0f)
#define TEXTANNOTICON_COLORg (0.0f)
#define TEXTANNOTICON_COLORb (1.0f)

#define SMALL_FLOAT (0.00001f)
#define PROOF_RESOLUTION (300)

enum widget_type
{
    NONE,
    TEXT,
    LISTBOX,
    COMBOBOX,
    SIGNATURE
};

typedef struct rect_node_s rect_node;
struct rect_node_s
{
    fz_rect rect;
    rect_node *next;
};

typedef struct
{
    int number;
    int width;
    int height;
    fz_rect media_box;
    fz_page *page;
    rect_node *changed_rects;
    rect_node *hq_changed_rects;
    fz_display_list *page_list;
    fz_display_list *annot_list;
} page_cache;

typedef struct globals_s globals;
struct globals_s
{
    fz_colorspace *colorspace;
    fz_document *doc;
    int resolution;
    fz_context *ctx;
    fz_rect *hit_bbox;
    int current;
    char *current_path;

    page_cache pages[NUM_CACHE];

    pp_pdf_alerts *alerts;

    JNIEnv *env;
    jclass thiz;

    float inkThickness;
    float inkColor[3];
    float highlightColor[3];
    float underlineColor[3];
    float strikeoutColor[3];
    float textAnnotIconColor[3];

    pdf_annot *focus_widget;
    int focus_widget_page;
};

extern jfieldID global_fid;
extern jfieldID buffer_fid;

globals *get_globals(JNIEnv *env, jobject thiz);
globals *get_globals_any_thread(JNIEnv *env, jobject thiz);
void init_annotation_defaults(globals *glo);
void drop_changed_rects(fz_context *ctx, rect_node **nodePtr);
void drop_page_cache(globals *glo, page_cache *pc);
void dump_annotation_display_lists(globals *glo);
void alerts_init(globals *glo);
void alerts_fin(globals *glo);
void close_doc(globals *glo);

JNIEXPORT void JNI_FN(MuPDFCore_gotoPageInternal)(JNIEnv *env, jobject thiz, int page);

#endif // OPENDROIDPDF_MUPDF_NATIVE_H
