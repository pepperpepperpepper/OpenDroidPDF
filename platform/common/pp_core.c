#include "pp_core.h"

#include <stdlib.h>
#include <string.h>

#include <pthread.h>

#include <mupdf/fitz.h>
#include <mupdf/pdf.h>

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

static fz_matrix
pp_invert_matrix_compat(fz_matrix m)
{
#if PP_MUPDF_API_NEW
	return fz_invert_matrix(m);
#else
	fz_matrix inv;
	fz_invert_matrix(&inv, &m);
	return inv;
#endif
}

static fz_point
pp_transform_point_compat(fz_point p, fz_matrix m)
{
#if PP_MUPDF_API_NEW
	return fz_transform_point(p, m);
#else
	fz_transform_point(&p, &m);
	return p;
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

static pdf_annot *
pp_pdf_create_annot_compat(fz_context *ctx, pdf_document *doc, pdf_page *page, int annot_type)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	return pdf_create_annot(ctx, page, (enum pdf_annot_type)annot_type);
#else
	return pdf_create_annot(ctx, doc, page, (fz_annot_type)annot_type);
#endif
}

static void
pp_pdf_delete_annot_compat(fz_context *ctx, pdf_document *doc, pdf_page *page, pdf_annot *annot)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	pdf_delete_annot(ctx, page, annot);
#else
	pdf_delete_annot(ctx, doc, page, annot);
#endif
}

static void
pp_pdf_update_page_compat(fz_context *ctx, pdf_document *doc, pdf_page *page)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	(void)pdf_update_page(ctx, page);
#else
	pdf_update_page(ctx, doc, page);
#endif
}

static void
pp_pdf_update_annot_compat(fz_context *ctx, pdf_document *doc, pdf_annot *annot)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	(void)pdf_update_annot(ctx, annot);
#else
	pdf_update_annot(ctx, doc, annot);
#endif
}

static void
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

static pdf_annot *
pp_pdf_first_annot_compat(fz_context *ctx, pdf_page *page)
{
	return pdf_first_annot(ctx, page);
}

static pdf_annot *
pp_pdf_next_annot_compat(fz_context *ctx, pdf_page *page, pdf_annot *annot)
{
#if PP_MUPDF_API_NEW
	(void)page;
	return pdf_next_annot(ctx, annot);
#else
	return pdf_next_annot(ctx, page, annot);
#endif
}

static pdf_obj *
pp_pdf_annot_obj_compat(fz_context *ctx, pdf_annot *annot)
{
#if PP_MUPDF_API_NEW
	return pdf_annot_obj(ctx, annot);
#else
	(void)ctx;
	return annot ? annot->obj : NULL;
#endif
}

static pdf_obj *
pp_pdf_new_real_compat(fz_context *ctx, pdf_document *doc, float f)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	return pdf_new_real(ctx, f);
#else
	return pdf_new_real(ctx, doc, f);
#endif
}

static pdf_obj *
pp_pdf_new_rect_compat(fz_context *ctx, pdf_document *doc, fz_rect rect)
{
#if PP_MUPDF_API_NEW
	return pdf_new_rect(ctx, doc, rect);
#else
	return pdf_new_rect(ctx, doc, &rect);
#endif
}

static fz_matrix
pp_pdf_page_to_pdf_ctm_compat(fz_context *ctx, pdf_page *page)
{
#if PP_MUPDF_API_NEW
	fz_rect mediabox;
	fz_matrix ctm;
	pdf_page_transform(ctx, page, &mediabox, &ctm);
	return ctm;
#else
	fz_matrix inv;
	fz_invert_matrix(&inv, &page->ctm);
	return inv;
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

static long long
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

int
pp_pdf_save_as(pp_ctx *pp, pp_doc *doc, const char *path)
{
	int ok = 0;
	if (!pp || !pp->ctx || !doc || !doc->doc || !path || !*path)
		return 0;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pdf_document *pdf = pdf_specifics(pp->ctx, doc->doc);
		if (!pdf)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "document is not a PDF");

#if PP_MUPDF_API_NEW
		pdf_write_options opts;
		pdf_parse_write_options(pp->ctx, &opts, NULL);
		pdf_save_document(pp->ctx, pdf, path, &opts);
#else
		/* fz_write_document uses non-const char* in older MuPDF. */
		fz_write_document(pp->ctx, doc->doc, (char *)path, NULL);
#endif
		ok = 1;
	}
	fz_always(pp->ctx)
		pp_unlock(pp);
	fz_catch(pp->ctx)
		ok = 0;

	return ok;
}
