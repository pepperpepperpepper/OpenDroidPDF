#include "pp_core.h"

#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <stdio.h>
#include <stdint.h>

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

static fz_rect
pp_transform_rect_compat(fz_rect r, fz_matrix m)
{
#if PP_MUPDF_API_NEW
	return fz_transform_rect(r, m);
#else
	fz_transform_rect(&r, &m);
	return r;
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
pp_close_output_compat(fz_context *ctx, fz_output *out)
{
#if PP_MUPDF_API_NEW
	fz_close_output(ctx, out);
#else
	(void)ctx;
	(void)out;
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

static fz_irect
pp_round_rect_compat(fz_rect rect)
{
#if PP_MUPDF_API_NEW
	return fz_round_rect(rect);
#else
	fz_irect bbox;
	fz_round_rect(&bbox, &rect);
	return bbox;
#endif
}

static void
pp_fill_image_compat(fz_context *ctx, fz_device *dev, fz_image *image, fz_matrix ctm, float alpha)
{
#if PP_MUPDF_API_NEW
	fz_fill_image(ctx, dev, image, ctm, alpha, fz_default_color_params);
#else
	fz_fill_image(ctx, dev, image, &ctm, alpha);
#endif
}

static fz_pixmap *
pp_new_pixmap_with_bbox_compat(fz_context *ctx, fz_colorspace *colorspace, fz_irect bbox)
{
#if PP_MUPDF_API_NEW
	return fz_new_pixmap_with_bbox(ctx, colorspace, bbox, NULL, 1);
#else
	return fz_new_pixmap_with_bbox(ctx, colorspace, &bbox);
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

static void
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

static fz_rect
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

static void
pp_pdf_set_annot_rect_compat(fz_context *ctx, pdf_document *doc, pdf_annot *annot, fz_rect rect)
{
#if PP_MUPDF_API_NEW
	(void)doc;
	pdf_set_annot_rect(ctx, annot, rect);
#else
	pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
	if (annot_obj)
		pdf_dict_puts_drop(ctx, annot_obj, "Rect", pp_pdf_new_rect_compat(ctx, doc, rect));
#endif
}

static void
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

static void
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

void
pp_free_string(pp_ctx *pp, char *s)
{
	if (!pp || !pp->ctx || !s)
		return;
	pp_lock(pp);
	fz_free(pp->ctx, s);
	pp_unlock(pp);
}

void
pp_free_string_mupdf(void *mupdf_ctx, char *s)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	if (!ctx || !s)
		return;
	fz_free(ctx, s);
}

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
		ctm = pp_identity_compat();
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

pp_cookie *
pp_cookie_new(pp_ctx *pp)
{
	/*
	 * Important: cookies must outlive document/context swaps on Android.
	 *
	 * The legacy Android glue used MuPDF's allocator (fz_calloc/fz_free) which
	 * ties the allocation to a specific fz_context. After "Save" the app can
	 * reinitialize the core with a new fz_context, and destroying a cookie using
	 * the new context corrupts the heap (free-with-wrong-allocator), leading to
	 * delayed SIGSEGVs in GC threads.
	 *
	 * Cookies are simple POD structs; allocate them with libc so they can be
	 * freed safely regardless of MuPDF context lifetimes.
	 */
	(void)pp;
	return (pp_cookie *)calloc(1, sizeof(fz_cookie));
}

void
pp_cookie_drop(pp_ctx *pp, pp_cookie *cookie)
{
	(void)pp;
	if (!cookie)
		return;
	free(cookie);
}

pp_cookie *
pp_cookie_new_mupdf(void *mupdf_ctx)
{
	(void)mupdf_ctx;
	return (pp_cookie *)calloc(1, sizeof(fz_cookie));
}

void
pp_cookie_drop_mupdf(void *mupdf_ctx, pp_cookie *cookie)
{
	(void)mupdf_ctx;
	if (!cookie)
		return;
	free(cookie);
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

			/*
			 * Heuristic: keep FreeText readable but avoid the legacy "giant font in a tiny box"
			 * outcome (which clips/truncates text). The rect comes from view-space taps, not a
			 * user-resized box, so favor a conservative default.
			 */
			font_size = (rect.y1 - rect.y0) * 0.18f;
			font_size = fmaxf(10.0f, fminf(18.0f, font_size));

#if PP_MUPDF_API_NEW
			pp_pdf_set_annot_rect_compat(ctx, pdf, annot, rect);
			pp_pdf_set_annot_contents_compat(ctx, pdf, annot, contents);
			pdf_set_annot_default_appearance(ctx, annot, "Helv", font_size, 3, color);
			pdf_set_annot_border_width(ctx, annot, 0.0f);
			/*
			 * MuPDF's FreeText appearance generation treats the annotation's /C color as the
			 * background fill. If we set /C to the same color as the text (from /DA), then
			 * black-on-black results in "invisible" text annotations (rendered as a solid bar).
			 *
			 * Use a neutral white background so the text color (from /DA) remains readable.
			 * This matches common viewer behavior (no visible border, readable text).
			 */
			{
				const float bg[3] = { 1.0f, 1.0f, 1.0f };
				pp_pdf_set_annot_color_opacity_dict(ctx, pdf, annot, bg, 1.0f);
			}
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
			fz_rect rect_pdf = pp_pdf_bound_annot_compat(ctx, pdf, pdfpage, annot);
			fz_rect rect_page = pp_transform_rect_compat(rect_pdf, pdf_to_page);
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

static void
pp_drop_string_list_impl(fz_context *ctx, pp_string_list *list)
{
	if (!ctx || !list)
		return;
	if (list->items)
	{
		for (int i = 0; i < list->count; i++)
		{
			if (list->items[i])
				fz_free(ctx, list->items[i]);
		}
		fz_free(ctx, list->items);
	}
	fz_free(ctx, list);
}

void
pp_drop_string_list(pp_ctx *pp, pp_string_list *list)
{
	if (!pp || !pp->ctx || !list)
		return;
	pp_lock(pp);
	pp_drop_string_list_impl(pp->ctx, list);
	pp_unlock(pp);
}

void
pp_drop_string_list_mupdf(void *mupdf_ctx, pp_string_list *list)
{
	pp_drop_string_list_impl((fz_context *)mupdf_ctx, list);
}

static void
pp_pdf_drop_widget_list_impl(fz_context *ctx, pp_pdf_widget_list *list)
{
	if (!ctx || !list)
		return;
	if (list->items)
	{
		for (int i = 0; i < list->count; i++)
		{
			if (list->items[i].name_utf8)
				fz_free(ctx, list->items[i].name_utf8);
		}
		fz_free(ctx, list->items);
	}
	fz_free(ctx, list);
}

void
pp_pdf_drop_widget_list(pp_ctx *pp, pp_pdf_widget_list *list)
{
	if (!pp || !pp->ctx || !list)
		return;
	pp_lock(pp);
	pp_pdf_drop_widget_list_impl(pp->ctx, list);
	pp_unlock(pp);
}

void
pp_pdf_drop_widget_list_mupdf(void *mupdf_ctx, pp_pdf_widget_list *list)
{
	pp_pdf_drop_widget_list_impl((fz_context *)mupdf_ctx, list);
}

#if PP_MUPDF_API_NEW
typedef pdf_annot pp_mupdf_widget;
#else
typedef pdf_widget pp_mupdf_widget;
#endif

static pp_mupdf_widget *
pp_pdf_first_widget_compat(fz_context *ctx, pdf_document *pdf, pdf_page *page)
{
#if PP_MUPDF_API_NEW
	(void)pdf;
	return (pp_mupdf_widget *)pdf_first_widget(ctx, page);
#else
	return (pp_mupdf_widget *)pdf_first_widget(ctx, pdf, page);
#endif
}

static pp_mupdf_widget *
pp_pdf_next_widget_compat(fz_context *ctx, pdf_document *pdf, pp_mupdf_widget *previous)
{
	(void)pdf;
#if PP_MUPDF_API_NEW
	return (pp_mupdf_widget *)pdf_next_widget(ctx, (pdf_annot *)previous);
#else
	return (pp_mupdf_widget *)pdf_next_widget(ctx, (pdf_widget *)previous);
#endif
}

static int
pp_pdf_widget_type_compat(fz_context *ctx, pdf_document *pdf, pp_mupdf_widget *widget)
{
#if PP_MUPDF_API_NEW
	(void)pdf;
	return (int)pdf_widget_type(ctx, (pdf_annot *)widget);
#else
	(void)pdf;
	return pdf_widget_get_type(ctx, (pdf_widget *)widget);
#endif
}

static fz_rect
pp_pdf_bound_widget_compat(fz_context *ctx, pdf_document *pdf, pp_mupdf_widget *widget)
{
#if PP_MUPDF_API_NEW
	(void)pdf;
	return pdf_bound_widget(ctx, (pdf_annot *)widget);
#else
	(void)pdf;
	fz_rect r;
	pdf_bound_widget(ctx, (pdf_widget *)widget, &r);
	return r;
#endif
}

static char *
pp_pdf_widget_name_utf8_compat(fz_context *ctx, pdf_document *pdf, pp_mupdf_widget *widget)
{
#if PP_MUPDF_API_NEW
	(void)pdf;
	pdf_obj *obj = pdf_annot_obj(ctx, (pdf_annot *)widget);
	if (!obj)
		return NULL;
	return pdf_load_field_name(ctx, obj);
#else
	(void)ctx;
	(void)pdf;
	(void)widget;
	return NULL;
#endif
}

static pp_mupdf_widget *
pp_pdf_nth_widget_compat(fz_context *ctx, pdf_document *pdf, pdf_page *pdfpage, int widget_index)
{
	int idx = 0;
	for (pp_mupdf_widget *w = pp_pdf_first_widget_compat(ctx, pdf, pdfpage); w; w = pp_pdf_next_widget_compat(ctx, pdf, w))
	{
		if (idx == widget_index)
			return w;
		idx++;
	}
	return NULL;
}

static char *
pp_pdf_widget_value_dup_utf8_compat(fz_context *ctx, pdf_document *pdf, pp_mupdf_widget *widget)
{
#if PP_MUPDF_API_NEW
	(void)pdf;
	const char *val = pdf_annot_field_value(ctx, (pdf_annot *)widget);
	return fz_strdup(ctx, val ? val : "");
#else
	char *val = pdf_text_widget_text(ctx, pdf, (pdf_widget *)widget);
	if (!val)
		val = fz_strdup(ctx, "");
	return val;
#endif
}

static int
pp_pdf_widget_set_text_utf8_compat(fz_context *ctx, pdf_document *pdf, pp_mupdf_widget *widget, const char *value_utf8)
{
#if PP_MUPDF_API_NEW
	(void)pdf;
	return pdf_set_text_field_value(ctx, (pdf_annot *)widget, value_utf8 ? value_utf8 : "");
#else
	return pdf_text_widget_set_text(ctx, pdf, (pdf_widget *)widget, (char *)(value_utf8 ? value_utf8 : ""));
#endif
}

static int
pp_pdf_list_widgets_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                         int pageW, int pageH,
                         pp_pdf_widget_list **out_list)
{
	pdf_document *pdf;
	pdf_page *pdfpage;
	pp_mupdf_widget *widget;
	pp_pdf_widget_list *list = NULL;
	pp_pdf_widget_info *items = NULL;
	int count = 0;
	int idx = 0;
	int ok = 0;
	fz_rect bounds;
	fz_matrix page_to_pix;
	float page_w;
	float page_h;

	if (out_list)
		*out_list = NULL;
	if (!ctx || !doc || !page || !out_list || pageW <= 0 || pageH <= 0)
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

	for (widget = pp_pdf_first_widget_compat(ctx, pdf, pdfpage); widget; widget = pp_pdf_next_widget_compat(ctx, pdf, widget))
		count++;

	fz_var(list);
	fz_var(items);
	fz_try(ctx)
	{
		list = (pp_pdf_widget_list *)fz_malloc(ctx, sizeof(pp_pdf_widget_list));
		memset(list, 0, sizeof(*list));
		list->count = count;
		if (count > 0)
		{
			items = (pp_pdf_widget_info *)fz_malloc(ctx, (size_t)count * sizeof(pp_pdf_widget_info));
			memset(items, 0, (size_t)count * sizeof(pp_pdf_widget_info));
			list->items = items;
		}

		idx = 0;
		for (widget = pp_pdf_first_widget_compat(ctx, pdf, pdfpage); widget; widget = pp_pdf_next_widget_compat(ctx, pdf, widget))
		{
			pp_pdf_widget_info *info = &items[idx++];
			fz_rect rect_page = pp_pdf_bound_widget_compat(ctx, pdf, widget);
			fz_rect rect_pix = pp_transform_rect_compat(rect_page, page_to_pix);
			info->bounds.x0 = rect_pix.x0;
			info->bounds.y0 = rect_pix.y0;
			info->bounds.x1 = rect_pix.x1;
			info->bounds.y1 = rect_pix.y1;
			info->type = pp_pdf_widget_type_compat(ctx, pdf, widget);
			info->name_utf8 = pp_pdf_widget_name_utf8_compat(ctx, pdf, widget);
		}

		*out_list = list;
		ok = 1;
	}
	fz_catch(ctx)
	{
		if (list)
			pp_pdf_drop_widget_list_impl(ctx, list);
		ok = 0;
	}

	return ok;
}

int
pp_pdf_list_widgets(pp_ctx *pp, pp_doc *doc, int page_index,
                    int pageW, int pageH,
                    pp_pdf_widget_list **out_list)
{
	int ok = 0;
	pp_cached_page *pc = NULL;
	fz_page *page = NULL;

	if (out_list)
		*out_list = NULL;
	if (!pp || !pp->ctx || !doc || !doc->doc || !out_list)
		return 0;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pc = pp_cache_ensure_page_locked(pp->ctx, doc, page_index);
		page = pc ? pc->page : NULL;
		if (!page)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "no page");

		ok = pp_pdf_list_widgets_impl(pp->ctx, doc->doc, page, pageW, pageH, out_list);
	}
	fz_always(pp->ctx)
		pp_unlock(pp);
	fz_catch(pp->ctx)
		ok = 0;

	return ok;
}

