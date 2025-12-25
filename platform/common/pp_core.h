#ifndef PP_CORE_H
#define PP_CORE_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct pp_ctx pp_ctx;
typedef struct pp_doc pp_doc;
typedef struct pp_cookie pp_cookie;

typedef struct pp_point
{
	float x;
	float y;
} pp_point;

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

/* PDF annotation helpers.
 *
 * Coordinate convention (inputs):
 * - Points are specified in "page pixel" space: [0..pageW) x [0..pageH), origin at top-left, y increases down.
 * - This matches the coordinate space used by pp_render_patch_rgba*.
 *
 * Units:
 * - thickness is in PDF user units ("points", 1/72 inch), matching existing Android behavior.
 *
 * Outputs:
 * - object_id, when available, is (objnum<<32)|gen (matches Android undo/erase stable IDs).
 */
int pp_pdf_add_ink_annot(pp_ctx *ctx, pp_doc *doc, int page_index,
                         int pageW, int pageH,
                         int arc_count, const int *arc_counts,
                         const pp_point *points, int point_count,
                         const float color_rgb[3], float thickness,
                         long long *out_object_id);

int pp_pdf_add_ink_annot_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                              int pageW, int pageH,
                              int arc_count, const int *arc_counts,
                              const pp_point *points, int point_count,
                              const float color_rgb[3], float thickness,
                              long long *out_object_id);

int pp_pdf_delete_annot_by_object_id(pp_ctx *ctx, pp_doc *doc, int page_index, long long object_id);
int pp_pdf_delete_annot_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index, long long object_id);

/* Add a non-ink PDF annotation (markup/text/free-text).
 *
 * Supported annot_type values: PDF_ANNOT_{HIGHLIGHT,UNDERLINE,STRIKE_OUT,TEXT,FREE_TEXT}
 * (or the older FZ_ANNOT_* equivalents when built against older MuPDF).
 *
 * Coordinate convention (inputs):
 * - Points are specified in "page pixel" space: [0..pageW) x [0..pageH), origin at top-left, y increases down.
 * - This matches the coordinate space used by pp_render_patch_rgba*.
 *
 * Markup quads:
 * - For highlight/underline/strikeout, points must be a multiple of 4 (one quad per 4 points).
 * - Points are accepted in the same ordering used by the Android UI glue.
 *
 * Text:
 * - contents_utf8 may be NULL (treated as "").
 * - For TEXT, points must contain 2 points (rect corners).
 * - For FREE_TEXT, points may contain 2+ points; the bounding rect is used.
 *
 * Outputs:
 * - object_id, when available, is (objnum<<32)|gen (matches Android undo/erase stable IDs).
 */
int pp_pdf_add_annot(pp_ctx *ctx, pp_doc *doc, int page_index,
                     int pageW, int pageH,
                     int annot_type,
                     const pp_point *points, int point_count,
                     const float color_rgb[3], float opacity,
                     const char *contents_utf8,
                     long long *out_object_id);

int pp_pdf_add_annot_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                          int pageW, int pageH,
                          int annot_type,
                          const pp_point *points, int point_count,
                          const float color_rgb[3], float opacity,
                          const char *contents_utf8,
                          long long *out_object_id);

typedef struct pp_pdf_annot_arc
{
	int count;
	pp_point *points; /* page pixel space */
} pp_pdf_annot_arc;

typedef struct pp_pdf_annot_info
{
	int type; /* MuPDF pdf_annot_type */
	float x0, y0, x1, y1; /* bounds in page pixel space */
	long long object_id;
	char *contents_utf8; /* owned by the list (NULL when absent) */
	int arc_count;
	pp_pdf_annot_arc *arcs; /* owned by the list (NULL unless INK) */
} pp_pdf_annot_info;

typedef struct pp_pdf_annot_list
{
	int count;
	pp_pdf_annot_info *items;
} pp_pdf_annot_list;

/* Enumerate PDF annotations for a page (bounds/contents/ink arcs).
 *
 * Output coordinates are in page pixel space (see pp_pdf_add_annot).
 *
 * The returned list (and all nested allocations) must be freed with pp_pdf_drop_annot_list*.
 */
int pp_pdf_list_annots(pp_ctx *ctx, pp_doc *doc, int page_index,
                       int pageW, int pageH,
                       pp_pdf_annot_list **out_list);

void pp_pdf_drop_annot_list(pp_ctx *ctx, pp_pdf_annot_list *list);

int pp_pdf_list_annots_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                            int pageW, int pageH,
                            pp_pdf_annot_list **out_list);

void pp_pdf_drop_annot_list_mupdf(void *mupdf_ctx, pp_pdf_annot_list *list);

int pp_pdf_save_as(pp_ctx *ctx, pp_doc *doc, const char *path);

#ifdef __cplusplus
}
#endif

#endif
