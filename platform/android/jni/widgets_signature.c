#include "mupdf_native.h"

/* This enum mirrors SignatureState in MuPDFPageView.java */
enum
{
    Signature_NoSupport,
    Signature_Unsigned,
    Signature_Signed
};

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_getFocusedWidgetSignatureState)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL)
        return Signature_NoSupport;
    fz_context *ctx = glo->ctx;
    if (glo->focus_widget == NULL)
        return Signature_NoSupport;
    if (pdf_widget_type(ctx, glo->focus_widget) != PDF_WIDGET_TYPE_SIGNATURE)
        return Signature_NoSupport;
    return pdf_widget_is_signed(ctx, glo->focus_widget) ? Signature_Signed : Signature_Unsigned;
}

JNIEXPORT jstring JNICALL
JNI_FN(MuPDFCore_checkFocusedSignatureInternal)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL)
        return (*env)->NewStringUTF(env, "No document loaded");

    fz_context *ctx = glo->ctx;
    char message[512] = "Signature check failed";
    pdf_pkcs7_verifier *verifier = NULL;
    pdf_pkcs7_distinguished_name *dn = NULL;
    char *dn_string = NULL;

    fz_var(verifier);
    fz_var(dn);
    fz_var(dn_string);

    fz_try(ctx)
    {
        pdf_annot *widget = glo->focus_widget;

        if (widget == NULL || pdf_widget_type(ctx, widget) != PDF_WIDGET_TYPE_SIGNATURE)
        {
            fz_strlcpy(message, "No signature widget selected", sizeof(message));
            goto signature_done;
        }

        if (!pdf_widget_is_signed(ctx, widget))
        {
            fz_strlcpy(message, "Signature field is not signed", sizeof(message));
            goto signature_done;
        }

        verifier = pkcs7_openssl_new_verifier(ctx);
        if (verifier == NULL)
            fz_throw(ctx, FZ_ERROR_GENERIC, "Unable to create PKCS7 verifier");

        pdf_signature_error digest_err = pdf_check_widget_digest(ctx, verifier, widget);
        if (digest_err != PDF_SIGNATURE_ERROR_OKAY)
        {
            const char *desc = pdf_signature_error_description(digest_err);
            fz_strlcpy(message, desc ? desc : "Signature digest verification failed", sizeof(message));
            goto signature_done;
        }

        pdf_signature_error cert_err = pdf_check_widget_certificate(ctx, verifier, widget);
        if (cert_err != PDF_SIGNATURE_ERROR_OKAY)
        {
            const char *desc = pdf_signature_error_description(cert_err);
            fz_strlcpy(message, desc ? desc : "Signature certificate verification failed", sizeof(message));
            goto signature_done;
        }

        dn = pdf_signature_get_widget_signatory(ctx, verifier, widget);
        if (dn)
            dn_string = pdf_signature_format_distinguished_name(ctx, dn);

        if (dn_string && dn_string[0] != '\0')
            snprintf(message, sizeof(message), "Signature is valid\nSigned by: %s", dn_string);
        else
            fz_strlcpy(message, "Signature is valid", sizeof(message));
signature_done:
        ;
    }
    fz_always(ctx)
    {
        if (dn)
            pdf_signature_drop_distinguished_name(ctx, dn);
        if (dn_string)
            fz_free(ctx, dn_string);
        if (verifier)
            pdf_drop_verifier(ctx, verifier);
    }
    fz_catch(ctx)
    {
        const char *err = fz_caught_message(ctx);
        fz_strlcpy(message, err ? err : "Signature check failed", sizeof(message));
    }

    return (*env)->NewStringUTF(env, message);
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_signFocusedSignatureInternal)(JNIEnv * env, jobject thiz, jstring jkeyfile, jstring jpassword)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL)
        return JNI_FALSE;

    fz_context *ctx = glo->ctx;
    pdf_annot *widget = glo->focus_widget;

    if (widget == NULL || pdf_widget_type(ctx, widget) != PDF_WIDGET_TYPE_SIGNATURE)
        return JNI_FALSE;

    const char *keyfile = NULL;
    const char *password = NULL;
    pdf_pkcs7_signer *signer = NULL;
    jboolean result = JNI_FALSE;

    fz_var(signer);

    keyfile = (*env)->GetStringUTFChars(env, jkeyfile, NULL);
    password = (*env)->GetStringUTFChars(env, jpassword, NULL);

    if (keyfile == NULL || password == NULL)
        goto cleanup;

    fz_try(ctx)
    {
        signer = pkcs7_openssl_read_pfx(ctx, keyfile, password);
        if (signer == NULL)
            fz_throw(ctx, FZ_ERROR_GENERIC, "Unable to read PKCS#12 key file");

        pdf_sign_signature(ctx, widget, signer, PDF_SIGNATURE_DEFAULT_APPEARANCE, NULL, NULL, NULL);

        page_cache *pc = &glo->pages[glo->current];
        if (pc->page)
            pdf_update_page(ctx, (pdf_page*)pc->page);
        dump_annotation_display_lists(glo);

        result = JNI_TRUE;
    }
    fz_always(ctx)
    {
        if (signer)
            pdf_drop_signer(ctx, signer);
    }
    fz_catch(ctx)
    {
        LOGE("signFocusedSignature: %s", fz_caught_message(ctx));
        result = JNI_FALSE;
    }

cleanup:
    if (password)
        (*env)->ReleaseStringUTFChars(env, jpassword, password);
    if (keyfile)
        (*env)->ReleaseStringUTFChars(env, jkeyfile, keyfile);

    return result;
}
