#include "pp_core_internal.h"
#include "pp_core_pdf_annots_internal.h"

static int
pp_pdf_set_ink_list(fz_context *ctx, pdf_document *pdf, pdf_annot *annot,
                    int arc_count, const int *counts, const fz_point *pts_pdf, int pt_count,
                    const float color[3], float thickness)
{
	pdf_obj *annot_obj;
	pdf_obj *list;
	pdf_obj *bs;
	pdf_obj *col;
	fz_rect rect;
	int rect_init = 0;
	int i, j, k = 0;

	if (arc_count <= 0 || !counts || !pts_pdf || pt_count <= 0 || !pdf || !annot)
		return 0;

	annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
	if (!annot_obj)
		return 0;

	list = pdf_new_array(ctx, pdf, arc_count);
	pdf_dict_puts_drop(ctx, annot_obj, "InkList", list);

	for (i = 0; i < arc_count; i++)
	{
		int count = counts[i];
		pdf_obj *arc = pdf_new_array(ctx, pdf, count * 2);
		pdf_array_push_drop(ctx, list, arc);

		for (j = 0; j < count && k < pt_count; j++)
		{
			fz_point pt = pts_pdf[k++];
			if (!rect_init)
			{
				rect.x0 = rect.x1 = pt.x;
				rect.y0 = rect.y1 = pt.y;
				rect_init = 1;
			}
			else
			{
				if (pt.x < rect.x0) rect.x0 = pt.x;
				if (pt.y < rect.y0) rect.y0 = pt.y;
				if (pt.x > rect.x1) rect.x1 = pt.x;
				if (pt.y > rect.y1) rect.y1 = pt.y;
			}

			pdf_array_push_drop(ctx, arc, pp_pdf_new_real_compat(ctx, pdf, pt.x));
			pdf_array_push_drop(ctx, arc, pp_pdf_new_real_compat(ctx, pdf, pt.y));
		}
	}

	if (rect_init && thickness > 0.0f)
	{
		rect.x0 -= thickness;
		rect.y0 -= thickness;
		rect.x1 += thickness;
		rect.y1 += thickness;
	}

	pdf_dict_puts_drop(ctx, annot_obj, "Rect", pp_pdf_new_rect_compat(ctx, pdf, rect));

	bs = pdf_new_dict(ctx, pdf, 1);
	pdf_dict_puts_drop(ctx, annot_obj, "BS", bs);
	pdf_dict_puts_drop(ctx, bs, "W", pp_pdf_new_real_compat(ctx, pdf, thickness));

	col = pdf_new_array(ctx, pdf, 3);
	pdf_dict_puts_drop(ctx, annot_obj, "C", col);
	for (i = 0; i < 3; i++)
		pdf_array_push_drop(ctx, col, pp_pdf_new_real_compat(ctx, pdf, color[i]));

	return 1;
}