int
pp_pdf_list_widgets_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                          int pageW, int pageH,
                          pp_pdf_widget_list **out_list)
{
	(void)page_index;
	return pp_pdf_list_widgets_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                pageW, pageH, out_list);
}

int
pp_pdf_widget_get_value_utf8(pp_ctx *pp, pp_doc *doc, int page_index, int widget_index, char **out_value_utf8)
{
	int ok = 0;
	pp_cached_page *pc = NULL;
	fz_page *page = NULL;

	if (out_value_utf8)
		*out_value_utf8 = NULL;
	if (!pp || !pp->ctx || !doc || !doc->doc || !out_value_utf8 || page_index < 0 || widget_index < 0)
		return 0;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pdf_document *pdf = pdf_specifics(pp->ctx, doc->doc);
		pdf_page *pdfpage;
		pp_mupdf_widget *w;
		char *val;

		if (!pdf)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "document is not a PDF");

		pc = pp_cache_ensure_page_locked(pp->ctx, doc, page_index);
		page = pc ? pc->page : NULL;
		if (!page)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "no page");
		pdfpage = (pdf_page *)page;

		w = pp_pdf_nth_widget_compat(pp->ctx, pdf, pdfpage, widget_index);
		if (!w)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "no widget");

		val = pp_pdf_widget_value_dup_utf8_compat(pp->ctx, pdf, w);
		if (!val)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "failed to read widget value");
		*out_value_utf8 = val;
		ok = 1;
	}
	fz_always(pp->ctx)
		pp_unlock(pp);
	fz_catch(pp->ctx)
		ok = 0;

	return ok;
}

