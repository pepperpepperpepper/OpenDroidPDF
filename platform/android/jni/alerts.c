#include "mupdf_native.h"

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
