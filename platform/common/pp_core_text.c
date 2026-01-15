#include "pp_core_internal.h"

#include <string.h>

static int
pp_page_text_utf8_impl(fz_context *ctx, fz_document *doc, fz_page *borrowed_page, int page_index, char **out_text_utf8)
{
	fz_page *page = NULL;
	fz_device *dev = NULL;
	fz_buffer *buf = NULL;
	fz_output *out = NULL;
	fz_rect bounds;
	fz_matrix ctm;
	int ok = 0;
	int owns_page = 0;
	char *out_str = NULL;

#if PP_MUPDF_API_NEW
	fz_stext_page *text = NULL;
	fz_var(text);
#else
	fz_text_sheet *sheet = NULL;
	fz_text_page *text = NULL;
	fz_var(sheet);
	fz_var(text);
#endif

	fz_var(page);
	fz_var(dev);
	fz_var(buf);
	fz_var(out);

	if (!ctx || !doc || !out_text_utf8 || page_index < 0)
		return 0;
	*out_text_utf8 = NULL;

	fz_try(ctx)
	{
		if (borrowed_page)
		{
			page = borrowed_page;
		}
		else
		{
			page = fz_load_page(ctx, doc, page_index);
			owns_page = 1;
		}

		bounds = pp_bound_page_compat(ctx, page);
		ctm = fz_identity;
#if !PP_MUPDF_API_NEW
		(void)bounds;
#endif

#if PP_MUPDF_API_NEW
		text = fz_new_stext_page(ctx, bounds);
		dev = fz_new_stext_device(ctx, text, NULL);
		pp_run_page_all_compat(ctx, page, dev, ctm, NULL);
		pp_close_device_compat(ctx, dev);
		fz_drop_device(ctx, dev);
		dev = NULL;

		buf = fz_new_buffer(ctx, 256);
		out = fz_new_output_with_buffer(ctx, buf);
		fz_print_stext_page_as_text(ctx, out, text);
		pp_close_output_compat(ctx, out);
#else
		sheet = fz_new_text_sheet(ctx);
		text = fz_new_text_page(ctx);
		dev = fz_new_text_device(ctx, sheet, text);
		pp_run_page_all_compat(ctx, page, dev, ctm, NULL);
		pp_close_device_compat(ctx, dev);
		fz_drop_device(ctx, dev);
		dev = NULL;

		buf = fz_new_buffer(ctx, 256);
		out = fz_new_output_with_buffer(ctx, buf);
		fz_print_text_page(ctx, out, text);
		pp_close_output_compat(ctx, out);
#endif

		{
			unsigned char *data = NULL;
			int len = fz_buffer_storage(ctx, buf, &data);
			if (len < 0)
				len = 0;
			out_str = (char *)fz_malloc(ctx, (size_t)len + 1u);
			if (len > 0 && data)
				memcpy(out_str, data, (size_t)len);
			out_str[len] = '\0';
		}

		*out_text_utf8 = out_str;
		out_str = NULL;
		ok = 1;
	}
	fz_always(ctx)
	{
		fz_drop_output(ctx, out);
		fz_drop_buffer(ctx, buf);
		if (dev)
			fz_drop_device(ctx, dev);
#if PP_MUPDF_API_NEW
		fz_drop_stext_page(ctx, text);
#else
		fz_drop_text_page(ctx, text);
		fz_drop_text_sheet(ctx, sheet);
#endif
		if (owns_page && page)
			fz_drop_page(ctx, page);
		fz_free(ctx, out_str);
	}
	fz_catch(ctx)
	{
		*out_text_utf8 = NULL;
		ok = 0;
	}

	return ok;
}

int
pp_page_text_utf8(pp_ctx *pp, pp_doc *doc, int page_index, char **out_text_utf8)
{
	pp_cached_page *pc = NULL;
	int ok = 0;

	if (!pp || !pp->ctx || !doc || !doc->doc || !out_text_utf8)
		return 0;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pc = pp_cache_ensure_page_locked(pp->ctx, doc, page_index);
		ok = pc && pc->page ? pp_page_text_utf8_impl(pp->ctx, doc->doc, pc->page, page_index, out_text_utf8) : 0;
	}
	fz_catch(pp->ctx)
	{
		ok = 0;
		*out_text_utf8 = NULL;
	}
	pp_unlock(pp);

	return ok;
}

int
pp_page_text_utf8_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index, char **out_text_utf8)
{
	return pp_page_text_utf8_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page, page_index, out_text_utf8);
}

