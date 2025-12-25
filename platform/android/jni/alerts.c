#include "mupdf_native.h"
#include "pp_core.h"

void alerts_init(globals *glo)
{
    if (!glo || !glo->ctx || !glo->doc)
        return;
    if (glo->alerts)
        return;

    /* PDF-only: returns NULL for non-PDF docs. */
    glo->alerts = pp_pdf_alerts_new_mupdf(glo->ctx, glo->doc);
}

void alerts_fin(globals *glo)
{
    if (!glo || !glo->alerts)
        return;
    pp_pdf_alerts_drop(glo->alerts);
    glo->alerts = NULL;
}

JNIEXPORT jobject JNICALL
JNI_FN(MuPDFCore_waitForAlertInternal)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals(env, thiz);
    jclass alertClass;
    jmethodID ctor;
    jstring title;
    jstring message;
    jobject obj;
    pp_pdf_alert alert;

    if (!glo || !glo->alerts)
        return NULL;

    if (!pp_pdf_alerts_wait(glo->alerts, &alert))
        return NULL;

    alertClass = (*env)->FindClass(env, PACKAGENAME "/MuPDFAlertInternal");
    if (alertClass == NULL)
    {
        pp_pdf_alert_free_mupdf(glo->ctx, &alert);
        return NULL;
    }

    ctor = (*env)->GetMethodID(env, alertClass, "<init>", "(Ljava/lang/String;IILjava/lang/String;I)V");
    if (ctor == NULL)
    {
        pp_pdf_alert_free_mupdf(glo->ctx, &alert);
        return NULL;
    }

    title = (*env)->NewStringUTF(env, alert.title_utf8 ? alert.title_utf8 : "");
    if (title == NULL)
    {
        pp_pdf_alert_free_mupdf(glo->ctx, &alert);
        return NULL;
    }

    message = (*env)->NewStringUTF(env, alert.message_utf8 ? alert.message_utf8 : "");
    if (message == NULL)
    {
        (*env)->DeleteLocalRef(env, title);
        pp_pdf_alert_free_mupdf(glo->ctx, &alert);
        return NULL;
    }

    obj = (*env)->NewObject(env, alertClass, ctor,
                            message, alert.icon_type, alert.button_group_type, title, alert.button_pressed);

    (*env)->DeleteLocalRef(env, title);
    (*env)->DeleteLocalRef(env, message);
    pp_pdf_alert_free_mupdf(glo->ctx, &alert);
    return obj;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_replyToAlertInternal)(JNIEnv * env, jobject thiz, jobject alertObj)
{
    globals *glo = get_globals(env, thiz);
    jclass alertClass;
    jfieldID field;
    int button_pressed;

    if (!glo || !glo->alerts || !alertObj)
        return;

    alertClass = (*env)->FindClass(env, PACKAGENAME "/MuPDFAlertInternal");
    if (alertClass == NULL)
        return;

    field = (*env)->GetFieldID(env, alertClass, "buttonPressed", "I");
    if (field == NULL)
        return;

    button_pressed = (*env)->GetIntField(env, alertObj, field);
    pp_pdf_alerts_reply(glo->alerts, button_pressed);
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_startAlertsInternal)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals(env, thiz);
    if (!glo || !glo->alerts)
        return;
    (void)pp_pdf_alerts_start(glo->alerts);
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_stopAlertsInternal)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals(env, thiz);
    if (!glo || !glo->alerts)
        return;
    pp_pdf_alerts_stop(glo->alerts);
}

