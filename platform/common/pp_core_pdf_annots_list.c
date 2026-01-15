#include "pp_core_internal.h"
#include "pp_core_pdf_annots_internal.h"

#include <string.h>

static int
pp_pdf_list_annots_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                        int pageW, int pageH,
                        pp_pdf_annot_list **out_list)
{
	pdf_document *pdf;
	pdf_page *pdfpage;
	pdf_annot *annot;
	pp_pdf_annot_list *list = NULL;
	pp_pdf_annot_info *items = NULL;
	int count = 0;
	int idx = 0;
	int ok = 0;
	fz_rect bounds;
	fz_matrix page_to_pix;
	fz_matrix pix_to_page;
	fz_matrix page_to_pdf;
	fz_matrix pdf_to_page;
	float page_w;
	float page_h;

	if (out_list)
		*out_list = NULL;
	if (!ctx || !doc || !page || pageW <= 0 || pageH <= 0 || !out_list)
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
	(void)pix_to_page;
	page_to_pdf = pp_pdf_page_to_pdf_ctm_compat(ctx, pdfpage);
	pdf_to_page = pp_invert_matrix_compat(page_to_pdf);

	for (annot = pp_pdf_first_annot_compat(ctx, pdfpage); annot; annot = pp_pdf_next_annot_compat(ctx, pdfpage, annot))
		count++;

	fz_var(list);
	fz_var(items);
	fz_try(ctx)
	{
		list = (pp_pdf_annot_list *)fz_malloc(ctx, sizeof(pp_pdf_annot_list));
		memset(list, 0, sizeof(*list));
		list->count = count;
		if (count > 0)
		{
			items = (pp_pdf_annot_info *)fz_malloc(ctx, (size_t)count * sizeof(pp_pdf_annot_info));
			memset(items, 0, (size_t)count * sizeof(pp_pdf_annot_info));
			list->items = items;
		}

		idx = 0;
		for (annot = pp_pdf_first_annot_compat(ctx, pdfpage); annot; annot = pp_pdf_next_annot_compat(ctx, pdfpage, annot))
		{
			pp_pdf_annot_info *info = &items[idx++];
			int type = pdf_annot_type(ctx, annot);
			fz_rect rect_page;
#if PP_MUPDF_API_NEW
			/*
			 * MuPDF 1.27 `pdf_bound_annot(ctx, annot)` returns page-space bounds:
			 * it reads the /Rect (PDF space) and applies the page transform.
			 */
			rect_page = pp_pdf_bound_annot_compat(ctx, pdf, pdfpage, annot);
#else
			/*
			 * MuPDF 1.8 `pdf_bound_annot(page, annot, &rect)` returns annot->pagerect
			 * (already in Fitz page space). Do NOT apply the page->ctm again.
			 */
			rect_page = pp_pdf_bound_annot_compat(ctx, pdf, pdfpage, annot);
#endif
			fz_rect rect_pix = pp_transform_rect_compat(rect_page, page_to_pix);

			info->type = type;
			info->x0 = rect_pix.x0;
			info->y0 = rect_pix.y0;
			info->x1 = rect_pix.x1;
			info->y1 = rect_pix.y1;
			info->object_id = pp_pdf_object_id_for_annot(ctx, annot);

#if PP_MUPDF_API_NEW
			if (type == (int)PDF_ANNOT_TEXT || type == (int)PDF_ANNOT_FREE_TEXT)
			{
				const char *t = pdf_annot_contents(ctx, annot);
				if (t)
					info->contents_utf8 = fz_strdup(ctx, t);
			}
#else
			if (type == (int)FZ_ANNOT_TEXT || type == (int)FZ_ANNOT_FREETEXT)
			{
				char *t = pdf_annot_contents(ctx, pdf, annot);
				if (t)
				{
					info->contents_utf8 = fz_strdup(ctx, t);
				}
			}
#endif

#if PP_MUPDF_API_NEW
			if (type == (int)PDF_ANNOT_INK)
#else
			if (type == (int)FZ_ANNOT_INK)
#endif
			{
				pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
				pdf_obj *inklist = annot_obj ? pdf_dict_gets(ctx, annot_obj, "InkList") : NULL;
#if !PP_MUPDF_API_NEW
				if (!inklist)
					inklist = pdf_annot_inklist(ctx, annot);
#endif
				if (inklist && pdf_is_array(ctx, inklist))
				{
					int nArcs = pdf_array_len(ctx, inklist);
					int ai;
					info->arc_count = nArcs;
					if (nArcs > 0)
					{
						info->arcs = (pp_pdf_annot_arc *)fz_malloc(ctx, (size_t)nArcs * sizeof(pp_pdf_annot_arc));
						memset(info->arcs, 0, (size_t)nArcs * sizeof(pp_pdf_annot_arc));
					}

					for (ai = 0; ai < nArcs; ai++)
					{
						pdf_obj *arc_obj = pdf_array_get(ctx, inklist, ai);
						int nNums;
						int nPts;
						int pi;
						pp_pdf_annot_arc *arc = &info->arcs[ai];

						if (!arc_obj || !pdf_is_array(ctx, arc_obj))
							continue;
						nNums = pdf_array_len(ctx, arc_obj);
						nPts = nNums / 2;
						arc->count = nPts;
						if (nPts > 0)
							arc->points = (pp_point *)fz_malloc(ctx, (size_t)nPts * sizeof(pp_point));

						for (pi = 0; pi < nPts; pi++)
						{
							float x = pdf_to_real(ctx, pdf_array_get(ctx, arc_obj, pi * 2 + 0));
							float y = pdf_to_real(ctx, pdf_array_get(ctx, arc_obj, pi * 2 + 1));
							fz_point p;
							p.x = x;
							p.y = y;
							p = pp_transform_point_compat(p, pdf_to_page);
							p = pp_transform_point_compat(p, page_to_pix);
							arc->points[pi].x = p.x;
							arc->points[pi].y = p.y;
						}
					}
				}
			}
		}

		*out_list = list;
		ok = 1;
	}
	fz_catch(ctx)
	{
		if (list)
		{
			/* Reuse the drop helper to free partial allocations. */
			pp_pdf_drop_annot_list_mupdf(ctx, list);
		}
		ok = 0;
	}
	return ok;
}

