#include "pp_core.h"

#include <stdlib.h>

#include <mupdf/fitz.h>

struct pp_ctx
{
	fz_context *ctx;
};

struct pp_doc
{
	fz_document *doc;
	char format[64];
};

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

	return pp;
}

void
pp_drop(pp_ctx *pp)
{
	if (!pp)
		return;
	if (pp->ctx)
		fz_drop_context(pp->ctx);
	free(pp);
}

pp_doc *
pp_open(pp_ctx *pp, const char *path)
{
	pp_doc *doc;

	if (!pp || !pp->ctx || !path)
		return NULL;

	doc = (pp_doc *)calloc(1, sizeof(pp_doc));
	if (!doc)
		return NULL;

	fz_try(pp->ctx)
		doc->doc = fz_open_document(pp->ctx, path);
	fz_catch(pp->ctx)
	{
		free(doc);
		return NULL;
	}

	doc->format[0] = 0;
	fz_lookup_metadata(pp->ctx, doc->doc, FZ_META_FORMAT, doc->format, (int)sizeof(doc->format));

	return doc;
}

void
pp_close(pp_ctx *pp, pp_doc *doc)
{
	if (!pp || !pp->ctx || !doc)
		return;
	if (doc->doc)
		fz_drop_document(pp->ctx, doc->doc);
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

	fz_try(pp->ctx)
		page_count = fz_count_pages(pp->ctx, doc->doc);
	fz_catch(pp->ctx)
		page_count = -1;

	return page_count;
}

int
pp_page_size(pp_ctx *pp, pp_doc *doc, int page_index, float *out_w, float *out_h)
{
	fz_page *page = NULL;
	fz_rect bounds;
	int ok = 0;

	if (out_w)
		*out_w = 0;
	if (out_h)
		*out_h = 0;

	if (!pp || !pp->ctx || !doc || !doc->doc || !out_w || !out_h || page_index < 0)
		return 0;

	fz_try(pp->ctx)
	{
		page = fz_load_page(pp->ctx, doc->doc, page_index);
		fz_bound_page(pp->ctx, page, &bounds);
		*out_w = bounds.x1 - bounds.x0;
		*out_h = bounds.y1 - bounds.y0;
		ok = 1;
	}
	fz_always(pp->ctx)
	{
		if (page)
			fz_drop_page(pp->ctx, page);
	}
	fz_catch(pp->ctx)
	{
		ok = 0;
	}

	return ok;
}

int
pp_render_page_rgba(pp_ctx *pp, pp_doc *doc, int page_index, int out_w, int out_h, unsigned char *rgba)
{
	fz_page *page = NULL;
	fz_pixmap *pix = NULL;
	fz_device *dev = NULL;
	fz_cookie cookie = {0};
	fz_rect bounds;
	fz_matrix ctm;
	float page_w;
	float page_h;
	int ok = 0;

	if (!pp || !pp->ctx || !doc || !doc->doc || !rgba || page_index < 0 || out_w <= 0 || out_h <= 0)
		return 0;

	fz_try(pp->ctx)
	{
		page = fz_load_page(pp->ctx, doc->doc, page_index);
		fz_bound_page(pp->ctx, page, &bounds);
		page_w = bounds.x1 - bounds.x0;
		page_h = bounds.y1 - bounds.y0;
		if (page_w <= 0 || page_h <= 0)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "invalid page bounds");

		fz_scale(&ctm, out_w / page_w, out_h / page_h);
		fz_pre_translate(&ctm, -bounds.x0, -bounds.y0);

		pix = fz_new_pixmap_with_data(pp->ctx, fz_device_rgb(pp->ctx), out_w, out_h, rgba);
		fz_clear_pixmap_with_value(pp->ctx, pix, 255);

		dev = fz_new_draw_device(pp->ctx, pix);
		fz_run_page(pp->ctx, page, dev, &ctm, &cookie);

		ok = 1;
	}
	fz_always(pp->ctx)
	{
		if (dev)
			fz_drop_device(pp->ctx, dev);
		if (pix)
			fz_drop_pixmap(pp->ctx, pix);
		if (page)
			fz_drop_page(pp->ctx, page);
	}
	fz_catch(pp->ctx)
	{
		ok = 0;
	}

	return ok;
}

