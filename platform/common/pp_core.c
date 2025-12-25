#include "pp_core.h"

#include <stdlib.h>
#include <string.h>

#include <pthread.h>

#include <mupdf/fitz.h>

#if defined(FZ_VERSION_MAJOR) && defined(FZ_VERSION_MINOR)
#define PP_MUPDF_API_NEW 1
#else
#define PP_MUPDF_API_NEW 0
#endif

struct pp_ctx
{
	fz_context *ctx;
	pthread_mutex_t lock;
};

typedef struct pp_cached_page
{
	int page_index;
	unsigned int last_used;
	fz_rect bounds_72;
	fz_page *page;
	fz_display_list *display_list;
} pp_cached_page;

struct pp_doc
{
	fz_document *doc;
	char format[64];
	unsigned int use_counter;
	pp_cached_page pages[3];
};

static void
pp_lock(pp_ctx *pp)
{
	pthread_mutex_lock(&pp->lock);
}

static void
pp_unlock(pp_ctx *pp)
{
	pthread_mutex_unlock(&pp->lock);
}

static fz_rect
pp_bound_page_compat(fz_context *ctx, fz_page *page)
{
#if PP_MUPDF_API_NEW
	return fz_bound_page(ctx, page);
#else
	fz_rect bounds;
	fz_bound_page(ctx, page, &bounds);
	return bounds;
#endif
}

static fz_matrix
pp_scale_compat(float sx, float sy)
{
#if PP_MUPDF_API_NEW
	return fz_scale(sx, sy);
#else
	fz_matrix m;
	fz_scale(&m, sx, sy);
	return m;
#endif
}

static fz_matrix
pp_identity_compat(void)
{
#if PP_MUPDF_API_NEW
	return fz_identity;
#else
	return fz_identity;
#endif
}

static fz_matrix
pp_pre_translate_compat(fz_matrix m, float tx, float ty)
{
#if PP_MUPDF_API_NEW
	return fz_pre_translate(m, tx, ty);
#else
	fz_pre_translate(&m, tx, ty);
	return m;
#endif
}

