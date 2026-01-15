#include "pp_core_internal.h"

#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <stdio.h>
#include <stdint.h>
#include <stdarg.h>

#include <pthread.h>

#include <mupdf/fitz.h>
#include <mupdf/pdf.h>

#if defined(__ANDROID__) && defined(OPD_DEBUG_AP_PATCH)
#include <android/log.h>
#define OPD_APLOGI(...) __android_log_print(ANDROID_LOG_INFO, "opd_ap_patch", __VA_ARGS__)
#define OPD_APLOGW(...) __android_log_print(ANDROID_LOG_WARN, "opd_ap_patch", __VA_ARGS__)
#else
#define OPD_APLOGI(...) ((void)0)
#define OPD_APLOGW(...) ((void)0)
#endif

void
pp_lock(pp_ctx *pp)
{
	pthread_mutex_lock(&pp->lock);
}

void
pp_unlock(pp_ctx *pp)
{
	pthread_mutex_unlock(&pp->lock);
}

fz_rect
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

fz_matrix
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

fz_matrix
pp_pre_translate_compat(fz_matrix m, float tx, float ty)
{
#if PP_MUPDF_API_NEW
	return fz_pre_translate(m, tx, ty);
#else
	fz_pre_translate(&m, tx, ty);
	return m;
#endif
}

fz_matrix
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

fz_point
pp_transform_point_compat(fz_point p, fz_matrix m)
{
#if PP_MUPDF_API_NEW
	return fz_transform_point(p, m);
#else
	fz_transform_point(&p, &m);
	return p;
#endif
}

fz_rect
pp_transform_rect_compat(fz_rect r, fz_matrix m)
{
#if PP_MUPDF_API_NEW
	return fz_transform_rect(r, m);
#else
	fz_transform_rect(&r, &m);
	return r;
#endif
}

void
pp_close_device_compat(fz_context *ctx, fz_device *dev)
{
#if PP_MUPDF_API_NEW
	fz_close_device(ctx, dev);
#else
	(void)ctx;
	(void)dev;
#endif
}

void
pp_close_output_compat(fz_context *ctx, fz_output *out)
{
#if PP_MUPDF_API_NEW
	fz_close_output(ctx, out);
#else
	(void)ctx;
	(void)out;
#endif
}
