#include "pp_core_pdf_annots_freetext_internal.h"

#include <math.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

/*
 * MuPDF's pdf_set_annot_default_appearance() deletes /DS and /RC (rich text) because
 * MuPDF's annotation editing API does not support them. OpenDroidPDF wants to preserve
 * Acrobat-authored rich text when present, so we update /DA directly and leave /DS + /RC intact.
 */
#if PP_MUPDF_API_NEW
static void
opd_pdf_set_annot_default_appearance_preserve_rich(fz_context *ctx,
                                                   pdf_document *doc,
                                                   pdf_annot *annot,
                                                   const char *font_key,
                                                   float font_size,
                                                   const float color_rgb[3])
{
	pdf_obj *annot_obj;
	char buf[128];
	float r, g, b;

	if (!ctx || !doc || !annot)
		return;

	annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
	if (!annot_obj)
		return;

	r = color_rgb ? color_rgb[0] : 0.0f;
	g = color_rgb ? color_rgb[1] : 0.0f;
	b = color_rgb ? color_rgb[2] : 0.0f;
	if (r < 0.0f) r = 0.0f;
	if (r > 1.0f) r = 1.0f;
	if (g < 0.0f) g = 0.0f;
	if (g > 1.0f) g = 1.0f;
	if (b < 0.0f) b = 0.0f;
	if (b > 1.0f) b = 1.0f;

	if (!font_key || !font_key[0])
		font_key = "Helv";
	font_size = fmaxf(0.0f, font_size);

	/* /DA is a PDF string, not a text string. */
	fz_snprintf(buf, (int)sizeof buf, "/%s %g Tf %g %g %g rg", font_key, font_size, r, g, b);

	pdf_dict_puts_drop(ctx, annot_obj, "DA", pdf_new_string(ctx, buf, strlen(buf)));
}
#endif

/* FreeText appearance patching lives in pp_core_pdf_annots_freetext_appearance.c. */

