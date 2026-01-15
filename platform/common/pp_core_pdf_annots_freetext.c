#include "pp_core_pdf_annots_freetext_internal.h"

int
pp_pdf_update_freetext_style_by_object_id(pp_ctx *pp, pp_doc *doc, int page_index,
                                          long long object_id,
                                          float font_size,
                                          const float color_rgb[3])
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

		ok = pp_pdf_update_freetext_style_by_object_id_impl(pp->ctx, doc->doc, page, object_id, NULL, font_size, color_rgb);

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
pp_pdf_update_freetext_background_by_object_id(pp_ctx *pp, pp_doc *doc, int page_index,
                                               long long object_id,
                                               const float fill_rgb[3],
                                               float opacity)
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

		ok = pp_pdf_update_freetext_background_by_object_id_impl(pp->ctx, doc->doc, page, object_id, fill_rgb, opacity);

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
pp_pdf_update_freetext_background_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                                     long long object_id,
                                                     const float fill_rgb[3],
                                                     float opacity)
{
	(void)page_index;
	return pp_pdf_update_freetext_background_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                                           object_id, fill_rgb, opacity);
}

int
pp_pdf_update_freetext_style_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                                long long object_id,
                                                float font_size,
                                                const float color_rgb[3])
{
	(void)page_index;
	return pp_pdf_update_freetext_style_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                                      object_id, NULL, font_size, color_rgb);
}

int
pp_pdf_update_freetext_style_by_object_id_with_font(pp_ctx *pp, pp_doc *doc, int page_index,
                                                   long long object_id,
                                                   const char *font_key,
                                                   float font_size,
                                                   const float color_rgb[3])
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

		ok = pp_pdf_update_freetext_style_by_object_id_impl(pp->ctx, doc->doc, page, object_id, font_key, font_size, color_rgb);

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
pp_pdf_update_freetext_style_by_object_id_with_font_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                                         long long object_id,
                                                         const char *font_key,
                                                         float font_size,
                                                         const float color_rgb[3])
{
	(void)page_index;
	return pp_pdf_update_freetext_style_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                                      object_id, font_key, font_size, color_rgb);
}

int
pp_pdf_update_freetext_border_by_object_id(pp_ctx *pp, pp_doc *doc, int page_index,
                                          long long object_id,
                                          const float border_rgb[3],
                                          float width_pt,
                                          int dashed,
                                          float radius_pt)
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

		ok = pp_pdf_update_freetext_border_by_object_id_impl(pp->ctx, doc->doc, page, object_id, border_rgb, width_pt, dashed, radius_pt);

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
pp_pdf_update_freetext_border_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                                long long object_id,
                                                const float border_rgb[3],
                                                float width_pt,
                                                int dashed,
                                                float radius_pt)
{
	(void)page_index;
	return pp_pdf_update_freetext_border_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                                       object_id, border_rgb, width_pt, dashed, radius_pt);
}

int
pp_pdf_update_freetext_alignment_by_object_id(pp_ctx *pp, pp_doc *doc, int page_index,
                                             long long object_id,
                                             int alignment)
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

		ok = pp_pdf_update_freetext_alignment_by_object_id_impl(pp->ctx, doc->doc, page, object_id, alignment);

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
pp_pdf_update_freetext_alignment_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                                   long long object_id,
                                                   int alignment)
{
	(void)page_index;
	return pp_pdf_update_freetext_alignment_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                                          object_id, alignment);
}

int
pp_pdf_update_freetext_rotation_by_object_id(pp_ctx *pp, pp_doc *doc, int page_index,
                                             long long object_id,
                                             int rotation_degrees)
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

		ok = pp_pdf_update_freetext_rotation_by_object_id_impl(pp->ctx, doc->doc, page, object_id, rotation_degrees);

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
pp_pdf_update_freetext_rotation_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                                   long long object_id,
                                                   int rotation_degrees)
{
	(void)page_index;
	return pp_pdf_update_freetext_rotation_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                                         object_id, rotation_degrees);
}

