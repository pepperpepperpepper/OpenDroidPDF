#include "pp_core_internal.h"

#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

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

				page_ctm = fz_identity;
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