int
pp_pdf_update_freetext_style_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                               long long object_id,
                                               const char *font_key,
                                               float font_size,
                                               const float color_rgb[3])
{
	pdf_document *pdf;
	pdf_page *pdfpage;
	pdf_annot *annot;
	float color[3];

	if (!ctx || !doc || !page || object_id < 0)
		return 0;
	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return 0;
	pdfpage = (pdf_page *)page;

	color[0] = color_rgb ? color_rgb[0] : 0.0f;
	color[1] = color_rgb ? color_rgb[1] : 0.0f;
	color[2] = color_rgb ? color_rgb[2] : 0.0f;
	font_size = fmaxf(6.0f, fminf(96.0f, font_size));

	for (annot = pp_pdf_first_annot_compat(ctx, pdfpage); annot; annot = pp_pdf_next_annot_compat(ctx, pdfpage, annot))
	{
		long long id = pp_pdf_object_id_for_annot(ctx, annot);
		if (id != object_id)
			continue;

		int type = pdf_annot_type(ctx, annot);
#if PP_MUPDF_API_NEW
		if (type != (int)PDF_ANNOT_FREE_TEXT)
			return 0;
#else
		if (type != (int)FZ_ANNOT_FREETEXT)
			return 0;
#endif

		/* Preserve the current FreeText font resource unless a new font is explicitly requested. */
		const char *font_key_use = font_key;
		if (font_key_use == NULL || font_key_use[0] == '\0')
		{
#if PP_MUPDF_API_NEW
			const char *existing_font = NULL;
			float existing_size = 0.0f;
			int n = 0;
			float existing_color[4] = {0};
			if (pdf_annot_has_default_appearance(ctx, annot))
			{
				pdf_annot_default_appearance(ctx, annot, &existing_font, &existing_size, &n, existing_color);
			}
			if (existing_font && existing_font[0] != '\0')
				font_key_use = existing_font;
#endif
		}
		if (font_key_use == NULL || font_key_use[0] == '\0')
			font_key_use = "Helv";

		/* Force appearance regeneration so style changes reflect in /AP. */
		pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
		if (annot_obj)
		{
			pp_pdf_capture_freetext_border_style_if_missing(ctx, pdf, annot);
			pp_pdf_suppress_freetext_border_generation(ctx, pdf, annot);
			pdf_dict_dels(ctx, annot_obj, "AP");

#if PP_MUPDF_API_NEW
			/*
			 * Keep OpenDroidPDF-owned rich text defaults (/DS) in sync with /DA changes.
			 * This ensures font size/color changes still apply when rich layout is enabled.
			 */
			const char *ds = pdf_dict_get_text_string_opt(ctx, annot_obj, PDF_NAME(DS));
			if (ds && opd_ds_has_marker(ds))
			{
				int flags = opd_text_style_flags_from_ds(ds);
				int q = 0;
				pdf_obj *q_obj = pdf_dict_get(ctx, annot_obj, PDF_NAME(Q));
				if (q_obj)
					q = pdf_to_int(ctx, q_obj);
				if (q < 0) q = 0;
				if (q > 2) q = 2;
				float line_height = opd_ds_float_property(ds, "line-height", OPD_DEFAULT_LINE_HEIGHT);
				float text_indent_pt = opd_ds_float_property(ds, "text-indent", OPD_DEFAULT_TEXT_INDENT_PT);
				char ds_buf[512];
				opd_build_freetext_ds(ds_buf, sizeof(ds_buf), font_key_use, font_size, color, q, flags, line_height, text_indent_pt);
				pdf_dict_put_text_string(ctx, annot_obj, PDF_NAME(DS), ds_buf);
			}
#endif
		}

#if PP_MUPDF_API_NEW
		opd_pdf_set_annot_default_appearance_preserve_rich(ctx, pdf, annot, font_key_use, font_size, color);
		pp_pdf_update_annot_compat(ctx, pdf, annot);
#else
		{
			fz_rect rect_pdf = pp_pdf_bound_annot_compat(ctx, pdf, pdfpage, annot);
			fz_point pos;
			pos.x = rect_pdf.x0;
			pos.y = rect_pdf.y0;
			char *t = pdf_annot_contents(ctx, pdf, annot);
			const char *font_full = "Helvetica";
			if (font_key_use)
			{
				if (!strcmp(font_key_use, "TiRo") || !strcmp(font_key_use, "Times-Roman"))
					font_full = "Times-Roman";
				else if (!strcmp(font_key_use, "Cour") || !strcmp(font_key_use, "Courier"))
					font_full = "Courier";
			}
			pdf_set_free_text_details(ctx, pdf, annot, &pos, (char *)(t ? t : ""), (char *)font_full, font_size, color);
			pp_pdf_set_annot_color_opacity_dict(ctx, pdf, annot, color, 1.0f);
			pp_pdf_update_annot_compat(ctx, pdf, annot);
		}
#endif

		pp_pdf_dirty_annot_compat(ctx, pdf, annot);
		pp_pdf_update_page_compat(ctx, pdf, pdfpage);
		pp_pdf_patch_freetext_background_appearance_if_needed(ctx, pdf, annot);
		return 1;
	}

	return 0;
}

int
pp_pdf_update_freetext_background_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                    long long object_id,
                                                    const float fill_rgb[3],
                                                    float opacity)
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

		int type = pdf_annot_type(ctx, annot);
#if PP_MUPDF_API_NEW
		if (type != (int)PDF_ANNOT_FREE_TEXT)
			return 0;
#else
		if (type != (int)FZ_ANNOT_FREETEXT)
			return 0;
#endif

		/* Force appearance regeneration so changes reflect in /AP. */
		pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
		if (annot_obj)
		{
			pp_pdf_capture_freetext_border_style_if_missing(ctx, pdf, annot);
			pp_pdf_suppress_freetext_border_generation(ctx, pdf, annot);
			pdf_dict_dels(ctx, annot_obj, "AP");
		}

		pp_pdf_set_annot_interior_color_dict(ctx, pdf, annot, fill_rgb);
		pp_pdf_set_annot_opacity_dict(ctx, pdf, annot, opacity);
		pp_pdf_update_annot_compat(ctx, pdf, annot);
		pp_pdf_dirty_annot_compat(ctx, pdf, annot);
		pp_pdf_update_page_compat(ctx, pdf, pdfpage);
		pp_pdf_patch_freetext_background_appearance_if_needed(ctx, pdf, annot);
		return 1;
	}

	return 0;
}

