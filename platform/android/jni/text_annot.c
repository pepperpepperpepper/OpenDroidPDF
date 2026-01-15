#include "mupdf_native.h"
#include "pp_core.h"
#include "pp_core_pdf_annots_internal.h"
#include <math.h>
#include <string.h>

static long long pp_object_id_from_annot(fz_context *ctx, pdf_annot *annot);

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
    pp_point *pts = NULL;
    float color[3];
    float alpha;
    const char *utf8 = NULL;
    long long object_id = -1;
    
    if (idoc == NULL)
        return;    
            
    switch (type)
    {
        case PDF_ANNOT_HIGHLIGHT:
            color[0] = glo->highlightColor[0];
            color[1] = glo->highlightColor[1];
            color[2] = glo->highlightColor[2];
            alpha = 0.69f; /* Match legacy Android behavior. */
            break;
        case PDF_ANNOT_UNDERLINE:
            color[0] = glo->underlineColor[0];
            color[1] = glo->underlineColor[1];
            color[2] = glo->underlineColor[2];
            alpha = 1.0f;
            break;
        case PDF_ANNOT_STRIKE_OUT:
            color[0] = glo->strikeoutColor[0];
            color[1] = glo->strikeoutColor[1];
            color[2] = glo->strikeoutColor[2];
            alpha = 1.0f;
            break;
        case PDF_ANNOT_CARET:
            /* Use the underline palette for proofreading insert marks. */
            color[0] = glo->underlineColor[0];
            color[1] = glo->underlineColor[1];
            color[2] = glo->underlineColor[2];
            alpha = 1.0f;
            break;
        case PDF_ANNOT_TEXT:
            color[0] = glo->textAnnotIconColor[0];
            color[1] = glo->textAnnotIconColor[1];
            color[2] = glo->textAnnotIconColor[2];
            alpha = 1.0f;
            break;
        case PDF_ANNOT_FREE_TEXT:
            color[0] = glo->freetextColor[0];
            color[1] = glo->freetextColor[1];
            color[2] = glo->freetextColor[2];
            alpha = 1.0f;
            break;
        default:
            return;
    }

    fz_var(pts);
    fz_try(ctx)
    {
        pt_cls = (*env)->FindClass(env, "android/graphics/PointF");
        if (pt_cls == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "FindClass");
        x_fid = (*env)->GetFieldID(env, pt_cls, "x", "F");
        if (x_fid == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetFieldID(x)");
        y_fid = (*env)->GetFieldID(env, pt_cls, "y", "F");
        if (y_fid == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetFieldID(y)");

        n = (*env)->GetArrayLength(env, points);

        pts = fz_malloc_array(ctx, n, pp_point);

        for (i = 0; i < n; i++)
        {
            jobject opt = (*env)->GetObjectArrayElement(env, points, i);
            pts[i].x = opt ? (*env)->GetFloatField(env, opt, x_fid) : 0.0f;
            pts[i].y = opt ? (*env)->GetFloatField(env, opt, y_fid) : 0.0f;
            (*env)->DeleteLocalRef(env, opt);
        }

        if (jtext != NULL)
            utf8 = (*env)->GetStringUTFChars(env, jtext, NULL);

        if (!pp_pdf_add_annot_mupdf(ctx, doc, pc->page, pc->number,
                                    pc->width, pc->height,
                                    (int)type,
                                    pts, n,
                                    color, alpha,
                                    utf8,
                                    &object_id))
            fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_add_annot failed");

        if (type == PDF_ANNOT_FREE_TEXT && object_id > 0)
        {
            float bg[3] = { glo->freetextBackgroundColor[0], glo->freetextBackgroundColor[1], glo->freetextBackgroundColor[2] };
            float border[3] = { glo->freetextBorderColor[0], glo->freetextBorderColor[1], glo->freetextBorderColor[2] };
            const char *font_key = "Helv";
            if (glo->freetextFontFamily == 1) font_key = "TiRo";
            else if (glo->freetextFontFamily == 2) font_key = "Cour";
            (void)pp_pdf_update_freetext_style_by_object_id_with_font_mupdf(ctx, doc, pc->page, pc->number, object_id, font_key, glo->freetextFontSize, color);
            if ((glo->freetextFontStyleFlags & 0x0F) != 0)
                (void)pp_pdf_update_freetext_style_flags_by_object_id_mupdf(ctx, doc, pc->page, pc->number, object_id, glo->freetextFontStyleFlags);
            if (fabsf(glo->freetextLineHeight - 1.2f) > 0.001f || fabsf(glo->freetextTextIndentPt) > 0.01f)
                (void)pp_pdf_update_freetext_paragraph_by_object_id_mupdf(ctx, doc, pc->page, pc->number, object_id, glo->freetextLineHeight, glo->freetextTextIndentPt);
            if (glo->freetextBackgroundOpacity > 0.001f)
                (void)pp_pdf_update_freetext_background_by_object_id_mupdf(ctx, doc, pc->page, pc->number, object_id, bg, glo->freetextBackgroundOpacity);
            if (glo->freetextBorderWidthPt > 0.001f)
                (void)pp_pdf_update_freetext_border_by_object_id_mupdf(ctx, doc, pc->page, pc->number, object_id, border, glo->freetextBorderWidthPt, glo->freetextBorderDashed, glo->freetextBorderRadiusPt);
        }

        dump_annotation_display_lists(glo);
    }
    fz_always(ctx)
    {
        if (utf8 != NULL)
            (*env)->ReleaseStringUTFChars(env, jtext, utf8);
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
JNI_FN(MuPDFCore_setFreeTextDefaults)(JNIEnv * env, jobject thiz,
                                     jfloat fontSize,
                                     jint fontFamily,
                                     jint fontStyleFlags,
                                     jfloat lineHeight,
                                     jfloat textIndentPt,
                                     jfloat textR, jfloat textG, jfloat textB,
                                     jfloat bgR, jfloat bgG, jfloat bgB, jfloat bgOpacity,
                                     jfloat borderR, jfloat borderG, jfloat borderB,
                                     jfloat borderWidthPt,
                                     jboolean borderDashed,
                                     jfloat borderRadiusPt)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return;

    if (fontSize < 6.0f) fontSize = 6.0f;
    if (fontSize > 96.0f) fontSize = 96.0f;
    glo->freetextFontSize = fontSize;
    int fam = (int)fontFamily;
    if (fam < 0 || fam > 2) fam = 0;
    glo->freetextFontFamily = fam;
    glo->freetextFontStyleFlags = ((int)fontStyleFlags) & 0x0F;
    glo->freetextLineHeight = lineHeight < 0.5f ? 1.2f : (lineHeight > 5.0f ? 5.0f : lineHeight);
    glo->freetextTextIndentPt = textIndentPt < -144.0f ? -144.0f : (textIndentPt > 144.0f ? 144.0f : textIndentPt);

    #define CLAMP01(v) ((v) < 0.0f ? 0.0f : ((v) > 1.0f ? 1.0f : (v)))
    glo->freetextColor[0] = CLAMP01(textR);
    glo->freetextColor[1] = CLAMP01(textG);
    glo->freetextColor[2] = CLAMP01(textB);
    glo->freetextBackgroundColor[0] = CLAMP01(bgR);
    glo->freetextBackgroundColor[1] = CLAMP01(bgG);
    glo->freetextBackgroundColor[2] = CLAMP01(bgB);
    glo->freetextBackgroundOpacity = CLAMP01(bgOpacity);
    glo->freetextBorderColor[0] = CLAMP01(borderR);
    glo->freetextBorderColor[1] = CLAMP01(borderG);
    glo->freetextBorderColor[2] = CLAMP01(borderB);
    glo->freetextBorderWidthPt = borderWidthPt < 0.0f ? 0.0f : (borderWidthPt > 24.0f ? 24.0f : borderWidthPt);
    glo->freetextBorderDashed = borderDashed ? 1 : 0;
    glo->freetextBorderRadiusPt = borderRadiusPt < 0.0f ? 0.0f : (borderRadiusPt > 48.0f ? 48.0f : borderRadiusPt);
    #undef CLAMP01
}

static int
opd_font_family_from_font_key(const char *font)
{
    if (!font || !font[0])
        return 0;
    if (!strcmp(font, "TiRo") || !strncmp(font, "Times", 5))
        return 1;
    if (!strcmp(font, "Cour") || !strncmp(font, "Cour", 4) || !strncmp(font, "Couri", 5))
        return 2;
    return 0;
}

static void
opd_rgb_from_default_appearance(float out_rgb[3], int n, const float color[4])
{
    if (!out_rgb) return;
    out_rgb[0] = 0.0f;
    out_rgb[1] = 0.0f;
    out_rgb[2] = 0.0f;
    if (!color) return;
    if (n <= 0) return;
    if (n == 1)
    {
        float g = color[0];
        out_rgb[0] = g;
        out_rgb[1] = g;
        out_rgb[2] = g;
        return;
    }
    if (n == 3)
    {
        out_rgb[0] = color[0];
        out_rgb[1] = color[1];
        out_rgb[2] = color[2];
        return;
    }
    if (n >= 4)
    {
        float c = color[0], m = color[1], y = color[2], k = color[3];
        float r = 1.0f - fminf(1.0f, c + k);
        float g = 1.0f - fminf(1.0f, m + k);
        float b = 1.0f - fminf(1.0f, y + k);
        out_rgb[0] = r;
        out_rgb[1] = g;
        out_rgb[2] = b;
        return;
    }
}

static void
opd_rgb_from_pdf_color_array(fz_context *ctx, pdf_obj *arr, float out_rgb[3])
{
    if (!out_rgb)
        return;
    out_rgb[0] = 0.0f;
    out_rgb[1] = 0.0f;
    out_rgb[2] = 0.0f;
    if (!ctx || !arr)
        return;
    if (!pdf_is_array(ctx, arr))
        return;

    int len = pdf_array_len(ctx, arr);
    if (len <= 0)
        return;
    if (len == 1)
    {
        float g = pdf_to_real(ctx, pdf_array_get(ctx, arr, 0));
        out_rgb[0] = g;
        out_rgb[1] = g;
        out_rgb[2] = g;
        return;
    }
    if (len >= 3)
    {
        out_rgb[0] = pdf_to_real(ctx, pdf_array_get(ctx, arr, 0));
        out_rgb[1] = pdf_to_real(ctx, pdf_array_get(ctx, arr, 1));
        out_rgb[2] = pdf_to_real(ctx, pdf_array_get(ctx, arr, 2));
        return;
    }
}

static float
opd_clamp01f(float v)
{
    if (v < 0.0f) return 0.0f;
    if (v > 1.0f) return 1.0f;
    return v;
}

static void
opd_clamp_rgb(float rgb[3])
{
    if (!rgb) return;
    rgb[0] = opd_clamp01f(rgb[0]);
    rgb[1] = opd_clamp01f(rgb[1]);
    rgb[2] = opd_clamp01f(rgb[2]);
}

static jfloatArray
opd_new_float_array(JNIEnv *env, int n)
{
    if (!env || n <= 0) return NULL;
    return (*env)->NewFloatArray(env, n);
}

JNIEXPORT jint JNICALL
JNI_FN(MuPDFCore_getFreeTextFontFamilyInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return 0;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];

    if (idoc == NULL)
        return 0;

    int out = 0;
    fz_try(ctx)
    {
        pdf_page *pdfpage = (pdf_page *)pc->page;
        for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
        {
            long long id = pp_object_id_from_annot(ctx, annot);
            if (id != (long long)objectNumber)
                continue;
            enum pdf_annot_type type = pdf_annot_type(ctx, annot);
            if (type != PDF_ANNOT_FREE_TEXT)
                break;

            const char *font = NULL;
            float size = 0.0f;
            int n = 0;
            float color[4] = {0};
            if (pdf_annot_has_default_appearance(ctx, annot))
            {
                pdf_annot_default_appearance(ctx, annot, &font, &size, &n, color);
                out = opd_font_family_from_font_key(font);
            }
            break;
        }
    }
    fz_catch(ctx)
    {
        LOGE("getFreeTextFontFamilyInternal: %s", fz_caught_message(ctx));
    }
    if (out < 0 || out > 2) out = 0;
    return (jint)out;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_updateFreeTextFontFamilyInternal)(JNIEnv * env, jobject thiz, jlong objectNumber, jint fontFamily)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];

    if (idoc == NULL)
        return;

    int fam = (int)fontFamily;
    if (fam < 0 || fam > 2) fam = 0;
    const char *font_key = "Helv";
    if (fam == 1) font_key = "TiRo";
    else if (fam == 2) font_key = "Cour";

    fz_try(ctx)
    {
        pdf_page *pdfpage = (pdf_page *)pc->page;
        for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
        {
            long long id = pp_object_id_from_annot(ctx, annot);
            if (id != (long long)objectNumber)
                continue;
            enum pdf_annot_type type = pdf_annot_type(ctx, annot);
            if (type != PDF_ANNOT_FREE_TEXT)
                break;

            float size = 12.0f;
            float rgb[3] = {0.0f, 0.0f, 0.0f};
            const char *font_existing = NULL;
            int n = 0;
            float color[4] = {0};
            if (pdf_annot_has_default_appearance(ctx, annot))
            {
                pdf_annot_default_appearance(ctx, annot, &font_existing, &size, &n, color);
                opd_rgb_from_default_appearance(rgb, n, color);
                if (size <= 0.0f) size = 12.0f;
            }
            if (!pp_pdf_update_freetext_style_by_object_id_with_font_mupdf(ctx, doc, pc->page, pc->number, (long long)objectNumber, font_key, size, rgb))
                fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_update_freetext_style_by_object_id_with_font failed");
            dump_annotation_display_lists(glo);
            break;
        }
    }
    fz_catch(ctx)
    {
        LOGE("updateFreeTextFontFamilyInternal: %s", fz_caught_message(ctx));
    }
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_hasFreeTextRichContentsInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return JNI_FALSE;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];

    if (idoc == NULL)
        return JNI_FALSE;

    int out = 0;
    fz_try(ctx)
    {
        pdf_page *pdfpage = (pdf_page *)pc->page;
        for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
        {
            long long id = pp_object_id_from_annot(ctx, annot);
            if (id != (long long)objectNumber)
                continue;
            enum pdf_annot_type type = pdf_annot_type(ctx, annot);
            if (type != PDF_ANNOT_FREE_TEXT)
                break;
            pdf_obj *rc = pdf_dict_get(ctx, annot->obj, PDF_NAME(RC));
            if (rc)
                out = 1;
            break;
        }
    }
    fz_catch(ctx)
    {
        LOGE("hasFreeTextRichContentsInternal: %s", fz_caught_message(ctx));
    }

    return out ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
