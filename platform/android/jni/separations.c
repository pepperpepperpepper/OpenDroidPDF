#include "mupdf_native.h"

static page_cache *find_cached_page(globals *glo, int page)
{
    for (int i = 0; i < NUM_CACHE; i++)
    {
        if (glo->pages[i].page != NULL && glo->pages[i].number == page)
            return &glo->pages[i];
    }
    return NULL;
}

JNIEXPORT int JNICALL
JNI_FN(MuPDFCore_getNumSepsOnPageInternal)(JNIEnv *env, jobject thiz, int page)
{
    globals *glo = get_globals(env, thiz);
    fz_context *ctx = glo->ctx;
    page_cache *pc = find_cached_page(glo, page);
    if (!pc)
        return 0;

    LOGI("Counting seps on page %d", page);

    fz_separations *seps = fz_page_separations(ctx, pc->page);
    int n = 0;
    if (seps)
    {
        n = fz_count_separations(ctx, seps);
        fz_drop_separations(ctx, seps);
    }
    return n;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_controlSepOnPageInternal)(JNIEnv *env, jobject thiz, int page, int sep, jboolean disable)
{
    globals *glo = get_globals(env, thiz);
    fz_context *ctx = glo->ctx;
    page_cache *pc = find_cached_page(glo, page);
    if (!pc)
        return;

    fz_separations *seps = fz_page_separations(ctx, pc->page);
    if (seps)
    {
        fz_set_separation_behavior(ctx, seps, sep, disable ? FZ_SEPARATION_DISABLED : FZ_SEPARATION_SPOT);
        fz_drop_separations(ctx, seps);
    }
}

JNIEXPORT jobject JNICALL
JNI_FN(MuPDFCore_getSepInternal)(JNIEnv *env, jobject thiz, int page, int sep)
{
    globals *glo = get_globals(env, thiz);
    fz_context *ctx = glo->ctx;
    page_cache *pc = find_cached_page(glo, page);
    if (!pc)
        return NULL;

    fz_separations *seps = fz_page_separations(ctx, pc->page);
    if (!seps)
        return NULL;
    const char *name = fz_separation_name(ctx, seps, sep);

    float rgb[3] = {0};
    fz_separation_equivalent(ctx, seps, sep, fz_device_rgb(ctx), rgb, NULL, fz_default_color_params);
    unsigned r = (unsigned)(rgb[0] < 0 ? 0 : rgb[0] > 1 ? 255 : rgb[0]*255.0f + 0.5f);
    unsigned g = (unsigned)(rgb[1] < 0 ? 0 : rgb[1] > 1 ? 255 : rgb[1]*255.0f + 0.5f);
    unsigned b = (unsigned)(rgb[2] < 0 ? 0 : rgb[2] > 1 ? 255 : rgb[2]*255.0f + 0.5f);
    unsigned int bgra = (r << 16) | (g << 8) | b | (0xFFu << 24);

    float cmykf[4] = {0};
    fz_separation_equivalent(ctx, seps, sep, fz_device_cmyk(ctx), cmykf, NULL, fz_default_color_params);
    unsigned C = (unsigned)(cmykf[0] < 0 ? 0 : cmykf[0] > 1 ? 255 : cmykf[0]*255.0f + 0.5f);
    unsigned M = (unsigned)(cmykf[1] < 0 ? 0 : cmykf[1] > 1 ? 255 : cmykf[1]*255.0f + 0.5f);
    unsigned Y = (unsigned)(cmykf[2] < 0 ? 0 : cmykf[2] > 1 ? 255 : cmykf[2]*255.0f + 0.5f);
    unsigned K = (unsigned)(cmykf[3] < 0 ? 0 : cmykf[3] > 1 ? 255 : cmykf[3]*255.0f + 0.5f);
    unsigned int cmyk = (C << 24) | (M << 16) | (Y << 8) | K;

    fz_drop_separations(ctx, seps);

    jobject jname = name ? (*env)->NewStringUTF(env, name) : NULL;
    jclass sepClass = (*env)->FindClass(env, PACKAGENAME "/Separation");
    if (sepClass == NULL)
        return NULL;

    jmethodID ctor = (*env)->GetMethodID(env, sepClass, "<init>", "(Ljava/lang/String;II)V");
    if (ctor == NULL)
        return NULL;

    return (*env)->NewObject(env, sepClass, ctor, jname, bgra, cmyk);
}