int
pp_pdf_update_freetext_border_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                long long object_id,
                                                const float border_rgb[3],
                                                float width_pt,
                                                int dashed,
                                                float radius_pt)
{
	pdf_document *pdf;
	pdf_page *pdfpage;
	pdf_annot *annot;
	float color[3];

	if (!ctx || !doc || !page || object_id < 0)
		return 0;
	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return 0;
	pdfpage = (pdf_page *)page;

	color[0] = border_rgb ? border_rgb[0] : 0.0f;
	color[1] = border_rgb ? border_rgb[1] : 0.0f;
	color[2] = border_rgb ? border_rgb[2] : 0.0f;
	for (int i = 0; i < 3; i++)
	{
		if (color[i] < 0.0f) color[i] = 0.0f;
		if (color[i] > 1.0f) color[i] = 1.0f;
	}

	if (width_pt < 0.0f) width_pt = 0.0f;
	if (width_pt > 24.0f) width_pt = 24.0f;
	if (radius_pt < 0.0f) radius_pt = 0.0f;
	if (radius_pt > 48.0f) radius_pt = 48.0f;
	dashed = dashed ? 1 : 0;

	for (annot = pp_pdf_first_annot_compat(ctx, pdfpage); annot; annot = pp_pdf_next_annot_compat(ctx, pdfpage, annot))
	{
		long long id = pp_pdf_object_id_for_annot(ctx, annot);
		if (id != object_id)
			continue;

		int type = pdf_annot_type(ctx, annot);
#if PP_MUPDF_API_NEW
		if (type != (int)PDF_ANNOT_FREE_TEXT)
			return 0;
#else
		if (type != (int)FZ_ANNOT_FREETEXT)
			return 0;
#endif

		pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
		if (!annot_obj)
			return 0;

		/* Store OpenDroidPDF border metadata and force AP regeneration (MuPDF border generation is suppressed). */
		pp_pdf_set_annot_color_dict(ctx, pdf, annot, color);

		if (width_pt > 0.0f)
			pdf_dict_puts_drop(ctx, annot_obj, "OPDBorderWidth", pp_pdf_new_real_compat(ctx, pdf, width_pt));
		else
			pdf_dict_dels(ctx, annot_obj, "OPDBorderWidth");

		if (dashed && width_pt > 0.0f)
			pdf_dict_puts_drop(ctx, annot_obj, "OPDBorderDashed", pp_pdf_new_real_compat(ctx, pdf, 1.0f));
		else
			pdf_dict_dels(ctx, annot_obj, "OPDBorderDashed");

		if (radius_pt > 0.0f)
			pdf_dict_puts_drop(ctx, annot_obj, "OPDBorderRadius", pp_pdf_new_real_compat(ctx, pdf, radius_pt));
		else
			pdf_dict_dels(ctx, annot_obj, "OPDBorderRadius");

		pp_pdf_suppress_freetext_border_generation(ctx, pdf, annot);
		pdf_dict_dels(ctx, annot_obj, "AP");

		pp_pdf_update_annot_compat(ctx, pdf, annot);
		pp_pdf_dirty_annot_compat(ctx, pdf, annot);
		pp_pdf_update_page_compat(ctx, pdf, pdfpage);
		pp_pdf_patch_freetext_background_appearance_if_needed(ctx, pdf, annot);
		return 1;
	}

	return 0;
}