static int
pp_search_page_impl(fz_context *ctx, fz_document *doc, fz_page *borrowed_page, int page_index,
                    int pageW, int pageH,
                    const char *needle,
                    pp_rect *hit_rects, int hit_max)
{
	fz_page *page = NULL;
	fz_device *dev = NULL;
	fz_rect bounds;
	fz_matrix ctm;
	float page_w;
	float page_h;
	int owns_page = 0;
	int hit_count = -1;

#if PP_MUPDF_API_NEW
	fz_stext_page *text = NULL;
	fz_quad *quads = NULL;
	fz_var(text);
	fz_var(quads);
#else
	fz_text_sheet *sheet = NULL;
	fz_text_page *text = NULL;
	fz_rect *hit_bbox = NULL;
	fz_var(sheet);
	fz_var(text);
	fz_var(hit_bbox);
#endif

	fz_var(page);
	fz_var(dev);

	if (!ctx || !doc || !needle || !hit_rects || hit_max <= 0 || page_index < 0 || pageW <= 0 || pageH <= 0)
		return -1;

	fz_try(ctx)
	{
		int i;
		if (borrowed_page)
		{
			page = borrowed_page;
		}
		else
		{
			page = fz_load_page(ctx, doc, page_index);
			owns_page = 1;
		}

		bounds = pp_bound_page_compat(ctx, page);
		page_w = bounds.x1 - bounds.x0;
		page_h = bounds.y1 - bounds.y0;
		if (page_w <= 0 || page_h <= 0)
			fz_throw(ctx, FZ_ERROR_GENERIC, "invalid page bounds");

		ctm = pp_scale_compat((float)pageW / page_w, (float)pageH / page_h);
		ctm = pp_pre_translate_compat(ctm, -bounds.x0, -bounds.y0);

#if PP_MUPDF_API_NEW
		text = fz_new_stext_page(ctx, bounds);
		dev = fz_new_stext_device(ctx, text, NULL);
		pp_run_page_all_compat(ctx, page, dev, ctm, NULL);
		pp_close_device_compat(ctx, dev);
		fz_drop_device(ctx, dev);
		dev = NULL;

		quads = (fz_quad *)fz_malloc(ctx, (size_t)hit_max * sizeof(fz_quad));
		hit_count = fz_search_stext_page(ctx, text, needle, NULL, quads, hit_max);
		for (i = 0; i < hit_count; i++)
		{
			fz_rect r = fz_rect_from_quad(quads[i]);
			hit_rects[i].x0 = r.x0;
			hit_rects[i].y0 = r.y0;
			hit_rects[i].x1 = r.x1;
			hit_rects[i].y1 = r.y1;
		}
#else
		sheet = fz_new_text_sheet(ctx);
		text = fz_new_text_page(ctx);
		dev = fz_new_text_device(ctx, sheet, text);
		pp_run_page_all_compat(ctx, page, dev, ctm, NULL);
		pp_close_device_compat(ctx, dev);
		fz_drop_device(ctx, dev);
		dev = NULL;

		hit_bbox = (fz_rect *)fz_malloc(ctx, (size_t)hit_max * sizeof(fz_rect));
		hit_count = fz_search_text_page(ctx, text, needle, hit_bbox, hit_max);
		for (i = 0; i < hit_count; i++)
		{
			hit_rects[i].x0 = hit_bbox[i].x0;
			hit_rects[i].y0 = hit_bbox[i].y0;
			hit_rects[i].x1 = hit_bbox[i].x1;
			hit_rects[i].y1 = hit_bbox[i].y1;
		}
#endif
	}
	fz_always(ctx)
	{
		if (dev)
			fz_drop_device(ctx, dev);
#if PP_MUPDF_API_NEW
		fz_free(ctx, quads);
		fz_drop_stext_page(ctx, text);
#else
		fz_free(ctx, hit_bbox);
		fz_drop_text_page(ctx, text);
		fz_drop_text_sheet(ctx, sheet);
#endif
		if (owns_page && page)
			fz_drop_page(ctx, page);
	}
	fz_catch(ctx)
	{
		hit_count = -1;
	}

	return hit_count;
}

int
pp_search_page(pp_ctx *pp, pp_doc *doc, int page_index,
               int pageW, int pageH,
               const char *needle,
               pp_rect *hit_rects, int hit_max)
{
	pp_cached_page *pc = NULL;
	int hit_count = -1;

	if (!pp || !pp->ctx || !doc || !doc->doc)
		return -1;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pc = pp_cache_ensure_page_locked(pp->ctx, doc, page_index);
		hit_count = pc && pc->page ? pp_search_page_impl(pp->ctx, doc->doc, pc->page, page_index,
		                                                pageW, pageH,
		                                                needle,
		                                                hit_rects, hit_max)
		                           : -1;
	}
	fz_catch(pp->ctx)
	{
		hit_count = -1;
	}
	pp_unlock(pp);

	return hit_count;
}

int
pp_search_page_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                     int pageW, int pageH,
                     const char *needle,
                     pp_rect *hit_rects, int hit_max)
{
	return pp_search_page_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page, page_index,
	                          pageW, pageH,
	                          needle,
	                          hit_rects, hit_max);
}