static int
pp_pdf_add_ink_annot_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                          int pageW, int pageH,
                          int arc_count, const int *arc_counts,
                          const pp_point *points, int point_count,
                          const float color_rgb[3], float thickness,
                          long long *out_object_id)
{
	pdf_document *pdf = NULL;
	pdf_page *pdfpage = NULL;
	pdf_annot *annot = NULL;
	fz_point *pts_pdf = NULL;
	int ok = 0;
	fz_rect bounds;
	fz_matrix page_to_pix;
	fz_matrix pix_to_page;
	fz_matrix page_to_pdf;
	float page_w;
	float page_h;
	int i;
	int expected_points = 0;
	float thickness_pts;
	float color[3];

	if (out_object_id)
		*out_object_id = -1;
	if (!ctx || !doc || !page || !points || point_count <= 0 || pageW <= 0 || pageH <= 0)
		return 0;

	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return 0;
	pdfpage = (pdf_page *)page;

	/* Convert from page-pixel space -> fitz page space -> PDF page space. */
	bounds = pp_bound_page_compat(ctx, page);
	page_w = bounds.x1 - bounds.x0;
	page_h = bounds.y1 - bounds.y0;
	if (page_w <= 0 || page_h <= 0)
		return 0;

	page_to_pix = pp_scale_compat((float)pageW / page_w, (float)pageH / page_h);
	page_to_pix = pp_pre_translate_compat(page_to_pix, -bounds.x0, -bounds.y0);
	pix_to_page = pp_invert_matrix_compat(page_to_pix);
	page_to_pdf = pp_pdf_page_to_pdf_ctm_compat(ctx, pdfpage);

	thickness_pts = thickness > 0.0f ? thickness : 3.0f;
	color[0] = color_rgb ? color_rgb[0] : 1.0f;
	color[1] = color_rgb ? color_rgb[1] : 0.0f;
	color[2] = color_rgb ? color_rgb[2] : 0.0f;

	for (i = 0; i < arc_count; i++)
		expected_points += arc_counts ? arc_counts[i] : 0;
	if (arc_count <= 0 || !arc_counts || expected_points <= 0)
		return 0;
	if (expected_points < point_count)
		point_count = expected_points;

	fz_var(annot);
	fz_var(pts_pdf);
	fz_try(ctx)
	{
#if PP_MUPDF_API_NEW
		const int annot_type = PDF_ANNOT_INK;
#else
		const int annot_type = FZ_ANNOT_INK;
#endif

		pts_pdf = (fz_point *)fz_malloc(ctx, (size_t)point_count * sizeof(fz_point));
		for (i = 0; i < point_count; i++)
		{
			fz_point p;
			p.x = points[i].x;
			p.y = points[i].y;
			p = pp_transform_point_compat(p, pix_to_page);
			p = pp_transform_point_compat(p, page_to_pdf);
			pts_pdf[i] = p;
		}

		annot = pp_pdf_create_annot_compat(ctx, pdf, pdfpage, annot_type);

		if (!pp_pdf_set_ink_list(ctx, pdf, annot, arc_count, arc_counts, pts_pdf, point_count, color, thickness_pts))
			fz_throw(ctx, FZ_ERROR_GENERIC, "failed to set InkList");

#if PP_MUPDF_API_NEW
		pdf_set_annot_opacity(ctx, annot, 1.0f);
#endif

		pp_pdf_dirty_annot_compat(ctx, pdf, annot);
		pp_pdf_update_page_compat(ctx, pdf, pdfpage);

		if (out_object_id)
			*out_object_id = pp_pdf_object_id_for_annot(ctx, annot);

		ok = 1;
	}
	fz_always(ctx)
	{
		if (pts_pdf)
			fz_free(ctx, pts_pdf);
	}
	fz_catch(ctx)
	{
		ok = 0;
	}

	return ok;
}

int
pp_pdf_add_ink_annot(pp_ctx *pp, pp_doc *doc, int page_index,
                     int pageW, int pageH,
                     int arc_count, const int *arc_counts,
                     const pp_point *points, int point_count,
                     const float color_rgb[3], float thickness,
                     long long *out_object_id)
{
	int ok = 0;
	pp_cached_page *pc = NULL;
	fz_page *page = NULL;

	if (!pp || !pp->ctx || !doc || !doc->doc)
		return 0;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pc = pp_cache_ensure_page_locked(pp->ctx, doc, page_index);
		page = pc ? pc->page : NULL;
		if (!page)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "no page");

		ok = pp_pdf_add_ink_annot_impl(pp->ctx, doc->doc, page,
		                              pageW, pageH,
		                              arc_count, arc_counts,
		                              points, point_count,
		                              color_rgb, thickness,
		                              out_object_id);

		/* Invalidate cached display list for this page so subsequent renders see the new annotation. */
		if (ok && pc && pc->display_list)
		{
			fz_drop_display_list(pp->ctx, pc->display_list);
			pc->display_list = NULL;
		}
	}
	fz_always(pp->ctx)
		pp_unlock(pp);
	fz_catch(pp->ctx)
		ok = 0;

	return ok;
}

int
pp_pdf_add_ink_annot_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                           int pageW, int pageH,
                           int arc_count, const int *arc_counts,
                           const pp_point *points, int point_count,
                           const float color_rgb[3], float thickness,
                           long long *out_object_id)
{
	(void)page_index;
	return pp_pdf_add_ink_annot_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                pageW, pageH,
	                                arc_count, arc_counts,
	                                points, point_count,
	                                color_rgb, thickness,
	                                out_object_id);
}

