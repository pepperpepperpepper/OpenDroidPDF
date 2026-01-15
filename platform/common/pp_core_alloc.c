#include "pp_core_internal.h"

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