int
pp_pdf_widget_set_text_utf8(pp_ctx *pp, pp_doc *doc, int page_index, int widget_index, const char *value_utf8)
{
	int ok = 0;
	pp_cached_page *pc = NULL;
	fz_page *page = NULL;

	if (!pp || !pp->ctx || !doc || !doc->doc || page_index < 0 || widget_index < 0)
		return 0;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pdf_document *pdf = pdf_specifics(pp->ctx, doc->doc);
		pdf_page *pdfpage;
		pp_mupdf_widget *w;
		int rc;

		if (!pdf)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "document is not a PDF");

		pc = pp_cache_ensure_page_locked(pp->ctx, doc, page_index);
		page = pc ? pc->page : NULL;
		if (!page)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "no page");
		pdfpage = (pdf_page *)page;

		w = pp_pdf_nth_widget_compat(pp->ctx, pdf, pdfpage, widget_index);
		if (!w)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "no widget");

		rc = pp_pdf_widget_set_text_utf8_compat(pp->ctx, pdf, w, value_utf8);
		if (rc)
		{
			pp_pdf_update_page_compat(pp->ctx, pdf, pdfpage);
			ok = 1;
			if (pc && pc->display_list)
			{
				fz_drop_display_list(pp->ctx, pc->display_list);
				pc->display_list = NULL;
			}
		}
	}
	fz_always(pp->ctx)
		pp_unlock(pp);
	fz_catch(pp->ctx)
		ok = 0;

	return ok;
}

int
pp_pdf_widget_get_value_utf8_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_widget, char **out_value_utf8)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	fz_document *doc = (fz_document *)mupdf_doc;
	pdf_document *pdf;

	if (out_value_utf8)
		*out_value_utf8 = NULL;
	if (!ctx || !doc || !out_value_utf8 || !mupdf_widget)
		return 0;

	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return 0;

	fz_try(ctx)
	{
		char *val = pp_pdf_widget_value_dup_utf8_compat(ctx, pdf, (pp_mupdf_widget *)mupdf_widget);
		if (!val)
			fz_throw(ctx, FZ_ERROR_GENERIC, "failed to read widget value");
		*out_value_utf8 = val;
	}
	fz_catch(ctx)
	{
		*out_value_utf8 = NULL;
		return 0;
	}

	return 1;
}

int
pp_pdf_widget_set_text_utf8_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                  void *mupdf_widget, const char *value_utf8)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	fz_document *doc = (fz_document *)mupdf_doc;
	fz_page *page = (fz_page *)mupdf_page;
	pdf_document *pdf;
	pdf_page *pdfpage;

	(void)page_index;
	if (!ctx || !doc || !page || !mupdf_widget)
		return 0;

	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return 0;
	pdfpage = (pdf_page *)page;

	{
		int ok = 0;
		fz_try(ctx)
		{
			int rc = pp_pdf_widget_set_text_utf8_compat(ctx, pdf, (pp_mupdf_widget *)mupdf_widget, value_utf8);
			if (rc)
			{
				pp_pdf_update_page_compat(ctx, pdf, pdfpage);
				ok = 1;
			}
			else
			{
				ok = 0;
			}
		}
		fz_catch(ctx)
		{
			ok = 0;
		}
		return ok;
	}
}

