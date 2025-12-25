#ifndef PP_CORE_H
#define PP_CORE_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct pp_ctx pp_ctx;
typedef struct pp_doc pp_doc;
typedef struct pp_cookie pp_cookie;

pp_ctx *pp_new(void);
void pp_drop(pp_ctx *ctx);

pp_doc *pp_open(pp_ctx *ctx, const char *path);
void pp_close(pp_ctx *ctx, pp_doc *doc);

const char *pp_format(pp_ctx *ctx, pp_doc *doc);

int pp_count_pages(pp_ctx *ctx, pp_doc *doc);
int pp_page_size(pp_ctx *ctx, pp_doc *doc, int page_index, float *out_w, float *out_h);

int pp_render_page_rgba(pp_ctx *ctx, pp_doc *doc, int page_index, int out_w, int out_h, unsigned char *rgba);

/* Abort/cancel cookie. The underlying implementation is MuPDF's fz_cookie, but we keep it opaque here. */
pp_cookie *pp_cookie_new(pp_ctx *ctx);
void pp_cookie_drop(pp_ctx *ctx, pp_cookie *cookie);
void pp_cookie_reset(pp_cookie *cookie);
void pp_cookie_abort(pp_cookie *cookie);
int pp_cookie_aborted(pp_cookie *cookie);

/* Low-level cookie helpers for platforms that already own MuPDF's fz_context. */
pp_cookie *pp_cookie_new_mupdf(void *mupdf_ctx);
void pp_cookie_drop_mupdf(void *mupdf_ctx, pp_cookie *cookie);

/* Patch rendering (Android-style): render a visible patch region into a caller RGBA buffer.
 *
 * pageW/pageH: full page pixel size (target scale).
 * patchX/patchY/patchW/patchH: patch rectangle in that pixel space.
 * rgba/stride: output buffer (stride in bytes per row). The buffer's row width is inferred as stride/4.
 */
int pp_render_patch_rgba(pp_ctx *ctx, pp_doc *doc, int page_index,
                         int pageW, int pageH,
                         int patchX, int patchY, int patchW, int patchH,
                         unsigned char *rgba, int stride, pp_cookie *cookie);

/* Low-level entry point for platforms that already own the MuPDF context/document.
 * The ctx/doc pointers are expected to be MuPDF's fz_context/fz_document pointers.
 */
int pp_render_patch_rgba_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                              int pageW, int pageH,
                              int patchX, int patchY, int patchW, int patchH,
                              unsigned char *rgba, int stride, pp_cookie *cookie);

#ifdef __cplusplus
}
#endif

#endif
