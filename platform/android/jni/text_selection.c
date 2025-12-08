#include "mupdf_native.h"

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