JNI_FN(MuPDFCore_getFreeTextStyleFlagsInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return 0;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];

    if (idoc == NULL)
        return 0;

    int out = 0;
    fz_try(ctx)
    {
        int flags = 0;
        if (pp_pdf_get_freetext_style_flags_by_object_id_mupdf(ctx, doc, pc->page, pc->number, (long long)objectNumber, &flags))
            out = flags;
    }
    fz_catch(ctx)
    {
        LOGE("getFreeTextStyleFlagsInternal: %s", fz_caught_message(ctx));
    }

    return (jint)out;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_updateFreeTextStyleFlagsInternal)(JNIEnv * env, jobject thiz, jlong objectNumber, jint styleFlags)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];

    if (idoc == NULL)
        return;

    fz_try(ctx)
    {
        if (!pp_pdf_update_freetext_style_flags_by_object_id_mupdf(ctx, doc, pc->page, pc->number, (long long)objectNumber, (int)styleFlags))
            fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_update_freetext_style_flags_by_object_id failed");
        dump_annotation_display_lists(glo);
    }
    fz_catch(ctx)
    {
        LOGE("updateFreeTextStyleFlagsInternal: %s", fz_caught_message(ctx));
    }
}

JNIEXPORT jfloatArray JNICALL
JNI_FN(MuPDFCore_getFreeTextParagraphInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return NULL;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];

    if (idoc == NULL)
        return NULL;

    float line_height = 1.2f;
    float text_indent_pt = 0.0f;
    fz_try(ctx)
    {
        (void)pp_pdf_get_freetext_paragraph_by_object_id_mupdf(ctx, doc, pc->page, pc->number, (long long)objectNumber, &line_height, &text_indent_pt);
    }
    fz_catch(ctx)
    {
        LOGE("getFreeTextParagraphInternal: %s", fz_caught_message(ctx));
    }

    jfloatArray out = opd_new_float_array(env, 2);
    if (out == NULL) return NULL;
    jfloat vals[2] = { (jfloat)line_height, (jfloat)text_indent_pt };
    (*env)->SetFloatArrayRegion(env, out, 0, 2, vals);
    return out;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_updateFreeTextParagraphInternal)(JNIEnv * env, jobject thiz, jlong objectNumber, jfloat lineHeight, jfloat textIndentPt)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];

    if (idoc == NULL)
        return;

    fz_try(ctx)
    {
        if (!pp_pdf_update_freetext_paragraph_by_object_id_mupdf(ctx, doc, pc->page, pc->number, (long long)objectNumber, (float)lineHeight, (float)textIndentPt))
            fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_update_freetext_paragraph_by_object_id failed");
        dump_annotation_display_lists(glo);
    }
    fz_catch(ctx)
    {
        LOGE("updateFreeTextParagraphInternal: %s", fz_caught_message(ctx));
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

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_deleteAnnotationByObjectNumberInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];

	if (idoc == NULL)
		return;

	fz_try(ctx)
	{
		if (pp_pdf_delete_annot_by_object_id_mupdf(ctx, doc, pc->page, pc->number, (long long)objectNumber))
			dump_annotation_display_lists(glo);
	}
	fz_catch(ctx)
	{
		LOGE("deleteAnnotationByObjectNumberInternal: %s", fz_caught_message(ctx));
	}
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_updateAnnotationContentsByObjectNumberInternal)(JNIEnv * env, jobject thiz, jlong objectNumber, jstring jtext)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];
	const char *utf8 = NULL;

	if (idoc == NULL)
		return;

	fz_try(ctx)
	{
		if (jtext != NULL)
			utf8 = (*env)->GetStringUTFChars(env, jtext, NULL);

		if (!pp_pdf_update_annot_contents_by_object_id_mupdf(ctx, doc, pc->page, pc->number, (long long)objectNumber, utf8))
			fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_update_annot_contents failed");

		dump_annotation_display_lists(glo);
	}
	fz_always(ctx)
	{
		if (utf8 != NULL)
			(*env)->ReleaseStringUTFChars(env, jtext, utf8);
	}
	fz_catch(ctx)
	{
		LOGE("updateAnnotationContentsByObjectNumberInternal: %s", fz_caught_message(ctx));
	}
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_updateAnnotationRectByObjectNumberInternal)(JNIEnv * env, jobject thiz, jlong objectNumber,
                                                            jfloat x0, jfloat y0, jfloat x1, jfloat y1)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];

	if (idoc == NULL)
		return;

	fz_try(ctx)
	{
		if (!pp_pdf_update_annot_rect_by_object_id_mupdf(ctx, doc, pc->page, pc->number,
		                                                pc->width, pc->height,
		                                                (long long)objectNumber,
		                                                x0, y0, x1, y1))
			fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_update_annot_rect failed");
		dump_annotation_display_lists(glo);
	}
	fz_catch(ctx)
	{
		LOGE("updateAnnotationRectByObjectNumberInternal: %s", fz_caught_message(ctx));
	}
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_updateFreeTextStyleByObjectNumberInternal)(JNIEnv * env, jobject thiz, jlong objectNumber,
                                                           jfloat fontSize, jfloat r, jfloat g, jfloat b)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];
	float color[3] = { r, g, b };

	if (idoc == NULL)
		return;

	fz_try(ctx)
	{
		if (!pp_pdf_update_freetext_style_by_object_id_mupdf(ctx, doc, pc->page, pc->number, (long long)objectNumber, fontSize, color))
			fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_update_freetext_style failed");
		dump_annotation_display_lists(glo);
	}
	fz_catch(ctx)
	{
		LOGE("updateFreeTextStyleByObjectNumberInternal: %s", fz_caught_message(ctx));
	}
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_updateFreeTextBackgroundByObjectNumberInternal)(JNIEnv * env, jobject thiz, jlong objectNumber,
                                                                 jfloat r, jfloat g, jfloat b, jfloat opacity)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];
	float color[3] = { r, g, b };

	if (idoc == NULL)
		return;

	fz_try(ctx)
	{
		if (!pp_pdf_update_freetext_background_by_object_id_mupdf(ctx, doc, pc->page, pc->number, (long long)objectNumber, color, opacity))
			fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_update_freetext_background failed");
		dump_annotation_display_lists(glo);
	}
	fz_catch(ctx)
	{
		LOGE("updateFreeTextBackgroundByObjectNumberInternal: %s", fz_caught_message(ctx));
	}
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_updateFreeTextBorderByObjectNumberInternal)(JNIEnv * env, jobject thiz, jlong objectNumber,
                                                             jfloat r, jfloat g, jfloat b,
                                                             jfloat widthPt, jboolean dashed, jfloat radiusPt)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];
	float color[3] = { r, g, b };

	if (idoc == NULL)
		return;

	fz_try(ctx)
	{
		if (!pp_pdf_update_freetext_border_by_object_id_mupdf(ctx, doc, pc->page, pc->number,
		                                                      (long long)objectNumber,
		                                                      color,
		                                                      widthPt,
		                                                      dashed ? 1 : 0,
		                                                      radiusPt))
			fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_update_freetext_border failed");
		dump_annotation_display_lists(glo);
	}
	fz_catch(ctx)
	{
		LOGE("updateFreeTextBorderByObjectNumberInternal: %s", fz_caught_message(ctx));
	}
}

