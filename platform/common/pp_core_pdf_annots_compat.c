#include "pp_core_pdf_annots_internal.h"

#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdarg.h>

pdf_annot *
pp_pdf_create_annot_compat(fz_context *ctx, pdf_document *doc, pdf_page *page, int annot_type)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	return pdf_create_annot(ctx, page, (enum pdf_annot_type)annot_type);
#else
	return pdf_create_annot(ctx, doc, page, (fz_annot_type)annot_type);
#endif
}

void
pp_pdf_delete_annot_compat(fz_context *ctx, pdf_document *doc, pdf_page *page, pdf_annot *annot)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	pdf_delete_annot(ctx, page, annot);
#else
	pdf_delete_annot(ctx, doc, page, annot);
#endif
}

void
pp_pdf_update_page_compat(fz_context *ctx, pdf_document *doc, pdf_page *page)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	(void)pdf_update_page(ctx, page);
#else
	pdf_update_page(ctx, doc, page);
#endif
}

void
pp_pdf_update_annot_compat(fz_context *ctx, pdf_document *doc, pdf_annot *annot)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	(void)pdf_update_annot(ctx, annot);
#else
	pdf_update_annot(ctx, doc, annot);
#endif
}

void
pp_pdf_dirty_annot_compat(fz_context *ctx, pdf_document *doc, pdf_annot *annot)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	pdf_dirty_annot(ctx, annot);
#else
	/* Older MuPDF doesn't have pdf_dirty_annot; pdf_update_annot marks it dirty. */
	pp_pdf_update_annot_compat(ctx, doc, annot);
#endif
}

pdf_annot *
pp_pdf_first_annot_compat(fz_context *ctx, pdf_page *page)
{
	return pdf_first_annot(ctx, page);
}

pdf_annot *
pp_pdf_next_annot_compat(fz_context *ctx, pdf_page *page, pdf_annot *annot)
{
#if PP_MUPDF_API_NEW
	(void)page;
	return pdf_next_annot(ctx, annot);
#else
	return pdf_next_annot(ctx, page, annot);
#endif
}

pdf_obj *
pp_pdf_annot_obj_compat(fz_context *ctx, pdf_annot *annot)
{
#if PP_MUPDF_API_NEW
	return pdf_annot_obj(ctx, annot);
#else
	(void)ctx;
	return annot ? annot->obj : NULL;
#endif
}

pdf_obj *
pp_pdf_new_real_compat(fz_context *ctx, pdf_document *doc, float f)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	return pdf_new_real(ctx, f);
#else
	return pdf_new_real(ctx, doc, f);
#endif
}

pdf_obj *
pp_pdf_new_rect_compat(fz_context *ctx, pdf_document *doc, fz_rect rect)
{
#if PP_MUPDF_API_NEW
	return pdf_new_rect(ctx, doc, rect);
#else
	return pdf_new_rect(ctx, doc, &rect);
#endif
}

int
pp_pdf_is_stream_compat(fz_context *ctx, pdf_document *doc, pdf_obj *ref, int num, int gen)
{
#if PP_MUPDF_API_NEW
	(void)gen;
	(void)ref;
	if (!doc || num <= 0)
		return 0;
	return pdf_obj_num_is_stream(ctx, doc, num) ? 1 : 0;
#else
	return pdf_is_stream(ctx, doc, num, gen);
#endif
}

void
pp_pdf_to_rect_compat(fz_context *ctx, pdf_obj *array, fz_rect *out)
{
	if (!out)
		return;
#if PP_MUPDF_API_NEW
	*out = pdf_to_rect(ctx, array);
#else
	pdf_to_rect(ctx, array, out);
#endif
}

fz_buffer *
pp_pdf_load_stream_compat(fz_context *ctx, pdf_document *doc, pdf_obj *ref, int num, int gen)
{
#if PP_MUPDF_API_NEW
	(void)gen;
	(void)ref;
	if (!doc || num <= 0)
		return NULL;
	return pdf_load_stream_number(ctx, doc, num);
#else
	return pdf_load_stream(ctx, doc, num, gen);
#endif
}

void
pp_fz_buffer_vprintf_compat(fz_context *ctx, fz_buffer *buf, const char *fmt, va_list args)
{
#if PP_MUPDF_API_NEW
	fz_append_vprintf(ctx, buf, fmt, args);
#else
	fz_buffer_vprintf(ctx, buf, fmt, args);
#endif
}

