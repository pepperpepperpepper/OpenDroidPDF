#ifndef PP_CORE_PDF_ANNOTS_INTERNAL_H
#define PP_CORE_PDF_ANNOTS_INTERNAL_H

#include <stdarg.h>
#include <stddef.h>

#include "pp_core_internal.h"

pdf_annot *pp_pdf_create_annot_compat(fz_context *ctx, pdf_document *doc, pdf_page *page, int annot_type);
void pp_pdf_delete_annot_compat(fz_context *ctx, pdf_document *doc, pdf_page *page, pdf_annot *annot);

void pp_pdf_update_annot_compat(fz_context *ctx, pdf_document *doc, pdf_annot *annot);
void pp_pdf_dirty_annot_compat(fz_context *ctx, pdf_document *doc, pdf_annot *annot);

pdf_annot *pp_pdf_first_annot_compat(fz_context *ctx, pdf_page *page);
pdf_annot *pp_pdf_next_annot_compat(fz_context *ctx, pdf_page *page, pdf_annot *annot);

pdf_obj *pp_pdf_annot_obj_compat(fz_context *ctx, pdf_annot *annot);
pdf_obj *pp_pdf_new_real_compat(fz_context *ctx, pdf_document *doc, float f);
pdf_obj *pp_pdf_new_rect_compat(fz_context *ctx, pdf_document *doc, fz_rect rect);
int pp_pdf_is_stream_compat(fz_context *ctx, pdf_document *doc, pdf_obj *ref, int num, int gen);
void pp_pdf_to_rect_compat(fz_context *ctx, pdf_obj *array, fz_rect *out);
fz_buffer *pp_pdf_load_stream_compat(fz_context *ctx, pdf_document *doc, pdf_obj *ref, int num, int gen);

void pp_fz_buffer_vprintf_compat(fz_context *ctx, fz_buffer *buf, const char *fmt, va_list args);
void pp_fz_buffer_printf_compat(fz_context *ctx, fz_buffer *buf, const char *fmt, ...);
void pp_fz_buffer_cat_compat(fz_context *ctx, fz_buffer *dst, fz_buffer *src);
void pp_fz_buffer_append_data_compat(fz_context *ctx, fz_buffer *buf, const void *data, size_t len);
size_t pp_fz_buffer_storage_compat(fz_context *ctx, fz_buffer *buf, unsigned char **data);

const unsigned char *pp_memmem_compat(const unsigned char *hay, size_t hay_len, const unsigned char *needle, size_t needle_len);

void pp_pdf_set_annot_contents_compat(fz_context *ctx, pdf_document *doc, pdf_annot *annot, const char *contents_utf8);
fz_rect pp_pdf_bound_annot_compat(fz_context *ctx, pdf_document *doc, pdf_page *page, pdf_annot *annot);
void pp_pdf_set_annot_rect_compat(fz_context *ctx, pdf_document *doc, pdf_annot *annot, fz_rect rect);

void pp_pdf_set_annot_color_opacity_dict(fz_context *ctx, pdf_document *doc, pdf_annot *annot, const float color[3], float opacity);
void pp_pdf_set_annot_interior_color_dict(fz_context *ctx, pdf_document *doc, pdf_annot *annot, const float color[3]);
void pp_pdf_set_annot_opacity_dict(fz_context *ctx, pdf_document *doc, pdf_annot *annot, float opacity);
void pp_pdf_set_annot_color_dict(fz_context *ctx, pdf_document *doc, pdf_annot *annot, const float color[3]);

void pp_pdf_set_markup_quadpoints_compat(fz_context *ctx, pdf_document *doc, pdf_annot *annot, const fz_point *pts, int pt_count);
fz_matrix pp_pdf_page_to_pdf_ctm_compat(fz_context *ctx, pdf_page *page);
long long pp_pdf_object_id_for_annot(fz_context *ctx, pdf_annot *annot);

void pp_pdf_capture_freetext_border_style_if_missing(fz_context *ctx, pdf_document *doc, pdf_annot *annot);
void pp_pdf_suppress_freetext_border_generation(fz_context *ctx, pdf_document *doc, pdf_annot *annot);
void pp_pdf_patch_freetext_background_appearance_if_needed(fz_context *ctx, pdf_document *doc, pdf_annot *annot);

#endif
