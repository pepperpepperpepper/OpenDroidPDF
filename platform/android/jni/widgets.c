#include "mupdf_native.h"
#include "pp_core.h"

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_getWidgetAreasInternal)(JNIEnv * env, jobject thiz, int pageNumber)
{
    jclass rectFClass;
    jmethodID ctor;
    jobjectArray arr;
    jobject rectF;
	page_cache *pc;
	globals *glo = get_globals(env, thiz);
	pp_pdf_widget_list *list = NULL;
	int count = 0;
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

	if (pp_pdf_list_widgets_mupdf(ctx, glo->doc, pc->page, pageNumber, pc->width, pc->height, &list) && list)
		count = list->count;

	arr = (*env)->NewObjectArray(env, count, rectFClass, NULL);
	if (arr == NULL)
	{
		if (list)
			pp_pdf_drop_widget_list_mupdf(ctx, list);
		return NULL;
	}

	for (int i = 0; i < count; i++)
	{
		pp_rect r = list->items[i].bounds;
		rectF = (*env)->NewObject(env, rectFClass, ctor, r.x0, r.y0, r.x1, r.y1);
		if (rectF == NULL)
		{
			pp_pdf_drop_widget_list_mupdf(ctx, list);
			return NULL;
		}
		(*env)->SetObjectArrayElement(env, arr, i, rectF);
		(*env)->DeleteLocalRef(env, rectF);
	}

	if (list)
		pp_pdf_drop_widget_list_mupdf(ctx, list);

	return arr;
}

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_passClickEventInternal)(JNIEnv * env, jobject thiz, int pageNumber, float x, float y)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return 0;
    fz_context *ctx = glo->ctx;
    page_cache *pc;

    JNI_FN(MuPDFCore_gotoPageInternal)(env, thiz, pageNumber);
    pc = &glo->pages[glo->current];
    if (pc->page == NULL || pc->number != pageNumber)
        return 0;
	int changed = pp_pdf_widget_click_mupdf(ctx, glo->doc, pc->page, pageNumber, pc->width, pc->height, x, y, (void **)&glo->focus_widget);
	if (glo->focus_widget)
		glo->focus_widget_page = pageNumber;
	else
		glo->focus_widget_page = -1;
	return changed;
}

JNIEXPORT jstring JNICALL
JNI_FN(MuPDFCore_getFocusedWidgetTextInternal)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->focus_widget == NULL)
        return (*env)->NewStringUTF(env, "");
    fz_context *ctx = glo->ctx;
	char *val = NULL;
	jstring out = NULL;
	if (!pp_pdf_widget_get_value_utf8_mupdf(ctx, glo->doc, glo->focus_widget, &val) || !val)
		return (*env)->NewStringUTF(env, "");
	out = (*env)->NewStringUTF(env, val);
	pp_free_string_mupdf(ctx, val);
	return out ? out : (*env)->NewStringUTF(env, "");
}

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_setFocusedWidgetTextInternal)(JNIEnv * env, jobject thiz, jstring jtext)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->focus_widget == NULL)
        return 0;
    fz_context *ctx = glo->ctx;
    const char *text = (*env)->GetStringUTFChars(env, jtext, NULL);
    page_cache *pc = &glo->pages[glo->current];
    int rc = pp_pdf_widget_set_text_utf8_mupdf(ctx, glo->doc, pc->page, pc->number, glo->focus_widget, text ? text : "");
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
    pp_string_list *list = NULL;
    if (!pp_pdf_widget_choice_options_mupdf(ctx, glo->doc, glo->focus_widget, &list) || !list)
        return NULL;

    jclass cls = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arr = (*env)->NewObjectArray(env, list->count, cls, NULL);
    for (int i = 0; i < list->count; i++)
    {
        jstring s = (*env)->NewStringUTF(env, list->items[i] ? list->items[i] : "");
        (*env)->SetObjectArrayElement(env, arr, i, s);
        (*env)->DeleteLocalRef(env, s);
    }
    pp_drop_string_list_mupdf(ctx, list);
    return arr;
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_getFocusedWidgetChoiceSelected)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->focus_widget == NULL)
        return NULL;
    fz_context *ctx = glo->ctx;
    pp_string_list *list = NULL;
    if (!pp_pdf_widget_choice_selected_mupdf(ctx, glo->doc, glo->focus_widget, &list) || !list)
        return NULL;

    jclass cls = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arr = (*env)->NewObjectArray(env, list->count, cls, NULL);
    for (int i = 0; i < list->count; i++)
    {
        jstring s = (*env)->NewStringUTF(env, list->items[i] ? list->items[i] : "");
        (*env)->SetObjectArrayElement(env, arr, i, s);
        (*env)->DeleteLocalRef(env, s);
    }
    pp_drop_string_list_mupdf(ctx, list);
	return arr;
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_getFocusedWidgetChoiceMultiSelectInternal)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL || glo->focus_widget == NULL)
		return JNI_FALSE;
	fz_context *ctx = glo->ctx;

	int multi = 0;
	fz_try(ctx)
	{
		multi = pdf_choice_widget_is_multiselect(ctx, glo->focus_widget);
	}
	fz_catch(ctx)
	{
		multi = 0;
	}
	return multi ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_getFocusedWidgetChoiceEditableInternal)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL || glo->focus_widget == NULL)
		return JNI_FALSE;
	fz_context *ctx = glo->ctx;

	pdf_annot *annot = glo->focus_widget;
	int editable = 0;
	fz_try(ctx)
	{
		enum pdf_widget_type type = pdf_widget_type(ctx, annot);
		if (type == PDF_WIDGET_TYPE_COMBOBOX)
		{
			int flags = pdf_field_flags(ctx, annot->obj);
			editable = (flags & PDF_CH_FIELD_IS_EDIT) != 0;
		}
	}
	fz_catch(ctx)
	{
		editable = 0;
	}
	return editable ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_setFocusedWidgetChoiceSelectedInternal)(JNIEnv * env, jobject thiz, jobjectArray arr)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->focus_widget == NULL)
        return;
    fz_context *ctx = glo->ctx;
    if (glo->focus_widget_page >= 0)
        JNI_FN(MuPDFCore_gotoPageInternal)(env, thiz, glo->focus_widget_page);
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
    page_cache *pc = &glo->pages[glo->current];
    (void)pp_pdf_widget_choice_set_selected_mupdf(ctx, glo->doc, pc->page, pc->number, glo->focus_widget, n, vals);
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

	switch (pp_pdf_widget_type_mupdf(ctx, glo->focus_widget))
	{
	case PDF_WIDGET_TYPE_TEXT: return TEXT;
	case PDF_WIDGET_TYPE_LISTBOX: return LISTBOX;
	case PDF_WIDGET_TYPE_COMBOBOX: return COMBOBOX;
	case PDF_WIDGET_TYPE_SIGNATURE: return SIGNATURE;
	default: break;
	}

	return NONE;
}
