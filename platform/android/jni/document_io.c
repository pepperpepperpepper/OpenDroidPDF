#include "mupdf_native.h"

#include <string.h>

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

/* Enable to log rendering times (render each frame 100 times and time) */
#undef TIME_DISPLAY_LIST
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
        init_annotation_defaults(glo);

#ifdef DEBUG
	/* Try and send stdout/stderr to file in debug builds. This
	 * path may not work on all platforms, but it works on the
	 * LG G3, and it's no worse than not redirecting it anywhere
	 * on anything else. */
	freopen("/storage/emulated/0/Download/stdout.txt", "a", stdout);
	freopen("/storage/emulated/0/Download/stderr.txt", "a", stderr);
#endif

        
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
    init_annotation_defaults(glo);
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

/* This enum should be kept in line with SignatureState in MuPDFPageView.java */
enum
{
	Signature_NoSupport,
	Signature_Unsigned,
	Signature_Signed
};

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


JNIEXPORT jint JNICALL
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
