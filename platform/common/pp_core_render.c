#include "pp_core_internal.h"

fz_device *
pp_new_draw_device_compat(fz_context *ctx, fz_pixmap *pix)
{
#if PP_MUPDF_API_NEW
	return fz_new_draw_device(ctx, fz_identity, pix);
#else
	return fz_new_draw_device(ctx, pix);
#endif
}

static fz_display_list *
pp_new_display_list_compat(fz_context *ctx, fz_rect bounds)
{
#if PP_MUPDF_API_NEW
	return fz_new_display_list(ctx, bounds);
#else
	(void)bounds;
	return fz_new_display_list(ctx);
#endif
}

static void
pp_run_display_list_compat(fz_context *ctx, fz_display_list *list, fz_device *dev, fz_matrix ctm, fz_rect scissor, fz_cookie *cookie)
{
#if PP_MUPDF_API_NEW
	fz_run_display_list(ctx, list, dev, ctm, scissor, cookie);
#else
	fz_run_display_list(ctx, list, dev, &ctm, &scissor, cookie);
#endif
}

static fz_pixmap *
pp_new_pixmap_with_bbox_and_data_rgba_compat(fz_context *ctx, fz_irect bbox, unsigned char *rgba)
{
#if PP_MUPDF_API_NEW
	return fz_new_pixmap_with_bbox_and_data(ctx, fz_device_rgb(ctx), bbox, NULL, 1, rgba);
#else
	return fz_new_pixmap_with_bbox_and_data(ctx, fz_device_rgb(ctx), &bbox, rgba);
#endif
}

static void
pp_run_page_with_annots_compat(fz_context *ctx, fz_page *page, fz_device *dev, fz_matrix ctm, fz_cookie *cookie, int render_annots)
{
#if PP_MUPDF_API_NEW
	fz_run_page_contents(ctx, page, dev, ctm, cookie);
	if (render_annots)
		fz_run_page_annots(ctx, page, dev, ctm, cookie);
	fz_run_page_widgets(ctx, page, dev, ctm, cookie);
#else
	fz_run_page(ctx, page, dev, &ctm, cookie);
#endif
}

void
pp_run_page_all_compat(fz_context *ctx, fz_page *page, fz_device *dev, fz_matrix ctm, fz_cookie *cookie)
{
	pp_run_page_with_annots_compat(ctx, page, dev, ctm, cookie, 1);
}

static void
pp_cache_ensure_display_list_locked(fz_context *ctx, pp_cached_page *pc, fz_cookie *cookie)
{
	fz_device *dev = NULL;
	fz_display_list *list = NULL;
	fz_matrix ident;

	if (!pc || !pc->page || pc->display_list)
		return;

	ident = fz_identity;
	fz_var(dev);
	fz_var(list);

	fz_try(ctx)
	{
		list = pp_new_display_list_compat(ctx, pc->bounds_72);
		dev = fz_new_list_device(ctx, list);
		pp_run_page_all_compat(ctx, pc->page, dev, ident, cookie);
		pp_close_device_compat(ctx, dev);
		pc->display_list = list;
		list = NULL;
	}
	fz_always(ctx)
	{
		if (dev)
			fz_drop_device(ctx, dev);
		if (list)
			fz_drop_display_list(ctx, list);
	}
	fz_catch(ctx)
	{
		/* Leave display_list NULL; callers will fall back to direct rendering. */
	}
}

int
pp_render_page_rgba(pp_ctx *pp, pp_doc *doc, int page_index, int out_w, int out_h, unsigned char *rgba)
{
	/* Full-page render is just a patch with origin at (0,0). */
	return pp_render_patch_rgba(pp, doc, page_index,
	                            out_w, out_h,
	                            0, 0, out_w, out_h,
	                            rgba, out_w * 4, NULL);
}

