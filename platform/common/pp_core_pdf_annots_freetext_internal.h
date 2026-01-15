#ifndef PP_CORE_PDF_ANNOTS_FREETEXT_INTERNAL_H
#define PP_CORE_PDF_ANNOTS_FREETEXT_INTERNAL_H

#include <stddef.h>

#include "pp_core_pdf_annots_internal.h"

/* FreeText rich text style flags (shared with Android UI bitmask). */
#define OPD_TEXT_STYLE_BOLD           (1 << 0)
#define OPD_TEXT_STYLE_ITALIC         (1 << 1)
#define OPD_TEXT_STYLE_UNDERLINE      (1 << 2)
#define OPD_TEXT_STYLE_STRIKETHROUGH  (1 << 3)
#define OPD_TEXT_STYLE_MASK (OPD_TEXT_STYLE_BOLD|OPD_TEXT_STYLE_ITALIC|OPD_TEXT_STYLE_UNDERLINE|OPD_TEXT_STYLE_STRIKETHROUGH)

/* Marker inside /DS that indicates OpenDroidPDF owns the rich-style string. */
#define OPD_DS_MARKER "-opd:1"

/* Defaults match MuPDF's CSS defaults (source/html/css-apply.c). */
#define OPD_DEFAULT_LINE_HEIGHT (1.2f)
#define OPD_DEFAULT_TEXT_INDENT_PT (0.0f)

char *opd_pdf_string_dup(fz_context *ctx, pdf_obj *obj);

void opd_parse_default_appearance(const char *da,
                                 char out_font_key[32],
                                 float *out_font_size,
                                 float out_rgb[3]);

int opd_ds_has_marker(const char *ds);
float opd_ds_float_property(const char *ds, const char *prop, float fallback);
int opd_text_style_flags_from_ds(const char *ds);

void opd_rgb_from_default_appearance(float out_rgb[3], int n, const float color[4]);

void opd_build_freetext_ds(char *out, size_t out_len,
                           const char *font_key,
                           float font_size,
                           const float rgb[3],
                           int alignment,
                           int style_flags,
                           float line_height,
                           float text_indent_pt);

int pp_pdf_update_freetext_style_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                  long long object_id,
                                                  const char *font_key,
                                                  float font_size,
                                                  const float color_rgb[3]);

int pp_pdf_update_freetext_background_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                       long long object_id,
                                                       const float fill_rgb[3],
                                                       float opacity);

int pp_pdf_update_freetext_border_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                   long long object_id,
                                                   const float border_rgb[3],
                                                   float width_pt,
                                                   int dashed,
                                                   float radius_pt);

int pp_pdf_update_freetext_alignment_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                      long long object_id,
                                                      int alignment);

int pp_pdf_update_freetext_rotation_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                     long long object_id,
                                                     int rotation_degrees);

int pp_pdf_get_freetext_style_flags_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                     long long object_id,
                                                     int *out_flags);

int pp_pdf_update_freetext_style_flags_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                        long long object_id,
                                                        int style_flags);

int pp_pdf_get_freetext_paragraph_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                   long long object_id,
                                                   float *out_line_height,
                                                   float *out_text_indent_pt);

int pp_pdf_update_freetext_paragraph_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                      long long object_id,
                                                      float line_height,
                                                      float text_indent_pt);

#endif

