#include <jni.h>
#include <time.h>
#include <pthread.h>
#include <android/log.h>
#include <android/bitmap.h>

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <time.h>
#include <stdlib.h>

#ifdef NDK_PROFILER
#include "prof.h"
#endif

#include "mupdf/fitz.h"
#include "mupdf/pdf.h"
#include "mupdf/pdf/annot.h"
#include "mupdf/helpers/pkcs7-openssl.h"
#include "mupdf/ucdn.h"
#include "pdf-annot-imp.h"

#define JNI_FN(A) Java_com_cgogolin_penandpdf_ ## A
#define PACKAGENAME "com/cgogolin/penandpdf"

#define LOG_TAG "libmupdf"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGT(...) __android_log_print(ANDROID_LOG_INFO,"alert",__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

/* Enable to log rendering times (render each frame 100 times and time) */
#undef TIME_DISPLAY_LIST

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

#define SMALL_FLOAT (0.00001)
#define PROOF_RESOLUTION (300)



enum
{
    NONE,
    TEXT,
    LISTBOX,
    COMBOBOX,
    SIGNATURE
};

static const jchar PDFDocEncoding[] = {
    0x0000, 0x0001, 0x0002, 0x0003, 0x0004, 0x0005, 0x0006, 0x0007,  
    0x0008, 0x0009, 0x000a, 0x000b, 0x000c, 0x000d, 0x000e, 0x000f,  
    0x0010, 0x0011, 0x0012, 0x0013, 0x0014, 0x0015, 0x0016, 0x0017,  
    0x02d8, 0x02c7, 0x02c6, 0x02d9, 0x02dd, 0x02db, 0x02da, 0x02dc,  
    0x0020, 0x0021, 0x0022, 0x0023, 0x0024, 0x0025, 0x0026, 0x0027,  
    0x0028, 0x0029, 0x002a, 0x002b, 0x002c, 0x002d, 0x002e, 0x002f,  
    0x0030, 0x0031, 0x0032, 0x0033, 0x0034, 0x0035, 0x0036, 0x0037,  
    0x0038, 0x0039, 0x003a, 0x003b, 0x003c, 0x003d, 0x003e, 0x003f,  
    0x0040, 0x0041, 0x0042, 0x0043, 0x0044, 0x0045, 0x0046, 0x0047,  
    0x0048, 0x0049, 0x004a, 0x004b, 0x004c, 0x004d, 0x004e, 0x004f,  
    0x0050, 0x0051, 0x0052, 0x0053, 0x0054, 0x0055, 0x0056, 0x0057,  
    0x0058, 0x0059, 0x005a, 0x005b, 0x005c, 0x005d, 0x005e, 0x005f,  
    0x0060, 0x0061, 0x0062, 0x0063, 0x0064, 0x0065, 0x0066, 0x0067,  
    0x0068, 0x0069, 0x006a, 0x006b, 0x006c, 0x006d, 0x006e, 0x006f,  
    0x0070, 0x0071, 0x0072, 0x0073, 0x0074, 0x0075, 0x0076, 0x0077,  
    0x0078, 0x0079, 0x007a, 0x007b, 0x007c, 0x007d, 0x007e, 0xfffd,  
    0x2022, 0x2020, 0x2021, 0x2026, 0x2014, 0x2013, 0x0192, 0x2044,  
    0x2039, 0x203a, 0x2212, 0x2030, 0x201e, 0x201c, 0x201d, 0x2018,  
    0x2019, 0x201a, 0x2122, 0xfb01, 0xfb02, 0x0141, 0x0152, 0x0160,  
    0x0178, 0x017d, 0x0131, 0x0142, 0x0153, 0x0161, 0x017e, 0xfffd,  
    0x20ac, 0x00a1, 0x00a2, 0x00a3, 0x00a4, 0x00a5, 0x00a6, 0x00a7,  
    0x00a8, 0x00a9, 0x00aa, 0x00ab, 0x00ac, 0xfffd, 0x00ae, 0x00af,  
    0x00b0, 0x00b1, 0x00b2, 0x00b3, 0x00b4, 0x00b5, 0x00b6, 0x00b7,  
    0x00b8, 0x00b9, 0x00ba, 0x00bb, 0x00bc, 0x00bd, 0x00be, 0x00bf,  
    0x00c0, 0x00c1, 0x00c2, 0x00c3, 0x00c4, 0x00c5, 0x00c6, 0x00c7,  
    0x00c8, 0x00c9, 0x00ca, 0x00cb, 0x00cc, 0x00cd, 0x00ce, 0x00cf,  
    0x00d0, 0x00d1, 0x00d2, 0x00d3, 0x00d4, 0x00d5, 0x00d6, 0x00d7,  
    0x00d8, 0x00d9, 0x00da, 0x00db, 0x00dc, 0x00dd, 0x00de, 0x00df,  
    0x00e0, 0x00e1, 0x00e2, 0x00e3, 0x00e4, 0x00e5, 0x00e6, 0x00e7,  
    0x00e8, 0x00e9, 0x00ea, 0x00eb, 0x00ec, 0x00ed, 0x00ee, 0x00ef,  
    0x00f0, 0x00f1, 0x00f2, 0x00f3, 0x00f4, 0x00f5, 0x00f6, 0x00f7,  
    0x00f8, 0x00f9, 0x00fa, 0x00fb, 0x00fc, 0x00fd, 0x00fe, 0x00ff,  
};

typedef struct rect_node_s rect_node;

#include <stdbool.h>

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

	int alerts_initialised;
	// fin_lock and fin_lock2 are used during shutdown. The two waiting tasks
	// show_alert and waitForAlertInternal respectively take these locks while
	// waiting. During shutdown, the conditions are signaled and then the fin_locks
	// are taken momentarily to ensure the blocked threads leave the controlled
	// area of code before the mutexes and condition variables are destroyed.
	pthread_mutex_t fin_lock;
	pthread_mutex_t fin_lock2;
	// alert_lock is the main lock guarding the variables directly below.
	pthread_mutex_t alert_lock;
	// Flag indicating if the alert system is active. When not active, both
	// show_alert and waitForAlertInternal return immediately.
	int alerts_active;
	// Pointer to the alert struct passed in by show_alert, and valid while
	// show_alert is blocked.
	pdf_alert_event *current_alert;
	// Flag and condition varibles to signal a request is present and a reply
	// is present, respectively. The condition variables alone are not sufficient
	// because of the pthreads permit spurious signals.
	int alert_request;
	int alert_reply;
	pthread_cond_t alert_request_cond;
	pthread_cond_t alert_reply_cond;

	// For the buffer reading mode, we need to implement stream reading, which
	// needs access to the following.
	JNIEnv *env;
	jclass thiz;

	float inkThickness;
    float inkColor[3];
    float highlightColor[3];
    float underlineColor[3];
    float strikeoutColor[3];
    float textAnnotIconColor[3];

    // Focused widget reference (kept), may be NULL
    pdf_annot *focus_widget;
};

static jfieldID global_fid;
static jfieldID buffer_fid;

static fz_font *load_noto(fz_context *ctx, const char *a, const char *b, const char *c, int idx)
{
	char buf[500];
	fz_font *font = NULL;
	fz_try(ctx)
	{
		fz_snprintf(buf, sizeof buf, "/system/fonts/%s%s%s.ttf", a, b, c);
		if (!fz_file_exists(ctx, buf))
			fz_snprintf(buf, sizeof buf, "/system/fonts/%s%s%s.otf", a, b, c);
		if (!fz_file_exists(ctx, buf))
			fz_snprintf(buf, sizeof buf, "/system/fonts/%s%s%s.ttc", a, b, c);
		if (fz_file_exists(ctx, buf))
			font = fz_new_font_from_file(ctx, NULL, buf, idx, 0);
	}
	fz_catch(ctx)
		return NULL;
	return font;
}

static fz_font *load_noto_cjk(fz_context *ctx, int lang)
{
	fz_font *font = load_noto(ctx, "NotoSerif", "CJK", "-Regular", lang);
	if (!font) font = load_noto(ctx, "NotoSans", "CJK", "-Regular", lang);
	if (!font) font = load_noto(ctx, "DroidSans", "Fallback", "", 0);
	return font;
}

static fz_font *load_noto_arabic(fz_context *ctx)
{
	fz_font *font = load_noto(ctx, "Noto", "Naskh", "-Regular", 0);
	if (!font) font = load_noto(ctx, "Noto", "NaskhArabic", "-Regular", 0);
	if (!font) font = load_noto(ctx, "Droid", "Naskh", "-Regular", 0);
	if (!font) font = load_noto(ctx, "NotoSerif", "Arabic", "-Regular", 0);
	if (!font) font = load_noto(ctx, "NotoSans", "Arabic", "-Regular", 0);
	if (!font) font = load_noto(ctx, "DroidSans", "Arabic", "-Regular", 0);
	return font;
}

static void penandpdf_set_ink_annot_list(
    fz_context *ctx,
    pdf_document *doc,
    pdf_annot *annot,
    fz_matrix ctm,
    int arc_count,
    int *counts,
    fz_point *pts,
    float color[3],
    float thickness)
{
    pdf_obj *list;
    pdf_obj *bs;
    pdf_obj *col;
    fz_rect rect;
    rect.x0 = rect.x1 = rect.y0 = rect.y1 = 0;
    int rect_init = 0;
    int i, j, k = 0;

    if (arc_count <= 0 || counts == NULL || pts == NULL)
        return;

    list = pdf_new_array(ctx, doc, arc_count);
    pdf_dict_puts_drop(ctx, annot->obj, "InkList", list);

    for (i = 0; i < arc_count; i++)
    {
        int count = counts[i];
        pdf_obj *arc = pdf_new_array(ctx, doc, count);
        pdf_array_push_drop(ctx, list, arc);

        for (j = 0; j < count; j++)
        {
            fz_point pt = pts[k++];
            pt = fz_transform_point(pt, ctm);
            pts[k-1] = pt;

            if (!rect_init)
            {
                rect.x0 = rect.x1 = pt.x;
                rect.y0 = rect.y1 = pt.y;
                rect_init = 1;
            }
            else
            {
                rect = fz_include_point_in_rect(rect, pt);
            }

            pdf_array_push_drop(ctx, arc, pdf_new_real(ctx, pt.x));
            pdf_array_push_drop(ctx, arc, pdf_new_real(ctx, pt.y));
        }
    }

    if (rect_init && thickness > 0.0f)
    {
        rect.x0 -= thickness;
        rect.y0 -= thickness;
        rect.x1 += thickness;
        rect.y1 += thickness;
    }
    pdf_dict_puts_drop(ctx, annot->obj, "Rect", pdf_new_rect(ctx, doc, rect));

    bs = pdf_new_dict(ctx, doc, 1);
    pdf_dict_puts_drop(ctx, annot->obj, "BS", bs);
    pdf_dict_puts_drop(ctx, bs, "W", pdf_new_real(ctx, thickness));

    col = pdf_new_array(ctx, doc, 3);
    pdf_dict_puts_drop(ctx, annot->obj, "C", col);
    for (i = 0; i < 3; i++)
        pdf_array_push_drop(ctx, col, pdf_new_real(ctx, color[i]));
}

static fz_font *load_noto_try(fz_context *ctx, const char *stem)
{
	fz_font *font = load_noto(ctx, "NotoSerif", stem, "-Regular", 0);
	if (!font) font = load_noto(ctx, "NotoSerif", stem, "-VF", 0);
	if (!font) font = load_noto(ctx, "NotoSans", stem, "-Regular", 0);
	if (!font) font = load_noto(ctx, "NotoSans", stem, "-VF", 0);
	if (!font) font = load_noto(ctx, "DroidSans", stem, "-Regular", 0);
	return font;
}

enum { JP, KR, SC, TC };

static fz_font *load_droid_fallback_font(fz_context *ctx, int script, int language, int serif, int bold, int italic)
{
	const char *stem = NULL;
	(void)serif;
	(void)bold;
	(void)italic;

	switch (script)
	{
	case UCDN_SCRIPT_HANGUL: return load_noto_cjk(ctx, KR);
	case UCDN_SCRIPT_HIRAGANA: return load_noto_cjk(ctx, JP);
	case UCDN_SCRIPT_KATAKANA: return load_noto_cjk(ctx, JP);
	case UCDN_SCRIPT_BOPOMOFO: return load_noto_cjk(ctx, TC);
	case UCDN_SCRIPT_HAN:
		switch (language)
		{
		case FZ_LANG_ja: return load_noto_cjk(ctx, JP);
		case FZ_LANG_ko: return load_noto_cjk(ctx, KR);
		case FZ_LANG_zh_Hans: return load_noto_cjk(ctx, SC);
		default:
		case FZ_LANG_zh_Hant: return load_noto_cjk(ctx, TC);
		}
	case UCDN_SCRIPT_ARABIC:
		return load_noto_arabic(ctx);
	default:
		stem = fz_lookup_noto_stem_from_script(ctx, script, language);
		if (stem)
			return load_noto_try(ctx, stem);
	}

	return NULL;
}

static fz_font *load_droid_cjk_font(fz_context *ctx, const char *name, int ros, int serif)
{
	(void)name;
	(void)serif;

	switch (ros)
	{
	case FZ_ADOBE_CNS: return load_noto_cjk(ctx, TC);
	case FZ_ADOBE_GB: return load_noto_cjk(ctx, SC);
	case FZ_ADOBE_JAPAN: return load_noto_cjk(ctx, JP);
	case FZ_ADOBE_KOREA: return load_noto_cjk(ctx, KR);
	}
	return NULL;
}

static fz_font *load_droid_font(fz_context *ctx, const char *name, int bold, int italic, int needs_exact_metrics)
{
	(void)ctx;
	(void)name;
	(void)bold;
	(void)italic;
	(void)needs_exact_metrics;
	return NULL;
}

static void install_android_system_fonts(fz_context *ctx)
{
	if (!ctx)
		return;
	fz_try(ctx)
	{
		fz_install_load_system_font_funcs(ctx,
			load_droid_font,
			load_droid_cjk_font,
			load_droid_fallback_font);
	}
	fz_catch(ctx)
	{
		fz_warn(ctx, "Failed to hook Android system fonts: %s", fz_caught_message(ctx));
	}
}

static void drop_changed_rects(fz_context *ctx, rect_node **nodePtr)
{
	rect_node *node = *nodePtr;
	while (node)
	{
		rect_node *tnode = node;
		node = node->next;
		fz_free(ctx, tnode);
	}

	*nodePtr = NULL;
}

static void drop_page_cache(globals *glo, page_cache *pc)
{
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;

	LOGI("Drop page %d", pc->number);
	fz_drop_display_list(ctx, pc->page_list);
	pc->page_list = NULL;
	fz_drop_display_list(ctx, pc->annot_list);
	pc->annot_list = NULL;
	fz_drop_page(ctx, pc->page);
	pc->page = NULL;
	drop_changed_rects(ctx, &pc->changed_rects);
	drop_changed_rects(ctx, &pc->hq_changed_rects);
}