JNIEXPORT jfloatArray JNICALL
JNI_FN(MuPDFCore_getFreeTextTextColorInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return NULL;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];

    if (idoc == NULL)
        return NULL;

    float rgb[3] = {0.0f, 0.0f, 0.0f};
    fz_try(ctx)
    {
        pdf_page *pdfpage = (pdf_page *)pc->page;
        for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
        {
            long long id = pp_object_id_from_annot(ctx, annot);
            if (id != (long long)objectNumber)
                continue;
            enum pdf_annot_type type = pdf_annot_type(ctx, annot);
            if (type != PDF_ANNOT_FREE_TEXT)
                break;

            const char *font = NULL;
            float size = 0.0f;
            int n = 0;
            float color[4] = {0};
            if (pdf_annot_has_default_appearance(ctx, annot))
            {
                pdf_annot_default_appearance(ctx, annot, &font, &size, &n, color);
                opd_rgb_from_default_appearance(rgb, n, color);
            }
            break;
        }
    }
    fz_catch(ctx)
    {
        LOGE("getFreeTextTextColorInternal: %s", fz_caught_message(ctx));
    }
    opd_clamp_rgb(rgb);

    jfloatArray out = opd_new_float_array(env, 3);
    if (out == NULL) return NULL;
    (*env)->SetFloatArrayRegion(env, out, 0, 3, rgb);
    return out;
}