int
pp_pdf_widget_choice_options_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_widget, pp_string_list **out_list)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	fz_document *doc = (fz_document *)mupdf_doc;
	pdf_document *pdf;
	pp_mupdf_widget *widget = (pp_mupdf_widget *)mupdf_widget;
	pp_string_list *list = NULL;
	void *tmp = NULL;
	int n = 0;
	int ok = 0;

	if (out_list)
		*out_list = NULL;
	if (!ctx || !doc || !widget || !out_list)
		return 0;

	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return 0;

	fz_var(list);
	fz_var(tmp);
	fz_try(ctx)
	{
#if PP_MUPDF_API_NEW
		n = pdf_choice_widget_options(ctx, (pdf_annot *)widget, 0, NULL);
#else
		n = pdf_choice_widget_options(ctx, pdf, (pdf_widget *)widget, 0, NULL);
#endif
		if (n <= 0)
			fz_throw(ctx, FZ_ERROR_GENERIC, "no options");

		list = (pp_string_list *)fz_malloc(ctx, sizeof(pp_string_list));
		memset(list, 0, sizeof(*list));
		list->count = n;
		list->items = (char **)fz_malloc(ctx, (size_t)n * sizeof(char *));
		memset(list->items, 0, (size_t)n * sizeof(char *));

#if PP_MUPDF_API_NEW
		tmp = fz_malloc(ctx, (size_t)n * sizeof(const char *));
		memset(tmp, 0, (size_t)n * sizeof(const char *));
		n = pdf_choice_widget_options(ctx, (pdf_annot *)widget, 0, (const char **)tmp);
		if (n != list->count)
			list->count = n;
		for (int i = 0; i < list->count; i++)
			list->items[i] = fz_strdup(ctx, ((const char **)tmp)[i] ? ((const char **)tmp)[i] : "");
		fz_free(ctx, tmp);
		tmp = NULL;
#else
		tmp = fz_malloc(ctx, (size_t)n * sizeof(char *));
		memset(tmp, 0, (size_t)n * sizeof(char *));
		n = pdf_choice_widget_options(ctx, pdf, (pdf_widget *)widget, 0, (char **)tmp);
		if (n != list->count)
			list->count = n;
		for (int i = 0; i < list->count; i++)
			list->items[i] = fz_strdup(ctx, ((char **)tmp)[i] ? ((char **)tmp)[i] : "");
		fz_free(ctx, tmp);
		tmp = NULL;
#endif

		*out_list = list;
		ok = 1;
	}
	fz_catch(ctx)
	{
		if (tmp)
			fz_free(ctx, tmp);
		if (list)
			pp_drop_string_list_impl(ctx, list);
		ok = 0;
	}

	return ok;
}

int
pp_pdf_widget_choice_selected_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_widget, pp_string_list **out_list)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	fz_document *doc = (fz_document *)mupdf_doc;
	pdf_document *pdf;
	pp_mupdf_widget *widget = (pp_mupdf_widget *)mupdf_widget;
	pp_string_list *list = NULL;
	void *tmp = NULL;
	int n = 0;
	int ok = 0;

	if (out_list)
		*out_list = NULL;
	if (!ctx || !doc || !widget || !out_list)
		return 0;

	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return 0;

	fz_var(list);
	fz_var(tmp);
	fz_try(ctx)
	{
#if PP_MUPDF_API_NEW
		n = pdf_choice_widget_value(ctx, (pdf_annot *)widget, NULL);
#else
		n = pdf_choice_widget_value(ctx, pdf, (pdf_widget *)widget, NULL);
#endif
		if (n < 0)
			n = 0;

		list = (pp_string_list *)fz_malloc(ctx, sizeof(pp_string_list));
		memset(list, 0, sizeof(*list));
		list->count = n;
		if (n > 0)
		{
			list->items = (char **)fz_malloc(ctx, (size_t)n * sizeof(char *));
			memset(list->items, 0, (size_t)n * sizeof(char *));

#if PP_MUPDF_API_NEW
			tmp = fz_malloc(ctx, (size_t)n * sizeof(const char *));
			memset(tmp, 0, (size_t)n * sizeof(const char *));
			n = pdf_choice_widget_value(ctx, (pdf_annot *)widget, (const char **)tmp);
			if (n != list->count)
				list->count = n;
			for (int i = 0; i < list->count; i++)
				list->items[i] = fz_strdup(ctx, ((const char **)tmp)[i] ? ((const char **)tmp)[i] : "");
#else
			tmp = fz_malloc(ctx, (size_t)n * sizeof(char *));
			memset(tmp, 0, (size_t)n * sizeof(char *));
			n = pdf_choice_widget_value(ctx, pdf, (pdf_widget *)widget, (char **)tmp);
			if (n != list->count)
				list->count = n;
			for (int i = 0; i < list->count; i++)
				list->items[i] = fz_strdup(ctx, ((char **)tmp)[i] ? ((char **)tmp)[i] : "");
#endif
		}

		if (tmp)
			fz_free(ctx, tmp);
		tmp = NULL;
		*out_list = list;
		ok = 1;
	}
	fz_catch(ctx)
	{
		if (tmp)
			fz_free(ctx, tmp);
		if (list)
			pp_drop_string_list_impl(ctx, list);
		ok = 0;
	}

	return ok;
}