int
pp_pdf_get_freetext_style_flags_by_object_id(pp_ctx *pp, pp_doc *doc, int page_index,
                                             long long object_id,
                                             int *out_flags)
{
	int ok = 0;
	pp_cached_page *pc = NULL;
	fz_page *page = NULL;

	if (!pp || !pp->ctx || !doc || !doc->doc)
		return 0;
	if (!out_flags)
		return 0;

	*out_flags = 0;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pc = pp_cache_ensure_page_locked(pp->ctx, doc, page_index);
		page = pc ? pc->page : NULL;
		if (!page)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "no page");

		ok = pp_pdf_get_freetext_style_flags_by_object_id_impl(pp->ctx, doc->doc, page, object_id, out_flags);

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
pp_pdf_get_freetext_style_flags_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                                   long long object_id,
                                                   int *out_flags)
{
	(void)page_index;
	return pp_pdf_get_freetext_style_flags_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                                         object_id, out_flags);
}

int
pp_pdf_update_freetext_style_flags_by_object_id(pp_ctx *pp, pp_doc *doc, int page_index,
                                                long long object_id,
                                                int style_flags)
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

		ok = pp_pdf_update_freetext_style_flags_by_object_id_impl(pp->ctx, doc->doc, page, object_id, style_flags);

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
pp_pdf_update_freetext_style_flags_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                                      long long object_id,
                                                      int style_flags)
{
	(void)page_index;
	return pp_pdf_update_freetext_style_flags_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                                            object_id, style_flags);
}

int
pp_pdf_get_freetext_paragraph_by_object_id(pp_ctx *pp, pp_doc *doc, int page_index,
                                           long long object_id,
                                           float *out_line_height,
                                           float *out_text_indent_pt)
{
	int ok = 0;
	pp_cached_page *pc = NULL;
	fz_page *page = NULL;

	if (!pp || !pp->ctx || !doc || !doc->doc)
		return 0;
	if (!out_line_height || !out_text_indent_pt)
		return 0;

	*out_line_height = OPD_DEFAULT_LINE_HEIGHT;
	*out_text_indent_pt = OPD_DEFAULT_TEXT_INDENT_PT;

	pp_lock(pp);
	fz_try(pp->ctx)
	{
		pc = pp_cache_ensure_page_locked(pp->ctx, doc, page_index);
		page = pc ? pc->page : NULL;
		if (!page)
			fz_throw(pp->ctx, FZ_ERROR_GENERIC, "no page");

		ok = pp_pdf_get_freetext_paragraph_by_object_id_impl(pp->ctx, doc->doc, page, object_id, out_line_height, out_text_indent_pt);

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
pp_pdf_get_freetext_paragraph_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                                 long long object_id,
                                                 float *out_line_height,
                                                 float *out_text_indent_pt)
{
	(void)page_index;
	return pp_pdf_get_freetext_paragraph_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                                       object_id, out_line_height, out_text_indent_pt);
}

int
pp_pdf_update_freetext_paragraph_by_object_id(pp_ctx *pp, pp_doc *doc, int page_index,
                                              long long object_id,
                                              float line_height,
                                              float text_indent_pt)
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

		ok = pp_pdf_update_freetext_paragraph_by_object_id_impl(pp->ctx, doc->doc, page, object_id, line_height, text_indent_pt);

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
pp_pdf_update_freetext_paragraph_by_object_id_mupdf(void *mupdf_ctx, void *mupdf_doc, void *mupdf_page, int page_index,
                                                    long long object_id,
                                                    float line_height,
                                                    float text_indent_pt)
{
	(void)page_index;
	return pp_pdf_update_freetext_paragraph_by_object_id_impl((fz_context *)mupdf_ctx, (fz_document *)mupdf_doc, (fz_page *)mupdf_page,
	                                                          object_id, line_height, text_indent_pt);
}

