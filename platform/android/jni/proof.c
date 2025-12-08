#include "mupdf_native.h"
#include <string.h>
#include <unistd.h>

#ifdef SUPPORT_GPROOF
static char *tmp_gproof_path(char *path)
{
    FILE *f;
    int i;
    char *buf = malloc(strlen(path) + 20 + 1);
    if (!buf)
        return NULL;

    for (i = 0; i < 10000; i++)
    {
        sprintf(buf, "%s.%d.gproof", path, i);

        LOGI("Trying for %s\n", buf);
        f = fopen(buf, "r");
        if (f != NULL)
        {
            fclose(f);
            continue;
        }

        f = fopen(buf, "w");
        if (f != NULL)
        {
            fclose(f);
            break;
        }
    }
    if (i == 10000)
    {
        LOGE("Failed to find temp gproof name");
        free(buf);
        return NULL;
    }

    LOGI("Rewritten to %s\n", buf);
    return buf;
}
#endif

JNIEXPORT jstring JNICALL
JNI_FN(MuPDFCore_startProofInternal)(JNIEnv * env, jobject thiz, int inResolution)
{
#ifdef SUPPORT_GPROOF
    globals *glo = get_globals(env, thiz);
    fz_context *ctx = glo->ctx;
    char *tmp;
    jstring ret;

    if (!glo->doc || !glo->current_path)
        return NULL;

    tmp = tmp_gproof_path(glo->current_path);
    if (!tmp)
        return NULL;

    int theResolution = PROOF_RESOLUTION;
    if (inResolution != 0)
        theResolution = inResolution;

    fz_try(ctx)
    {
        fz_write_gproof_file(ctx, glo->current_path, glo->doc, tmp, theResolution, "", "");

        LOGI("Creating %s\n", tmp);
        ret = (*env)->NewStringUTF(env, tmp);
    }
    fz_always(ctx)
    {
        free(tmp);
    }
    fz_catch(ctx)
    {
        ret = NULL;
    }
    return ret;
#else
    (void)env;
    (void)thiz;
    (void)inResolution;
    return NULL;
#endif
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_endProofInternal)(JNIEnv * env, jobject thiz, jstring jfilename)
{
#ifdef SUPPORT_GPROOF
    globals *glo = get_globals(env, thiz);
    const char *tmp;

    if (!glo->doc || !glo->current_path || jfilename == NULL)
        return;

    tmp = (*env)->GetStringUTFChars(env, jfilename, NULL);
    if (tmp)
    {
        LOGI("Deleting %s\n", tmp);

        unlink(tmp);
        (*env)->ReleaseStringUTFChars(env, jfilename, tmp);
    }
#else
    (void)env;
    (void)thiz;
    (void)jfilename;
#endif
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_gprfSupportedInternal)(JNIEnv * env)
{
#ifdef SUPPORT_GPROOF
    (void)env;
    return JNI_TRUE;
#else
    (void)env;
    return JNI_FALSE;
#endif
}
