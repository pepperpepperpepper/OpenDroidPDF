#include "pp_core_internal.h"
#include "pp_core_pdf_annots_internal.h"

static int
pp_pdf_delete_annot_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page, long long object_id)
{
	pdf_document *pdf;
	pdf_page *pdfpage;
	pdf_annot *annot;

	if (!ctx || !doc || !page || object_id < 0)
		return 0;
	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return 0;
	pdfpage = (pdf_page *)page;

	for (annot = pp_pdf_first_annot_compat(ctx, pdfpage); annot; annot = pp_pdf_next_annot_compat(ctx, pdfpage, annot))
	{
		long long id = pp_pdf_object_id_for_annot(ctx, annot);
		if (id != object_id)
			continue;
		pp_pdf_delete_annot_compat(ctx, pdf, pdfpage, annot);
		pp_pdf_update_page_compat(ctx, pdf, pdfpage);
		return 1;
	}

	return 0;
}

int
pp_pdf_delete_annot_by_object_id(pp_ctx *pp, pp_doc *doc, int page_index, long long object_id)
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

		ok = pp_pdf_delete_annot_by_object_id_impl(pp->ctx, doc->doc, page, object_id);

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
pp_pdf_delete_annot_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index, long long object_id)
{
	(void)page_index;
	return pp_pdf_delete_annot_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page, object_id);
}

static int
pp_pdf_update_annot_contents_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page, long long object_id, const char *contents_utf8)
{
	pdf_document *pdf;
	pdf_page *pdfpage;
	pdf_annot *annot;

	if (!ctx || !doc || !page || object_id < 0)
		return 0;
	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return 0;
	pdfpage = (pdf_page *)page;

	for (annot = pp_pdf_first_annot_compat(ctx, pdfpage); annot; annot = pp_pdf_next_annot_compat(ctx, pdfpage, annot))
	{
		long long id = pp_pdf_object_id_for_annot(ctx, annot);
		if (id != object_id)
			continue;
		/* Force appearance regeneration so /AP stays in sync with /Contents. */
		pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
		if (annot_obj)
		{
			int type = pdf_annot_type(ctx, annot);
#if PP_MUPDF_API_NEW
			if (type == (int)PDF_ANNOT_FREE_TEXT)
#else
			if (type == (int)FZ_ANNOT_FREETEXT)
#endif
			{
				/*
				 * If this FreeText annotation was authored with rich contents (/RC),
				 * MuPDF's appearance generator will prioritize /RC over /Contents.
				 *
				 * OpenDroidPDF currently edits FreeText as plain text. When the user commits
				 * new contents, drop /RC so /AP regenerates from /Contents (+ /DS when present).
				 *
				 * Note: we intentionally keep /DS intact to preserve baseline styling
				 * (font/color/alignment) when it exists.
				 */
				pdf_dict_dels(ctx, annot_obj, "RC");
				pp_pdf_capture_freetext_border_style_if_missing(ctx, pdf, annot);
				pp_pdf_suppress_freetext_border_generation(ctx, pdf, annot);
			}
			pdf_dict_dels(ctx, annot_obj, "AP");
		}
		pp_pdf_set_annot_contents_compat(ctx, pdf, annot, contents_utf8);
		pp_pdf_dirty_annot_compat(ctx, pdf, annot);
		pp_pdf_update_annot_compat(ctx, pdf, annot);
		pp_pdf_update_page_compat(ctx, pdf, pdfpage);
		pp_pdf_patch_freetext_background_appearance_if_needed(ctx, pdf, annot);
		return 1;
	}

	return 0;
}

int
pp_pdf_update_annot_contents_by_object_id(pp_ctx *pp, pp_doc *doc, int page_index, long long object_id, const char *contents_utf8)
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

		ok = pp_pdf_update_annot_contents_by_object_id_impl(pp->ctx, doc->doc, page, object_id, contents_utf8);

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
pp_pdf_update_annot_contents_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index, long long object_id, const char *contents_utf8)
{
	(void)page_index;
	return pp_pdf_update_annot_contents_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page, object_id, contents_utf8);
}

