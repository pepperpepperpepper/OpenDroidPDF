#include "mupdf_native.h"
#include "pp_core.h"


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
    pp_point *pts = NULL;
    int *counts = NULL;
    int total = 0;
    float color[3];

    if (idoc == NULL)
    {
        LOGE("addInkAnnotation: document is not a PDF");
        jclass cls = (*env)->FindClass(env, "java/lang/IllegalStateException");
        if (cls != NULL)
            (*env)->ThrowNew(env, cls, "Document is not a PDF; cannot add ink annotation");
        (*env)->DeleteLocalRef(env, cls);
        return;
    }

    color[0] = glo->inkColor[0];
    color[1] = glo->inkColor[1];
    color[2] = glo->inkColor[2];

    fz_var(pts);
    fz_var(counts);
    fz_try(ctx)
    {
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

        pts = fz_malloc_array(ctx, total, pp_point);

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
                k++;
            }
            (*env)->DeleteLocalRef(env, arc);
        }

        float thickness;

        thickness = glo->inkThickness;
        if (thickness <= 0.0f)
            thickness = INK_THICKNESS;

        if (!pp_pdf_add_ink_annot_mupdf(ctx, doc, pc->page, pc->number,
                                       pc->width, pc->height,
                                       n, counts,
                                       pts, total,
                                       color, thickness,
                                       NULL))
        {
            fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_add_ink_annot failed");
        }

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