static void dump_annotation_display_lists(globals *glo)
{
	fz_context *ctx = glo->ctx;
	int i;

	for (i = 0; i < NUM_CACHE; i++) {
		fz_drop_display_list(ctx, glo->pages[i].annot_list);
		glo->pages[i].annot_list = NULL;
	}
}

static void show_alert(globals *glo, pdf_alert_event *alert)
{
	pthread_mutex_lock(&glo->fin_lock2);
	pthread_mutex_lock(&glo->alert_lock);

	LOGT("Enter show_alert: %s", alert->title);
	alert->button_pressed = 0;

	if (glo->alerts_active)
	{
		glo->current_alert = alert;
		glo->alert_request = 1;
		pthread_cond_signal(&glo->alert_request_cond);

		while (glo->alerts_active && !glo->alert_reply)
			pthread_cond_wait(&glo->alert_reply_cond, &glo->alert_lock);
		glo->alert_reply = 0;
		glo->current_alert = NULL;
	}

	LOGT("Exit show_alert");

	pthread_mutex_unlock(&glo->alert_lock);
	pthread_mutex_unlock(&glo->fin_lock2);
}

static void event_cb(fz_context *ctx, pdf_document *doc, pdf_doc_event *event, void *data)
{
	globals *glo = (globals *)data;

	switch (event->type)
	{
	case PDF_DOCUMENT_EVENT_ALERT:
		show_alert(glo, pdf_access_alert_event(ctx, event));
		break;
	}
}

static void alerts_init(globals *glo)
{
	fz_context *ctx = glo->ctx;
	pdf_document *idoc = pdf_specifics(ctx, glo->doc);

	if (!idoc || glo->alerts_initialised)
		return;

	if (idoc)
		pdf_enable_js(ctx, idoc);

	glo->alerts_active = 0;
	glo->alert_request = 0;
	glo->alert_reply = 0;
	pthread_mutex_init(&glo->fin_lock, NULL);
	pthread_mutex_init(&glo->fin_lock2, NULL);
	pthread_mutex_init(&glo->alert_lock, NULL);
	pthread_cond_init(&glo->alert_request_cond, NULL);
	pthread_cond_init(&glo->alert_reply_cond, NULL);

    pdf_set_doc_event_callback(ctx, idoc, event_cb, NULL, glo);
	LOGT("alert_init");
	glo->alerts_initialised = 1;
}

static void alerts_fin(globals *glo)
{
	fz_context *ctx = glo->ctx;
	pdf_document *idoc = pdf_specifics(ctx, glo->doc);
	if (!glo->alerts_initialised)
		return;

	LOGT("Enter alerts_fin");
    if (idoc)
        pdf_set_doc_event_callback(ctx, idoc, NULL, NULL, NULL);

	// Set alerts_active false and wake up show_alert and waitForAlertInternal,
	pthread_mutex_lock(&glo->alert_lock);
	glo->current_alert = NULL;
	glo->alerts_active = 0;
	pthread_cond_signal(&glo->alert_request_cond);
	pthread_cond_signal(&glo->alert_reply_cond);
	pthread_mutex_unlock(&glo->alert_lock);

	// Wait for the fin_locks.
	pthread_mutex_lock(&glo->fin_lock);
	pthread_mutex_unlock(&glo->fin_lock);
	pthread_mutex_lock(&glo->fin_lock2);
	pthread_mutex_unlock(&glo->fin_lock2);

	pthread_cond_destroy(&glo->alert_reply_cond);
	pthread_cond_destroy(&glo->alert_request_cond);
	pthread_mutex_destroy(&glo->alert_lock);
	pthread_mutex_destroy(&glo->fin_lock2);
	pthread_mutex_destroy(&glo->fin_lock);
	LOGT("Exit alerts_fin");
	glo->alerts_initialised = 0;
}

// Should only be called from the single background AsyncTask thread
static globals *get_globals(JNIEnv *env, jobject thiz)
{
    // Be defensive: if the cached field id was not initialised (e.g. called
    // before openFile/openBuffer set global_fid, or after a classloader
    // change), resolve it on demand.
    if (global_fid == NULL)
    {
        jclass clazz = (*env)->GetObjectClass(env, thiz);
        global_fid = (*env)->GetFieldID(env, clazz, "globals", "J");
        // clazz is a local ref; letting it go out of scope is fine here.
    }

    globals *glo = (globals *)(intptr_t)((*env)->GetLongField(env, thiz, global_fid));
    if (glo != NULL)
    {
        glo->env = env;
        glo->thiz = thiz;
    }
    return glo;
}

// May be called from any thread, provided the values of glo->env and glo->thiz
// are not used.
static globals *get_globals_any_thread(JNIEnv *env, jobject thiz)
{
	return (globals *)(intptr_t)((*env)->GetLongField(env, thiz, global_fid));
}

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
	srand(time(NULL));
	return JNI_VERSION_1_2; // return this to make dalvik happy: https://groups.google.com/forum/#!topic/android-ndk/ukQBmKJH2eM
}


JNIEXPORT jlong JNICALL
JNI_FN(MuPDFCore_openFile)(JNIEnv * env, jobject thiz, jstring jfilename)
{
	const char *filename;
	globals *glo;
	fz_context *ctx;
	jclass clazz;

#ifdef NDK_PROFILER
	monstartup("libmupdf.so");
#endif

	clazz = (*env)->GetObjectClass(env, thiz);
	global_fid = (*env)->GetFieldID(env, clazz, "globals", "J");

		glo = calloc(1, sizeof(*glo));
		if (glo == NULL)
			return 0;
		glo->resolution = 160;
		glo->alerts_initialised = 0;

        // Initialize annotation styling defaults (mirror openFile path)
        glo->inkThickness = INK_THICKNESS;
        glo->inkColor[0] = INK_COLORr;
        glo->inkColor[1] = INK_COLORg;
        glo->inkColor[2] = INK_COLORb;
        glo->highlightColor[0] = HIGHLIGHT_COLORr;
        glo->highlightColor[1] = HIGHLIGHT_COLORg;
        glo->highlightColor[2] = HIGHLIGHT_COLORb;
        glo->underlineColor[0] = UNDERLINE_COLORr;
        glo->underlineColor[1] = UNDERLINE_COLORg;
        glo->underlineColor[2] = UNDERLINE_COLORb;
        glo->strikeoutColor[0] = STRIKEOUT_COLORr;
        glo->strikeoutColor[1] = STRIKEOUT_COLORg;
        glo->strikeoutColor[2] = STRIKEOUT_COLORb;
        glo->textAnnotIconColor[0] = TEXTANNOTICON_COLORr;
        glo->textAnnotIconColor[1] = TEXTANNOTICON_COLORg;
        glo->textAnnotIconColor[2] = TEXTANNOTICON_COLORb;
        glo->focus_widget = NULL;

#ifdef DEBUG
	/* Try and send stdout/stderr to file in debug builds. This
	 * path may not work on all platforms, but it works on the
	 * LG G3, and it's no worse than not redirecting it anywhere
	 * on anything else. */
	freopen("/storage/emulated/0/Download/stdout.txt", "a", stdout);
	freopen("/storage/emulated/0/Download/stderr.txt", "a", stderr);
#endif

        //Initialized defaults for annotation styling
    glo->inkThickness = INK_THICKNESS;
    glo->inkColor[0] = INK_COLORr;
    glo->inkColor[1] = INK_COLORg;
    glo->inkColor[2] = INK_COLORb;
    glo->highlightColor[0] = HIGHLIGHT_COLORr;
    glo->highlightColor[1] = HIGHLIGHT_COLORg;
    glo->highlightColor[2] = HIGHLIGHT_COLORb;
    glo->underlineColor[0] = UNDERLINE_COLORr;
    glo->underlineColor[1] = UNDERLINE_COLORg;
    glo->underlineColor[2] = UNDERLINE_COLORb;        
    glo->strikeoutColor[0] = STRIKEOUT_COLORr;
    glo->strikeoutColor[1] = STRIKEOUT_COLORg;
    glo->strikeoutColor[2] = STRIKEOUT_COLORb;
    glo->textAnnotIconColor[0] = TEXTANNOTICON_COLORr;
    glo->textAnnotIconColor[1] = TEXTANNOTICON_COLORg;
    glo->textAnnotIconColor[2] = TEXTANNOTICON_COLORb;
    glo->focus_widget = NULL;
        
    filename = (*env)->GetStringUTFChars(env, jfilename, NULL);
    if (filename == NULL)
    {
        LOGE("Failed to get filename");
        free(glo);
        return 0;
    }

	/* 128 MB store for low memory devices. Tweak as necessary. */
	glo->ctx = ctx = fz_new_context(NULL, NULL, 128 << 20);
	if (!ctx)
	{
		LOGE("Failed to initialise context");
		(*env)->ReleaseStringUTFChars(env, jfilename, filename);
		free(glo);
		return 0;
	}

	fz_register_document_handlers(ctx);
	install_android_system_fonts(ctx);

	glo->doc = NULL;
	fz_try(ctx)
	{
		glo->colorspace = fz_device_rgb(ctx);

		LOGI("Opening document...");
		fz_try(ctx)
		{
			glo->current_path = fz_strdup(ctx, (char *)filename);
			glo->doc = fz_open_document(ctx, (char *)filename);
			alerts_init(glo);
		}
		fz_catch(ctx)
		{
			fz_throw(ctx, FZ_ERROR_GENERIC, "Cannot open document: '%s'", filename);
		}
		LOGI("Done!");
	}
	fz_catch(ctx)
	{
            LOGE("Failed: %s", fz_caught_message(ctx));
		fz_drop_document(ctx, glo->doc);
		glo->doc = NULL;
		fz_drop_context(ctx);
		glo->ctx = NULL;
		free(glo);
		glo = NULL;
	}

	(*env)->ReleaseStringUTFChars(env, jfilename, filename);

	return (jlong)(intptr_t)glo;
}

// Use MuPDF's memory/buffer stream helpers for openBuffer

JNIEXPORT jlong JNICALL
JNI_FN(MuPDFCore_openBuffer)(JNIEnv * env, jobject thiz, jstring jmagic)
{
	globals *glo;
	fz_context *ctx;
    jclass clazz;
    fz_stream *stream = NULL;
	const char *magic;

#ifdef NDK_PROFILER
	monstartup("libmupdf.so");
#endif

	clazz = (*env)->GetObjectClass(env, thiz);
	global_fid = (*env)->GetFieldID(env, clazz, "globals", "J");

	glo = calloc(1, sizeof(*glo));
	if (glo == NULL)
		return 0;
	glo->resolution = 160;
	glo->alerts_initialised = 0;
	glo->env = env;
	glo->thiz = thiz;
	buffer_fid = (*env)->GetFieldID(env, clazz, "fileBuffer", "[B");

	magic = (*env)->GetStringUTFChars(env, jmagic, NULL);
	if (magic == NULL)
	{
		LOGE("Failed to get magic");
		free(glo);
		return 0;
	}

	/* 128 MB store for low memory devices. Tweak as necessary. */
	glo->ctx = ctx = fz_new_context(NULL, NULL, 128 << 20);
	if (!ctx)
	{
		LOGE("Failed to initialise context");
		(*env)->ReleaseStringUTFChars(env, jmagic, magic);
		free(glo);
		return 0;
	}

    fz_register_document_handlers(ctx);
    install_android_system_fonts(ctx);
    fz_var(stream);

	glo->doc = NULL;
	fz_try(ctx)
	{
        // Build a memory-backed stream from the Java byte[]
        jbyteArray array = (jbyteArray)(*env)->GetObjectField(env, thiz, buffer_fid);
        jsize alen = (*env)->GetArrayLength(env, array);
        jboolean isCopy = 0;
        jbyte *adata = (*env)->GetByteArrayElements(env, array, &isCopy);
        fz_buffer *buf = fz_new_buffer_from_copied_data(ctx, (const unsigned char *)adata, (size_t)alen);
        (*env)->ReleaseByteArrayElements(env, array, adata, JNI_ABORT);
        (*env)->DeleteLocalRef(env, array);
        stream = fz_open_buffer(ctx, buf);
        fz_drop_buffer(ctx, buf);

		glo->colorspace = fz_device_rgb(ctx);

		LOGI("Opening document...");
		fz_try(ctx)
		{
			glo->current_path = NULL;
			glo->doc = fz_open_document_with_stream(ctx, magic, stream);
			alerts_init(glo);
		}
		fz_catch(ctx)
		{
			fz_throw(ctx, FZ_ERROR_GENERIC, "Cannot open memory document");
		}
		LOGI("Done!");
	}
	fz_always(ctx)
	{
		fz_drop_stream(ctx, stream);
	}
	fz_catch(ctx)
	{
        LOGE("Failed: %s", fz_caught_message(ctx));
		fz_drop_document(ctx, glo->doc);
		glo->doc = NULL;
		fz_drop_context(ctx);
		glo->ctx = NULL;
		free(glo);
		glo = NULL;
	}

	(*env)->ReleaseStringUTFChars(env, jmagic, magic);

	return (jlong)(intptr_t)glo;
}

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_countPagesInternal)(JNIEnv *env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	int count = 0;

	fz_try(ctx)
	{
		count = fz_count_pages(ctx, glo->doc);
	}
	fz_catch(ctx)
	{
        LOGE("exception while counting pages: %s", fz_caught_message(ctx));
	}
	return count;
}