static int
pp_pdf_update_annot_rect_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                           int pageW, int pageH,
                                           long long object_id,
                                           float x0, float y0, float x1, float y1)
{
	pdf_document *pdf;
	pdf_page *pdfpage;
	pdf_annot *annot;
	fz_rect bounds;
	fz_matrix page_to_pix;
	fz_matrix pix_to_page;
	fz_matrix page_to_pdf;
	float page_w;
	float page_h;
	fz_point p0;
	fz_point p1;
	fz_rect rect_page;
	fz_rect rect_pdf;
	fz_rect rect_set;

	if (!ctx || !doc || !page || object_id < 0 || pageW <= 0 || pageH <= 0)
		return 0;
	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return 0;
	pdfpage = (pdf_page *)page;

	bounds = pp_bound_page_compat(ctx, page);
	page_w = bounds.x1 - bounds.x0;
	page_h = bounds.y1 - bounds.y0;
	if (page_w <= 0 || page_h <= 0)
		return 0;

	page_to_pix = pp_scale_compat((float)pageW / page_w, (float)pageH / page_h);
	page_to_pix = pp_pre_translate_compat(page_to_pix, -bounds.x0, -bounds.y0);
	pix_to_page = pp_invert_matrix_compat(page_to_pix);
	page_to_pdf = pp_pdf_page_to_pdf_ctm_compat(ctx, pdfpage);

	/* Convert from page-pixel -> fitz page -> PDF page space. */
	p0.x = x0; p0.y = y0;
	p1.x = x1; p1.y = y1;
	p0 = pp_transform_point_compat(p0, pix_to_page);
	p1 = pp_transform_point_compat(p1, pix_to_page);

	rect_page.x0 = fminf(p0.x, p1.x);
	rect_page.y0 = fminf(p0.y, p1.y);
	rect_page.x1 = fmaxf(p0.x, p1.x);
	rect_page.y1 = fmaxf(p0.y, p1.y);

#if PP_MUPDF_API_NEW
	/*
	 * MuPDF 1.27 `pdf_set_annot_rect(ctx, annot, rect)` expects rect in *page space*:
	 * it applies the page->PDF transform internally before writing /Rect.
	 *
	 * Our Java/UI layer operates in page pixel coords; we converted to Fitz page space above.
	 */
	(void)page_to_pdf;
	rect_set = rect_page;
#else
	/*
	 * MuPDF 1.8 expects /Rect in PDF space when updating the annotation object directly.
	 */
	(void)rect_page;
	p0 = pp_transform_point_compat(p0, page_to_pdf);
	p1 = pp_transform_point_compat(p1, page_to_pdf);
	rect_pdf.x0 = fminf(p0.x, p1.x);
	rect_pdf.y0 = fminf(p0.y, p1.y);
	rect_pdf.x1 = fmaxf(p0.x, p1.x);
	rect_pdf.y1 = fmaxf(p0.y, p1.y);
	rect_set = rect_pdf;
#endif

	for (annot = pp_pdf_first_annot_compat(ctx, pdfpage); annot; annot = pp_pdf_next_annot_compat(ctx, pdfpage, annot))
	{
		long long id = pp_pdf_object_id_for_annot(ctx, annot);
		if (id != object_id)
			continue;
		/* Force appearance regeneration so FreeText wraps/reflows when resized. */
		pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
		if (annot_obj)
		{
			int type = pdf_annot_type(ctx, annot);
#if PP_MUPDF_API_NEW
			if (type == (int)PDF_ANNOT_FREE_TEXT)
#else
			if (type == (int)FZ_ANNOT_FREETEXT)
#endif
			{
				pp_pdf_capture_freetext_border_style_if_missing(ctx, pdf, annot);
				pp_pdf_suppress_freetext_border_generation(ctx, pdf, annot);
			}
			pdf_dict_dels(ctx, annot_obj, "AP");
		}
		pp_pdf_set_annot_rect_compat(ctx, pdf, annot, rect_set);
		pp_pdf_dirty_annot_compat(ctx, pdf, annot);
		pp_pdf_update_annot_compat(ctx, pdf, annot);
		pp_pdf_update_page_compat(ctx, pdf, pdfpage);
		pp_pdf_patch_freetext_background_appearance_if_needed(ctx, pdf, annot);
		return 1;
	}

	return 0;
}

int
pp_pdf_update_annot_rect_by_object_id(pp_ctx *pp, pp_doc *doc, int page_index,
                                      int pageW, int pageH,
                                      long long object_id,
                                      float x0, float y0, float x1, float y1)
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

		ok = pp_pdf_update_annot_rect_by_object_id_impl(pp->ctx, doc->doc, page, pageW, pageH, object_id, x0, y0, x1, y1);

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
pp_pdf_update_annot_rect_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                            int pageW, int pageH,
                                            long long object_id,
                                            float x0, float y0, float x1, float y1)
{
	(void)page_index;
	return pp_pdf_update_annot_rect_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                                  pageW, pageH, object_id, x0, y0, x1, y1);
}