int
pp_pdf_update_freetext_alignment_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                   long long object_id,
                                                   int alignment)
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

	if (alignment < 0) alignment = 0;
	if (alignment > 2) alignment = 2;

	for (annot = pp_pdf_first_annot_compat(ctx, pdfpage); annot; annot = pp_pdf_next_annot_compat(ctx, pdfpage, annot))
	{
		long long id = pp_pdf_object_id_for_annot(ctx, annot);
		if (id != object_id)
			continue;

		int type = pdf_annot_type(ctx, annot);
#if PP_MUPDF_API_NEW
		if (type != (int)PDF_ANNOT_FREE_TEXT)
			return 0;
#else
		if (type != (int)FZ_ANNOT_FREETEXT)
			return 0;
#endif

		pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
		if (!annot_obj)
			return 0;

		pp_pdf_capture_freetext_border_style_if_missing(ctx, pdf, annot);
		pp_pdf_suppress_freetext_border_generation(ctx, pdf, annot);
		pdf_dict_dels(ctx, annot_obj, "AP");

		/* /Q: 0=left, 1=center, 2=right */
		pdf_dict_puts_drop(ctx, annot_obj, "Q", pp_pdf_new_real_compat(ctx, pdf, (float)alignment));

#if PP_MUPDF_API_NEW
		/*
		 * If OpenDroidPDF is using rich layout for this FreeText box, /Q is ignored by the
		 * appearance generator once /DS is present. Keep /DS in sync so alignment changes apply.
		 */
		const char *ds = pdf_dict_get_text_string_opt(ctx, annot_obj, PDF_NAME(DS));
		if (ds && opd_ds_has_marker(ds))
		{
			int flags = opd_text_style_flags_from_ds(ds);
			const char *font = "Helv";
			float size = 12.0f;
			int n = 0;
			float color4[4] = {0};
			float rgb[3] = {0.0f, 0.0f, 0.0f};
			if (pdf_annot_has_default_appearance(ctx, annot))
			{
				pdf_annot_default_appearance(ctx, annot, &font, &size, &n, color4);
				opd_rgb_from_default_appearance(rgb, n, color4);
				if (size <= 0.0f) size = 12.0f;
			}
			float line_height = opd_ds_float_property(ds, "line-height", OPD_DEFAULT_LINE_HEIGHT);
			float text_indent_pt = opd_ds_float_property(ds, "text-indent", OPD_DEFAULT_TEXT_INDENT_PT);
			char ds_buf[512];
			opd_build_freetext_ds(ds_buf, sizeof(ds_buf), font, size, rgb, alignment, flags, line_height, text_indent_pt);
			pdf_dict_put_text_string(ctx, annot_obj, PDF_NAME(DS), ds_buf);
		}
#endif

		pp_pdf_update_annot_compat(ctx, pdf, annot);
		pp_pdf_dirty_annot_compat(ctx, pdf, annot);
		pp_pdf_update_page_compat(ctx, pdf, pdfpage);
		pp_pdf_patch_freetext_background_appearance_if_needed(ctx, pdf, annot);
		return 1;
	}

	return 0;
}

int
pp_pdf_update_freetext_rotation_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                  long long object_id,
                                                  int rotation_degrees)
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

	/* Normalize to [0..359]. */
	if (rotation_degrees < 0 || rotation_degrees >= 360)
	{
		rotation_degrees %= 360;
		if (rotation_degrees < 0)
			rotation_degrees += 360;
	}

	for (annot = pp_pdf_first_annot_compat(ctx, pdfpage); annot; annot = pp_pdf_next_annot_compat(ctx, pdfpage, annot))
	{
		long long id = pp_pdf_object_id_for_annot(ctx, annot);
		if (id != object_id)
			continue;

		int type = pdf_annot_type(ctx, annot);
#if PP_MUPDF_API_NEW
		if (type != (int)PDF_ANNOT_FREE_TEXT)
			return 0;
#else
		if (type != (int)FZ_ANNOT_FREETEXT)
			return 0;
#endif

		pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
		if (!annot_obj)
			return 0;

		pp_pdf_capture_freetext_border_style_if_missing(ctx, pdf, annot);
		pp_pdf_suppress_freetext_border_generation(ctx, pdf, annot);
		pdf_dict_dels(ctx, annot_obj, "AP");

		/* Undocumented MuPDF feature: /Rotate on FreeText to rotate appearance generation. */
		pdf_dict_puts_drop(ctx, annot_obj, "Rotate", pp_pdf_new_real_compat(ctx, pdf, (float)rotation_degrees));

		pp_pdf_update_annot_compat(ctx, pdf, annot);
		pp_pdf_dirty_annot_compat(ctx, pdf, annot);
		pp_pdf_update_page_compat(ctx, pdf, pdfpage);
		pp_pdf_patch_freetext_background_appearance_if_needed(ctx, pdf, annot);
		return 1;
	}

	return 0;
}

int
pp_pdf_get_freetext_style_flags_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                  long long object_id,
                                                  int *out_flags)
{
	pdf_document *pdf;
	pdf_page *pdfpage;
	pdf_annot *annot;

	if (!ctx || !doc || !page || object_id < 0 || !out_flags)
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

		int type = pdf_annot_type(ctx, annot);
#if PP_MUPDF_API_NEW
		if (type != (int)PDF_ANNOT_FREE_TEXT)
			return 0;
#else
		if (type != (int)FZ_ANNOT_FREETEXT)
			return 0;
#endif

		pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
		if (!annot_obj)
			return 0;

		char *ds = opd_pdf_string_dup(ctx, pdf_dict_gets(ctx, annot_obj, "DS"));
		*out_flags = opd_text_style_flags_from_ds(ds);
		fz_free(ctx, ds);
		return 1;
	}

	return 0;
}