static int
pp_render_patch_rgba_impl(fz_context *ctx, fz_document *doc, fz_page *borrowed_page, fz_display_list *borrowed_list, int page_index,
                          int pageW, int pageH,
                          int patchX, int patchY, int patchW, int patchH,
                          unsigned char *rgba, int stride, fz_cookie *cookie,
                          int render_annots)
{
	fz_page *page = NULL;
	fz_pixmap *pix = NULL;
	fz_device *dev = NULL;
	fz_rect bounds;
	fz_matrix ctm;
	float page_w;
	float page_h;
	int ok = 0;
	int row_pixels;
	int owns_page = 0;
	fz_display_list *list = NULL;

	(void)patchW;

	if (!ctx || !doc || !rgba || page_index < 0 || pageW <= 0 || pageH <= 0 || patchH <= 0 || stride <= 0)
		return 0;
	render_annots = render_annots ? 1 : 0;
	if ((stride & 3) != 0)
		return 0;
	row_pixels = stride / 4;
	if (row_pixels <= 0)
		return 0;

	fz_try(ctx)
	{
		fz_irect pixbbox;

		if (borrowed_page)
		{
			page = borrowed_page;
		}
		else
		{
			page = fz_load_page(ctx, doc, page_index);
			owns_page = 1;
		}
		if (borrowed_list)
		{
			list = borrowed_list;
		}
		else
		{
			list = NULL;
		}

		bounds = pp_bound_page_compat(ctx, page);
		page_w = bounds.x1 - bounds.x0;
		page_h = bounds.y1 - bounds.y0;
		if (page_w <= 0 || page_h <= 0)
			fz_throw(ctx, FZ_ERROR_GENERIC, "invalid page bounds");

		ctm = pp_scale_compat((float)pageW / page_w, (float)pageH / page_h);
		ctm = pp_pre_translate_compat(ctm, -bounds.x0, -bounds.y0);

		pixbbox.x0 = patchX;
		pixbbox.y0 = patchY;
		pixbbox.x1 = patchX + row_pixels;
		pixbbox.y1 = patchY + patchH;

		pix = pp_new_pixmap_with_bbox_and_data_rgba_compat(ctx, pixbbox, rgba);
		fz_clear_pixmap_with_value(ctx, pix, 255);

		dev = pp_new_draw_device_compat(ctx, pix);
		if (list)
		{
			fz_rect scissor;
			scissor.x0 = (float)pixbbox.x0;
			scissor.y0 = (float)pixbbox.y0;
			scissor.x1 = (float)pixbbox.x1;
			scissor.y1 = (float)pixbbox.y1;
			pp_run_display_list_compat(ctx, list, dev, ctm, scissor, cookie);
		}
		else
		{
			pp_run_page_with_annots_compat(ctx, page, dev, ctm, cookie, render_annots);
		}

		if (cookie && cookie->abort)
			ok = 0;
		else
			ok = 1;

	}
	fz_always(ctx)
	{
		if (dev)
			fz_drop_device(ctx, dev);
		if (pix)
			fz_drop_pixmap(ctx, pix);
		if (owns_page && page)
			fz_drop_page(ctx, page);
	}
	fz_catch(ctx)
	{
		ok = 0;
	}

	return ok;
}

int
pp_render_patch_rgba(pp_ctx *pp, pp_doc *doc, int page_index,
                     int pageW, int pageH,
                     int patchX, int patchY, int patchW, int patchH,
                     unsigned char *rgba, int stride, pp_cookie *cookie)
{
	fz_cookie stack_cookie = {0};
	fz_cookie *fz_cookie_ptr = cookie ? (fz_cookie *)cookie : &stack_cookie;
	pp_cached_page *pc = NULL;
	fz_page *page = NULL;
	fz_display_list *list = NULL;

	if (!pp || !pp->ctx || !doc || !doc->doc)
		return 0;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pc = pp_cache_ensure_page_locked(pp->ctx, doc, page_index);
		if (pc)
		{
			pp_cache_ensure_display_list_locked(pp->ctx, pc, fz_cookie_ptr);
			page = pc->page;
			list = pc->display_list;
		}
	}
	fz_always(pp->ctx)
	{
		/* Keep the lock held for the render: MuPDF's fz_context is not thread-safe. */
	}
	fz_catch(pp->ctx)
	{
		pp_unlock(pp);
		return 0;
	}

	{
		int ok = pp_render_patch_rgba_impl(pp->ctx, doc->doc, page, list, page_index,
		                                  pageW, pageH,
		                                  patchX, patchY, patchW, patchH,
		                                  rgba, stride, fz_cookie_ptr,
		                                  1);
		pp_unlock(pp);
		return ok;
	}
}

int
pp_render_patch_rgba_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                           int pageW, int pageH,
                           int patchX, int patchY, int patchW, int patchH,
                           unsigned char *rgba, int stride, pp_cookie *cookie)
{
	fz_cookie stack_cookie = {0};
	fz_cookie *fz_cookie_ptr = cookie ? (fz_cookie *)cookie : &stack_cookie;
	return pp_render_patch_rgba_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page, NULL, page_index,
	                                pageW, pageH,
	                                patchX, patchY, patchW, patchH,
	                                rgba, stride, fz_cookie_ptr,
	                                1);
}

int
pp_render_patch_rgba_mupdf_opts(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                               int pageW, int pageH,
                               int patchX, int patchY, int patchW, int patchH,
                               unsigned char *rgba, int stride, pp_cookie *cookie,
                               int render_annots)
{
	fz_cookie stack_cookie = {0};
	fz_cookie *fz_cookie_ptr = cookie ? (fz_cookie *)cookie : &stack_cookie;
	return pp_render_patch_rgba_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page, NULL, page_index,
	                                pageW, pageH,
	                                patchX, patchY, patchW, patchH,
	                                rgba, stride, fz_cookie_ptr,
	                                render_annots);
}