JNIEXPORT jstring JNICALL
JNI_FN(MuPDFCore_fileFormatInternal)(JNIEnv * env, jobject thiz)
{
	char info[64];
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;

	fz_lookup_metadata(ctx, glo->doc, FZ_META_FORMAT, info, sizeof(info));

	return (*env)->NewStringUTF(env, info);
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_isUnencryptedPDFInternal)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals_any_thread(env, thiz);
	if (glo == NULL)
		return JNI_FALSE;

	fz_context *ctx = glo->ctx;
	pdf_document *idoc = pdf_specifics(ctx, glo->doc);
	if (idoc == NULL)
		return JNI_FALSE; // Not a PDF

    // Consider unencrypted if no password is needed
    return (fz_needs_password(ctx, glo->doc) == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_gotoPageInternal)(JNIEnv *env, jobject thiz, int page)
{
	int i;
	int furthest;
	int furthest_dist = -1;
	float zoom;
	fz_matrix ctm;
	fz_irect bbox;
	page_cache *pc;
	globals *glo = get_globals(env, thiz);
	if (glo == NULL)
		return;
	fz_context *ctx = glo->ctx;

	for (i = 0; i < NUM_CACHE; i++)
	{
		if (glo->pages[i].page != NULL && glo->pages[i].number == page)
		{
			/* The page is already cached */
			glo->current = i;
			return;
		}

		if (glo->pages[i].page == NULL)
		{
			/* cache record unused, and so a good one to use */
			furthest = i;
			furthest_dist = INT_MAX;
		}
		else
		{
			int dist = abs(glo->pages[i].number - page);

			/* Further away - less likely to be needed again */
			if (dist > furthest_dist)
			{
				furthest_dist = dist;
				furthest = i;
			}
		}
	}

	glo->current = furthest;
	pc = &glo->pages[glo->current];

	drop_page_cache(glo, pc);

	/* In the event of an error, ensure we give a non-empty page */
	pc->width = 100;
	pc->height = 100;

	pc->number = page;
	LOGI("Goto page %d...", page);
	fz_try(ctx)
	{
		fz_rect rect;
		LOGI("Load page %d", pc->number);
            pc->page = fz_load_page(ctx, glo->doc, pc->number);
            zoom = glo->resolution / 72;
            pc->media_box = fz_bound_page(ctx, pc->page);
            ctm = fz_scale(zoom, zoom);
            rect = pc->media_box;
            bbox = fz_round_rect(fz_transform_rect(rect, ctm));
		pc->width = bbox.x1-bbox.x0;
		pc->height = bbox.y1-bbox.y0;
	}
	fz_catch(ctx)
	{
		LOGE("cannot make displaylist from page %d", pc->number);
	}
}

JNIEXPORT float JNICALL
JNI_FN(MuPDFCore_getPageWidth)(JNIEnv *env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	LOGI("PageWidth=%d", glo->pages[glo->current].width);
	return glo->pages[glo->current].width;
}

JNIEXPORT float JNICALL
JNI_FN(MuPDFCore_getPageHeight)(JNIEnv *env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	LOGI("PageHeight=%d", glo->pages[glo->current].height);
	return glo->pages[glo->current].height;
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_javascriptSupported)(JNIEnv *env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	pdf_document *idoc = pdf_specifics(ctx, glo->doc);
	if (idoc)
		return pdf_js_supported(ctx, idoc);
	return 0;
}

static void update_changed_rects(globals *glo, page_cache *pc, pdf_document *idoc)
{
    fz_context *ctx = glo->ctx;
    (void)idoc;
    /* New MuPDF updates page appearance in one call; skip polling changed annots. */
    pdf_update_page(ctx, (pdf_page *)pc->page);
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_drawPage)(JNIEnv *env, jobject thiz, jobject bitmap,
		int pageW, int pageH, int patchX, int patchY, int patchW, int patchH, jlong cookiePtr)
{
	AndroidBitmapInfo info;
	void *pixels;
	int ret;
	fz_device *dev = NULL;
	float zoom;
	fz_matrix ctm;
	fz_irect bbox;
	fz_rect rect;
	fz_pixmap *pix = NULL;
	float xscale, yscale;
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	page_cache *pc = &glo->pages[glo->current];
	int hq = (patchW < pageW || patchH < pageH);
	fz_matrix scale;
	fz_cookie *cookie = (fz_cookie *)(intptr_t)cookiePtr;

	if (pc->page == NULL)
		return 0;

	fz_var(pix);
	fz_var(dev);

	LOGI("In native method\n");
	if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
		LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
		return 0;
	}

	LOGI("Checking format\n");
	if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
		LOGE("Bitmap format is not RGBA_8888 !");
		return 0;
	}

	LOGI("locking pixels\n");
	if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
		return 0;
	}

	/* Call mupdf to render display list to screen */
	LOGI("Rendering page(%d)=%dx%d patch=[%d,%d,%d,%d]",
			pc->number, pageW, pageH, patchX, patchY, patchW, patchH);

	fz_try(ctx)
	{
		fz_irect pixbbox;
		pdf_document *idoc = pdf_specifics(ctx, doc);

		if (idoc)
		{
			/* Update the changed-rects for both hq patch and main bitmap */
			update_changed_rects(glo, pc, idoc);

			/* Then drop the changed-rects for the bitmap we're about to
			render because we are rendering the entire area */
			drop_changed_rects(ctx, hq ? &pc->hq_changed_rects : &pc->changed_rects);
		}

            if (pc->page_list == NULL)
            {
                /* Render to list */
                pc->page_list = fz_new_display_list(ctx, pc->media_box);
                dev = fz_new_list_device(ctx, pc->page_list);

                LOGI("native draw_page() with cookie=%ld", (long)(intptr_t)cookie);
                if (cookie != NULL && !cookie->abort)
                    fz_run_page_contents(ctx, pc->page, dev, fz_identity, cookie);
                fz_drop_device(ctx, dev);
                dev = NULL;
			if (cookie != NULL && cookie->abort)
			{
				fz_drop_display_list(ctx, pc->page_list);
				pc->page_list = NULL;
				fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");
			}
		}
            if (pc->annot_list == NULL)
            {
                pdf_annot *annot;
                pc->annot_list = fz_new_display_list(ctx, pc->media_box);
                dev = fz_new_list_device(ctx, pc->annot_list);
                for (annot = pdf_first_annot(ctx, (pdf_page*)pc->page); annot; annot = pdf_next_annot(ctx, annot)) 
                {
                    if (cookie == NULL || cookie->abort)
                        break;
                    pdf_run_annot(ctx, annot, dev, fz_identity, cookie);
                }
                fz_drop_device(ctx, dev);
                dev = NULL;
			if (cookie != NULL && cookie->abort)
			{
				fz_drop_display_list(ctx, pc->annot_list);
				pc->annot_list = NULL;
				fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");
			}
		}
		bbox.x0 = patchX;
		bbox.y0 = patchY;
		bbox.x1 = patchX + patchW;
		bbox.y1 = patchY + patchH;
		pixbbox = bbox;
		pixbbox.x1 = pixbbox.x0 + info.width;
		/* pixmaps cannot handle right-edge padding, so the bbox must be expanded to
		 * match the pixels data */
		pix = fz_new_pixmap_with_bbox_and_data(ctx, glo->colorspace, pixbbox, NULL, 1, (unsigned char *)pixels);
		if (pc->page_list == NULL && pc->annot_list == NULL)
		{
			fz_clear_pixmap_with_value(ctx, pix, 0xd0);
			break;
		}
		fz_clear_pixmap_with_value(ctx, pix, 0xff);

		zoom = glo->resolution / 72;
                ctm = fz_scale(zoom, zoom);
		rect = pc->media_box;
		bbox = fz_round_rect(fz_transform_rect(rect, ctm));
		/* Now, adjust ctm so that it would give the correct page width
		 * heights. */
		xscale = (float)pageW/(float)(bbox.x1-bbox.x0);
		yscale = (float)pageH/(float)(bbox.y1-bbox.y0);
                ctm = fz_pre_scale(ctm, xscale, yscale);
		rect = pc->media_box;
                rect = fz_transform_rect(rect, ctm);
                dev = fz_new_draw_device(ctx, fz_identity, pix);
#ifdef TIME_DISPLAY_LIST
		{
			clock_t time;
			int i;

			LOGI("Executing display list");
			time = clock();
			for (i=0; i<100;i++) {
#endif
                        if (pc->page_list)
                            fz_run_display_list(ctx, pc->page_list, dev, ctm, rect, cookie);
				if (cookie != NULL && cookie->abort)
					fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");

                        if (pc->annot_list)
                            fz_run_display_list(ctx, pc->annot_list, dev, ctm, rect, cookie);
				if (cookie != NULL && cookie->abort)
					fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");

#ifdef TIME_DISPLAY_LIST
			}
			time = clock() - time;
			LOGI("100 renders in %d (%d per sec)", time, CLOCKS_PER_SEC);
		}
#endif
		fz_drop_device(ctx, dev);
		dev = NULL;
		fz_drop_pixmap(ctx, pix);
		LOGI("Rendered");
	}
	fz_always(ctx)
	{
		fz_drop_device(ctx, dev);
		dev = NULL;
	}
	fz_catch(ctx)
	{
		LOGE("Render failed");
	}

	AndroidBitmap_unlockPixels(env, bitmap);

	return 1;
}

static char *widget_type_string(int t)
{
	switch(t)
	{
    case PDF_WIDGET_TYPE_BUTTON: return "pushbutton";
	case PDF_WIDGET_TYPE_CHECKBOX: return "checkbox";
	case PDF_WIDGET_TYPE_RADIOBUTTON: return "radiobutton";
	case PDF_WIDGET_TYPE_TEXT: return "text";
	case PDF_WIDGET_TYPE_LISTBOX: return "listbox";
	case PDF_WIDGET_TYPE_COMBOBOX: return "combobox";
	case PDF_WIDGET_TYPE_SIGNATURE: return "signature";
	default: return "non-widget";
	}
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_updatePageInternal)(JNIEnv *env, jobject thiz, jobject bitmap, int page,
		int pageW, int pageH, int patchX, int patchY, int patchW, int patchH, jlong cookiePtr)
{
	AndroidBitmapInfo info;
	void *pixels;
	int ret;
	fz_device *dev = NULL;
	float zoom;
	fz_matrix ctm;
	fz_irect bbox;
	fz_rect rect;
	fz_pixmap *pix = NULL;
	float xscale, yscale;
	pdf_document *idoc;
	page_cache *pc = NULL;
	int hq = (patchW < pageW || patchH < pageH);
	int i;
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	rect_node *crect;
	fz_matrix scale;
	fz_cookie *cookie = (fz_cookie *)(intptr_t)cookiePtr;

	for (i = 0; i < NUM_CACHE; i++)
	{
		if (glo->pages[i].page != NULL && glo->pages[i].number == page)
		{
			pc = &glo->pages[i];
			break;
		}
	}

	if (pc == NULL)
	{
		/* Without a cached page object we cannot perform a partial update so
		render the entire bitmap instead */
		JNI_FN(MuPDFCore_gotoPageInternal)(env, thiz, page);
		return JNI_FN(MuPDFCore_drawPage)(env, thiz, bitmap, pageW, pageH, patchX, patchY, patchW, patchH, (jlong)(intptr_t)cookie);
	}

	idoc = pdf_specifics(ctx, doc);

	fz_var(pix);
	fz_var(dev);

	LOGI("In native method\n");
	if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
		LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
		return 0;
	}

	LOGI("Checking format\n");
	if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
		LOGE("Bitmap format is not RGBA_8888 !");
		return 0;
	}

	LOGI("locking pixels\n");
	if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
		return 0;
	}

	/* Call mupdf to render display list to screen */
	LOGI("Rendering page(%d)=%dx%d patch=[%d,%d,%d,%d]",
			pc->number, pageW, pageH, patchX, patchY, patchW, patchH);

	fz_try(ctx)
	{
			pdf_annot *annot;
		fz_irect pixbbox;

		if (idoc)
		{
			/* Update the changed-rects for both hq patch and main bitmap */
			update_changed_rects(glo, pc, idoc);
		}

                if (pc->page_list == NULL)
                {
                    /* Render to list */
                    pc->page_list = fz_new_display_list(ctx, pc->media_box);
                    dev = fz_new_list_device(ctx, pc->page_list);
			fz_run_page_contents(ctx, pc->page, dev, fz_identity, cookie);
			fz_drop_device(ctx, dev);
			dev = NULL;
			if (cookie != NULL && cookie->abort)
			{
				fz_drop_display_list(ctx, pc->page_list);
				pc->page_list = NULL;
				fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");
			}
		}

                if (pc->annot_list == NULL) {
                    pc->annot_list = fz_new_display_list(ctx, pc->media_box);
                    dev = fz_new_list_device(ctx, pc->annot_list);
                    for (pdf_annot *annot = pdf_first_annot(ctx, (pdf_page*)pc->page); annot; annot = pdf_next_annot(ctx, annot))
                        pdf_run_annot(ctx, annot, dev, fz_identity, cookie);
			fz_drop_device(ctx, dev);
			dev = NULL;
			if (cookie != NULL && cookie->abort)
			{
				fz_drop_display_list(ctx, pc->annot_list);
				pc->annot_list = NULL;
				fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");
			}
		}

		bbox.x0 = patchX;
		bbox.y0 = patchY;
		bbox.x1 = patchX + patchW;
		bbox.y1 = patchY + patchH;
		pixbbox = bbox;
		pixbbox.x1 = pixbbox.x0 + info.width;
		/* pixmaps cannot handle right-edge padding, so the bbox must be expanded to
		 * match the pixels data */
		pix = fz_new_pixmap_with_bbox_and_data(ctx, glo->colorspace, pixbbox, NULL, 1, (unsigned char *)pixels);

		zoom = glo->resolution / 72;
            ctm = fz_scale(zoom, zoom);
            rect = pc->media_box;
            bbox = fz_round_rect(fz_transform_rect(rect, ctm));
		/* Now, adjust ctm so that it would give the correct page width
		 * heights. */
		xscale = (float)pageW/(float)(bbox.x1-bbox.x0);
		yscale = (float)pageH/(float)(bbox.y1-bbox.y0);
            ctm = fz_pre_scale(ctm, xscale, yscale);
            rect = pc->media_box;
            rect = fz_transform_rect(rect, ctm);

		LOGI("Start partial update");
		for (crect = hq ? pc->hq_changed_rects : pc->changed_rects; crect; crect = crect->next)
		{
			fz_irect abox;
                fz_rect arect = crect->rect;
                arect = fz_transform_rect(arect, ctm);
                arect = fz_intersect_rect(arect, rect);
                abox = fz_round_rect(arect);

			LOGI("Update rectangle (%d, %d, %d, %d)", abox.x0, abox.y0, abox.x1, abox.y1);
                if (!fz_is_empty_irect(abox))
			{
				LOGI("And it isn't empty");
                    fz_clear_pixmap_rect_with_value(ctx, pix, 0xff, abox);
                dev = fz_new_draw_device_with_bbox(ctx, fz_identity, pix, &abox);
				if (pc->page_list)
                    fz_run_display_list(ctx, pc->page_list, dev, ctm, arect, cookie);
				if (cookie != NULL && cookie->abort)
					fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");

				if (pc->annot_list)
                    fz_run_display_list(ctx, pc->annot_list, dev, ctm, arect, cookie);
				if (cookie != NULL && cookie->abort)
					fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");

				fz_drop_device(ctx, dev);
				dev = NULL;
			}
		}
		LOGI("End partial update");

		/* Drop the changed rects we've just rendered */
		drop_changed_rects(ctx, hq ? &pc->hq_changed_rects : &pc->changed_rects);

		LOGI("Rendered");
	}
	fz_always(ctx)
	{
		fz_drop_device(ctx, dev);
		dev = NULL;
	}
	fz_catch(ctx)
	{
		LOGE("Render failed");
	}

	fz_drop_pixmap(ctx, pix);
	AndroidBitmap_unlockPixels(env, bitmap);

	return 1;
}