static fz_device *
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
pp_close_device_compat(fz_context *ctx, fz_device *dev)
{
#if PP_MUPDF_API_NEW
	fz_close_device(ctx, dev);
#else
	(void)ctx;
	(void)dev;
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
pp_run_page_all_compat(fz_context *ctx, fz_page *page, fz_device *dev, fz_matrix ctm, fz_cookie *cookie)
{
#if PP_MUPDF_API_NEW
	fz_run_page_contents(ctx, page, dev, ctm, cookie);
	fz_run_page_annots(ctx, page, dev, ctm, cookie);
	fz_run_page_widgets(ctx, page, dev, ctm, cookie);
#else
	fz_run_page(ctx, page, dev, &ctm, cookie);
#endif
}

static void
pp_drop_cached_page(fz_context *ctx, pp_cached_page *pc)
{
	if (!pc)
		return;
	if (pc->display_list)
		fz_drop_display_list(ctx, pc->display_list);
	pc->display_list = NULL;
	if (pc->page)
		fz_drop_page(ctx, pc->page);
	pc->page = NULL;
	pc->page_index = -1;
	pc->last_used = 0;
	pc->bounds_72 = fz_empty_rect;
}

static void
pp_clear_page_cache_locked(fz_context *ctx, pp_doc *doc)
{
	int i;
	if (!doc)
		return;
	for (i = 0; i < (int)(sizeof(doc->pages) / sizeof(doc->pages[0])); i++)
		pp_drop_cached_page(ctx, &doc->pages[i]);
	doc->use_counter = 1;
}

static int
pp_cache_find_index(pp_doc *doc, int page_index)
{
	int i;
	for (i = 0; i < (int)(sizeof(doc->pages) / sizeof(doc->pages[0])); i++)
	{
		if (doc->pages[i].page && doc->pages[i].page_index == page_index)
			return i;
	}
	return -1;
}

static int
pp_cache_choose_slot(pp_doc *doc)
{
	int i;
	int best = 0;
	unsigned int best_used = 0;

	for (i = 0; i < (int)(sizeof(doc->pages) / sizeof(doc->pages[0])); i++)
	{
		pp_cached_page *pc = &doc->pages[i];
		if (!pc->page)
			return i;
		if (i == 0 || pc->last_used < best_used)
		{
			best = i;
			best_used = pc->last_used;
		}
	}
	return best;
}

static pp_cached_page *
pp_cache_ensure_page_locked(fz_context *ctx, pp_doc *doc, int page_index)
{
	int idx;
	pp_cached_page *pc;

	idx = pp_cache_find_index(doc, page_index);
	if (idx >= 0)
	{
		pc = &doc->pages[idx];
		pc->last_used = doc->use_counter++;
		return pc;
	}

	idx = pp_cache_choose_slot(doc);
	pc = &doc->pages[idx];
	pp_drop_cached_page(ctx, pc);

	pc->page_index = page_index;
	pc->last_used = doc->use_counter++;
	pc->display_list = NULL;

	pc->page = fz_load_page(ctx, doc->doc, page_index);
	pc->bounds_72 = pp_bound_page_compat(ctx, pc->page);

	return pc;
}

static void
pp_cache_ensure_display_list_locked(fz_context *ctx, pp_cached_page *pc, fz_cookie *cookie)
{
	fz_device *dev = NULL;
	fz_display_list *list = NULL;
	fz_matrix ident;

	if (!pc || !pc->page || pc->display_list)
		return;

	ident = pp_identity_compat();
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

pp_ctx *
pp_new(void)
{
	pp_ctx *pp = (pp_ctx *)calloc(1, sizeof(pp_ctx));
	if (!pp)
		return NULL;

	pp->ctx = fz_new_context(NULL, NULL, FZ_STORE_UNLIMITED);
	if (!pp->ctx)
	{
		free(pp);
		return NULL;
	}

	fz_try(pp->ctx)
		fz_register_document_handlers(pp->ctx);
	fz_catch(pp->ctx)
	{
		fz_drop_context(pp->ctx);
		free(pp);
		return NULL;
	}

	if (pthread_mutex_init(&pp->lock, NULL) != 0)
	{
		fz_drop_context(pp->ctx);
		free(pp);
		return NULL;
	}

	return pp;
}

void
pp_drop(pp_ctx *pp)
{
	if (!pp)
		return;
	if (pp->ctx)
		fz_drop_context(pp->ctx);
	pthread_mutex_destroy(&pp->lock);
	free(pp);
}

pp_doc *
pp_open(pp_ctx *pp, const char *path)
{
	pp_doc *doc;
	int i;

	if (!pp || !pp->ctx || !path)
		return NULL;

	doc = (pp_doc *)calloc(1, sizeof(pp_doc));
	if (!doc)
		return NULL;

	for (i = 0; i < (int)(sizeof(doc->pages) / sizeof(doc->pages[0])); i++)
	{
		doc->pages[i].page_index = -1;
		doc->pages[i].last_used = 0;
		doc->pages[i].bounds_72 = fz_empty_rect;
		doc->pages[i].page = NULL;
		doc->pages[i].display_list = NULL;
	}
	doc->use_counter = 1;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		doc->doc = fz_open_document(pp->ctx, path);
		doc->format[0] = 0;
		fz_lookup_metadata(pp->ctx, doc->doc, FZ_META_FORMAT, doc->format, (int)sizeof(doc->format));
	}
	fz_always(pp->ctx)
		pp_unlock(pp);
	fz_catch(pp->ctx)
	{
		free(doc);
		return NULL;
	}

	return doc;
}

void
pp_close(pp_ctx *pp, pp_doc *doc)
{
	if (!pp || !pp->ctx || !doc)
		return;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pp_clear_page_cache_locked(pp->ctx, doc);
		if (doc->doc)
			fz_drop_document(pp->ctx, doc->doc);
	}
	fz_always(pp->ctx)
		pp_unlock(pp);
	fz_catch(pp->ctx)
	{
		/* best-effort cleanup; keep going */
	}
	free(doc);
}

