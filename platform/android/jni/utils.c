#include "mupdf_native.h"

jfieldID global_fid = NULL;
jfieldID buffer_fid = NULL;

void drop_changed_rects(fz_context *ctx, rect_node **nodePtr)
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

void drop_page_cache(globals *glo, page_cache *pc)
{
    fz_context *ctx = glo->ctx;

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

void dump_annotation_display_lists(globals *glo)
{
    fz_context *ctx = glo->ctx;
    for (int i = 0; i < NUM_CACHE; i++)
    {
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

void alerts_init(globals *glo)
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

void alerts_fin(globals *glo)
{
    fz_context *ctx = glo->ctx;
    pdf_document *idoc = pdf_specifics(ctx, glo->doc);
    if (!glo->alerts_initialised)
        return;

    LOGT("Enter alerts_fin");
    if (idoc)
        pdf_set_doc_event_callback(ctx, idoc, NULL, NULL, NULL);

    pthread_mutex_lock(&glo->alert_lock);
    glo->current_alert = NULL;
    glo->alerts_active = 0;
    pthread_cond_signal(&glo->alert_request_cond);
    pthread_cond_signal(&glo->alert_reply_cond);
    pthread_mutex_unlock(&glo->alert_lock);

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

globals *get_globals(JNIEnv *env, jobject thiz)
{
    if (global_fid == NULL)
    {
        jclass clazz = (*env)->GetObjectClass(env, thiz);
        global_fid = (*env)->GetFieldID(env, clazz, "globals", "J");
    }

    globals *glo = (globals *)(intptr_t)((*env)->GetLongField(env, thiz, global_fid));
    if (glo != NULL)
    {
        glo->env = env;
        glo->thiz = thiz;
    }
    return glo;
}

globals *get_globals_any_thread(JNIEnv *env, jobject thiz)
{
    return (globals *)(intptr_t)((*env)->GetLongField(env, thiz, global_fid));
}

void init_annotation_defaults(globals *glo)
{
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
}

void close_doc(globals *glo)
{
    if (!glo || !glo->ctx)
        return;

    fz_free(glo->ctx, glo->hit_bbox);
    glo->hit_bbox = NULL;

    for (int i = 0; i < NUM_CACHE; i++)
        drop_page_cache(glo, &glo->pages[i]);

    alerts_fin(glo);

    if (glo->doc)
    {
        fz_drop_document(glo->ctx, glo->doc);
        glo->doc = NULL;
    }
}

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void)vm;
    (void)reserved;
    srand(time(NULL));
    return JNI_VERSION_1_2;
}