/* legacy text helpers removed; migrated to structured text (stext) APIs */

static int
countOutlineItems(fz_outline *outline)
{
    int count = 0;

    while (outline)
    {
        if (outline->title && outline->page.page >= 0)
            count++;

        count += countOutlineItems(outline->down);
        outline = outline->next;
    }

	return count;
}

static int
fillInOutlineItems(JNIEnv * env, jclass olClass, jmethodID ctor, jobjectArray arr, int pos, fz_outline *outline, int level)
{
    while (outline)
    {
        /* Treat entries with valid page as internal links */
        if (outline->page.page >= 0)
        {
            int page = outline->page.page;
            if (outline->title)
            {
                jobject ol;
                jstring title = (*env)->NewStringUTF(env, outline->title);
                if (title == NULL) return -1;
                ol = (*env)->NewObject(env, olClass, ctor, level, title, page);
                if (ol == NULL) return -1;
                (*env)->SetObjectArrayElement(env, arr, pos, ol);
                (*env)->DeleteLocalRef(env, ol);
                (*env)->DeleteLocalRef(env, title);
                pos++;
            }
        }
        pos = fillInOutlineItems(env, olClass, ctor, arr, pos, outline->down, level+1);
        if (pos < 0) return -1;
        outline = outline->next;
    }

    return pos;
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_needsPasswordInternal)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;

	return fz_needs_password(ctx, glo->doc) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_authenticatePasswordInternal)(JNIEnv *env, jobject thiz, jstring password)
{
	const char *pw;
	int result;
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;

	pw = (*env)->GetStringUTFChars(env, password, NULL);
	if (pw == NULL)
		return JNI_FALSE;

	result = fz_authenticate_password(ctx, glo->doc, (char *)pw);
	(*env)->ReleaseStringUTFChars(env, password, pw);
	return result;
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_hasOutlineInternal)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	fz_outline *outline = fz_load_outline(ctx, glo->doc);

	fz_drop_outline(glo->ctx, outline);
	return (outline == NULL) ? JNI_FALSE : JNI_TRUE;
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_getOutlineInternal)(JNIEnv * env, jobject thiz)
{
	jclass olClass;
	jmethodID ctor;
	jobjectArray arr;
	jobject ol;
	fz_outline *outline;
	int nItems;
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	jobjectArray ret;

	olClass = (*env)->FindClass(env, PACKAGENAME "/OutlineItem");
	if (olClass == NULL) return NULL;
	ctor = (*env)->GetMethodID(env, olClass, "<init>", "(ILjava/lang/String;I)V");
	if (ctor == NULL) return NULL;

	outline = fz_load_outline(ctx, glo->doc);
	nItems = countOutlineItems(outline);

	arr = (*env)->NewObjectArray(env,
					nItems,
					olClass,
					NULL);
	if (arr == NULL) return NULL;

	ret = fillInOutlineItems(env, olClass, ctor, arr, 0, outline, 0) > 0
			? arr
			:NULL;
	fz_drop_outline(glo->ctx, outline);
	return ret;
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_searchPage)(JNIEnv * env, jobject thiz, jstring jtext)
{
    jclass rectClass;
    jmethodID ctor;
    jobjectArray arr;
    jobject rect;
    fz_stext_page *text = NULL;
    fz_device *dev = NULL;
    float zoom;
    fz_matrix ctm;
    int i;
    int hit_count = 0;
    const char *str;
    globals *glo = get_globals(env, thiz);
    fz_context *ctx = glo->ctx;
    page_cache *pc = &glo->pages[glo->current];

    rectClass = (*env)->FindClass(env, "android/graphics/RectF");
    if (rectClass == NULL) return NULL;
    ctor = (*env)->GetMethodID(env, rectClass, "<init>", "(FFFF)V");
    if (ctor == NULL) return NULL;
    str = (*env)->GetStringUTFChars(env, jtext, NULL);
    if (str == NULL) return NULL;

    fz_var(text);
    fz_var(dev);

    fz_try(ctx)
    {
        if (glo->hit_bbox == NULL)
            glo->hit_bbox = fz_malloc_array(ctx, MAX_SEARCH_HITS, fz_rect);

        zoom = glo->resolution / 72;
        ctm = fz_scale(zoom, zoom);
        text = fz_new_stext_page(ctx, fz_bound_page(ctx, pc->page));
        dev = fz_new_stext_device(ctx, text, NULL);
        fz_run_page(ctx, pc->page, dev, ctm, NULL);
        fz_drop_device(ctx, dev);
        dev = NULL;

        fz_quad quads[MAX_SEARCH_HITS];
        hit_count = fz_search_stext_page(ctx, text, str, NULL, quads, MAX_SEARCH_HITS);
        for (i = 0; i < hit_count; i++)
        {
            fz_rect r = fz_rect_from_quad(quads[i]);
            glo->hit_bbox[i] = r;
        }
    }
    fz_always(ctx)
    {
        fz_drop_stext_page(ctx, text);
        fz_drop_device(ctx, dev);
    }
    fz_catch(ctx)
    {
        jclass cls;
        (*env)->ReleaseStringUTFChars(env, jtext, str);
        cls = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        if (cls != NULL)
            (*env)->ThrowNew(env, cls, "Out of memory in MuPDFCore_searchPage");
        (*env)->DeleteLocalRef(env, cls);

        return NULL;
    }

    (*env)->ReleaseStringUTFChars(env, jtext, str);

    arr = (*env)->NewObjectArray(env, hit_count, rectClass, NULL);
    if (arr == NULL) return NULL;

    for (i = 0; i < hit_count; i++) {
        rect = (*env)->NewObject(env, rectClass, ctor,
                    (float) (glo->hit_bbox[i].x0),
                    (float) (glo->hit_bbox[i].y0),
                    (float) (glo->hit_bbox[i].x1),
                    (float) (glo->hit_bbox[i].y1));
        if (rect == NULL)
            return NULL;
        (*env)->SetObjectArrayElement(env, arr, i, rect);
        (*env)->DeleteLocalRef(env, rect);
    }

    return arr;
}


JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_text)(JNIEnv * env, jobject thiz)
{
    jclass textCharClass;
    jclass textSpanClass;
    jclass textLineClass;
    jclass textBlockClass;
    jmethodID ctor;
    jobjectArray barr = NULL;
    fz_stext_page *text = NULL;
    fz_device *dev = NULL;
    float zoom;
    fz_matrix ctm;
    globals *glo = get_globals(env, thiz);
    fz_context *ctx = glo->ctx;
    page_cache *pc = &glo->pages[glo->current];

    textCharClass = (*env)->FindClass(env, PACKAGENAME "/TextChar");
    if (textCharClass == NULL) return NULL;
    textSpanClass = (*env)->FindClass(env, "[L" PACKAGENAME "/TextChar;");
    if (textSpanClass == NULL) return NULL;
    textLineClass = (*env)->FindClass(env, "[[L" PACKAGENAME "/TextChar;");
    if (textLineClass == NULL) return NULL;
    textBlockClass = (*env)->FindClass(env, "[[[L" PACKAGENAME "/TextChar;");
    if (textBlockClass == NULL) return NULL;
    ctor = (*env)->GetMethodID(env, textCharClass, "<init>", "(FFFFC)V");
    if (ctor == NULL) return NULL;

    fz_var(text);
    fz_var(dev);

    fz_try(ctx)
    {
        zoom = glo->resolution / 72;
        ctm = fz_scale(zoom, zoom);

        text = fz_new_stext_page(ctx, fz_bound_page(ctx, pc->page));
        dev = fz_new_stext_device(ctx, text, NULL);
        fz_run_page(ctx, pc->page, dev, ctm, NULL);
        fz_drop_device(ctx, dev);
        dev = NULL;

        int block_count = 0;
        for (fz_stext_block *b = text->first_block; b; b = b->next)
            if (b->type == FZ_STEXT_BLOCK_TEXT)
                block_count++;

        barr = (*env)->NewObjectArray(env, block_count, textBlockClass, NULL);
        if (barr == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "NewObjectArray failed");

        int bindex = 0;
        for (fz_stext_block *b = text->first_block; b; b = b->next)
        {
            if (b->type != FZ_STEXT_BLOCK_TEXT) continue;

            int line_count = 0;
            for (fz_stext_line *ln = b->u.t.first_line; ln; ln = ln->next) line_count++;

            jobjectArray larr = (*env)->NewObjectArray(env, line_count, textLineClass, NULL);
            if (larr == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "NewObjectArray failed");

            int lindex = 0;
            for (fz_stext_line *ln = b->u.t.first_line; ln; ln = ln->next)
            {
                jobjectArray sarr = (*env)->NewObjectArray(env, 1, textSpanClass, NULL);
                if (sarr == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "NewObjectArray failed");

                int char_count = 0;
                for (fz_stext_char *ch = ln->first_char; ch; ch = ch->next) char_count++;

                jobjectArray carr = (*env)->NewObjectArray(env, char_count, textCharClass, NULL);
                if (carr == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "NewObjectArray failed");

                int cindex = 0;
                for (fz_stext_char *ch = ln->first_char; ch; ch = ch->next)
                {
                    fz_rect rb = fz_rect_from_quad(ch->quad);
                    jobject cobj = (*env)->NewObject(env, textCharClass, ctor, rb.x0, rb.y0, rb.x1, rb.y1, (jchar)ch->c);
                    if (cobj == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "NewObjectfailed");
                    (*env)->SetObjectArrayElement(env, carr, cindex++, cobj);
                    (*env)->DeleteLocalRef(env, cobj);
                }

                (*env)->SetObjectArrayElement(env, sarr, 0, carr);
                (*env)->DeleteLocalRef(env, carr);

                (*env)->SetObjectArrayElement(env, larr, lindex++, sarr);
                (*env)->DeleteLocalRef(env, sarr);
            }

            (*env)->SetObjectArrayElement(env, barr, bindex++, larr);
            (*env)->DeleteLocalRef(env, larr);
        }
    }
    fz_always(ctx)
    {
        fz_drop_stext_page(ctx, text);
        fz_drop_device(ctx, dev);
    }
    fz_catch(ctx)
    {
        jclass cls = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        if (cls != NULL)
            (*env)->ThrowNew(env, cls, "Out of memory in MuPDFCore_text");
        (*env)->DeleteLocalRef(env, cls);

        return NULL;
    }

    return barr;
}


JNIEXPORT jbyteArray JNICALL
JNI_FN(MuPDFCore_textAsHtml)(JNIEnv * env, jobject thiz)
{
    fz_stext_page *text = NULL;
    fz_device *dev = NULL;
    fz_matrix ctm;
    globals *glo = get_globals(env, thiz);
    fz_context *ctx = glo->ctx;
    page_cache *pc = &glo->pages[glo->current];
    jbyteArray bArray = NULL;
    fz_buffer *buf = NULL;
    fz_output *out = NULL;

    fz_var(text);
    fz_var(dev);
    fz_var(buf);
    fz_var(out);

    fz_try(ctx)
    {
        ctm = fz_identity;
        text = fz_new_stext_page(ctx, fz_bound_page(ctx, pc->page));
        dev = fz_new_stext_device(ctx, text, NULL);
        fz_run_page(ctx, pc->page, dev, ctm, NULL);
        fz_close_device(ctx, dev);

        buf = fz_new_buffer(ctx, 256);
        out = fz_new_output_with_buffer(ctx, buf);
        fz_print_stext_header_as_html(ctx, out);
        fz_print_stext_page_as_html(ctx, out, text, pc->number);
        fz_print_stext_trailer_as_html(ctx, out);
        fz_close_output(ctx, out);

        unsigned char *data; size_t len = fz_buffer_storage(ctx, buf, &data);
        bArray = (*env)->NewByteArray(env, (jsize)len);
        if (bArray == NULL)
            fz_throw(ctx, FZ_ERROR_GENERIC, "Failed to make byteArray");
        (*env)->SetByteArrayRegion(env, bArray, 0, (jsize)len, (jbyte *)data);

    }
    fz_always(ctx)
    {
        fz_drop_stext_page(ctx, text);
        fz_drop_device(ctx, dev);
        fz_drop_output(ctx, out);
        fz_drop_buffer(ctx, buf);
    }
    fz_catch(ctx)
    {
        jclass cls = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        if (cls != NULL)
            (*env)->ThrowNew(env, cls, "Out of memory in MuPDFCore_textAsHtml");
        (*env)->DeleteLocalRef(env, cls);

        return NULL;
    }
    return bArray;
}


JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_addMarkupAnnotationInternal)(JNIEnv * env, jobject thiz, jobjectArray points, enum pdf_annot_type type, jstring jtext)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];
    jclass pt_cls;
    jfieldID x_fid, y_fid;
    int i, n;
    fz_point *pts = NULL;
    float color[3];
    float alpha;
    float line_height;
    float line_thickness;
    
    if (idoc == NULL)
        return;    
            
        switch (type)
        {
        case PDF_ANNOT_HIGHLIGHT:
            color[0] = glo->highlightColor[0];
            color[1] = glo->highlightColor[1];
            color[2] = glo->highlightColor[2];
            alpha = 0.69f; //HACK: Alphas smaller than 0.7 also get /BM Multiply in pdf_dev_alpha and so are displayed "behind" the text!
            line_thickness = 1.0;
            line_height = 0.5;
            break;
        case PDF_ANNOT_UNDERLINE:
            color[0] = glo->underlineColor[0];
            color[1] = glo->underlineColor[1];
            color[2] = glo->underlineColor[2];
            alpha = 1.0f;
            line_thickness = LINE_THICKNESS;
            line_height = UNDERLINE_HEIGHT;
            break;
        case PDF_ANNOT_STRIKE_OUT:
            color[0] = glo->strikeoutColor[0];
            color[1] = glo->strikeoutColor[1];
            color[2] = glo->strikeoutColor[2];
            alpha = 1.0f;
            line_thickness = LINE_THICKNESS;
            line_height = STRIKE_HEIGHT;
            break;
        case PDF_ANNOT_TEXT:
            color[0] = glo->textAnnotIconColor[0];
            color[1] = glo->textAnnotIconColor[1];
            color[2] = glo->textAnnotIconColor[2];
            alpha = 1.0f;
            break;
        case PDF_ANNOT_FREE_TEXT:
            color[0] = glo->inkColor[0];
            color[1] = glo->inkColor[1];
            color[2] = glo->inkColor[2];
            alpha = 1.0f;
            line_thickness = 0.0f;
            line_height = 0.0f;
            break;
        default:
            return;
    }

    fz_var(pts);
    fz_try(ctx)
    {
        pdf_annot *annot;
        fz_matrix ctm;

        float zoom = glo->resolution / 72;
        zoom = 1.0 / zoom;
        ctm = fz_scale(zoom, zoom);
//        pt_cls = (*env)->FindClass(env, "android.graphics.PointF");
        pt_cls = (*env)->FindClass(env, "android/graphics/PointF");
        if (pt_cls == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "FindClass");
        x_fid = (*env)->GetFieldID(env, pt_cls, "x", "F");
        if (x_fid == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetFieldID(x)");
        y_fid = (*env)->GetFieldID(env, pt_cls, "y", "F");
        if (y_fid == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetFieldID(y)");

        n = (*env)->GetArrayLength(env, points);

        pts = fz_malloc_array(ctx, n, fz_point);

        for (i = 0; i < n; i++)
        {
                //Fix the order of the points in the quad points of highlight annotations
            jobject opt;
            if(type == PDF_ANNOT_HIGHLIGHT)
                {
                if(i%4 == 2)
                    opt = (*env)->GetObjectArrayElement(env, points, i+1);
                else if(i%4 == 3)
                    opt = (*env)->GetObjectArrayElement(env, points, i-1);
                else
                    opt = (*env)->GetObjectArrayElement(env, points, i);
            }
            else
                opt = (*env)->GetObjectArrayElement(env, points, i);
            
            pts[i].x = opt ? (*env)->GetFloatField(env, opt, x_fid) : 0.0f;
            pts[i].y = opt ? (*env)->GetFloatField(env, opt, y_fid) : 0.0f;
            pts[i] = fz_transform_point(pts[i], ctm);
        }

        annot = pdf_create_annot(ctx, (pdf_page *)pc->page, type); // creates a simple annot without AP

            //Now we generate the AP:
        if(type == PDF_ANNOT_TEXT)
        {
                //Ensure order of points
            if(pts[0].x > pts[1].x)
            {
                float z = pts[1].x;
                pts[1].x = pts[0].x;
                pts[0].x = z;
            }
            if(pts[0].y > pts[1].y)
            {
                float z = pts[1].y;
                pts[1].y = pts[0].y;
                pts[0].y = z;
            }
            
            
            fz_rect rect = {pts[0].x, pts[0].y, pts[1].x, pts[1].y};

           const char *utf8 = (*env)->GetStringUTFChars(env, jtext, NULL);
           pdf_set_annot_rect(ctx, (pdf_annot *)annot, rect);
           if (utf8)
               pdf_set_annot_contents(ctx, (pdf_annot *)annot, utf8);
           
               //Generate an appearance stream (AP) for the annotation (this should only be done once for each document and then the relevant xobject just referenced...)
           const float linewidth = (pts[1].x - pts[0].x)*0.06;
           fz_matrix page_ctm = fz_identity;
           fz_display_list *dlist = NULL;
           fz_device *dev = NULL;
           fz_path *path = NULL;
           fz_stroke_state *stroke = NULL;
           
           fz_var(path);
           fz_var(stroke);
           fz_var(dev);
           fz_var(dlist);
           fz_try(ctx)
           {
               dlist = fz_new_display_list(ctx, rect);
               dev = fz_new_list_device(ctx, dlist);

               stroke = fz_new_stroke_state(ctx);
               stroke->linewidth = linewidth;
               const float halflinewidth = linewidth*0.5;
               path = fz_new_path(ctx);

               fz_moveto(ctx, path, pts[0].x, pts[1].y-halflinewidth);
               fz_lineto(ctx, path, pts[1].x-halflinewidth, pts[1].y-halflinewidth);
               fz_lineto(ctx, path, pts[1].x-halflinewidth, 0.8*pts[0].y+0.2*pts[1].y);
               fz_lineto(ctx, path, 0.3*pts[1].x+0.7*pts[0].x, 0.8*pts[0].y+0.2*pts[1].y);
               fz_lineto(ctx, path, pts[0].x+halflinewidth, pts[0].y+halflinewidth);
               fz_lineto(ctx, path, pts[0].x+halflinewidth, pts[1].y);

               
               fz_moveto(ctx, path, 0.8*pts[0].x+0.2*pts[1].x, 0.8*pts[1].y+0.2*pts[0].y-halflinewidth);
               fz_lineto(ctx, path, 0.2*pts[0].x+0.8*pts[1].x, 0.8*pts[1].y+0.2*pts[0].y-halflinewidth);
               fz_moveto(ctx, path, 0.8*pts[0].x+0.2*pts[1].x, 0.6*pts[1].y+0.4*pts[0].y);
               fz_lineto(ctx, path, 0.2*pts[0].x+0.8*pts[1].x, 0.6*pts[1].y+0.4*pts[0].y);
               fz_moveto(ctx, path, 0.8*pts[0].x+0.2*pts[1].x, 0.4*pts[1].y+0.6*pts[0].y+halflinewidth);
               fz_lineto(ctx, path, 0.4*pts[0].x+0.6*pts[1].x, 0.4*pts[1].y+0.6*pts[0].y+halflinewidth);
               
               fz_stroke_path(ctx, dev, path, stroke, page_ctm, fz_device_rgb(ctx), color, alpha, fz_default_color_params);
               rect = fz_transform_rect(rect, page_ctm);
               /* Set appearance from display list */
               pdf_set_annot_appearance_from_display_list(ctx, (pdf_annot *)annot, "N", NULL, fz_identity, dlist);
           }
           fz_always(ctx)
           {
               fz_drop_device(ctx, dev);
               fz_drop_display_list(ctx, dlist);
               fz_drop_stroke_state(ctx, stroke);
               fz_drop_path(ctx, path);

               if (utf8)
                   (*env)->ReleaseStringUTFChars(env, jtext, utf8);
           }
           fz_catch(ctx)
           {
               fz_rethrow(ctx);
           }
        } //Add a markup annotation
        else if (type == PDF_ANNOT_FREE_TEXT)
        {
            const char *utf8 = NULL;
            float minx = 0.0f, maxx = 0.0f, miny = 0.0f, maxy = 0.0f;
            fz_rect rect;
            float font_size;

            if (n >= 1)
            {
                minx = maxx = pts[0].x;
                miny = maxy = pts[0].y;
            }

            for (i = 1; i < n; ++i)
            {
                minx = fminf(minx, pts[i].x);
                maxx = fmaxf(maxx, pts[i].x);
                miny = fminf(miny, pts[i].y);
                maxy = fmaxf(maxy, pts[i].y);
            }

            if (maxx - minx < 16.0f)
            {
                float pad = 8.0f;
                minx -= pad;
                maxx += pad;
            }
            if (maxy - miny < 12.0f)
            {
                float pad = 12.0f - (maxy - miny);
                maxy += pad;
            }

            if (minx > maxx)
            {
                float tmp = minx;
                minx = maxx;
                maxx = tmp;
            }
            if (miny > maxy)
            {
                float tmp = miny;
                miny = maxy;
                maxy = tmp;
            }

            rect.x0 = minx;
            rect.x1 = maxx;
            rect.y0 = miny;
            rect.y1 = maxy;
            pdf_set_annot_rect(ctx, (pdf_annot *)annot, rect);

            if (jtext != NULL)
                utf8 = (*env)->GetStringUTFChars(env, jtext, NULL);

            pdf_set_annot_contents(ctx, (pdf_annot *)annot, utf8 ? utf8 : "");

            font_size = (rect.y1 - rect.y0) * 0.8f;
            font_size = fmaxf(10.0f, fminf(72.0f, font_size));
            pdf_set_annot_default_appearance(ctx, (pdf_annot *)annot, "Helv", font_size, 3, color);
            pdf_set_annot_border_width(ctx, (pdf_annot *)annot, 0.0f);
            pdf_set_annot_opacity(ctx, (pdf_annot *)annot, 1.0f);
            pdf_update_annot(ctx, (pdf_annot *)annot);

            if (utf8 != NULL)
                (*env)->ReleaseStringUTFChars(env, jtext, utf8);
        }
        else
        {
            if (n >= 4)
            {
                int qn = n/4;
                fz_quad *qv = fz_malloc_array(ctx, qn, fz_quad);
                int qi;
                for (qi = 0; qi < qn; ++qi)
                {
                    qv[qi].ul = pts[qi*4 + 0];
                    qv[qi].ur = pts[qi*4 + 1];
                    qv[qi].ll = pts[qi*4 + 2];
                    qv[qi].lr = pts[qi*4 + 3];
                }
                pdf_set_annot_quad_points(ctx, (pdf_annot *)annot, qn, qv);
                fz_free(ctx, qv);
            }
            pdf_set_annot_color(ctx, (pdf_annot *)annot, 3, color);
            pdf_set_annot_opacity(ctx, (pdf_annot *)annot, alpha);
        }
        
        dump_annotation_display_lists(glo);
    }
    fz_always(ctx)
    {
        fz_free(ctx, pts);
    }
    fz_catch(ctx)
    {
        LOGE("addMarkupAnnotationInternal: %s failed", fz_caught_message(ctx));
        jclass cls = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        if (cls != NULL)
            (*env)->ThrowNew(env, cls, "Out of memory in MuPDFCore_searchPage");
        (*env)->DeleteLocalRef(env, cls);
    }
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_addInkAnnotationInternal)(JNIEnv * env, jobject thiz, jobjectArray arcs)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];
    jclass pt_cls;
    jfieldID x_fid, y_fid;
    int i, j, k, n;
    fz_point *pts = NULL;
    int *counts = NULL;
    int total = 0;
    float color[3];

    if (idoc == NULL)
        return;

    color[0] = glo->inkColor[0];
    color[1] = glo->inkColor[1];
    color[2] = glo->inkColor[2];

    fz_var(pts);
    fz_var(counts);
    fz_try(ctx)
    {
		pdf_annot *annot;
        fz_matrix ctm;

        float zoom = glo->resolution / 72;
        zoom = 1.0 / zoom;
        ctm = fz_scale(zoom, zoom);
        pt_cls = (*env)->FindClass(env, "android/graphics/PointF");
        if (pt_cls == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "FindClass");
        x_fid = (*env)->GetFieldID(env, pt_cls, "x", "F");
        if (x_fid == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetFieldID(x)");
        y_fid = (*env)->GetFieldID(env, pt_cls, "y", "F");
        if (y_fid == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetFieldID(y)");

        n = (*env)->GetArrayLength(env, arcs);

        counts = fz_malloc_array(ctx, n, int);

        for (i = 0; i < n; i++)
        {
            jobjectArray arc = (jobjectArray)(*env)->GetObjectArrayElement(env, arcs, i);
            int count = (*env)->GetArrayLength(env, arc);

            counts[i] = count;
            total += count;
        }

        pts = fz_malloc_array(ctx, total, fz_point);

        k = 0;
        for (i = 0; i < n; i++)
        {
            jobjectArray arc = (jobjectArray)(*env)->GetObjectArrayElement(env, arcs, i);
            int count = counts[i];

            for (j = 0; j < count; j++)
            {
                jobject pt = (*env)->GetObjectArrayElement(env, arc, j);

                pts[k].x = pt ? (*env)->GetFloatField(env, pt, x_fid) : 0.0f;
                pts[k].y = pt ? (*env)->GetFloatField(env, pt, y_fid) : 0.0f;
                (*env)->DeleteLocalRef(env, pt);
                pts[k] = fz_transform_point(pts[k], ctm);
                k++;
            }
            (*env)->DeleteLocalRef(env, arc);
        }

        float thickness;

        annot = pdf_create_annot(ctx, (pdf_page *)pc->page, PDF_ANNOT_INK);

        thickness = glo->inkThickness;
        if (thickness <= 0.0f)
            thickness = INK_THICKNESS;

        penandpdf_set_ink_annot_list(ctx, idoc, (pdf_annot *)annot, ctm, n, counts, pts, color, thickness);

        /* Make the ink fully opaque; adjust here if a UI-controlled alpha is introduced later. */
        pdf_set_annot_opacity(ctx, (pdf_annot *)annot, 1.0f);

        dump_annotation_display_lists(glo);
    }
    fz_always(ctx)
    {
        fz_free(ctx, pts);
        fz_free(ctx, counts);
    }
    fz_catch(ctx)
    {
        LOGE("addInkAnnotation: %s failed", fz_caught_message(ctx));
        jclass cls = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        if (cls != NULL)
            (*env)->ThrowNew(env, cls, "Out of memory in MuPDFCore_searchPage");
        (*env)->DeleteLocalRef(env, cls);
    }
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_deleteAnnotationInternal)(JNIEnv * env, jobject thiz, int annot_index)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];
	pdf_annot *annot;
	int i;

	if (idoc == NULL)
		return;

	fz_try(ctx)
	{
			annot = pdf_first_annot(ctx, (pdf_page *)pc->page);
			for (i = 0; i < annot_index && annot; i++)
				annot = pdf_next_annot(ctx, annot);

		if (annot)
		{
			pdf_delete_annot(ctx, (pdf_page *)pc->page, annot);
			pdf_update_page(ctx, (pdf_page *)pc->page);
			dump_annotation_display_lists(glo);
		}
	}
	fz_catch(ctx)
	{
		LOGE("deleteAnnotationInternal: %s", fz_caught_message(ctx));
	}
}

/* Close the document, at least enough to be able to save over it. This
 * may be called again later, so must be idempotent. */
static void close_doc(globals *glo)
{
	int i;

	fz_free(glo->ctx, glo->hit_bbox);
	glo->hit_bbox = NULL;

	for (i = 0; i < NUM_CACHE; i++)
		drop_page_cache(glo, &glo->pages[i]);

	alerts_fin(glo);

	fz_drop_document(glo->ctx, glo->doc);
	glo->doc = NULL;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_destroying)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);

	if (glo == NULL)
		return;
	LOGI("Destroying");
	fz_free(glo->ctx, glo->current_path);
	glo->current_path = NULL;
	// Drop any kept focused widget before closing
	if (glo->focus_widget)
	{
		fz_context *ctx = glo->ctx;
		pdf_drop_widget(ctx, glo->focus_widget);
		glo->focus_widget = NULL;
	}
	close_doc(glo);
	fz_drop_context(glo->ctx);
	glo->ctx = NULL;
	free(glo);
#ifdef MEMENTO
	LOGI("Destroying dump start");
	Memento_listBlocks();
	Memento_stats();
	LOGI("Destroying dump end");
#endif
#ifdef NDK_PROFILER
	// Apparently we should really be writing to whatever path we get
	// from calling getFilesDir() in the java part, which supposedly
	// gives /sdcard/data/data/com.artifex.MuPDF/gmon.out, but that's
	// unfriendly.
	setenv("CPUPROFILE", "/sdcard/gmon.out", 1);
	moncleanup();
