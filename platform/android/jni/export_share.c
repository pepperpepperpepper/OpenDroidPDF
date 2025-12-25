#include "mupdf_native.h"
#include "pp_core.h"

#include <stdlib.h>
#include <string.h>
#include <stdio.h>

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_hasChangesInternal)(JNIEnv * env, jobject thiz)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL)
        return JNI_FALSE;
    fz_context *ctx = glo->ctx;
    return pp_pdf_has_unsaved_changes_mupdf(ctx, glo->doc) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_saveAsInternal)(JNIEnv *env, jobject thiz, jstring jpath)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->doc == NULL)
        return 0;
    fz_context *ctx = glo->ctx;
    const char *new_path = NULL;
    if (jpath != NULL)
        new_path = (*env)->GetStringUTFChars(env, jpath, NULL);

    const char *target_path = new_path ? new_path : glo->current_path;
    if (target_path == NULL)
    {
        if (new_path) (*env)->ReleaseStringUTFChars(env, jpath, new_path);
        return 0;
    }

    int incremental = (glo->current_path && new_path && strcmp(glo->current_path, new_path) == 0) ? 1 : 0;
    int ok = pp_export_pdf_mupdf(ctx, glo->doc, target_path, incremental);

    if (ok)
    {
        if (target_path != glo->current_path)
        {
            fz_free(ctx, glo->current_path);
            glo->current_path = fz_strdup(ctx, target_path);
        }
    }
    if (new_path) (*env)->ReleaseStringUTFChars(env, jpath, new_path);
    return ok;
}
