#include "pp_core_internal.h"

#include <stdlib.h>
#include <string.h>

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