#endif
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_getPageLinksInternal)(JNIEnv * env, jobject thiz, int pageNumber)
{
	jclass linkInfoClass;
	jclass linkInfoInternalClass;
	jclass linkInfoExternalClass;
	jclass linkInfoRemoteClass;
	jmethodID ctorInternal;
	jmethodID ctorExternal;
	jmethodID ctorRemote;
	jobjectArray arr;
	jobject linkInfo;
	fz_matrix ctm;
	float zoom;
	fz_link *list;
	fz_link *link;
	int count;
	page_cache *pc;
	globals *glo = get_globals(env, thiz);
    if (glo == NULL) return NULL;
	fz_context *ctx = glo->ctx;
	
    linkInfoClass = (*env)->FindClass(env, PACKAGENAME "/LinkInfo");
    if (linkInfoClass == NULL) fz_throw(glo->ctx, FZ_ERROR_GENERIC, "FindClass LinkInfo failed");
    linkInfoInternalClass = (*env)->FindClass(env, PACKAGENAME "/LinkInfoInternal");
    if (linkInfoInternalClass == NULL) fz_throw(glo->ctx, FZ_ERROR_GENERIC, "FindClass LinkInfoInternal failed");
    linkInfoExternalClass = (*env)->FindClass(env, PACKAGENAME "/LinkInfoExternal");
    if (linkInfoExternalClass == NULL) fz_throw(glo->ctx, FZ_ERROR_GENERIC, "FindClass LinkInfoExternal failed");
    linkInfoRemoteClass = (*env)->FindClass(env, PACKAGENAME "/LinkInfoRemote");
    if (linkInfoRemoteClass == NULL) fz_throw(glo->ctx, FZ_ERROR_GENERIC, "FindClass LinkInfoRemote failed");
    ctorInternal = (*env)->GetMethodID(env, linkInfoInternalClass, "<init>", "(FFFFIFFFFI)V");
    if (ctorInternal == NULL) fz_throw(glo->ctx, FZ_ERROR_GENERIC, "GetMethodID LinkInfoInternal() failed");
    ctorExternal = (*env)->GetMethodID(env, linkInfoExternalClass, "<init>", "(FFFFLjava/lang/String;)V");
    if (ctorExternal == NULL) fz_throw(glo->ctx, FZ_ERROR_GENERIC, "GetMethodID");
    ctorRemote = (*env)->GetMethodID(env, linkInfoRemoteClass, "<init>", "(FFFFLjava/lang/String;IZ)V");
    if (ctorRemote == NULL) fz_throw(glo->ctx, FZ_ERROR_GENERIC, "GetMethodID LinkInfoRemote() failed");

    JNI_FN(MuPDFCore_gotoPageInternal)(env, thiz, pageNumber);
    pc = &glo->pages[glo->current];
    if (pc->page == NULL || pc->number != pageNumber)
//        fz_throw(glo->ctx, FZ_ERROR_GENERIC, "MuPDFCore_gotoPageInternal failed");
        return NULL;

    zoom = glo->resolution / 72;
    ctm = fz_scale(zoom, zoom);

    list = fz_load_links(ctx, pc->page);
    if (list == NULL)
//        fz_throw(glo->ctx, FZ_ERROR_GENERIC, "fz_load_links() returned NULL");
        return NULL;
    
    count = 0;
    for (link = list; link; link = link->next)
    {
        if (link->uri)
            count++;
    }

    arr = (*env)->NewObjectArray(env, count, linkInfoClass, NULL);
    if (arr == NULL)
    {
        fz_drop_link(glo->ctx, list);
        fz_throw(glo->ctx, FZ_ERROR_GENERIC, "NewObjectArray() failed");
    }

    count = 0;
    for (link = list; link; link = link->next)
    {
        fz_rect rect = link->rect;
        rect = fz_transform_rect(rect, ctm);

        if (fz_is_external_link(ctx, link->uri))
        {
            jstring juri = (*env)->NewStringUTF(env, link->uri);
            linkInfo = (*env)->NewObject(env, linkInfoExternalClass, ctorExternal,
                                         (float)rect.x0, (float)rect.y0, (float)rect.x1, (float)rect.y1,
                                         juri);
        }
        else
        {
            float lx = 0, ly = 0;
            fz_location loc = fz_resolve_link(ctx, glo->doc, link->uri, &lx, &ly);
            int page_num = fz_page_number_from_location(ctx, glo->doc, loc);
            linkInfo = (*env)->NewObject(env, linkInfoInternalClass, ctorInternal,
                                         (float)rect.x0, (float)rect.y0, (float)rect.x1, (float)rect.y1,
                                         page_num, lx, ly, lx, ly, 0);
        }

        if (linkInfo == NULL)
        {
            fz_drop_link(glo->ctx, list);
            fz_throw(glo->ctx, FZ_ERROR_GENERIC, "linkInfo = NULL");
        }
        (*env)->SetObjectArrayElement(env, arr, count, linkInfo);
        (*env)->DeleteLocalRef(env, linkInfo);
        count++;
    }
    fz_drop_link(glo->ctx, list);

    return arr;
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_getWidgetAreasInternal)(JNIEnv * env, jobject thiz, int pageNumber)
{
    jclass rectFClass;
    jmethodID ctor;
    jobjectArray arr;
    jobject rectF;
    pdf_annot *widget;
	fz_matrix ctm;
	float zoom;
	int count;
	page_cache *pc;
	globals *glo = get_globals(env, thiz);
	if (glo == NULL)
		return NULL;
	fz_context *ctx = glo->ctx;

	rectFClass = (*env)->FindClass(env, "android/graphics/RectF");
	if (rectFClass == NULL) return NULL;
	ctor = (*env)->GetMethodID(env, rectFClass, "<init>", "(FFFF)V");
	if (ctor == NULL) return NULL;

	JNI_FN(MuPDFCore_gotoPageInternal)(env, thiz, pageNumber);
	pc = &glo->pages[glo->current];
	if (pc->number != pageNumber || pc->page == NULL)
		return NULL;

    zoom = glo->resolution / 72;
    ctm = fz_scale(zoom, zoom);

    count = 0;
    for (widget = pdf_first_widget(ctx, (pdf_page *)pc->page); widget; widget = pdf_next_widget(ctx, widget))
        count ++;

	arr = (*env)->NewObjectArray(env, count, rectFClass, NULL);
	if (arr == NULL) return NULL;

	count = 0;
    for (widget = pdf_first_widget(ctx, (pdf_page *)pc->page); widget; widget = pdf_next_widget(ctx, widget))
    {
        fz_rect rect;
        rect = pdf_bound_widget(ctx, widget);
        rect = fz_transform_rect(rect, ctm);

		rectF = (*env)->NewObject(env, rectFClass, ctor,
				(float)rect.x0, (float)rect.y0, (float)rect.x1, (float)rect.y1);
		if (rectF == NULL) return NULL;
		(*env)->SetObjectArrayElement(env, arr, count, rectF);
		(*env)->DeleteLocalRef(env, rectF);

		count ++;
	}

	return arr;
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_getAnnotationsInternal)(JNIEnv * env, jobject thiz, int pageNumber)
{
	jclass annotClass, pt_cls, ptarr_cls;
    jfieldID x_fid, y_fid;
    jmethodID Annotation;
    jmethodID PointF;
    jobjectArray arr;
    jobject jannot;
	pdf_annot *annot;
    fz_matrix ctm;
    float zoom;
    int count;
    page_cache *pc;
    globals *glo = get_globals(env, thiz);
    fz_context *ctx = glo->ctx;
    
    if (glo == NULL) return NULL;

    annotClass = (*env)->FindClass(env, PACKAGENAME "/Annotation");
    if (annotClass == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "FindClass");
    
    Annotation = (*env)->GetMethodID(env, annotClass, "<init>", "(FFFFI[[Landroid/graphics/PointF;Ljava/lang/String;J)V"); 
    if (Annotation == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetMethodID");

    pt_cls = (*env)->FindClass(env, "android/graphics/PointF");
    if (pt_cls == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "FindClass");
    x_fid = (*env)->GetFieldID(env, pt_cls, "x", "F");
    if (x_fid == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetFieldID(x)");
    y_fid = (*env)->GetFieldID(env, pt_cls, "y", "F");
    if (y_fid == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetFieldID(y)");
    PointF = (*env)->GetMethodID(env, pt_cls, "<init>", "(FF)V");
    
    JNI_FN(MuPDFCore_gotoPageInternal)(env, thiz, pageNumber);
    pc = &glo->pages[glo->current];
    if (pc->number != pageNumber || pc->page == NULL)
        return NULL;

    zoom = glo->resolution / 72;
    ctm = fz_scale(zoom, zoom);

    count = 0;
    for (pdf_annot *annot = pdf_first_annot(ctx, (pdf_page*)pc->page); annot; annot = pdf_next_annot(ctx, annot))
        count ++;

    arr = (*env)->NewObjectArray(env, count, annotClass, NULL);
    if (arr == NULL) return NULL;

    count = 0;
    for (pdf_annot *annot = pdf_first_annot(ctx, (pdf_page*)pc->page); annot; annot = pdf_next_annot(ctx, annot))
    {
            //Get the type
        enum pdf_annot_type type = pdf_annot_type(ctx, annot);

            //Get the text of the annotation
        jstring jtext = NULL;
        if(type == PDF_ANNOT_TEXT || type == PDF_ANNOT_FREE_TEXT)
        {
            const char *text = pdf_annot_contents(ctx, (pdf_annot *)annot);
            if (text != NULL)
                jtext = (*env)->NewStringUTF(env, text);
        }

        
            //Get the inklist
        jobjectArray arcs = NULL;
    if(type == PDF_ANNOT_INK)
        {
            int nArcs = pdf_annot_ink_list_count(ctx, (pdf_annot *)annot);
            int i;
            float pageHeight = (&glo->pages[glo->current])->height;
            for(i = 0; i < nArcs; i++)
            {
                int nArc = pdf_annot_ink_list_stroke_count(ctx, (pdf_annot *)annot, i);
                jobjectArray arci = (*env)->NewObjectArray(env, nArc, pt_cls, NULL);
                
                if(i==0) { //Get the class of the array of pointF and create the array of arrays 
                    ptarr_cls = (*env)->GetObjectClass(env, arci);
                    if (ptarr_cls == NULL) {
                        fz_throw(glo->ctx, FZ_ERROR_GENERIC, "GetObjectClass()");
                    }
                    else {
                        arcs = (*env)->NewObjectArray(env, nArcs, ptarr_cls, NULL);
                        if (arcs == NULL) fz_throw(glo->ctx, FZ_ERROR_GENERIC, "arcs == NULL");
                    }
                }
                
                if (arci == NULL) return NULL;
                int j;
                for(j = 0; j < nArc; j++)
                {
                    fz_point point = pdf_annot_ink_list_stroke_vertex(ctx, (pdf_annot *)annot, i, j);
                    point = fz_transform_point(point, ctm);
                    point.y = pageHeight - point.y;//Flip y here because pdf coordinate system is upside down
                    jobject pfobj = (*env)->NewObject(env, pt_cls, PointF, point.x, point.y);
                    (*env)->SetObjectArrayElement(env, arci, j, pfobj);
                    (*env)->DeleteLocalRef(env, pfobj);
                }
                (*env)->SetObjectArrayElement(env, arcs, i, arci);
                (*env)->DeleteLocalRef(env, arci);
            }
        }

            //Get the rect
        fz_rect rect;
        rect = pdf_bound_annot(ctx, (pdf_annot *)annot);
        rect = fz_transform_rect(rect, ctm);

            //Get a stable object identifier for undo/redo matching
        jlong objectNumber = -1;
        pdf_obj *annot_obj = pdf_annot_obj(ctx, (pdf_annot *)annot);
        if (annot_obj)
        {
            int num = pdf_to_num(ctx, annot_obj);
            int gen = pdf_to_gen(ctx, annot_obj);
            objectNumber = (((jlong)num) << 32) | (jlong)(gen & 0xffffffffu);
        }

            //Create the annotation
        if(Annotation != NULL)
        {
            jannot = (*env)->NewObject(env, annotClass, Annotation, (float)rect.x0, (float)rect.y0, (float)rect.x1, (float)rect.y1, type, arcs, jtext, objectNumber); 
        }
            
        if (jannot == NULL) return NULL;
        (*env)->SetObjectArrayElement(env, arr, count, jannot);

            //Clean up
        (*env)->DeleteLocalRef(env, jannot);
        (*env)->DeleteLocalRef(env, jtext);
        
        count ++;
    }

    return arr;
}

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_passClickEventInternal)(JNIEnv * env, jobject thiz, int pageNumber, float x, float y)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return 0;
    fz_context *ctx = glo->ctx;
    page_cache *pc;
    float zoom;
    fz_matrix ctm;
    int changed = 0;

    JNI_FN(MuPDFCore_gotoPageInternal)(env, thiz, pageNumber);
    pc = &glo->pages[glo->current];
    if (pc->page == NULL || pc->number != pageNumber)
        return 0;

    zoom = glo->resolution / 72.0f;
    ctm = fz_scale(zoom, zoom);

    for (pdf_annot *w = pdf_first_widget(ctx, (pdf_page*)pc->page); w; w = pdf_next_widget(ctx, w))
    {
        fz_rect r = fz_transform_rect(pdf_bound_widget(ctx, w), ctm);
        if (x >= r.x0 && x <= r.x1 && y >= r.y0 && y <= r.y1)
        {
            enum pdf_widget_type t = pdf_widget_type(ctx, w);
            // Update focus reference
            if (glo->focus_widget)
            {
                if (glo->focus_widget != w)
                {
                    pdf_drop_widget(ctx, glo->focus_widget);
                    glo->focus_widget = pdf_keep_widget(ctx, w);
                }
            }
            else
            {
                glo->focus_widget = pdf_keep_widget(ctx, w);
            }

            // Toggle check/radio on click; others handled by UI dialogs
            if (t == PDF_WIDGET_TYPE_CHECKBOX || t == PDF_WIDGET_TYPE_RADIOBUTTON)
            {
                fz_try(ctx)
                {
                    changed = pdf_toggle_widget(ctx, w);
                    // Regenerate appearances if required
                    if (changed)
                        pdf_update_page(ctx, (pdf_page*)pc->page);
                }
                fz_catch(ctx)
                {
                    LOGE("passClickEvent toggle failed: %s", fz_caught_message(ctx));
                    changed = 0;
                }
            }
            return changed;
        }
    }
    return 0;
}

JNIEXPORT jstring JNICALL
JNI_FN(MuPDFCore_getFocusedWidgetTextInternal)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->focus_widget == NULL)
        return (*env)->NewStringUTF(env, "");
    fz_context *ctx = glo->ctx;
    const char *val = NULL;
    fz_try(ctx)
    {
        val = pdf_annot_field_value(ctx, glo->focus_widget);
    }
    fz_catch(ctx)
    {
        LOGE("getFocusedWidgetText: %s", fz_caught_message(ctx));
        val = "";
    }
    return (*env)->NewStringUTF(env, val ? val : "");
}

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_setFocusedWidgetTextInternal)(JNIEnv * env, jobject thiz, jstring jtext)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->focus_widget == NULL)
        return 0;
    fz_context *ctx = glo->ctx;
    const char *text = (*env)->GetStringUTFChars(env, jtext, NULL);
    int rc = 0;
    fz_try(ctx)
    {
        rc = pdf_set_text_field_value(ctx, glo->focus_widget, text ? text : "");
        if (rc)
        {
            // Update appearances
            page_cache *pc = &glo->pages[glo->current];
            pdf_update_page(ctx, (pdf_page*)pc->page);
        }
    }
    fz_catch(ctx)
    {
        LOGE("setFocusedWidgetText failed: %s", fz_caught_message(ctx));
        rc = 0;
    }
    if (text) (*env)->ReleaseStringUTFChars(env, jtext, text);
    return rc;
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_getFocusedWidgetChoiceOptions)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->focus_widget == NULL)
        return NULL;
    fz_context *ctx = glo->ctx;
    int n = 0;
    jobjectArray arr = NULL;
    fz_try(ctx)
    {
        n = pdf_choice_widget_options(ctx, glo->focus_widget, 0, NULL);
        if (n <= 0)
            fz_throw(ctx, FZ_ERROR_GENERIC, "no options");
        const char **opts = fz_malloc_array(ctx, n, const char*);
        int i;
        n = pdf_choice_widget_options(ctx, glo->focus_widget, 0, opts);
        jclass cls = (*env)->FindClass(env, "java/lang/String");
        arr = (*env)->NewObjectArray(env, n, cls, NULL);
        for (i = 0; i < n; i++)
        {
            jstring s = (*env)->NewStringUTF(env, opts[i] ? opts[i] : "");
            (*env)->SetObjectArrayElement(env, arr, i, s);
            (*env)->DeleteLocalRef(env, s);
        }
        fz_free(ctx, (void*)opts);
    }
    fz_catch(ctx)
    {
        LOGE("getFocusedWidgetChoiceOptions failed: %s", fz_caught_message(ctx));
        return NULL;
    }
    return arr;
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_getFocusedWidgetChoiceSelected)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->focus_widget == NULL)
        return NULL;
    fz_context *ctx = glo->ctx;
    int n = 0;
    jobjectArray arr = NULL;
    fz_try(ctx)
    {
        n = pdf_choice_widget_value(ctx, glo->focus_widget, NULL);
        if (n < 0)
            n = 0;
        const char **vals = NULL;
        if (n > 0)
        {
            vals = fz_malloc_array(ctx, n, const char*);
            n = pdf_choice_widget_value(ctx, glo->focus_widget, vals);
        }
        jclass cls = (*env)->FindClass(env, "java/lang/String");
        arr = (*env)->NewObjectArray(env, n, cls, NULL);
        for (int i = 0; i < n; i++)
        {
            jstring s = (*env)->NewStringUTF(env, vals[i] ? vals[i] : "");
            (*env)->SetObjectArrayElement(env, arr, i, s);
            (*env)->DeleteLocalRef(env, s);
        }
        if (vals) fz_free(ctx, (void*)vals);
    }
    fz_catch(ctx)
    {
        LOGE("getFocusedWidgetChoiceSelected failed: %s", fz_caught_message(ctx));
        return NULL;
    }
    return arr;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_setFocusedWidgetChoiceSelectedInternal)(JNIEnv * env, jobject thiz, jobjectArray arr)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->focus_widget == NULL)
        return;
    fz_context *ctx = glo->ctx;
    int n = (*env)->GetArrayLength(env, arr);
    const char **vals = NULL;
    if (n > 0)
        vals = fz_malloc_array(ctx, n, const char*);
    for (int i = 0; i < n; i++)
    {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, arr, i);
        const char *cs = (*env)->GetStringUTFChars(env, s, NULL);
        vals[i] = cs;
        (*env)->DeleteLocalRef(env, s);
    }
    fz_try(ctx)
    {
        pdf_choice_widget_set_value(ctx, glo->focus_widget, n, vals);
        page_cache *pc = &glo->pages[glo->current];
        pdf_update_page(ctx, (pdf_page*)pc->page);
    }
    fz_catch(ctx)
    {
        LOGE("setFocusedWidgetChoiceSelected failed: %s", fz_caught_message(ctx));
    }
    for (int i = 0; i < n; i++)
    {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, arr, i);
        const char *cs = vals[i];
        if (cs)
            (*env)->ReleaseStringUTFChars(env, s, cs);
        (*env)->DeleteLocalRef(env, s);
    }
    if (vals) fz_free(ctx, (void*)vals);
}

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_getFocusedWidgetTypeInternal)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
    if (glo->focus_widget == NULL)
        return NONE;

	switch (pdf_widget_type(ctx, glo->focus_widget))
	{
	case PDF_WIDGET_TYPE_TEXT: return TEXT;
	case PDF_WIDGET_TYPE_LISTBOX: return LISTBOX;
	case PDF_WIDGET_TYPE_COMBOBOX: return COMBOBOX;
	case PDF_WIDGET_TYPE_SIGNATURE: return SIGNATURE;
	default: break;
	}

	return NONE;
}

