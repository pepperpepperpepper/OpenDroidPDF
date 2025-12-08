#include "mupdf_native.h"

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
    pdf_document *idoc = pdf_specifics(ctx, glo->doc);

    return (idoc && pdf_has_unsaved_changes(ctx, idoc)) ? JNI_TRUE : JNI_FALSE;
}

static char *tmp_path(JNIEnv * env, const char *path)
{
    (void)env;
    const int rnd_length = 6;
    char *rnd = malloc(sizeof(char) * (rnd_length + 1));
    if (!rnd)
        return NULL;
    for (int i = 0; i < rnd_length; i++)
        rnd[i] = "0123456789abcdef"[random() % 16];
    rnd[rnd_length] = '\0';

    size_t buf_len = strlen(path) + 1 + rnd_length + 4 + 1; /* _ + rnd + .pdf + NUL */
    char *buf = malloc(buf_len);
    if (!buf)
    {
        free(rnd);
        return NULL;
    }

    strcpy(buf, path);
    strcat(buf, "_");
    strcat(buf, rnd);
    strcat(buf, ".pdf");

    free(rnd);
    return buf;
}

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_saveAsInternal)(JNIEnv *env, jobject thiz, jstring jpath)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL || glo->doc == NULL)
        return 0;
    fz_context *ctx = glo->ctx;
    pdf_document *idoc = pdf_specifics(ctx, glo->doc);
    const char *new_path = NULL;
    if (jpath != NULL)
        new_path = (*env)->GetStringUTFChars(env, jpath, NULL);

    const char *target_path = new_path ? new_path : glo->current_path;
    if (target_path == NULL)
    {
        if (new_path) (*env)->ReleaseStringUTFChars(env, jpath, new_path);
        return 0;
    }

    int ok = 0;
    char *tmp = tmp_path(env, target_path);
    if (!tmp)
    {
        if (new_path) (*env)->ReleaseStringUTFChars(env, jpath, new_path);
        return 0;
    }

    fz_document_writer *wri = NULL;
    char opts[256];
    opts[0] = 0;
    if (glo->current_path && new_path && strcmp(glo->current_path, new_path) == 0)
        fz_strlcpy(opts, "incremental=yes", sizeof(opts));

    fz_try(ctx)
    {
        if (idoc)
        {
            pdf_write_options save_opts;
            pdf_parse_write_options(ctx, &save_opts, opts[0] ? opts : NULL);
            pdf_save_document(ctx, idoc, tmp, &save_opts);
        }
        else
        {
            wri = fz_new_pdf_writer(ctx, tmp, opts[0] ? opts : NULL);
            fz_write_document(ctx, wri, glo->doc);
            fz_close_document_writer(ctx, wri);
        }
        ok = 1;
    }
    fz_always(ctx)
    {
        if (wri) fz_drop_document_writer(ctx, wri);
    }
    fz_catch(ctx)
    {
        LOGE("saveAs failed: %s", fz_caught_message(ctx));
        ok = 0;
    }

    if (ok)
    {
        if (rename(tmp, target_path) == 0)
        {
            if (target_path != glo->current_path)
            {
                fz_free(ctx, glo->current_path);
                glo->current_path = fz_strdup(ctx, target_path);
            }
        }
        else
        {
            LOGE("rename(%s -> %s) failed", tmp, target_path);
            ok = 0;
        }
    }
    free(tmp);
    if (new_path) (*env)->ReleaseStringUTFChars(env, jpath, new_path);
    return ok;
}
