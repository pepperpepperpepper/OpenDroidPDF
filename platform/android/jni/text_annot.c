#include "mupdf_native.h"
#include "pp_core.h"

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
                                    NULL))
            fz_throw(ctx, FZ_ERROR_GENERIC, "pp_pdf_add_annot failed");

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