/* This enum should be kept in line with SignatureState in MuPDFPageView.java */
enum
{
	Signature_NoSupport,
	Signature_Unsigned,
	Signature_Signed
};

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_getFocusedWidgetSignatureState)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
    if (glo->focus_widget == NULL)
        return Signature_NoSupport;
    if (pdf_widget_type(ctx, glo->focus_widget) != PDF_WIDGET_TYPE_SIGNATURE)
        return Signature_NoSupport;
    return pdf_widget_is_signed(ctx, glo->focus_widget) ? Signature_Signed : Signature_Unsigned;
}

JNIEXPORT jstring JNICALL
JNI_FN(MuPDFCore_checkFocusedSignatureInternal)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL)
		return (*env)->NewStringUTF(env, "No document loaded");

	fz_context *ctx = glo->ctx;
	char message[512] = "Signature check failed";
	pdf_pkcs7_verifier *verifier = NULL;
	pdf_pkcs7_distinguished_name *dn = NULL;
	char *dn_string = NULL;

	fz_var(verifier);
	fz_var(dn);
	fz_var(dn_string);

	fz_try(ctx)
	{
		pdf_annot *widget = glo->focus_widget;

		if (widget == NULL || pdf_widget_type(ctx, widget) != PDF_WIDGET_TYPE_SIGNATURE)
		{
			fz_strlcpy(message, "No signature widget selected", sizeof(message));
			goto signature_done;
		}

		if (!pdf_widget_is_signed(ctx, widget))
		{
			fz_strlcpy(message, "Signature field is not signed", sizeof(message));
			goto signature_done;
		}

		verifier = pkcs7_openssl_new_verifier(ctx);
		if (verifier == NULL)
			fz_throw(ctx, FZ_ERROR_GENERIC, "Unable to create PKCS7 verifier");

		pdf_signature_error digest_err = pdf_check_widget_digest(ctx, verifier, widget);
		if (digest_err != PDF_SIGNATURE_ERROR_OKAY)
		{
			const char *desc = pdf_signature_error_description(digest_err);
			fz_strlcpy(message, desc ? desc : "Signature digest verification failed", sizeof(message));
			goto signature_done;
		}

		pdf_signature_error cert_err = pdf_check_widget_certificate(ctx, verifier, widget);
		if (cert_err != PDF_SIGNATURE_ERROR_OKAY)
		{
			const char *desc = pdf_signature_error_description(cert_err);
			fz_strlcpy(message, desc ? desc : "Signature certificate verification failed", sizeof(message));
			goto signature_done;
		}

		dn = pdf_signature_get_widget_signatory(ctx, verifier, widget);
		if (dn)
			dn_string = pdf_signature_format_distinguished_name(ctx, dn);

		if (dn_string && dn_string[0] != '\0')
			snprintf(message, sizeof(message), "Signature is valid\nSigned by: %s", dn_string);
		else
			fz_strlcpy(message, "Signature is valid", sizeof(message));
signature_done:
		;
	}
	fz_always(ctx)
	{
		if (dn)
			pdf_signature_drop_distinguished_name(ctx, dn);
		if (dn_string)
			fz_free(ctx, dn_string);
		if (verifier)
			pdf_drop_verifier(ctx, verifier);
	}
	fz_catch(ctx)
	{
		const char *err = fz_caught_message(ctx);
		fz_strlcpy(message, err ? err : "Signature check failed", sizeof(message));
	}

	return (*env)->NewStringUTF(env, message);
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_signFocusedSignatureInternal)(JNIEnv * env, jobject thiz, jstring jkeyfile, jstring jpassword)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL)
		return JNI_FALSE;

	fz_context *ctx = glo->ctx;
	pdf_annot *widget = glo->focus_widget;

	if (widget == NULL || pdf_widget_type(ctx, widget) != PDF_WIDGET_TYPE_SIGNATURE)
		return JNI_FALSE;

	const char *keyfile = NULL;
	const char *password = NULL;
	pdf_pkcs7_signer *signer = NULL;
	jboolean result = JNI_FALSE;

	fz_var(signer);

	keyfile = (*env)->GetStringUTFChars(env, jkeyfile, NULL);
	password = (*env)->GetStringUTFChars(env, jpassword, NULL);

	if (keyfile == NULL || password == NULL)
		goto cleanup;

	fz_try(ctx)
	{
		signer = pkcs7_openssl_read_pfx(ctx, keyfile, password);
		if (signer == NULL)
			fz_throw(ctx, FZ_ERROR_GENERIC, "Unable to read PKCS#12 key file");

		pdf_sign_signature(ctx, widget, signer, PDF_SIGNATURE_DEFAULT_APPEARANCE, NULL, NULL, NULL);

		/* Refresh page rendering after signing */
		page_cache *pc = &glo->pages[glo->current];
		if (pc->page)
			pdf_update_page(ctx, (pdf_page*)pc->page);
		dump_annotation_display_lists(glo);

		result = JNI_TRUE;
	}
	fz_always(ctx)
	{
		if (signer)
			pdf_drop_signer(ctx, signer);
	}
	fz_catch(ctx)
	{
		LOGE("signFocusedSignature: %s", fz_caught_message(ctx));
		result = JNI_FALSE;
	}

cleanup:
	if (password)
		(*env)->ReleaseStringUTFChars(env, jpassword, password);
	if (keyfile)
		(*env)->ReleaseStringUTFChars(env, jkeyfile, keyfile);

	return result;
}

JNIEXPORT jobject JNICALL
JNI_FN(MuPDFCore_waitForAlertInternal)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	jclass alertClass;
	jmethodID ctor;
	jstring title;
	jstring message;
	int alert_present;
	pdf_alert_event alert;

	LOGT("Enter waitForAlert");
	pthread_mutex_lock(&glo->fin_lock);
	pthread_mutex_lock(&glo->alert_lock);

	while (glo->alerts_active && !glo->alert_request)
		pthread_cond_wait(&glo->alert_request_cond, &glo->alert_lock);
	glo->alert_request = 0;

	alert_present = (glo->alerts_active && glo->current_alert);

	if (alert_present)
		alert = *glo->current_alert;

	pthread_mutex_unlock(&glo->alert_lock);
	pthread_mutex_unlock(&glo->fin_lock);
	LOGT("Exit waitForAlert %d", alert_present);

	if (!alert_present)
		return NULL;

	alertClass = (*env)->FindClass(env, PACKAGENAME "/MuPDFAlertInternal");
	if (alertClass == NULL)
		return NULL;

	ctor = (*env)->GetMethodID(env, alertClass, "<init>", "(Ljava/lang/String;IILjava/lang/String;I)V");
	if (ctor == NULL)
		return NULL;

	title = (*env)->NewStringUTF(env, alert.title);
	if (title == NULL)
		return NULL;

	message = (*env)->NewStringUTF(env, alert.message);
	if (message == NULL)
		return NULL;

	return (*env)->NewObject(env, alertClass, ctor, message, alert.icon_type, alert.button_group_type, title, alert.button_pressed);
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_replyToAlertInternal)(JNIEnv * env, jobject thiz, jobject alert)
{
	globals *glo = get_globals(env, thiz);
	jclass alertClass;
	jfieldID field;
	int button_pressed;

	alertClass = (*env)->FindClass(env, PACKAGENAME "/MuPDFAlertInternal");
	if (alertClass == NULL)
		return;

	field = (*env)->GetFieldID(env, alertClass, "buttonPressed", "I");
	if (field == NULL)
		return;

	button_pressed = (*env)->GetIntField(env, alert, field);

	LOGT("Enter replyToAlert");
	pthread_mutex_lock(&glo->alert_lock);

	if (glo->alerts_active && glo->current_alert)
	{
		// Fill in button_pressed and signal reply received.
		glo->current_alert->button_pressed = button_pressed;
		glo->alert_reply = 1;
		pthread_cond_signal(&glo->alert_reply_cond);
	}

	pthread_mutex_unlock(&glo->alert_lock);
	LOGT("Exit replyToAlert");
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_startAlertsInternal)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);

	if (!glo->alerts_initialised)
		return;

	LOGT("Enter startAlerts");
	pthread_mutex_lock(&glo->alert_lock);

	glo->alert_reply = 0;
	glo->alert_request = 0;
	glo->alerts_active = 1;
	glo->current_alert = NULL;

	pthread_mutex_unlock(&glo->alert_lock);
	LOGT("Exit startAlerts");
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_stopAlertsInternal)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);

	if (!glo->alerts_initialised)
		return;

	LOGT("Enter stopAlerts");
	pthread_mutex_lock(&glo->alert_lock);

	glo->alert_reply = 0;
	glo->alert_request = 0;
	glo->alerts_active = 0;
	glo->current_alert = NULL;
	pthread_cond_signal(&glo->alert_reply_cond);
	pthread_cond_signal(&glo->alert_request_cond);

	pthread_mutex_unlock(&glo->alert_lock);
	LOGT("Exit stopAleerts");
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_hasChangesInternal)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	pdf_document *idoc = pdf_specifics(ctx, glo->doc);

	return (idoc && pdf_has_unsaved_changes(ctx, idoc)) ? JNI_TRUE : JNI_FALSE;
}

static const char * android_tmp_folder(JNIEnv * env) {
    jclass coreClass = (*env)->FindClass(env, PACKAGENAME "/MuPDFCore");
   jmethodID getCacheDir = (*env)->GetStaticMethodID(env, coreClass, "getCacheDir", "()Ljava/lang/String;");
   jstring cache_dir = (jstring)(*env)->CallStaticObjectMethod(env, coreClass, getCacheDir);
//	jstring cache_dir = (*env)->GetStaticFieldID(env, coreClass, "cachDir", "Ljava/lang/String");
    const char *path_chars = (*env)->GetStringUTFChars(env, cache_dir, NULL);
    return path_chars;
}