int
pp_pdf_widget_choice_set_selected_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                       void *mupdf_widget, int n, const char *values[])
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	fz_document *doc = (fz_document *)mupdf_doc;
	fz_page *page = (fz_page *)mupdf_page;
	pp_mupdf_widget *widget = (pp_mupdf_widget *)mupdf_widget;
	pdf_document *pdf;
	pdf_page *pdfpage;

	(void)page_index;
	if (!ctx || !doc || !page || !widget)
		return 0;
	if (n < 0)
		return 0;
	if (n > 0 && !values)
		return 0;

	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return 0;
	pdfpage = (pdf_page *)page;

	{
		int ok = 0;
		fz_try(ctx)
		{
#if PP_MUPDF_API_NEW
			pdf_choice_widget_set_value(ctx, (pdf_annot *)widget, n, values);
#else
			pdf_choice_widget_set_value(ctx, pdf, (pdf_widget *)widget, n, (char **)values);
#endif
			pp_pdf_update_page_compat(ctx, pdf, pdfpage);
			ok = 1;
		}
		fz_catch(ctx)
		{
			ok = 0;
		}
		return ok;
	}
}

int
pp_pdf_widget_click_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                          int pageW, int pageH,
                          float x, float y,
                          void **inout_focus_widget)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	fz_document *doc = (fz_document *)mupdf_doc;
	fz_page *page = (fz_page *)mupdf_page;
	pdf_document *pdf;
	pdf_page *pdfpage;
	fz_rect bounds;
	fz_matrix page_to_pix;
	float page_w;
	float page_h;

	(void)page_index;
	if (!ctx || !doc || !page || pageW <= 0 || pageH <= 0)
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

#if PP_MUPDF_API_NEW
	for (pp_mupdf_widget *w = pp_pdf_first_widget_compat(ctx, pdf, pdfpage); w; w = pp_pdf_next_widget_compat(ctx, pdf, w))
	{
		fz_rect r = pp_transform_rect_compat(pp_pdf_bound_widget_compat(ctx, pdf, w), page_to_pix);
		if (x >= r.x0 && x <= r.x1 && y >= r.y0 && y <= r.y1)
		{
			int changed = 0;
			int t = pp_pdf_widget_type_compat(ctx, pdf, w);

			if (inout_focus_widget)
			{
				pdf_annot *old = (pdf_annot *)(*inout_focus_widget);
				pdf_annot *nw = (pdf_annot *)w;
				if (old && old != nw)
				{
					pdf_drop_widget(ctx, old);
					old = NULL;
				}
				if (!old)
					*inout_focus_widget = (void *)pdf_keep_widget(ctx, nw);
			}

			if (t == PDF_WIDGET_TYPE_CHECKBOX || t == PDF_WIDGET_TYPE_RADIOBUTTON)
			{
				fz_try(ctx)
				{
					changed = pdf_toggle_widget(ctx, (pdf_annot *)w);
					if (changed)
						pp_pdf_update_page_compat(ctx, pdf, pdfpage);
				}
				fz_catch(ctx)
				{
					changed = 0;
				}
			}
			return changed;
		}
	}
	return 0;
#else
	{
		fz_matrix pix_to_page = pp_invert_matrix_compat(page_to_pix);
		fz_point pt;
		pdf_ui_event ev;
		int changed = 0;

		pt.x = x;
		pt.y = y;
		pt = pp_transform_point_compat(pt, pix_to_page);

		memset(&ev, 0, sizeof(ev));
		ev.etype = PDF_EVENT_TYPE_POINTER;
		ev.event.pointer.pt = pt;
		ev.event.pointer.ptype = PDF_POINTER_DOWN;
		changed |= pdf_pass_event(ctx, pdf, pdfpage, &ev);
		ev.event.pointer.ptype = PDF_POINTER_UP;
		changed |= pdf_pass_event(ctx, pdf, pdfpage, &ev);

		if (changed)
			pp_pdf_update_page_compat(ctx, pdf, pdfpage);
		if (inout_focus_widget)
			*inout_focus_widget = (void *)pdf_focused_widget(ctx, pdf);
		return changed;
	}
#endif
}

int
pp_pdf_widget_type_mupdf(void *mupdf_ctx, void *mupdf_widget)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	pp_mupdf_widget *widget = (pp_mupdf_widget *)mupdf_widget;
	int type = 0;
	if (!ctx || !widget)
		return 0;

	fz_try(ctx)
	{
#if PP_MUPDF_API_NEW
		type = (int)pdf_widget_type(ctx, (pdf_annot *)widget);
#else
		type = pdf_widget_get_type(ctx, (pdf_widget *)widget);
#endif
	}
	fz_catch(ctx)
	{
		type = 0;
	}

	return type;
}

struct pp_pdf_alerts
{
	fz_context *ctx;
	pdf_document *pdf;
	int initialised;
	int alerts_active;
	int alert_request;
	int alert_reply;
	pdf_alert_event *current_alert;
	pthread_mutex_t fin_lock;
	pthread_mutex_t fin_lock2;
	pthread_mutex_t alert_lock;
	pthread_cond_t alert_request_cond;
	pthread_cond_t alert_reply_cond;
};

static void
pp_pdf_set_doc_event_callback_compat(fz_context *ctx, pdf_document *doc, pdf_doc_event_cb *event_cb, void *data)
{
#if PP_MUPDF_API_NEW
	pdf_set_doc_event_callback(ctx, doc, event_cb, NULL, data);
#else
	pdf_set_doc_event_callback(ctx, doc, event_cb, data);
#endif
}

static void
pp_pdf_show_alert(pp_pdf_alerts *alerts, pdf_alert_event *alert)
{
	if (!alerts || !alerts->initialised)
		return;

	pthread_mutex_lock(&alerts->fin_lock2);
	pthread_mutex_lock(&alerts->alert_lock);

	alert->button_pressed = 0;

	if (alerts->alerts_active)
	{
		alerts->current_alert = alert;
		alerts->alert_request = 1;
		pthread_cond_signal(&alerts->alert_request_cond);

		while (alerts->alerts_active && !alerts->alert_reply)
			pthread_cond_wait(&alerts->alert_reply_cond, &alerts->alert_lock);
		alerts->alert_reply = 0;
		alerts->current_alert = NULL;
	}

	pthread_mutex_unlock(&alerts->alert_lock);
	pthread_mutex_unlock(&alerts->fin_lock2);
}

static void
pp_pdf_alert_event_cb(fz_context *ctx, pdf_document *doc, pdf_doc_event *event, void *data)
{
	(void)ctx;
	(void)doc;
	pp_pdf_alerts *alerts = (pp_pdf_alerts *)data;

	if (!alerts || !event)
		return;

	switch (event->type)
	{
	case PDF_DOCUMENT_EVENT_ALERT:
		pp_pdf_show_alert(alerts, pdf_access_alert_event(alerts->ctx, event));
		break;
	}
}