void
pp_fz_buffer_printf_compat(fz_context *ctx, fz_buffer *buf, const char *fmt, ...)
{
	va_list args;
	va_start(args, fmt);
	pp_fz_buffer_vprintf_compat(ctx, buf, fmt, args);
	va_end(args);
}

void
pp_fz_buffer_cat_compat(fz_context *ctx, fz_buffer *dst, fz_buffer *src)
{
#if PP_MUPDF_API_NEW
	fz_append_buffer(ctx, dst, src);
#else
	fz_buffer_cat(ctx, dst, src);
#endif
}

void
pp_fz_buffer_append_data_compat(fz_context *ctx, fz_buffer *buf, const void *data, size_t len)
{
#if PP_MUPDF_API_NEW
	fz_append_data(ctx, buf, data, len);
#else
	fz_write_buffer(ctx, buf, data, (int)len);
#endif
}

size_t
pp_fz_buffer_storage_compat(fz_context *ctx, fz_buffer *buf, unsigned char **data)
{
#if PP_MUPDF_API_NEW
	return fz_buffer_storage(ctx, buf, data);
#else
	return (size_t)fz_buffer_storage(ctx, buf, data);
#endif
}

const unsigned char *
pp_memmem_compat(const unsigned char *hay, size_t hay_len, const unsigned char *needle, size_t needle_len)
{
	if (!hay || !needle || needle_len == 0 || hay_len < needle_len)
		return NULL;
	for (size_t i = 0; i + needle_len <= hay_len; i++)
	{
		if (hay[i] != needle[0])
			continue;
		if (memcmp(hay + i, needle, needle_len) == 0)
			return hay + i;
	}
	return NULL;
}

void
pp_pdf_set_annot_contents_compat(fz_context *ctx, pdf_document *doc, pdf_annot *annot, const char *contents_utf8)
{
	const char *text = contents_utf8 ? contents_utf8 : "";
#if PP_MUPDF_API_NEW
	(void)doc;
	pdf_set_annot_contents(ctx, annot, text);
#else
	pdf_set_annot_contents(ctx, doc, annot, (char *)text);
#endif
}

fz_rect
pp_pdf_bound_annot_compat(fz_context *ctx, pdf_document *doc, pdf_page *page, pdf_annot *annot)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	(void)page;
	return pdf_bound_annot(ctx, annot);
#else
	fz_rect rect;
	(void)doc;
	pdf_bound_annot(ctx, page, annot, &rect);
	return rect;
#endif
}

void
pp_pdf_set_annot_rect_compat(fz_context *ctx, pdf_document *doc, pdf_annot *annot, fz_rect rect)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	pdf_set_annot_rect(ctx, annot, rect);
#else
	pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
	if (annot_obj)
		pdf_dict_puts_drop(ctx, annot_obj, "Rect", pp_pdf_new_rect_compat(ctx, doc, rect));
	/*
	 * MuPDF 1.8 keeps annotation geometry cached in the pdf_annot struct (rect + pagerect).
	 * If we update /Rect directly without syncing these fields, pdf_update_appearance will
	 * re-write the stale rect back into the object, causing "move/resize" to appear to do
	 * nothing and selection boxes to drift.
	 */
	if (annot && annot->page)
	{
		annot->rect = rect;
		annot->pagerect = rect;
		fz_transform_rect(&annot->pagerect, &annot->page->ctm);
	}
#endif
}

void
pp_pdf_set_annot_color_opacity_dict(fz_context *ctx, pdf_document *doc, pdf_annot *annot, const float color[3], float opacity)
{
	int i;
	pdf_obj *annot_obj;
	pdf_obj *col;

	if (!doc || !annot)
		return;

	annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
	if (!annot_obj)
		return;

	col = pdf_new_array(ctx, doc, 3);
	pdf_dict_puts_drop(ctx, annot_obj, "C", col);
	for (i = 0; i < 3; i++)
		pdf_array_push_drop(ctx, col, pp_pdf_new_real_compat(ctx, doc, color[i]));

	if (opacity > 0.0f && opacity < 1.0f)
	{
		pdf_dict_puts_drop(ctx, annot_obj, "CA", pp_pdf_new_real_compat(ctx, doc, opacity));
		pdf_dict_puts_drop(ctx, annot_obj, "ca", pp_pdf_new_real_compat(ctx, doc, opacity));
	}
}

