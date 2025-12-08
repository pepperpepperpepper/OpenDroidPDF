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
