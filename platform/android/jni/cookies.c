#include "mupdf_native.h"
#include "pp_core.h"

JNIEXPORT jlong JNICALL
JNI_FN(MuPDFCore_createCookie)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals_any_thread(env, thiz);
    if (glo == NULL)
        return 0;
    return (jlong) (intptr_t) pp_cookie_new_mupdf(glo->ctx);
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_destroyCookie)(JNIEnv * env, jobject thiz, jlong cookiePtr)
{
    pp_cookie *cookie = (pp_cookie *) (intptr_t) cookiePtr;
    globals *glo = get_globals_any_thread(env, thiz);
    if (glo == NULL)
        return;
    if (glo->ctx == NULL)
        return;
    pp_cookie_drop_mupdf(glo->ctx, cookie);
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_abortCookie)(JNIEnv * env, jobject thiz, jlong cookiePtr)
{
    (void)env;
    (void)thiz;
    pp_cookie_abort((pp_cookie *)(intptr_t)cookiePtr);
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_cookieAborted)(JNIEnv * env, jobject thiz, jlong cookiePtr)
{
    (void)env;
    (void)thiz;
    return pp_cookie_aborted((pp_cookie *)(intptr_t)cookiePtr) ? JNI_TRUE : JNI_FALSE;
}
