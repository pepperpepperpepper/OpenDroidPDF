#ifndef PP_CORE_INTERNAL_H
#define PP_CORE_INTERNAL_H

#include "pp_core.h"

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

void pp_lock(pp_ctx *pp);
void pp_unlock(pp_ctx *pp);

fz_rect pp_bound_page_compat(fz_context *ctx, fz_page *page);
fz_matrix pp_scale_compat(float sx, float sy);
fz_matrix pp_pre_translate_compat(fz_matrix m, float tx, float ty);
fz_matrix pp_invert_matrix_compat(fz_matrix m);
fz_point pp_transform_point_compat(fz_point p, fz_matrix m);
fz_rect pp_transform_rect_compat(fz_rect r, fz_matrix m);

fz_device *pp_new_draw_device_compat(fz_context *ctx, fz_pixmap *pix);

void pp_close_device_compat(fz_context *ctx, fz_device *dev);
void pp_close_output_compat(fz_context *ctx, fz_output *out);
void pp_run_page_all_compat(fz_context *ctx, fz_page *page, fz_device *dev, fz_matrix ctm, fz_cookie *cookie);

void pp_pdf_update_page_compat(fz_context *ctx, pdf_document *doc, pdf_page *page);

void pp_clear_page_cache_locked(fz_context *ctx, pp_doc *doc);
pp_cached_page *pp_cache_ensure_page_locked(fz_context *ctx, pp_doc *doc, int page_index);

#endif
