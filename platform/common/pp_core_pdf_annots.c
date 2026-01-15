#include "pp_core_internal.h"
#include "pp_core_pdf_annots_internal.h"

#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <stdio.h>
#include <stdint.h>

/*
 * This file owns "add annotation" APIs for non-ink annotations.
 *
 * The PDF annotation implementation is intentionally split across cohesive units:
 * - pp_core_pdf_annots_compat.c: MuPDF API compatibility + shared helpers
 * - pp_core_pdf_annots_freetext.c: FreeText style + rich-text helpers
 * - pp_core_pdf_annots_freetext_appearance.c: FreeText /AP patching (fill/border)
 * - pp_core_pdf_annots_ink.c: Ink annotation creation
 * - pp_core_pdf_annots_list.c: Annotation listing
 * - pp_core_pdf_annots_edit.c: Delete/update operations
 */

static int
pp_pdf_add_annot_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                      int pageW, int pageH,
                      int annot_type,
                      const pp_point *points, int point_count,
                      const float color_rgb[3], float opacity,
                      const char *contents_utf8,
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
	float color[3];
	const char *contents = contents_utf8 ? contents_utf8 : "";

	if (out_object_id)
		*out_object_id = -1;
	if (!ctx || !doc || !page || !points || point_count <= 0 || pageW <= 0 || pageH <= 0)
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

	color[0] = color_rgb ? color_rgb[0] : 1.0f;
	color[1] = color_rgb ? color_rgb[1] : 0.0f;
	color[2] = color_rgb ? color_rgb[2] : 0.0f;

	fz_var(annot);
	fz_var(pts_pdf);
	fz_try(ctx)
	{
		pts_pdf = (fz_point *)fz_malloc(ctx, (size_t)point_count * sizeof(fz_point));

		/* Convert from page-pixel -> fitz page -> PDF page space. */
		for (i = 0; i < point_count; i++)
		{
			fz_point p;
			p.x = points[i].x;
			p.y = points[i].y;

			/* Keep Android's legacy highlight quad point ordering fix (swap last two points per quad). */
#if PP_MUPDF_API_NEW
			if (annot_type == (int)PDF_ANNOT_HIGHLIGHT)
#else
			if (annot_type == (int)FZ_ANNOT_HIGHLIGHT)
#endif
			{
				if ((i % 4) == 2 && i + 1 < point_count)
				{
					p.x = points[i + 1].x;
					p.y = points[i + 1].y;
				}
				else if ((i % 4) == 3)
				{
					p.x = points[i - 1].x;
					p.y = points[i - 1].y;
				}
			}

			p = pp_transform_point_compat(p, pix_to_page);
			p = pp_transform_point_compat(p, page_to_pdf);
			pts_pdf[i] = p;
		}

		annot = pp_pdf_create_annot_compat(ctx, pdf, pdfpage, annot_type);

#if PP_MUPDF_API_NEW
		if (annot_type == (int)PDF_ANNOT_TEXT)
#else
		if (annot_type == (int)FZ_ANNOT_TEXT)
#endif
		{
			fz_rect rect;
			if (point_count < 2)
				fz_throw(ctx, FZ_ERROR_GENERIC, "TEXT annot requires 2 points");

			/* Ensure rect order. */
			float x0 = pts_pdf[0].x, y0 = pts_pdf[0].y;
			float x1 = pts_pdf[1].x, y1 = pts_pdf[1].y;
			if (x0 > x1) { float t = x0; x0 = x1; x1 = t; }
			if (y0 > y1) { float t = y0; y0 = y1; y1 = t; }
			rect.x0 = x0; rect.y0 = y0; rect.x1 = x1; rect.y1 = y1;

			/* Old MuPDF uses a point-based setter for sticky notes; new uses rects. */
#if !PP_MUPDF_API_NEW
			{
				fz_point pos;
				pos.x = rect.x0;
				pos.y = rect.y0;
				pdf_set_text_annot_position(ctx, pdf, annot, pos);
			}
#endif
			pp_pdf_set_annot_rect_compat(ctx, pdf, annot, rect);
			pp_pdf_set_annot_contents_compat(ctx, pdf, annot, contents);
			pp_pdf_set_annot_color_opacity_dict(ctx, pdf, annot, color, opacity > 0.0f ? opacity : 1.0f);
			pp_pdf_update_annot_compat(ctx, pdf, annot);
		}
#if PP_MUPDF_API_NEW
		else if (annot_type == (int)PDF_ANNOT_FREE_TEXT)
#else
		else if (annot_type == (int)FZ_ANNOT_FREETEXT)
#endif
		{
			float minx = 0.0f, maxx = 0.0f, miny = 0.0f, maxy = 0.0f;
			fz_rect rect;
			float font_size;

			if (point_count >= 1)
			{
				minx = maxx = pts_pdf[0].x;
				miny = maxy = pts_pdf[0].y;
			}
			for (i = 1; i < point_count; i++)
			{
				minx = fminf(minx, pts_pdf[i].x);
				maxx = fmaxf(maxx, pts_pdf[i].x);
				miny = fminf(miny, pts_pdf[i].y);
				maxy = fmaxf(maxy, pts_pdf[i].y);
			}

			/* Minimum size padding (matches legacy Android glue). */
			if (maxx - minx < 16.0f)
			{
				float pad = 8.0f;
				minx -= pad;
				maxx += pad;
			}
			if (maxy - miny < 12.0f)
			{
				float pad = 12.0f - (maxy - miny);
				maxy += pad;
			}

			if (minx > maxx) { float t = minx; minx = maxx; maxx = t; }
			if (miny > maxy) { float t = miny; miny = maxy; maxy = t; }

			rect.x0 = minx;
			rect.y0 = miny;
			rect.x1 = maxx;
			rect.y1 = maxy;

			/* Acrobat-ish: FreeText uses an explicit font size; resizing the box should not scale it. */
			font_size = 12.0f;

#if PP_MUPDF_API_NEW
			pp_pdf_set_annot_rect_compat(ctx, pdf, annot, rect);
			pp_pdf_set_annot_contents_compat(ctx, pdf, annot, contents);
			/* Preserve any existing /DS + /RC by updating /DA directly (MuPDF's helper deletes them). */
			{
				pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
				char da_buf[128];
				if (annot_obj)
				{
					float r = color[0], g = color[1], b = color[2];
					if (r < 0.0f) r = 0.0f; if (r > 1.0f) r = 1.0f;
					if (g < 0.0f) g = 0.0f; if (g > 1.0f) g = 1.0f;
					if (b < 0.0f) b = 0.0f; if (b > 1.0f) b = 1.0f;
					fz_snprintf(da_buf, (int)sizeof da_buf, "/%s %g Tf %g %g %g rg", "Helv", font_size, r, g, b);
					pdf_dict_puts_drop(ctx, annot_obj, "DA", pdf_new_string(ctx, da_buf, strlen(da_buf)));
				}
			}
			pdf_set_annot_border_width(ctx, annot, 0.0f);
			pp_pdf_update_annot_compat(ctx, pdf, annot);
#else
			{
				fz_point pos;
				pos.x = rect.x0;
				pos.y = rect.y0;
				/* Old MuPDF expects full Base-14 font names (e.g. Helvetica), not abbreviations. */
				pdf_set_free_text_details(ctx, pdf, annot, &pos, (char *)contents, (char *)"Helvetica", font_size, color);
			}
			pp_pdf_set_annot_rect_compat(ctx, pdf, annot, rect);
			pp_pdf_set_annot_color_opacity_dict(ctx, pdf, annot, color, 1.0f);
			pp_pdf_update_annot_compat(ctx, pdf, annot);
#endif

			/* Persist app-only metadata: userResized=false (auto-fit is allowed until the user resizes). */
			{
				pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
				if (annot_obj)
				{
					pdf_obj *key = NULL;
#if defined(PDF_TRUE) && defined(PDF_FALSE)
					key = pdf_new_name(ctx, "OPDUserResized");
					pdf_dict_put(ctx, annot_obj, key, PDF_FALSE);
#else
					key = pdf_new_name(ctx, pdf, "OPDUserResized");
					pdf_obj *val = pdf_new_bool(ctx, pdf, 0);
					pdf_dict_put_drop(ctx, annot_obj, key, val);
#endif
					pdf_drop_obj(ctx, key);
				}
			}
		}
#if PP_MUPDF_API_NEW
		else if (annot_type == (int)PDF_ANNOT_CARET)
#else
		else if (annot_type == (int)FZ_ANNOT_CARET)
#endif
		{
			float minx = 0.0f, maxx = 0.0f, miny = 0.0f, maxy = 0.0f;
			fz_rect rect;
			if (point_count < 1)
				fz_throw(ctx, FZ_ERROR_GENERIC, "CARET annot requires at least 1 point");

			minx = maxx = pts_pdf[0].x;
			miny = maxy = pts_pdf[0].y;
			for (i = 1; i < point_count; i++)
			{
				minx = fminf(minx, pts_pdf[i].x);
				maxx = fmaxf(maxx, pts_pdf[i].x);
				miny = fminf(miny, pts_pdf[i].y);
				maxy = fmaxf(maxy, pts_pdf[i].y);
			}

			if (minx > maxx) { float t = minx; minx = maxx; maxx = t; }
			if (miny > maxy) { float t = miny; miny = maxy; maxy = t; }

			/* Keep the caret visible: apply a reasonable minimum box. */
			if (maxx - minx < 6.0f)
			{
				float pad = (6.0f - (maxx - minx)) * 0.5f;
				minx -= pad;
				maxx += pad;
			}
			if (maxy - miny < 10.0f)
			{
				float pad = (10.0f - (maxy - miny));
				maxy += pad;
			}

			rect.x0 = minx;
			rect.y0 = miny;
			rect.x1 = maxx;
			rect.y1 = maxy;

			pp_pdf_set_annot_rect_compat(ctx, pdf, annot, rect);
			pp_pdf_set_annot_contents_compat(ctx, pdf, annot, contents);
			pp_pdf_set_annot_color_opacity_dict(ctx, pdf, annot, color, opacity > 0.0f ? opacity : 1.0f);
			pp_pdf_update_annot_compat(ctx, pdf, annot);
		}
		else
		{
			/* Markup annotations (highlight/underline/strikeout). */
			if (point_count >= 4)
				pp_pdf_set_markup_quadpoints_compat(ctx, pdf, annot, pts_pdf, point_count);
			pp_pdf_set_annot_color_opacity_dict(ctx, pdf, annot, color, opacity > 0.0f ? opacity : 1.0f);
			pp_pdf_update_annot_compat(ctx, pdf, annot);
		}

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
pp_pdf_add_annot(pp_ctx *pp, pp_doc *doc, int page_index,
                 int pageW, int pageH,
                 int annot_type,
                 const pp_point *points, int point_count,
                 const float color_rgb[3], float opacity,
                 const char *contents_utf8,
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

		ok = pp_pdf_add_annot_impl(pp->ctx, doc->doc, page,
		                          pageW, pageH,
		                          annot_type,
		                          points, point_count,
		                          color_rgb, opacity,
		                          contents_utf8,
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
pp_pdf_add_annot_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                       int pageW, int pageH,
                       int annot_type,
                       const pp_point *points, int point_count,
                       const float color_rgb[3], float opacity,
                       const char *contents_utf8,
                       long long *out_object_id)
{
	(void)page_index;
	return pp_pdf_add_annot_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                            pageW, pageH,
	                            annot_type,
	                            points, point_count,
	                            color_rgb, opacity,
	                            contents_utf8,
	                            out_object_id);
}