pp_pdf_alerts *
pp_pdf_alerts_new_mupdf(void *mupdf_ctx, void *mupdf_doc)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	fz_document *doc = (fz_document *)mupdf_doc;
	pdf_document *pdf;
	pp_pdf_alerts *alerts = NULL;

	if (!ctx || !doc)
		return NULL;

	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return NULL;

	fz_var(alerts);
	fz_try(ctx)
	{
		alerts = (pp_pdf_alerts *)fz_malloc(ctx, sizeof(pp_pdf_alerts));
		memset(alerts, 0, sizeof(*alerts));
		alerts->ctx = ctx;
		alerts->pdf = pdf;
		alerts->alerts_active = 0;
		alerts->alert_request = 0;
		alerts->alert_reply = 0;
		alerts->current_alert = NULL;
		pthread_mutex_init(&alerts->fin_lock, NULL);
		pthread_mutex_init(&alerts->fin_lock2, NULL);
		pthread_mutex_init(&alerts->alert_lock, NULL);
		pthread_cond_init(&alerts->alert_request_cond, NULL);
		pthread_cond_init(&alerts->alert_reply_cond, NULL);

		pdf_enable_js(ctx, pdf);
		pp_pdf_set_doc_event_callback_compat(ctx, pdf, pp_pdf_alert_event_cb, alerts);

		alerts->initialised = 1;
	}
	fz_catch(ctx)
	{
		if (alerts)
			fz_free(ctx, alerts);
		alerts = NULL;
	}

	return alerts;
}

void
pp_pdf_alerts_drop(pp_pdf_alerts *alerts)
{
	if (!alerts || !alerts->initialised)
		return;

	fz_context *ctx = alerts->ctx;

	/* Disable callbacks first to prevent new show_alert calls. */
	if (alerts->pdf)
		pp_pdf_set_doc_event_callback_compat(ctx, alerts->pdf, NULL, NULL);

	pthread_mutex_lock(&alerts->alert_lock);
	alerts->current_alert = NULL;
	alerts->alerts_active = 0;
	pthread_cond_signal(&alerts->alert_request_cond);
	pthread_cond_signal(&alerts->alert_reply_cond);
	pthread_mutex_unlock(&alerts->alert_lock);

	/* Wait for any in-flight wait/show calls. */
	pthread_mutex_lock(&alerts->fin_lock);
	pthread_mutex_unlock(&alerts->fin_lock);
	pthread_mutex_lock(&alerts->fin_lock2);
	pthread_mutex_unlock(&alerts->fin_lock2);

	pthread_cond_destroy(&alerts->alert_reply_cond);
	pthread_cond_destroy(&alerts->alert_request_cond);
	pthread_mutex_destroy(&alerts->alert_lock);
	pthread_mutex_destroy(&alerts->fin_lock2);
	pthread_mutex_destroy(&alerts->fin_lock);

	alerts->initialised = 0;
	fz_free(ctx, alerts);
}

int
pp_pdf_alerts_start(pp_pdf_alerts *alerts)
{
	if (!alerts || !alerts->initialised)
		return 0;
	pthread_mutex_lock(&alerts->alert_lock);
	alerts->alert_reply = 0;
	alerts->alert_request = 0;
	alerts->alerts_active = 1;
	alerts->current_alert = NULL;
	pthread_mutex_unlock(&alerts->alert_lock);
	return 1;
}

void
pp_pdf_alerts_stop(pp_pdf_alerts *alerts)
{
	if (!alerts || !alerts->initialised)
		return;
	pthread_mutex_lock(&alerts->alert_lock);
	alerts->alert_reply = 0;
	alerts->alert_request = 0;
	alerts->alerts_active = 0;
	alerts->current_alert = NULL;
	pthread_cond_signal(&alerts->alert_reply_cond);
	pthread_cond_signal(&alerts->alert_request_cond);
	pthread_mutex_unlock(&alerts->alert_lock);
}

int
pp_pdf_alerts_wait(pp_pdf_alerts *alerts, pp_pdf_alert *out_alert)
{
	pdf_alert_event alert;
	int alert_present;

	if (!alerts || !alerts->initialised || !out_alert)
		return 0;

	memset(out_alert, 0, sizeof(*out_alert));

	pthread_mutex_lock(&alerts->fin_lock);
	pthread_mutex_lock(&alerts->alert_lock);

	while (alerts->alerts_active && !alerts->alert_request)
		pthread_cond_wait(&alerts->alert_request_cond, &alerts->alert_lock);
	alerts->alert_request = 0;

	alert_present = (alerts->alerts_active && alerts->current_alert);
	if (alert_present)
		alert = *alerts->current_alert;

	pthread_mutex_unlock(&alerts->alert_lock);
	pthread_mutex_unlock(&alerts->fin_lock);

	if (!alert_present)
		return 0;

	/* Copy strings into MuPDF-managed memory so callers can outlive the callback window. */
	{
		int ok = 0;
		fz_try(alerts->ctx)
		{
			out_alert->title_utf8 = fz_strdup(alerts->ctx, alert.title ? alert.title : "");
			out_alert->message_utf8 = fz_strdup(alerts->ctx, alert.message ? alert.message : "");
			out_alert->icon_type = alert.icon_type;
			out_alert->button_group_type = alert.button_group_type;
			out_alert->button_pressed = alert.button_pressed;
			ok = 1;
		}
		fz_catch(alerts->ctx)
		{
			pp_pdf_alert_free_mupdf(alerts->ctx, out_alert);
			ok = 0;
		}
		return ok;
	}
}

void
pp_pdf_alerts_reply(pp_pdf_alerts *alerts, int button_pressed)
{
	if (!alerts || !alerts->initialised)
		return;

	pthread_mutex_lock(&alerts->alert_lock);
	if (alerts->alerts_active && alerts->current_alert)
	{
		alerts->current_alert->button_pressed = button_pressed;
		alerts->alert_reply = 1;
		pthread_cond_signal(&alerts->alert_reply_cond);
	}
	pthread_mutex_unlock(&alerts->alert_lock);
}