int
pp_pdf_list_annots(pp_ctx *pp, pp_doc *doc, int page_index,
                   int pageW, int pageH,
                   pp_pdf_annot_list **out_list)
{
	int ok = 0;
	pp_cached_page *pc = NULL;
	fz_page *page = NULL;

	if (out_list)
		*out_list = NULL;
	if (!pp || !pp->ctx || !doc || !doc->doc)
		return 0;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pc = pp_cache_ensure_page_locked(pp->ctx, doc, page_index);
		page = pc ? pc->page : NULL;
		if (!page)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "no page");

		ok = pp_pdf_list_annots_impl(pp->ctx, doc->doc, page, pageW, pageH, out_list);
	}
	fz_always(pp->ctx)
		pp_unlock(pp);
	fz_catch(pp->ctx)
		ok = 0;

	return ok;
}

void
pp_pdf_drop_annot_list(pp_ctx *pp, pp_pdf_annot_list *list)
{
	if (!pp || !pp->ctx || !list)
		return;

	pp_lock(pp);
	pp_pdf_drop_annot_list_mupdf(pp->ctx, list);
	pp_unlock(pp);
}

int
pp_pdf_list_annots_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                         int pageW, int pageH,
                         pp_pdf_annot_list **out_list)
{
	(void)page_index;
	return pp_pdf_list_annots_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page, pageW, pageH, out_list);
}

void
pp_pdf_drop_annot_list_mupdf(void *mupdf_ctx, pp_pdf_annot_list *list)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	int i;

	if (!ctx || !list)
		return;

	if (list->items)
	{
		for (i = 0; i < list->count; i++)
		{
			pp_pdf_annot_info *info = &list->items[i];
			int ai;
			if (info->contents_utf8)
				fz_free(ctx, info->contents_utf8);
			if (info->arcs)
			{
				for (ai = 0; ai < info->arc_count; ai++)
				{
					if (info->arcs[ai].points)
						fz_free(ctx, info->arcs[ai].points);
				}
				fz_free(ctx, info->arcs);
			}
		}
		fz_free(ctx, list->items);
	}

	fz_free(ctx, list);
}

