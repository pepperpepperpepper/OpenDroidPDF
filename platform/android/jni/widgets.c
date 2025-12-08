#include "mupdf_native.h"

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