void
pp_pdf_alert_free_mupdf(void *mupdf_ctx, pp_pdf_alert *alert)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	if (!ctx || !alert)
		return;
	if (alert->title_utf8)
		fz_free(ctx, alert->title_utf8);
	if (alert->message_utf8)
		fz_free(ctx, alert->message_utf8);
	memset(alert, 0, sizeof(*alert));
}

static int
pp_export_flattened_pdf_impl(fz_context *ctx, fz_document *src, const char *path, int dpi)
{
	pdf_document *out_pdf = NULL;
	int ok = 0;
	int page_count;
	float scale;

	fz_var(out_pdf);

	if (!ctx || !src || !path || !*path)
		return 0;
	if (dpi <= 0)
		dpi = 150;
	scale = (float)dpi / 72.0f;

	fz_try(ctx)
	{
		page_count = fz_count_pages(ctx, src);
		if (page_count <= 0)
			fz_throw(ctx, FZ_ERROR_GENERIC, "failed to count pages");

		out_pdf = pdf_create_document(ctx);
		if (!out_pdf)
			fz_throw(ctx, FZ_ERROR_GENERIC, "failed to create output PDF");

		for (int i = 0; i < page_count; i++)
		{
			fz_page *page = NULL;
			fz_pixmap *pix = NULL;
			fz_device *draw_dev = NULL;
			fz_image *img = NULL;
			pdf_page *newpage = NULL;
			fz_device *pdf_dev = NULL;
#if PP_MUPDF_API_NEW
			pdf_obj *resources = NULL;
			fz_buffer *contents = NULL;
			pdf_obj *page_obj = NULL;
#endif
			fz_rect bounds;
			float page_w;
			float page_h;
			fz_matrix ctm;
			fz_rect pix_rect;
			fz_irect bbox;
			fz_rect out_rect;
			fz_matrix page_ctm;
			fz_matrix img_ctm;

			fz_var(page);
			fz_var(pix);
			fz_var(draw_dev);
			fz_var(img);
			fz_var(newpage);
			fz_var(pdf_dev);
#if PP_MUPDF_API_NEW
			fz_var(resources);
			fz_var(contents);
			fz_var(page_obj);
#endif

			fz_try(ctx)
			{
				page = fz_load_page(ctx, src, i);
				if (!page)
					fz_throw(ctx, FZ_ERROR_GENERIC, "failed to load page %d", i);

				bounds = pp_bound_page_compat(ctx, page);
				page_w = bounds.x1 - bounds.x0;
				page_h = bounds.y1 - bounds.y0;
				if (page_w <= 0 || page_h <= 0)
					fz_throw(ctx, FZ_ERROR_GENERIC, "invalid bounds for page %d", i);

				ctm = pp_scale_compat(scale, scale);
				ctm = pp_pre_translate_compat(ctm, -bounds.x0, -bounds.y0);

				pix_rect.x0 = 0;
				pix_rect.y0 = 0;
				pix_rect.x1 = page_w * scale;
				pix_rect.y1 = page_h * scale;
				bbox = pp_round_rect_compat(pix_rect);

				pix = pp_new_pixmap_with_bbox_compat(ctx, fz_device_rgb(ctx), bbox);
				fz_clear_pixmap_with_value(ctx, pix, 255);

				draw_dev = pp_new_draw_device_compat(ctx, pix);
				pp_run_page_all_compat(ctx, page, draw_dev, ctm, NULL);
				pp_close_device_compat(ctx, draw_dev);

				img = fz_new_image_from_pixmap(ctx, pix, NULL);

				out_rect.x0 = 0;
				out_rect.y0 = 0;
				out_rect.x1 = page_w;
				out_rect.y1 = page_h;

#if PP_MUPDF_API_NEW
				pdf_dev = pdf_page_write(ctx, out_pdf, out_rect, &resources, &contents);
				img_ctm = pp_scale_compat(page_w, page_h);
				pp_fill_image_compat(ctx, pdf_dev, img, img_ctm, 1.0f);
				pp_close_device_compat(ctx, pdf_dev);

				page_obj = pdf_add_page(ctx, out_pdf, out_rect, 0, resources, contents);
				pdf_insert_page(ctx, out_pdf, INT_MAX, page_obj);
#else
				newpage = pdf_create_page(ctx, out_pdf, out_rect, 72, 0);
				pdf_dev = pdf_page_write(ctx, out_pdf, newpage);

				page_ctm = pp_identity_compat();
				fz_begin_page(ctx, pdf_dev, &out_rect, &page_ctm);
				img_ctm = pp_scale_compat(page_w, page_h);
				pp_fill_image_compat(ctx, pdf_dev, img, img_ctm, 1.0f);
				fz_end_page(ctx, pdf_dev);
				pp_close_device_compat(ctx, pdf_dev);

				pdf_insert_page(ctx, out_pdf, newpage, INT_MAX);
#endif
			}
			fz_always(ctx)
			{
				if (pdf_dev)
					fz_drop_device(ctx, pdf_dev);
				if (newpage)
					pdf_drop_page(ctx, newpage);
#if PP_MUPDF_API_NEW
				if (page_obj)
					pdf_drop_obj(ctx, page_obj);
				if (resources)
					pdf_drop_obj(ctx, resources);
				if (contents)
					fz_drop_buffer(ctx, contents);
#endif
				if (img)
					fz_drop_image(ctx, img);
				if (draw_dev)
					fz_drop_device(ctx, draw_dev);
				if (pix)
					fz_drop_pixmap(ctx, pix);
				if (page)
					fz_drop_page(ctx, page);
			}
			fz_catch(ctx)
			{
				fz_rethrow(ctx);
			}
		}

#if !PP_MUPDF_API_NEW
		pdf_finish_edit(ctx, out_pdf);
#endif

#if PP_MUPDF_API_NEW
		{
			pdf_write_options opts;
			pdf_parse_write_options(ctx, &opts, NULL);
			pdf_save_document(ctx, out_pdf, path, &opts);
		}
#else
		fz_write_document(ctx, &out_pdf->super, (char *)path, NULL);
#endif

		ok = 1;
	}
	fz_always(ctx)
	{
		if (out_pdf)
			fz_drop_document(ctx, &out_pdf->super);
	}
	fz_catch(ctx)
	{
		ok = 0;
	}

	return ok;
}