JNIEXPORT jfloatArray JNICALL
JNI_FN(MuPDFCore_getFreeTextBackgroundInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return NULL;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];

    if (idoc == NULL)
        return NULL;

    float rgb[3] = {1.0f, 1.0f, 1.0f};
    float opacity = 0.0f;
    fz_try(ctx)
    {
        pdf_page *pdfpage = (pdf_page *)pc->page;
        for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
        {
            long long id = pp_object_id_from_annot(ctx, annot);
            if (id != (long long)objectNumber)
                continue;
            enum pdf_annot_type type = pdf_annot_type(ctx, annot);
            if (type != PDF_ANNOT_FREE_TEXT)
                break;

            pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
            if (annot_obj)
            {
                pdf_obj *ic = pdf_dict_gets(ctx, annot_obj, "IC");
                if (ic)
                {
                    opd_rgb_from_pdf_color_array(ctx, ic, rgb);
                    opacity = 1.0f;
                    pdf_obj *ca = pdf_dict_gets(ctx, annot_obj, "CA");
                    if (!ca) ca = pdf_dict_gets(ctx, annot_obj, "ca");
                    if (ca) opacity = pdf_to_real(ctx, ca);
                }
            }
            break;
        }
    }
    fz_catch(ctx)
    {
        LOGE("getFreeTextBackgroundInternal: %s", fz_caught_message(ctx));
    }
    opd_clamp_rgb(rgb);
    opacity = opd_clamp01f(opacity);

    jfloat vals[4] = { rgb[0], rgb[1], rgb[2], opacity };
    jfloatArray out = opd_new_float_array(env, 4);
    if (out == NULL) return NULL;
    (*env)->SetFloatArrayRegion(env, out, 0, 4, vals);
    return out;
}