static char *tmp_path(JNIEnv * env, char *path)
{
	int rnd_length = 6;
	char *rnd = malloc(sizeof(char) * (rnd_length +1));
	if (!rnd)
		return NULL;
	int i;
	for (i=0; i<rnd_length; i++)
		rnd[i] = "0123456789abcdef"[random() % 16];
	rnd[rnd_length] = '\0';
	
	int f;
	char *buf = malloc(strlen(path) + 1 + strlen(rnd) + 4 + 1);
	if (!buf)
		return NULL;
	
	strcpy(buf, path);
	strcat(buf, "_");
	strcat(buf, rnd);
	strcat(buf, ".pdf");

	return buf;
	
	/* f = mkstemp(buf); //mkstemp() is broke on android and rename() (which we use below) can fail if we try to rename from the official cach directory, so we have no choice but to generate a tmp path by hand in the given directoy...*/
	
	/* if (f >= 0) */
	/* { */
	/* 	close(f); */
	/* 	return buf; */
	/* } */
	/* else */
	/* { */
	/* 	free(buf); */
	/* 	return NULL; */
	/* } */
}

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_saveAsInternal)(JNIEnv *env, jobject thiz, jstring jpath)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->doc == NULL)
        return 0;
    fz_context *ctx = glo->ctx;
    const char *new_path = NULL;
    if (jpath != NULL)
        new_path = (*env)->GetStringUTFChars(env, jpath, NULL);

    const char *target_path = new_path ? new_path : glo->current_path;
    if (target_path == NULL)
    {
        if (new_path) (*env)->ReleaseStringUTFChars(env, jpath, new_path);
        return 0;
    }

    int ok = 0;
    char *tmp = tmp_path(env, (char *)target_path);
    if (!tmp)
    {
        if (new_path) (*env)->ReleaseStringUTFChars(env, jpath, new_path);
        return 0;
    }

    fz_document_writer *wri = NULL;
    char opts[256];
    opts[0] = 0;
    // Incremental only if saving back to the same path and there are changes
    if (glo->current_path && new_path && strcmp(glo->current_path, new_path) == 0)
        fz_strlcpy(opts, "incremental=yes", sizeof(opts));

    fz_try(ctx)
    {
        wri = fz_new_pdf_writer(ctx, tmp, opts[0] ? opts : NULL);
        fz_write_document(ctx, wri, glo->doc);
        fz_close_document_writer(ctx, wri);
        ok = 1;
    }
    fz_always(ctx)
    {
        if (wri) fz_drop_document_writer(ctx, wri);
    }
    fz_catch(ctx)
    {
        LOGE("saveAs failed: %s", fz_caught_message(ctx));
        ok = 0;
    }

    if (ok)
    {
        // Move temp file into place
        if (rename(tmp, target_path) == 0)
        {
            if (target_path != glo->current_path)
            {
                fz_free(ctx, glo->current_path);
                glo->current_path = fz_strdup(ctx, target_path);
            }
        }
        else
        {
            LOGE("rename(%s -> %s) failed", tmp, target_path);
            ok = 0;
        }
    }
    free(tmp);
    if (new_path) (*env)->ReleaseStringUTFChars(env, jpath, new_path);
    return ok;
}


JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_setInkColor)(JNIEnv * env, jobject thiz, float r, float g, float b)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return NULL;
    glo->inkColor[0] = r;
    glo->inkColor[1] = g;
    glo->inkColor[2] = b;
    return NULL;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_setInkThickness)(JNIEnv *env, jobject thiz, float inkThickness)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL)
        return;
    glo->inkThickness = inkThickness;
}


JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_setHighlightColor)(JNIEnv * env, jobject thiz, float r, float g, float b)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return NULL;
    glo->highlightColor[0] = r;
    glo->highlightColor[1] = g;
    glo->highlightColor[2] = b;
    return NULL;
}


JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_setUnderlineColor)(JNIEnv * env, jobject thiz, float r, float g, float b)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return NULL;
    glo->underlineColor[0] = r;
    glo->underlineColor[1] = g;
    glo->underlineColor[2] = b;
    return NULL;
}


JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_setStrikeoutColor)(JNIEnv * env, jobject thiz, float r, float g, float b)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return NULL;
    glo->strikeoutColor[0] = r;
    glo->strikeoutColor[1] = g;
    glo->strikeoutColor[2] = b;
    return NULL;
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_setTextAnnotIconColor)(JNIEnv * env, jobject thiz, float r, float g, float b)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return NULL;
    glo->textAnnotIconColor[0] = r;
    glo->textAnnotIconColor[1] = g;
    glo->textAnnotIconColor[2] = b;
    return NULL;
}

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_insertBlankPageBeforeInternal)(JNIEnv * env, jobject thiz, int position)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->doc == NULL)
        return 0;
    fz_context *ctx = glo->ctx;
    pdf_document *idoc = pdf_specifics(ctx, glo->doc);
    if (idoc == NULL)
        return 0;

    int count = fz_count_pages(ctx, glo->doc);
    if (position < 0) position = 0;
    if (position > count) position = count;

    // Derive mediabox from target page if possible, else default A4
    fz_rect mediabox;
    if (count > 0 && position < count)
    {
        fz_page *p = NULL;
        fz_try(ctx) { p = fz_load_page(ctx, glo->doc, position); }
        fz_catch(ctx) { p = NULL; }
        if (p)
        {
            mediabox = fz_bound_page(ctx, p);
            fz_drop_page(ctx, p);
        }
        else
            mediabox = fz_make_rect(0, 0, 595, 842);
    }
    else
        mediabox = fz_make_rect(0, 0, 595, 842);

    pdf_obj *resources = NULL;
    fz_buffer *contents = NULL;
    fz_device *dev = NULL;
    int ok = 0;
    fz_try(ctx)
    {
        dev = pdf_page_write(ctx, idoc, mediabox, &resources, &contents);
        // No content for a blank page
        fz_close_device(ctx, dev);
        pdf_obj *page_obj = pdf_add_page(ctx, idoc, mediabox, 0, resources, contents);
        pdf_insert_page(ctx, idoc, position, page_obj);
        pdf_update_page(ctx, (pdf_page*)fz_load_page(ctx, glo->doc, position));
        ok = 1;
    }
    fz_always(ctx)
    {
        fz_drop_device(ctx, dev);
        if (resources) pdf_drop_obj(ctx, resources);
        if (contents) fz_drop_buffer(ctx, contents);
    }
    fz_catch(ctx)
    {
        LOGE("insertBlankPageBefore failed: %s", fz_caught_message(ctx));
        ok = 0;
    }
    return ok;
}

JNIEXPORT jlong JNICALL
JNI_FN(MuPDFCore_createCookie)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals_any_thread(env, thiz);
	if (glo == NULL)
		return 0;
	fz_context *ctx = glo->ctx;

	return (jlong) (intptr_t) fz_calloc_no_throw(ctx,1, sizeof(fz_cookie));
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_destroyCookie)(JNIEnv * env, jobject thiz, jlong cookiePtr)
{
	fz_cookie *cookie = (fz_cookie *) (intptr_t) cookiePtr;
	globals *glo = get_globals_any_thread(env, thiz);
	if (glo == NULL)
		return;
	fz_context *ctx = glo->ctx;
	if (ctx == NULL)
		return;
	
	fz_free(ctx, cookie);
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_abortCookie)(JNIEnv * env, jobject thiz, jlong cookiePtr)
{
	fz_cookie *cookie = (fz_cookie *) (intptr_t) cookiePtr;
	if (cookie != NULL)
		cookie->abort = 1;
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_cookieAborted)(JNIEnv * env, jobject thiz, jlong cookiePtr)
{
	fz_cookie *cookie = (fz_cookie *) (intptr_t) cookiePtr;
	if (cookie == NULL || cookie->abort == 1)
		return true;
    else
        return false;
}


static char *tmp_gproof_path(char *path)
{
	FILE *f;
	int i;
	char *buf = malloc(strlen(path) + 20 + 1);
	if (!buf)
		return NULL;

	for (i = 0; i < 10000; i++)
	{
		sprintf(buf, "%s.%d.gproof", path, i);

		LOGI("Trying for %s\n", buf);
		f = fopen(buf, "r");
		if (f != NULL)
		{
			fclose(f);
			continue;
		}

		f = fopen(buf, "w");
		if (f != NULL)
		{
			fclose(f);
			break;
		}
	}
	if (i == 10000)
	{
		LOGE("Failed to find temp gproof name");
		free(buf);
		return NULL;
	}

	LOGI("Rewritten to %s\n", buf);
	return buf;
}

JNIEXPORT jstring JNICALL
JNI_FN(MuPDFCore_startProofInternal)(JNIEnv * env, jobject thiz, int inResolution)
{
#ifdef SUPPORT_GPROOF
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	char *tmp;
	jstring ret;

	if (!glo->doc || !glo->current_path)
		return NULL;

	tmp = tmp_gproof_path(glo->current_path);
	if (!tmp)
		return NULL;

	int theResolution = PROOF_RESOLUTION;
	if (inResolution != 0)
		theResolution = inResolution;

	fz_try(ctx)
	{
		fz_write_gproof_file(ctx, glo->current_path, glo->doc, tmp, theResolution, "", "");

		LOGI("Creating %s\n", tmp);
		ret = (*env)->NewStringUTF(env, tmp);
	}
	fz_always(ctx)
	{
		free(tmp);
	}
	fz_catch(ctx)
	{
		ret = NULL;
	}
	return ret;
#else
	return NULL;
#endif
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_endProofInternal)(JNIEnv * env, jobject thiz, jstring jfilename)
{
#ifdef SUPPORT_GPROOF
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	const char *tmp;

	if (!glo->doc || !glo->current_path || jfilename == NULL)
		return;

	tmp = (*env)->GetStringUTFChars(env, jfilename, NULL);
	if (tmp)
	{
		LOGI("Deleting %s\n", tmp);

		unlink(tmp);
		(*env)->ReleaseStringUTFChars(env, jfilename, tmp);
	}
#endif
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_gprfSupportedInternal)(JNIEnv * env)
{
#ifdef SUPPORT_GPROOF
	return JNI_TRUE;
#else
	return JNI_FALSE;
#endif
}

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_getNumSepsOnPageInternal)(JNIEnv *env, jobject thiz, int page)
{
		globals *glo = get_globals(env, thiz);
		fz_context *ctx = glo->ctx;
		int i;

	for (i = 0; i < NUM_CACHE; i++)
	{
		if (glo->pages[i].page != NULL && glo->pages[i].number == page)
			break;
	}
	if (i == NUM_CACHE)
		return 0;

		LOGI("Counting seps on page %d", page);

		fz_separations *seps = fz_page_separations(ctx, glo->pages[i].page);
		int n = 0;
		if (seps)
		{
			n = fz_count_separations(ctx, seps);
			fz_drop_separations(ctx, seps);
		}
		return n;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_controlSepOnPageInternal)(JNIEnv *env, jobject thiz, int page, int sep, jboolean disable)
{
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	int i;

	for (i = 0; i < NUM_CACHE; i++)
	{
		if (glo->pages[i].page != NULL && glo->pages[i].number == page)
			break;
	}
	if (i == NUM_CACHE)
		return;

		fz_separations *seps = fz_page_separations(ctx, glo->pages[i].page);
		if (seps)
		{
			fz_set_separation_behavior(ctx, seps, sep, disable ? FZ_SEPARATION_DISABLED : FZ_SEPARATION_SPOT);
			fz_drop_separations(ctx, seps);
		}
}

JNIEXPORT jobject JNICALL
JNI_FN(MuPDFCore_getSepInternal)(JNIEnv *env, jobject thiz, int page, int sep)
{
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	const char *name;
	char rgba[4];
	unsigned int bgra;
	unsigned int cmyk;
	jobject jname;
	jclass sepClass;
	jmethodID ctor;
	int i;

	for (i = 0; i < NUM_CACHE; i++)
	{
		if (glo->pages[i].page != NULL && glo->pages[i].number == page)
			break;
	}
	if (i == NUM_CACHE)
		return NULL;

		fz_separations *seps = fz_page_separations(ctx, glo->pages[i].page);
		if (!seps)
			return NULL;
		name = fz_separation_name(ctx, seps, sep);
		// Compute RGB equivalent and pack BGRA
		float rgb[3] = {0};
		fz_separation_equivalent(ctx, seps, sep, fz_device_rgb(ctx), rgb, NULL, fz_default_color_params);
		unsigned r = (unsigned)(rgb[0] < 0 ? 0 : rgb[0] > 1 ? 255 : rgb[0]*255.0f + 0.5f);
		unsigned g = (unsigned)(rgb[1] < 0 ? 0 : rgb[1] > 1 ? 255 : rgb[1]*255.0f + 0.5f);
		unsigned b = (unsigned)(rgb[2] < 0 ? 0 : rgb[2] > 1 ? 255 : rgb[2]*255.0f + 0.5f);
		bgra = (r << 16) | (g << 8) | (b) | (0xFFu<<24);
		// Compute CMYK equivalent pack as 0xCCMMYYKK
		float cmykf[4] = {0};
		fz_separation_equivalent(ctx, seps, sep, fz_device_cmyk(ctx), cmykf, NULL, fz_default_color_params);
		unsigned C = (unsigned)(cmykf[0] < 0 ? 0 : cmykf[0] > 1 ? 255 : cmykf[0]*255.0f + 0.5f);
		unsigned M = (unsigned)(cmykf[1] < 0 ? 0 : cmykf[1] > 1 ? 255 : cmykf[1]*255.0f + 0.5f);
		unsigned Y = (unsigned)(cmykf[2] < 0 ? 0 : cmykf[2] > 1 ? 255 : cmykf[2]*255.0f + 0.5f);
		unsigned K = (unsigned)(cmykf[3] < 0 ? 0 : cmykf[3] > 1 ? 255 : cmykf[3]*255.0f + 0.5f);
		cmyk = (C<<24) | (M<<16) | (Y<<8) | K;
		fz_drop_separations(ctx, seps);
	jname = name ? (*env)->NewStringUTF(env, name) : NULL;

	sepClass = (*env)->FindClass(env, PACKAGENAME "/Separation");
	if (sepClass == NULL)
		return NULL;

	ctor = (*env)->GetMethodID(env, sepClass, "<init>", "(Ljava/lang/String;II)V");
	if (ctor == NULL)
		return NULL;

	return (*env)->NewObject(env, sepClass, ctor, jname, bgra, cmyk);
}