void
pp_pdf_set_annot_interior_color_dict(fz_context *ctx, pdf_document *doc, pdf_annot *annot, const float color[3])
{
	int i;
	pdf_obj *annot_obj;
	pdf_obj *col;

	if (!doc || !annot)
		return;

	annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
	if (!annot_obj)
		return;

	if (!color)
	{
		pdf_dict_dels(ctx, annot_obj, "IC");
		return;
	}

	col = pdf_new_array(ctx, doc, 3);
	pdf_dict_puts_drop(ctx, annot_obj, "IC", col);
	for (i = 0; i < 3; i++)
		pdf_array_push_drop(ctx, col, pp_pdf_new_real_compat(ctx, doc, color[i]));
}

void
pp_pdf_set_annot_opacity_dict(fz_context *ctx, pdf_document *doc, pdf_annot *annot, float opacity)
{
	pdf_obj *annot_obj;

	if (!doc || !annot)
		return;

	annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
	if (!annot_obj)
		return;

	/* Keep values inside the spec range and avoid leaving stale entries behind. */
	if (opacity < 0.0f) opacity = 0.0f;
	if (opacity > 1.0f) opacity = 1.0f;

	if (opacity >= 0.0f && opacity < 1.0f)
	{
		pdf_dict_puts_drop(ctx, annot_obj, "CA", pp_pdf_new_real_compat(ctx, doc, opacity));
		pdf_dict_puts_drop(ctx, annot_obj, "ca", pp_pdf_new_real_compat(ctx, doc, opacity));
	}
	else
	{
		pdf_dict_dels(ctx, annot_obj, "CA");
		pdf_dict_dels(ctx, annot_obj, "ca");
	}
}

void
pp_pdf_set_annot_color_dict(fz_context *ctx, pdf_document *doc, pdf_annot *annot, const float color[3])
{
	int i;
	pdf_obj *annot_obj;
	pdf_obj *col;

	if (!doc || !annot || !color)
		return;

	annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
	if (!annot_obj)
		return;

	col = pdf_new_array(ctx, doc, 3);
	pdf_dict_puts_drop(ctx, annot_obj, "C", col);
	for (i = 0; i < 3; i++)
		pdf_array_push_drop(ctx, col, pp_pdf_new_real_compat(ctx, doc, color[i]));
}

void
pp_pdf_set_markup_quadpoints_compat(fz_context *ctx, pdf_document *doc, pdf_annot *annot, const fz_point *pts, int pt_count)
{
	if (!doc || !annot || !pts || pt_count <= 0)
		return;

#if PP_MUPDF_API_NEW
	int qn = pt_count / 4;
	int i;
	fz_quad *qv = (fz_quad *)fz_malloc(ctx, (size_t)qn * sizeof(fz_quad));
	for (i = 0; i < qn; i++)
	{
		qv[i].ul = pts[i * 4 + 0];
		qv[i].ur = pts[i * 4 + 1];
		qv[i].ll = pts[i * 4 + 2];
		qv[i].lr = pts[i * 4 + 3];
	}
	pdf_set_annot_quad_points(ctx, annot, qn, qv);
	fz_free(ctx, qv);
#else
	pdf_set_markup_annot_quadpoints(ctx, doc, annot, (fz_point *)pts, pt_count);
#endif
}

fz_matrix
pp_pdf_page_to_pdf_ctm_compat(fz_context *ctx, pdf_page *page)
{
#if PP_MUPDF_API_NEW
	fz_rect mediabox;
	fz_matrix ctm;
	/*
	 * MuPDF 1.27 `pdf_page_transform` produces a matrix mapping PDF -> page.
	 * Our callers want page -> PDF (to convert view/page-space geometry into /Rect etc),
	 * so return the inverse.
	 */
	pdf_page_transform(ctx, page, &mediabox, &ctm);
	return pp_invert_matrix_compat(ctm);
#else
	fz_matrix inv;
	fz_invert_matrix(&inv, &page->ctm);
	return inv;
#endif
}

long long
pp_pdf_object_id_for_annot(fz_context *ctx, pdf_annot *annot)
{
	pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
	if (!annot_obj)
		return -1;
	int num = pdf_to_num(ctx, annot_obj);
	if (num <= 0)
		return -1;
	int gen = pdf_to_gen(ctx, annot_obj);
	return (((long long)num) << 32) | (long long)(gen & 0xffffffffu);
}