JNIEXPORT jfloatArray JNICALL
JNI_FN(MuPDFCore_getFreeTextBorderInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return NULL;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];

    if (idoc == NULL)
        return NULL;

    float rgb[3] = {0.0f, 0.0f, 0.0f};
    float width_pt = 0.0f;
    float dashed = 0.0f;
    float radius_pt = 0.0f;

    fz_try(ctx)
    {
        pdf_page *pdfpage = (pdf_page *)pc->page;
        for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
        {
            long long id = pp_object_id_from_annot(ctx, annot);
            if (id != (long long)objectNumber)
                continue;
            enum pdf_annot_type type = pdf_annot_type(ctx, annot);
            if (type != PDF_ANNOT_FREE_TEXT)
                break;

            pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
            if (annot_obj)
            {
                pdf_obj *c = pdf_dict_gets(ctx, annot_obj, "C");
                if (c) opd_rgb_from_pdf_color_array(ctx, c, rgb);

                pdf_obj *w = pdf_dict_gets(ctx, annot_obj, "OPDBorderWidth");
                if (w) width_pt = pdf_to_real(ctx, w);
                if (width_pt < 0.0f) width_pt = 0.0f;
                if (width_pt > 24.0f) width_pt = 24.0f;

                pdf_obj *d = pdf_dict_gets(ctx, annot_obj, "OPDBorderDashed");
                if (d && width_pt > 0.0f)
                {
                    float v = pdf_to_real(ctx, d);
                    dashed = (v > 0.5f) ? 1.0f : 0.0f;
                }

                pdf_obj *r = pdf_dict_gets(ctx, annot_obj, "OPDBorderRadius");
                if (r) radius_pt = pdf_to_real(ctx, r);
                if (radius_pt < 0.0f) radius_pt = 0.0f;
                if (radius_pt > 48.0f) radius_pt = 48.0f;
            }
            break;
        }
    }
    fz_catch(ctx)
    {
        LOGE("getFreeTextBorderInternal: %s", fz_caught_message(ctx));
    }
    opd_clamp_rgb(rgb);

    jfloat vals[6] = { rgb[0], rgb[1], rgb[2], width_pt, dashed, radius_pt };
    jfloatArray out = opd_new_float_array(env, 6);
    if (out == NULL) return NULL;
    (*env)->SetFloatArrayRegion(env, out, 0, 6, vals);
    return out;
}

