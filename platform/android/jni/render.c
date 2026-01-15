#include "mupdf_native.h"
#include "pp_core.h"

JNIEXPORT void JNICALL JNI_FN(MuPDFCore_gotoPageInternal)(JNIEnv *env, jobject thiz, int page);

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_drawPage)(JNIEnv *env, jobject thiz, jobject bitmap,
		int pageW, int pageH, int patchX, int patchY, int patchW, int patchH, jlong cookiePtr)
{
	AndroidBitmapInfo info;
	void *pixels;
	int ret = 0;
	int ok = 0;
	globals *glo = get_globals(env, thiz);
	page_cache *pc = &glo->pages[glo->current];
	pp_cookie *cookie = (pp_cookie *)(intptr_t)cookiePtr;

	if (!glo || !glo->ctx || !glo->doc || pc->number < 0)
		return 0;

	LOGI("native drawPage page=%d page=%dx%d patch=[%d,%d,%d,%d]",
	     pc->number, pageW, pageH, patchX, patchY, patchW, patchH);
	if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
		LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
		return 0;
	}

	if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
		LOGE("Bitmap format is not RGBA_8888 !");
		return 0;
	}

	if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
		return 0;
	}

	ok = pp_render_patch_rgba_mupdf_opts(glo->ctx, glo->doc, pc->page, pc->number,
	                               pageW, pageH,
	                               patchX, patchY, patchW, patchH,
	                               (unsigned char *)pixels, info.stride, cookie,
	                               glo->render_annots);
	if (!ok)
		LOGE("drawPage render failed page=%d", pc->number);

	AndroidBitmap_unlockPixels(env, bitmap);

	return ok ? 1 : 0;
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
	/* Guard against stale negative pages from recycled views. */
	if (page < 0)
		page = 0;

	AndroidBitmapInfo info;
	void *pixels;
	int ret = 0;
	int ok = 0;
	globals *glo = get_globals(env, thiz);
	fz_page *cached_page = NULL;
	int i;
	pp_cookie *cookie = (pp_cookie *)(intptr_t)cookiePtr;

	if (!glo || !glo->ctx || !glo->doc)
		return 0;

	LOGI("native updatePage page=%d page=%dx%d patch=[%d,%d,%d,%d]",
	     page, pageW, pageH, patchX, patchY, patchW, patchH);
	if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
		LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
		return 0;
	}

	if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
		LOGE("Bitmap format is not RGBA_8888 !");
		return 0;
	}

	if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
		return 0;
	}

	for (i = 0; i < NUM_CACHE; i++)
	{
		if (glo->pages[i].page != NULL && glo->pages[i].number == page)
		{
			cached_page = glo->pages[i].page;
			break;
		}
	}

	ok = pp_render_patch_rgba_mupdf_opts(glo->ctx, glo->doc, cached_page, page,
	                               pageW, pageH,
	                               patchX, patchY, patchW, patchH,
	                               (unsigned char *)pixels, info.stride, cookie,
	                               glo->render_annots);
	if (!ok)
		LOGE("updatePage render failed page=%d", page);

	AndroidBitmap_unlockPixels(env, bitmap);

	return ok ? 1 : 0;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_setAnnotationRenderingEnabled)(JNIEnv *env, jobject thiz, jboolean enabled)
{
	globals *glo = get_globals_any_thread(env, thiz);
	if (!glo || !glo->ctx)
		return;
	glo->render_annots = enabled ? 1 : 0;
	/* Best-effort: drop cached annotation display lists so future renders can't reuse stale layers. */
	dump_annotation_display_lists(glo);
}
