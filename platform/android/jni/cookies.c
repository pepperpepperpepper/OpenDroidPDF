#include "mupdf_native.h"

JNIEXPORT jlong JNICALL
JNI_FN(MuPDFCore_createCookie)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals_any_thread(env, thiz);
    if (glo == NULL)
        return 0;
    fz_context *ctx = glo->ctx;

    return (jlong) (intptr_t) fz_calloc_no_throw(ctx, 1, sizeof(fz_cookie));
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_destroyCookie)(JNIEnv * env, jobject thiz, jlong cookiePtr)
{
    fz_cookie *cookie = (fz_cookie *) (intptr_t) cookiePtr;
    globals *glo = get_globals_any_thread(env, thiz);
    if (glo == NULL)
        return;
    fz_context *ctx = glo->ctx;
    if (ctx == NULL)
        return;

    fz_free(ctx, cookie);
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_abortCookie)(JNIEnv * env, jobject thiz, jlong cookiePtr)
{
    fz_cookie *cookie = (fz_cookie *) (intptr_t) cookiePtr;
    if (cookie != NULL)
        cookie->abort = 1;
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_cookieAborted)(JNIEnv * env, jobject thiz, jlong cookiePtr)
{
    fz_cookie *cookie = (fz_cookie *) (intptr_t) cookiePtr;
    if (cookie == NULL || cookie->abort == 1)
        return true;
    else
        return false;
}