static long long
pp_object_id_from_annot(fz_context *ctx, pdf_annot *annot)
{
	if (!ctx || !annot || !annot->obj)
		return -1;
	int num = pdf_to_num(ctx, annot->obj);
	if (num <= 0)
		return -1;
	int gen = pdf_to_gen(ctx, annot->obj);
	return (((long long)num) << 32) | (long long)(gen & 0xffffffffu);
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_getFreeTextUserResizedInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return JNI_FALSE;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];

	if (idoc == NULL)
		return JNI_FALSE;

	jboolean out = JNI_TRUE; /* Default to "user resized" when the flag is missing (conservative). */
	fz_try(ctx)
	{
		pdf_page *pdfpage = (pdf_page *)pc->page;
		for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
		{
			long long id = pp_object_id_from_annot(ctx, annot);
			if (id != (long long)objectNumber)
				continue;
			enum pdf_annot_type type = pdf_annot_type(ctx, annot);
			if (type != PDF_ANNOT_FREE_TEXT)
				break;

			pdf_obj *val = pdf_dict_gets(ctx, annot->obj, "OPDUserResized");
			if (val)
				out = pdf_to_bool(ctx, val) ? JNI_TRUE : JNI_FALSE;
			break;
		}
	}
	fz_catch(ctx)
	{
		LOGE("getFreeTextUserResizedInternal: %s", fz_caught_message(ctx));
	}
	return out;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_setFreeTextUserResizedInternal)(JNIEnv * env, jobject thiz, jlong objectNumber, jboolean userResized)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];

	if (idoc == NULL)
		return;

	fz_try(ctx)
	{
		pdf_page *pdfpage = (pdf_page *)pc->page;
		for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
		{
			long long id = pp_object_id_from_annot(ctx, annot);
			if (id != (long long)objectNumber)
				continue;
			enum pdf_annot_type type = pdf_annot_type(ctx, annot);
			if (type != PDF_ANNOT_FREE_TEXT)
				break;
			{
				pdf_obj *key = pdf_new_name(ctx, "OPDUserResized");
				pdf_dict_put_bool(ctx, annot->obj, key, userResized ? 1 : 0);
				pdf_drop_obj(ctx, key);
			}
			pdf_update_annot(ctx, annot);
			pdf_update_page(ctx, pdfpage);
			dump_annotation_display_lists(glo);
			break;
		}
	}
	fz_catch(ctx)
	{
		LOGE("setFreeTextUserResizedInternal: %s", fz_caught_message(ctx));
	}
}

JNIEXPORT jfloat JNICALL
JNI_FN(MuPDFCore_getFreeTextFontSizeInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return 12.0f;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];

	if (idoc == NULL)
		return 12.0f;

	float out = 12.0f;
	fz_try(ctx)
	{
		pdf_page *pdfpage = (pdf_page *)pc->page;
		for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
		{
			long long id = pp_object_id_from_annot(ctx, annot);
			if (id != (long long)objectNumber)
				continue;
			enum pdf_annot_type type = pdf_annot_type(ctx, annot);
			if (type != PDF_ANNOT_FREE_TEXT)
				break;

			const char *font = NULL;
			float size = 12.0f;
			int n = 0;
			float color[4] = { 0 };
			if (pdf_annot_has_default_appearance(ctx, annot))
			{
				pdf_annot_default_appearance(ctx, annot, &font, &size, &n, color);
				if (size > 0.0f)
					out = size;
			}
			break;
		}
	}
	fz_catch(ctx)
	{
		LOGE("getFreeTextFontSizeInternal: %s", fz_caught_message(ctx));
	}
	return out;
}