const char *
pp_format(pp_ctx *pp, pp_doc *doc)
{
	(void)pp;
	if (!doc)
		return "";
	return doc->format;
}

int
pp_count_pages(pp_ctx *pp, pp_doc *doc)
{
	int page_count = -1;

	if (!pp || !pp->ctx || !doc || !doc->doc)
		return -1;

	pp_lock(pp);
	fz_try(pp->ctx)
		page_count = fz_count_pages(pp->ctx, doc->doc);
	fz_always(pp->ctx)
		pp_unlock(pp);
	fz_catch(pp->ctx)
		page_count = -1;

	return page_count;
}

int
pp_page_size(pp_ctx *pp, pp_doc *doc, int page_index, float *out_w, float *out_h)
{
	fz_rect bounds;
	int ok = 0;
	pp_cached_page *pc = NULL;

	if (out_w)
		*out_w = 0;
	if (out_h)
		*out_h = 0;

	if (!pp || !pp->ctx || !doc || !doc->doc || !out_w || !out_h || page_index < 0)
		return 0;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pc = pp_cache_ensure_page_locked(pp->ctx, doc, page_index);
		bounds = pc ? pc->bounds_72 : fz_empty_rect;
		*out_w = bounds.x1 - bounds.x0;
		*out_h = bounds.y1 - bounds.y0;
		ok = 1;
	}
	fz_always(pp->ctx)
		pp_unlock(pp);
	fz_catch(pp->ctx)
	{
		ok = 0;
	}

	return ok;
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
                          unsigned char *rgba, int stride, fz_cookie *cookie)
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
			pp_run_page_all_compat(ctx, page, dev, ctm, cookie);
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
		                                  rgba, stride, fz_cookie_ptr);
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
	                                rgba, stride, fz_cookie_ptr);
}

pp_cookie *
pp_cookie_new(pp_ctx *pp)
{
	if (!pp || !pp->ctx)
		return NULL;
	pp_lock(pp);
	pp_cookie *cookie = (pp_cookie *)fz_calloc_no_throw(pp->ctx, 1, sizeof(fz_cookie));
	pp_unlock(pp);
	return cookie;
}

void
pp_cookie_drop(pp_ctx *pp, pp_cookie *cookie)
{
	if (!pp || !pp->ctx || !cookie)
		return;
	pp_lock(pp);
	fz_free(pp->ctx, (fz_cookie *)cookie);
	pp_unlock(pp);
}

pp_cookie *
pp_cookie_new_mupdf(void *mupdf_ctx)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	if (!ctx)
		return NULL;
	return (pp_cookie *)fz_calloc_no_throw(ctx, 1, sizeof(fz_cookie));
}

void
pp_cookie_drop_mupdf(void *mupdf_ctx, pp_cookie *cookie)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	if (!ctx || !cookie)
		return;
	fz_free(ctx, (fz_cookie *)cookie);
}

void
pp_cookie_reset(pp_cookie *cookie)
{
	if (!cookie)
		return;
	memset((fz_cookie *)cookie, 0, sizeof(fz_cookie));
}

void
pp_cookie_abort(pp_cookie *cookie)
{
	fz_cookie *fz_cookie_ptr = (fz_cookie *)cookie;
	if (!fz_cookie_ptr)
		return;
	fz_cookie_ptr->abort = 1;
}

int
pp_cookie_aborted(pp_cookie *cookie)
{
	fz_cookie *fz_cookie_ptr = (fz_cookie *)cookie;
	if (!fz_cookie_ptr || fz_cookie_ptr->abort)
		return 1;
	return 0;
}