int
pp_pdf_update_freetext_style_flags_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                     long long object_id,
                                                     int style_flags)
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

	style_flags &= OPD_TEXT_STYLE_MASK;

	for (annot = pp_pdf_first_annot_compat(ctx, pdfpage); annot; annot = pp_pdf_next_annot_compat(ctx, pdfpage, annot))
	{
		long long id = pp_pdf_object_id_for_annot(ctx, annot);
		if (id != object_id)
			continue;

		int type = pdf_annot_type(ctx, annot);
#if PP_MUPDF_API_NEW
		if (type != (int)PDF_ANNOT_FREE_TEXT)
			return 0;
#else
		if (type != (int)FZ_ANNOT_FREETEXT)
			return 0;
#endif

		pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
		if (!annot_obj)
			return 0;

		char font_key[32];
		float size = 12.0f;
		float rgb[3] = {0.0f, 0.0f, 0.0f};
		{
			char *da = opd_pdf_string_dup(ctx, pdf_dict_gets(ctx, annot_obj, "DA"));
			opd_parse_default_appearance(da, font_key, &size, rgb);
			fz_free(ctx, da);
		}

		int q = 0;
		pdf_obj *q_obj = pdf_dict_gets(ctx, annot_obj, "Q");
		if (q_obj)
			q = pdf_to_int(ctx, q_obj);
		if (q < 0) q = 0;
		if (q > 2) q = 2;

		char *ds_existing = opd_pdf_string_dup(ctx, pdf_dict_gets(ctx, annot_obj, "DS"));
		float line_height = opd_ds_float_property(ds_existing, "line-height", OPD_DEFAULT_LINE_HEIGHT);
		float text_indent_pt = opd_ds_float_property(ds_existing, "text-indent", OPD_DEFAULT_TEXT_INDENT_PT);

		char ds_buf[512];
		opd_build_freetext_ds(ds_buf, sizeof(ds_buf), font_key, size, rgb, q, style_flags, line_height, text_indent_pt);
		fz_free(ctx, ds_existing);

#if PP_MUPDF_API_NEW
		pdf_dict_puts_drop(ctx, annot_obj, "DS", pdf_new_string(ctx, ds_buf, strlen(ds_buf)));
#else
		pdf_dict_puts_drop(ctx, annot_obj, "DS", pdf_new_string(ctx, pdf, ds_buf, (int)strlen(ds_buf)));
#endif

		pp_pdf_capture_freetext_border_style_if_missing(ctx, pdf, annot);
		pp_pdf_suppress_freetext_border_generation(ctx, pdf, annot);
		pdf_dict_dels(ctx, annot_obj, "AP");

		pp_pdf_update_annot_compat(ctx, pdf, annot);
		pp_pdf_dirty_annot_compat(ctx, pdf, annot);
		pp_pdf_update_page_compat(ctx, pdf, pdfpage);
		pp_pdf_patch_freetext_background_appearance_if_needed(ctx, pdf, annot);
		return 1;
	}

	return 0;
}

int
pp_pdf_get_freetext_paragraph_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                long long object_id,
                                                float *out_line_height,
                                                float *out_text_indent_pt)
{
	pdf_document *pdf;
	pdf_page *pdfpage;
	pdf_annot *annot;

	if (!ctx || !doc || !page || object_id < 0)
		return 0;
	if (!out_line_height || !out_text_indent_pt)
		return 0;

	*out_line_height = OPD_DEFAULT_LINE_HEIGHT;
	*out_text_indent_pt = OPD_DEFAULT_TEXT_INDENT_PT;

	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return 0;
	pdfpage = (pdf_page *)page;

	for (annot = pp_pdf_first_annot_compat(ctx, pdfpage); annot; annot = pp_pdf_next_annot_compat(ctx, pdfpage, annot))
	{
		long long id = pp_pdf_object_id_for_annot(ctx, annot);
		if (id != object_id)
			continue;

		int type = pdf_annot_type(ctx, annot);
#if PP_MUPDF_API_NEW
		if (type != (int)PDF_ANNOT_FREE_TEXT)
			return 0;
#else
		if (type != (int)FZ_ANNOT_FREETEXT)
			return 0;
#endif

		pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
		if (!annot_obj)
			return 0;

		char *ds = opd_pdf_string_dup(ctx, pdf_dict_gets(ctx, annot_obj, "DS"));
		*out_line_height = opd_ds_float_property(ds, "line-height", OPD_DEFAULT_LINE_HEIGHT);
		*out_text_indent_pt = opd_ds_float_property(ds, "text-indent", OPD_DEFAULT_TEXT_INDENT_PT);
		fz_free(ctx, ds);
		return 1;
	}

	return 0;
}

