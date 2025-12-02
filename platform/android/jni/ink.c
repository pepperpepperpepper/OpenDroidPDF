#include "mupdf_native.h"

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