int
pp_export_flattened_pdf(pp_ctx *pp, pp_doc *doc, const char *path, int dpi)
{
	int ok;

	if (!pp || !pp->ctx || !doc || !doc->doc)
		return 0;

	pp_lock(pp);
	ok = pp_export_flattened_pdf_impl(pp->ctx, doc->doc, path, dpi);
	pp_unlock(pp);

	return ok;
}

int
pp_export_flattened_pdf_mupdf(void *mupdf_ctx, void *mupdf_doc, const char *path, int dpi)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	fz_document *doc = (fz_document *)mupdf_doc;
	return pp_export_flattened_pdf_impl(ctx, doc, path, dpi);
}

static char *
pp_tmp_pdf_path_for_target(fz_context *ctx, const char *path)
{
	const int rnd_length = 6;
	char rnd[rnd_length + 1];
	unsigned long v;
	size_t buf_len;
	char *buf;

	if (!path || !*path)
		return NULL;

	/* Keep it deterministic but reasonably unique per-process. */
	static unsigned long counter = 0;
	counter++;
	v = (unsigned long)((uintptr_t)ctx) ^ (counter * 2654435761u);

	for (int i = 0; i < rnd_length; i++)
	{
		rnd[i] = "0123456789abcdef"[v & 0xF];
		v = (v >> 4) | (v << (sizeof(v) * 8 - 4));
	}
	rnd[rnd_length] = '\0';

	buf_len = strlen(path) + 1 + rnd_length + 4 + 1; /* _ + rnd + .pdf + NUL */
	buf = fz_malloc(ctx, buf_len);
	fz_strlcpy(buf, path, buf_len);
	fz_strlcat(buf, "_", buf_len);
	fz_strlcat(buf, rnd, buf_len);
	fz_strlcat(buf, ".pdf", buf_len);
	return buf;
}

static int
pp_export_pdf_impl(fz_context *ctx, fz_document *doc, const char *path, int incremental)
{
	int ok = 0;
	char *tmp = NULL;
#if PP_MUPDF_API_NEW
	fz_document_writer *wri = NULL;
#endif

	if (!ctx || !doc || !path || !*path)
		return 0;

	fz_try(ctx)
	{
		tmp = pp_tmp_pdf_path_for_target(ctx, path);
		if (!tmp)
			fz_throw(ctx, FZ_ERROR_GENERIC, "failed to build temp output path");

#if PP_MUPDF_API_NEW
		pdf_document *pdf = pdf_specifics(ctx, doc);
		if (pdf)
		{
			pdf_write_options opts;
			pdf_parse_write_options(ctx, &opts, incremental ? "incremental=yes" : NULL);
			pdf_save_document(ctx, pdf, tmp, &opts);
		}
		else
		{
			wri = fz_new_pdf_writer(ctx, tmp, incremental ? "incremental=yes" : NULL);
			fz_write_document(ctx, wri, doc);
			fz_close_document_writer(ctx, wri);
			fz_drop_document_writer(ctx, wri);
			wri = NULL;
		}
#else
		(void)incremental;
		fz_write_document(ctx, doc, tmp, NULL);
#endif

		if (rename(tmp, path) != 0)
			fz_throw(ctx, FZ_ERROR_GENERIC, "rename(%s -> %s) failed", tmp, path);

		ok = 1;
	}
	fz_always(ctx)
	{
#if PP_MUPDF_API_NEW
		if (wri)
			fz_drop_document_writer(ctx, wri);
#endif
		if (tmp)
			fz_free(ctx, tmp);
	}
	fz_catch(ctx)
	{
		fz_warn(ctx, "pp_export_pdf failed: %s", fz_caught_message(ctx));
		ok = 0;
	}

	return ok;
}

int
pp_export_pdf(pp_ctx *pp, pp_doc *doc, const char *path, int incremental)
{
	int ok;

	if (!pp || !pp->ctx || !doc || !doc->doc)
		return 0;

	pp_lock(pp);
	ok = pp_export_pdf_impl(pp->ctx, doc->doc, path, incremental);
	pp_unlock(pp);

	return ok;
}

int
pp_export_pdf_mupdf(void *mupdf_ctx, void *mupdf_doc, const char *path, int incremental)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	fz_document *doc = (fz_document *)mupdf_doc;
	return pp_export_pdf_impl(ctx, doc, path, incremental);
}

int
pp_pdf_has_unsaved_changes(pp_ctx *pp, pp_doc *doc)
{
	int changed = 0;

	if (!pp || !pp->ctx || !doc || !doc->doc)
		return 0;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pdf_document *pdf = pdf_specifics(pp->ctx, doc->doc);
		changed = (pdf && pdf_has_unsaved_changes(pp->ctx, pdf)) ? 1 : 0;
	}
	fz_catch(pp->ctx)
	{
		changed = 0;
	}
	pp_unlock(pp);

	return changed;
}

int
pp_pdf_has_unsaved_changes_mupdf(void *mupdf_ctx, void *mupdf_doc)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	fz_document *doc = (fz_document *)mupdf_doc;
	int changed = 0;

	if (!ctx || !doc)
		return 0;

	fz_try(ctx)
	{
		pdf_document *pdf = pdf_specifics(ctx, doc);
		changed = (pdf && pdf_has_unsaved_changes(ctx, pdf)) ? 1 : 0;
	}
	fz_catch(ctx)
	{
		changed = 0;
	}

	return changed;
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

int
pp_pdf_save_as_mupdf(void *mupdf_ctx, void *mupdf_doc, const char *path)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	fz_document *doc = (fz_document *)mupdf_doc;
	int ok = 0;

	if (!ctx || !doc || !path || !*path)
		return 0;

	fz_try(ctx)
	{
		pdf_document *pdf = pdf_specifics(ctx, doc);
		if (!pdf)
			fz_throw(ctx, FZ_ERROR_GENERIC, "document is not a PDF");

#if PP_MUPDF_API_NEW
		pdf_write_options opts;
		pdf_parse_write_options(ctx, &opts, NULL);
		pdf_save_document(ctx, pdf, path, &opts);
#else
		/* fz_write_document uses non-const char* in older MuPDF. */
		fz_write_document(ctx, doc, (char *)path, NULL);
#endif
		ok = 1;
	}
	fz_catch(ctx)
	{
		ok = 0;
	}

	return ok;
}
