#include "pp_core_internal.h"

#include <string.h>

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