JNIEXPORT jint JNICALL
JNI_FN(MuPDFCore_getFreeTextAlignmentInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return 0;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];

	if (idoc == NULL)
		return 0;

	int out = 0;
	fz_try(ctx)
	{
		pdf_page *pdfpage = (pdf_page *)pc->page;
		for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
		{
			long long id = pp_object_id_from_annot(ctx, annot);
			if (id != (long long)objectNumber)
				continue;
			enum pdf_annot_type type = pdf_annot_type(ctx, annot);
			if (type != PDF_ANNOT_FREE_TEXT)
				break;
			out = pdf_to_int(ctx, pdf_dict_get(ctx, annot->obj, PDF_NAME(Q)));
			break;
		}
	}
	fz_catch(ctx)
	{
		LOGE("getFreeTextAlignmentInternal: %s", fz_caught_message(ctx));
	}
	if (out < 0) out = 0;
	if (out > 2) out = 2;
	return (jint)out;
}

JNIEXPORT jint JNICALL
JNI_FN(MuPDFCore_getFreeTextRotationInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return 0;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];

	if (idoc == NULL)
		return 0;

	int out = 0;
	fz_try(ctx)
	{
		pdf_page *pdfpage = (pdf_page *)pc->page;
		for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
		{
			long long id = pp_object_id_from_annot(ctx, annot);
			if (id != (long long)objectNumber)
				continue;
			enum pdf_annot_type type = pdf_annot_type(ctx, annot);
			if (type != PDF_ANNOT_FREE_TEXT)
				break;
			pdf_obj *val = pdf_dict_gets(ctx, annot->obj, "Rotate");
			if (val)
				out = pdf_to_int(ctx, val);
			break;
		}
	}
	fz_catch(ctx)
	{
		LOGE("getFreeTextRotationInternal: %s", fz_caught_message(ctx));
	}

	/* Normalize to [0..359] to match UI semantics. */
	if (out < 0 || out >= 360)
	{
		out %= 360;
		if (out < 0) out += 360;
	}
	return (jint)out;
}