int
pp_pdf_update_freetext_paragraph_by_object_id_impl(fz_context *ctx, fz_document *doc, fz_page *page,
                                                   long long object_id,
                                                   float line_height,
                                                   float text_indent_pt)
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

	if (isnan(line_height) || isinf(line_height))
		line_height = OPD_DEFAULT_LINE_HEIGHT;
	if (line_height < 0.5f) line_height = OPD_DEFAULT_LINE_HEIGHT;
	if (line_height > 5.0f) line_height = 5.0f;

	if (isnan(text_indent_pt) || isinf(text_indent_pt))
		text_indent_pt = OPD_DEFAULT_TEXT_INDENT_PT;
	if (text_indent_pt < -144.0f) text_indent_pt = -144.0f;
	if (text_indent_pt > 144.0f) text_indent_pt = 144.0f;

	for (annot = pp_pdf_first_annot_compat(ctx, pdfpage); annot; annot = pp_pdf_next_annot_compat(ctx, pdfpage, annot))
	{
		long long id = pp_pdf_object_id_for_annot(ctx, annot);
		if (id != object_id)
			continue;

		int type = pdf_annot_type(ctx, annot);
#if PP_MUPDF_API_NEW
		if (type != (int)PDF_ANNOT_FREE_TEXT)
			return 0;
#else
		if (type != (int)FZ_ANNOT_FREETEXT)
			return 0;
#endif

		pdf_obj *annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
		if (!annot_obj)
			return 0;

		/* Derive current style from /DA + /Q + /DS (for style flags). */
		float size = 12.0f;
		float rgb[3] = {0.0f, 0.0f, 0.0f};
		{
			char font_key[32];
			char *da = opd_pdf_string_dup(ctx, pdf_dict_gets(ctx, annot_obj, "DA"));
			opd_parse_default_appearance(da, font_key, &size, rgb);
			fz_free(ctx, da);

			/* Prefer style flags from any existing /DS (keeps bold/italic etc). */
			char *ds_existing = opd_pdf_string_dup(ctx, pdf_dict_gets(ctx, annot_obj, "DS"));
			int flags = opd_text_style_flags_from_ds(ds_existing);
			fz_free(ctx, ds_existing);

			int q = 0;
			pdf_obj *q_obj = pdf_dict_gets(ctx, annot_obj, "Q");
			if (q_obj)
				q = pdf_to_int(ctx, q_obj);
			if (q < 0) q = 0;
			if (q > 2) q = 2;

			char ds_buf[512];
			opd_build_freetext_ds(ds_buf, sizeof(ds_buf), font_key, size, rgb, q, flags, line_height, text_indent_pt);

#if PP_MUPDF_API_NEW
			pdf_dict_puts_drop(ctx, annot_obj, "DS", pdf_new_string(ctx, ds_buf, strlen(ds_buf)));
#else
			pdf_dict_puts_drop(ctx, annot_obj, "DS", pdf_new_string(ctx, pdf, ds_buf, (int)strlen(ds_buf)));
#endif
		}

		pp_pdf_capture_freetext_border_style_if_missing(ctx, pdf, annot);
		pp_pdf_suppress_freetext_border_generation(ctx, pdf, annot);
		pdf_dict_dels(ctx, annot_obj, "AP");

		pp_pdf_update_annot_compat(ctx, pdf, annot);
		pp_pdf_dirty_annot_compat(ctx, pdf, annot);
		pp_pdf_update_page_compat(ctx, pdf, pdfpage);
		pp_pdf_patch_freetext_background_appearance_if_needed(ctx, pdf, annot);
		return 1;
	}

	return 0;
}
