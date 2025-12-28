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

typedef struct pp_rect
{
	float x0;
	float y0;
	float x1;
	float y1;
} pp_rect;

pp_ctx *pp_new(void);
void pp_drop(pp_ctx *ctx);

pp_doc *pp_open(pp_ctx *ctx, const char *path);
void pp_close(pp_ctx *ctx, pp_doc *doc);

const char *pp_format(pp_ctx *ctx, pp_doc *doc);

int pp_count_pages(pp_ctx *ctx, pp_doc *doc);
int pp_page_size(pp_ctx *ctx, pp_doc *doc, int page_index, float *out_w, float *out_h);

int pp_render_page_rgba(pp_ctx *ctx, pp_doc *doc, int page_index, int out_w, int out_h, unsigned char *rgba);

/* Free a string returned by pp_core APIs (allocated with MuPDF's allocator). */
void pp_free_string(pp_ctx *ctx, char *s);
void pp_free_string_mupdf(void *mupdf_ctx, char *s);

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

/* Extract plain text (UTF-8) for a page. Caller must free the returned string with pp_free_string*. */
int pp_page_text_utf8(pp_ctx *ctx, pp_doc *doc, int page_index, char **out_text_utf8);
int pp_page_text_utf8_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index, char **out_text_utf8);

/* Search for a UTF-8 needle on a page and return up to hit_max bounding rectangles.
 *
 * Output coordinates are in page pixel space (see pp_render_patch_rgba*).
 *
 * Returns the number of hits found (0..hit_max) or -1 on error.
 */
int pp_search_page(pp_ctx *ctx, pp_doc *doc, int page_index,
                   int pageW, int pageH,
                   const char *needle,
                   pp_rect *hit_rects, int hit_max);

int pp_search_page_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                         int pageW, int pageH,
                         const char *needle,
                         pp_rect *hit_rects, int hit_max);

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
int pp_pdf_update_annot_contents_by_object_id(pp_ctx *ctx, pp_doc *doc, int page_index, long long object_id, const char *contents_utf8);
int pp_pdf_update_annot_contents_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index, long long object_id, const char *contents_utf8);
int pp_pdf_update_annot_rect_by_object_id(pp_ctx *ctx, pp_doc *doc, int page_index,
                                         int pageW, int pageH,
                                         long long object_id,
                                         float x0, float y0, float x1, float y1);
int pp_pdf_update_annot_rect_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                              int pageW, int pageH,
                                              long long object_id,
                                              float x0, float y0, float x1, float y1);
int pp_pdf_update_freetext_style_by_object_id(pp_ctx *ctx, pp_doc *doc, int page_index,
                                             long long object_id,
                                             float font_size,
                                             const float color_rgb[3]);
int pp_pdf_update_freetext_style_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                                   long long object_id,
                                                   float font_size,
                                                   const float color_rgb[3]);

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

typedef struct pp_string_list
{
	int count;
	char **items;
} pp_string_list;

typedef struct pp_pdf_widget_info
{
	pp_rect bounds; /* page pixel space */
	int type; /* MuPDF enum pdf_widget_type */
	char *name_utf8; /* owned by the list (NULL when unavailable) */
} pp_pdf_widget_info;

typedef struct pp_pdf_widget_list
{
	int count;
	pp_pdf_widget_info *items;
} pp_pdf_widget_list;

typedef struct pp_pdf_alerts pp_pdf_alerts;

typedef struct pp_pdf_alert
{
	char *title_utf8;
	char *message_utf8;
	int icon_type;
	int button_group_type;
	int button_pressed;
} pp_pdf_alert;

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

/* Enumerate PDF widgets for a page (bounds/type/field name). */
int pp_pdf_list_widgets(pp_ctx *ctx, pp_doc *doc, int page_index,
                        int pageW, int pageH,
                        pp_pdf_widget_list **out_list);
void pp_pdf_drop_widget_list(pp_ctx *ctx, pp_pdf_widget_list *list);

int pp_pdf_list_widgets_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                              int pageW, int pageH,
                              pp_pdf_widget_list **out_list);
void pp_pdf_drop_widget_list_mupdf(void *mupdf_ctx, pp_pdf_widget_list *list);

/* Widget interaction helpers (PDF-only; return 0 for non-PDF docs/widgets). */
int pp_pdf_widget_click_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                              int pageW, int pageH,
                              float x, float y,
                              void **inout_focus_widget);
int pp_pdf_widget_type_mupdf(void *mupdf_ctx, void *mupdf_widget);

int pp_pdf_widget_get_value_utf8(pp_ctx *ctx, pp_doc *doc, int page_index, int widget_index, char **out_value_utf8);
int pp_pdf_widget_set_text_utf8(pp_ctx *ctx, pp_doc *doc, int page_index, int widget_index, const char *value_utf8);

int pp_pdf_widget_get_value_utf8_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_widget, char **out_value_utf8);
int pp_pdf_widget_set_text_utf8_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                      void *mupdf_widget, const char *value_utf8);

int pp_pdf_widget_choice_options_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_widget, pp_string_list **out_list);
int pp_pdf_widget_choice_selected_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_widget, pp_string_list **out_list);
int pp_pdf_widget_choice_set_selected_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                            void *mupdf_widget, int n, const char *values[]);

void pp_drop_string_list(pp_ctx *ctx, pp_string_list *list);
void pp_drop_string_list_mupdf(void *mupdf_ctx, pp_string_list *list);

/* PDF JS alert loop. This is a PDF-only concept; alerts_new returns NULL for non-PDF docs. */
pp_pdf_alerts *pp_pdf_alerts_new_mupdf(void *mupdf_ctx, void *mupdf_doc);
void pp_pdf_alerts_drop(pp_pdf_alerts *alerts);
int pp_pdf_alerts_start(pp_pdf_alerts *alerts);
void pp_pdf_alerts_stop(pp_pdf_alerts *alerts);
int pp_pdf_alerts_wait(pp_pdf_alerts *alerts, pp_pdf_alert *out_alert);
void pp_pdf_alerts_reply(pp_pdf_alerts *alerts, int button_pressed);
void pp_pdf_alert_free_mupdf(void *mupdf_ctx, pp_pdf_alert *alert);

int pp_pdf_save_as(pp_ctx *ctx, pp_doc *doc, const char *path);
int pp_pdf_save_as_mupdf(void *mupdf_ctx, void *mupdf_doc, const char *path);

/* Return 1 if the document is a PDF and has unsaved changes, otherwise 0. */
int pp_pdf_has_unsaved_changes(pp_ctx *ctx, pp_doc *doc);
int pp_pdf_has_unsaved_changes_mupdf(void *mupdf_ctx, void *mupdf_doc);

/* Export as PDF without flattening:
 * - If the source is a PDF: save it (optionally incremental).
 * - Otherwise: write it through a PDF writer (best-effort conversion).
 *
 * The write is atomic-ish: write to a sibling temp file then rename over the target.
 */
int pp_export_pdf(pp_ctx *ctx, pp_doc *doc, const char *path, int incremental);
int pp_export_pdf_mupdf(void *mupdf_ctx, void *mupdf_doc, const char *path, int incremental);

/* Flatten export: render pages (including annots/widgets) into a new PDF. */
int pp_export_flattened_pdf(pp_ctx *ctx, pp_doc *doc, const char *path, int dpi);
int pp_export_flattened_pdf_mupdf(void *mupdf_ctx, void *mupdf_doc, const char *path, int dpi);

#ifdef __cplusplus
}
#endif

#endif