JNIEXPORT jint JNICALL
JNI_FN(MuPDFCore_getFreeTextFlagsInternal)(JNIEnv * env, jobject thiz, jlong objectNumber)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return 0;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];

	if (idoc == NULL)
		return 0;

	int out = 0;
	fz_try(ctx)
	{
		pdf_page *pdfpage = (pdf_page *)pc->page;
		for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
		{
			long long id = pp_object_id_from_annot(ctx, annot);
			if (id != (long long)objectNumber)
				continue;
			enum pdf_annot_type type = pdf_annot_type(ctx, annot);
			if (type != PDF_ANNOT_FREE_TEXT)
				break;
			out = pdf_to_int(ctx, pdf_dict_get(ctx, annot->obj, PDF_NAME(F)));
			break;
		}
	}
	fz_catch(ctx)
	{
		LOGE("getFreeTextFlagsInternal: %s", fz_caught_message(ctx));
	}
	return (jint)out;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_updateFreeTextLocksInternal)(JNIEnv * env, jobject thiz, jlong objectNumber, jboolean lockPositionSize, jboolean lockContents)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];

	if (idoc == NULL)
		return;

	fz_try(ctx)
	{
		pdf_page *pdfpage = (pdf_page *)pc->page;
		for (pdf_annot *annot = pdf_first_annot(ctx, pdfpage); annot; annot = pdf_next_annot(ctx, annot))
		{
			long long id = pp_object_id_from_annot(ctx, annot);
			if (id != (long long)objectNumber)
				continue;
				enum pdf_annot_type type = pdf_annot_type(ctx, annot);
				if (type != PDF_ANNOT_FREE_TEXT)
					break;

				int flags = pdf_to_int(ctx, pdf_dict_get(ctx, annot->obj, PDF_NAME(F)));
				if (lockPositionSize) flags |= PDF_ANNOT_IS_LOCKED; else flags &= ~PDF_ANNOT_IS_LOCKED;
				if (lockContents) flags |= PDF_ANNOT_IS_LOCKED_CONTENTS; else flags &= ~PDF_ANNOT_IS_LOCKED_CONTENTS;
				pdf_dict_put_drop(ctx, annot->obj, PDF_NAME(F), pdf_new_int(ctx, flags));
				pdf_update_annot(ctx, annot);
				pdf_update_page(ctx, pdfpage);
				dump_annotation_display_lists(glo);
				break;
			}
	}
	fz_catch(ctx)
	{
		LOGE("updateFreeTextLocksInternal: %s", fz_caught_message(ctx));
	}
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_updateFreeTextAlignmentInternal)(JNIEnv * env, jobject thiz, jlong objectNumber, jint alignment)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];

	if (idoc == NULL)
		return;

	if (alignment < 0) alignment = 0;
	if (alignment > 2) alignment = 2;

	fz_try(ctx)
	{
		if (!pp_pdf_update_freetext_alignment_by_object_id_mupdf(ctx, doc, pc->page, pc->number,
		                                                        (long long)objectNumber,
		                                                        (int)alignment))
			fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_update_freetext_alignment failed");
		dump_annotation_display_lists(glo);
	}
	fz_catch(ctx)
	{
		LOGE("updateFreeTextAlignmentInternal: %s", fz_caught_message(ctx));
	}
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_updateFreeTextRotationInternal)(JNIEnv * env, jobject thiz, jlong objectNumber, jint rotationDegrees)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];

	if (idoc == NULL)
		return;

	/* Normalize to [0..359]. */
	if (rotationDegrees < 0 || rotationDegrees >= 360)
	{
		rotationDegrees %= 360;
		if (rotationDegrees < 0) rotationDegrees += 360;
	}

	fz_try(ctx)
	{
		if (!pp_pdf_update_freetext_rotation_by_object_id_mupdf(ctx, doc, pc->page, pc->number,
		                                                       (long long)objectNumber,
		                                                       (int)rotationDegrees))
			fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_update_freetext_rotation failed");
		dump_annotation_display_lists(glo);
	}
	fz_catch(ctx)
	{
		LOGE("updateFreeTextRotationInternal: %s", fz_caught_message(ctx));
	}
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_getAnnotationsInternal)(JNIEnv * env, jobject thiz, int pageNumber)
{
	jclass annotClass, pt_cls, ptarr_cls;
    jmethodID Annotation;
    jmethodID PointF;
    jobjectArray arr;
    jobject jannot;
    page_cache *pc;
    globals *glo = get_globals(env, thiz);
    fz_context *ctx;
    pdf_document *idoc;
    pp_pdf_annot_list *list = NULL;
    int count;
    
    if (glo == NULL)
        return NULL;
    ctx = glo->ctx;
    idoc = pdf_specifics(ctx, glo->doc);
    if (idoc == NULL)
        return NULL;

    annotClass = (*env)->FindClass(env, PACKAGENAME "/Annotation");
    if (annotClass == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "FindClass");
    
    Annotation = (*env)->GetMethodID(env, annotClass, "<init>", "(FFFFI[[Landroid/graphics/PointF;Ljava/lang/String;J)V"); 
    if (Annotation == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetMethodID");

    pt_cls = (*env)->FindClass(env, "android/graphics/PointF");
    if (pt_cls == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "FindClass");
    PointF = (*env)->GetMethodID(env, pt_cls, "<init>", "(FF)V");
    if (PointF == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetMethodID(PointF)");

    ptarr_cls = (*env)->FindClass(env, "[Landroid/graphics/PointF;");
    if (ptarr_cls == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "FindClass(PointF[])");
    
    JNI_FN(MuPDFCore_gotoPageInternal)(env, thiz, pageNumber);
    pc = &glo->pages[glo->current];
    if (pc->number != pageNumber || pc->page == NULL)
        return NULL;

    if (!pp_pdf_list_annots_mupdf(ctx, glo->doc, pc->page, pc->number,
                                  pc->width, pc->height,
                                  &list) || list == NULL)
        return NULL;

    count = list->count;
    arr = (*env)->NewObjectArray(env, count, annotClass, NULL);
    if (arr == NULL) goto cleanup;

    for (int idx = 0; idx < list->count; idx++)
    {
        pp_pdf_annot_info *info = &list->items[idx];
        int type = info->type;

        jstring jtext = NULL;
        if (info->contents_utf8 != NULL)
            jtext = (*env)->NewStringUTF(env, info->contents_utf8);

        jobjectArray arcs = NULL;
        if (info->arcs != NULL && info->arc_count > 0)
        {
            arcs = (*env)->NewObjectArray(env, info->arc_count, ptarr_cls, NULL);
            if (arcs == NULL) { arr = NULL; goto cleanup; }

            for (int ai = 0; ai < info->arc_count; ai++)
            {
                pp_pdf_annot_arc *arc = &info->arcs[ai];
                jobjectArray arci = (*env)->NewObjectArray(env, arc->count, pt_cls, NULL);
                if (arci == NULL) { arr = NULL; goto cleanup; }
                for (int pi = 0; pi < arc->count; pi++)
                {
                    jobject pfobj = (*env)->NewObject(env, pt_cls, PointF, arc->points[pi].x, arc->points[pi].y);
                    (*env)->SetObjectArrayElement(env, arci, pi, pfobj);
                    (*env)->DeleteLocalRef(env, pfobj);
                }
                (*env)->SetObjectArrayElement(env, arcs, ai, arci);
                (*env)->DeleteLocalRef(env, arci);
            }
        }

        float x0 = info->x0, y0 = info->y0, x1 = info->x1, y1 = info->y1;
        if (x0 > x1) { float t = x0; x0 = x1; x1 = t; }
        if (y0 > y1) { float t = y0; y0 = y1; y1 = t; }

            //Create the annotation
        if(Annotation != NULL)
        {
            jannot = (*env)->NewObject(env, annotClass, Annotation, x0, y0, x1, y1, type, arcs, jtext, (jlong)info->object_id); 
        }
            
        if (jannot == NULL) { arr = NULL; goto cleanup; }
        (*env)->SetObjectArrayElement(env, arr, idx, jannot);

            //Clean up
        (*env)->DeleteLocalRef(env, jannot);
        if (jtext != NULL) (*env)->DeleteLocalRef(env, jtext);
        if (arcs != NULL) (*env)->DeleteLocalRef(env, arcs);
        
    }

cleanup:
    if (list != NULL)
        pp_pdf_drop_annot_list_mupdf(ctx, list);
    return arr;
}
