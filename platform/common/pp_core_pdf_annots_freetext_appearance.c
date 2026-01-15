#include "pp_core_pdf_annots_internal.h"

#include <math.h>
#include <string.h>
#include <ctype.h>

#if defined(__ANDROID__) && defined(OPD_DEBUG_AP_PATCH)
#include <android/log.h>
#define OPD_APLOGI(...) __android_log_print(ANDROID_LOG_INFO, "opd_ap_patch", __VA_ARGS__)
#define OPD_APLOGW(...) __android_log_print(ANDROID_LOG_WARN, "opd_ap_patch", __VA_ARGS__)
#else
#define OPD_APLOGI(...) ((void)0)
#define OPD_APLOGW(...) ((void)0)
#endif

void
pp_pdf_capture_freetext_border_style_if_missing(fz_context *ctx, pdf_document *doc, pdf_annot *annot)
{
	pdf_obj *annot_obj;
	float w = 0.0f;
	int dashed = 0;

	if (!ctx || !doc || !annot)
		return;

	annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
	if (!annot_obj)
		return;

	if (pdf_dict_gets(ctx, annot_obj, "OPDBorderWidth"))
		return;

	pdf_obj *bs = pdf_dict_gets(ctx, annot_obj, "BS");
	if (bs && pdf_is_dict(ctx, bs))
	{
		pdf_obj *w_obj = pdf_dict_gets(ctx, bs, "W");
		if (w_obj) w = pdf_to_real(ctx, w_obj);
		pdf_obj *s_obj = pdf_dict_gets(ctx, bs, "S");
		if (s_obj && pdf_is_name(ctx, s_obj))
		{
			const char *name = pdf_to_name(ctx, s_obj);
			if (name && strcmp(name, "D") == 0)
				dashed = 1;
		}
	}
	else
	{
		pdf_obj *border = pdf_dict_gets(ctx, annot_obj, "Border");
		if (border && pdf_is_array(ctx, border) && pdf_array_len(ctx, border) >= 3)
		{
			pdf_obj *w_obj = pdf_array_get(ctx, border, 2);
			if (w_obj) w = pdf_to_real(ctx, w_obj);
		}
	}

	if (w > 0.0f)
	{
		pdf_dict_puts_drop(ctx, annot_obj, "OPDBorderWidth", pp_pdf_new_real_compat(ctx, doc, w));
		if (dashed)
			pdf_dict_puts_drop(ctx, annot_obj, "OPDBorderDashed", pp_pdf_new_real_compat(ctx, doc, 1.0f));
	}
}

void
pp_pdf_suppress_freetext_border_generation(fz_context *ctx, pdf_document *doc, pdf_annot *annot)
{
	pdf_obj *annot_obj;
	pdf_obj *bs;

	if (!ctx || !doc || !annot)
		return;

	annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
	if (!annot_obj)
		return;

	bs = pdf_dict_gets(ctx, annot_obj, "BS");
	if (!bs || !pdf_is_dict(ctx, bs))
	{
		bs = pdf_new_dict(ctx, doc, 2);
		pdf_dict_puts_drop(ctx, annot_obj, "BS", bs);
	}

	/* Prevent MuPDF from generating a border in /AP; OpenDroidPDF draws border via a patched appearance stream. */
	pdf_dict_puts_drop(ctx, bs, "W", pp_pdf_new_real_compat(ctx, doc, 0.0f));

	/* Some viewers prefer /Border (array form). If present, force it to 0 width to avoid double-stroking. */
	pdf_obj *border = pdf_dict_gets(ctx, annot_obj, "Border");
	if (border && pdf_is_array(ctx, border) && pdf_array_len(ctx, border) >= 3)
	{
		pdf_obj *arr = pdf_new_array(ctx, doc, 3);
		pdf_array_push_drop(ctx, arr, pp_pdf_new_real_compat(ctx, doc, 0.0f));
		pdf_array_push_drop(ctx, arr, pp_pdf_new_real_compat(ctx, doc, 0.0f));
		pdf_array_push_drop(ctx, arr, pp_pdf_new_real_compat(ctx, doc, 0.0f));
		pdf_dict_puts_drop(ctx, annot_obj, "Border", arr);
	}
}

/*
 * MuPDF's default FreeText appearance generator does not currently emit an interior fill
 * rectangle even when the annotation dictionary includes /IC. Acrobat does.
 *
 * Additionally, OpenDroidPDF supports border controls (width/color/dash/rounding) that must
 * render consistently across viewers. We patch the generated /AP stream to paint:
 * - a filled rect (when /IC is present), and
 * - a border (when OPD border metadata is present)
 * behind the text while allowing MuPDF to own text layout/wrapping.
 *
 * This is a post-processing step so MuPDF continues to own text layout/wrapping.
 */
void
pp_pdf_patch_freetext_background_appearance_if_needed(fz_context *ctx, pdf_document *doc, pdf_annot *annot)
{
	pdf_obj *annot_obj;
	pdf_obj *ic;
	pdf_obj *c;
	pdf_obj *bw_obj;
	pdf_obj *bd_obj;
	pdf_obj *br_obj;
	pdf_obj *ap;
	pdf_obj *n;
	pdf_obj *stream_ref;
	pdf_obj *stream_dict;
	pdf_obj *bbox_obj;
	pdf_obj *res;
	pdf_obj *extg;
	pdf_obj *hgs;
	fz_rect bbox;
	fz_buffer *orig = NULL;
	fz_buffer *patched = NULL;
	float fill_rgb[3] = { 0 };
	float border_rgb[3] = { 0 };
	float border_width_pt = 0.0f;
	float border_radius_pt = 0.0f;
	int has_fill = 0;
	int has_border = 0;
	int border_dashed = 0;
	unsigned char *orig_data = NULL;
	size_t orig_len = 0;
	size_t tail_off = 0;
	int num;
	int gen;
	int has_h_gstate = 0;

	if (!ctx || !doc || !annot)
		return;

	annot_obj = pp_pdf_annot_obj_compat(ctx, annot);
	if (!annot_obj)
		return;

	/* Only patch FreeText. */
	{
		int type = pdf_annot_type(ctx, annot);
#if PP_MUPDF_API_NEW
		if (type != (int)PDF_ANNOT_FREE_TEXT)
			return;
#else
		if (type != (int)FZ_ANNOT_FREETEXT)
			return;
#endif
	}

	/* Fill: /IC (standard). */
	ic = pdf_dict_gets(ctx, annot_obj, "IC");
	if (ic && pdf_is_array(ctx, ic) && pdf_array_len(ctx, ic) >= 3)
	{
		has_fill = 1;
		fill_rgb[0] = pdf_to_real(ctx, pdf_array_get(ctx, ic, 0));
		fill_rgb[1] = pdf_to_real(ctx, pdf_array_get(ctx, ic, 1));
		fill_rgb[2] = pdf_to_real(ctx, pdf_array_get(ctx, ic, 2));
		for (int i = 0; i < 3; i++)
		{
			if (fill_rgb[i] < 0.0f) fill_rgb[i] = 0.0f;
			if (fill_rgb[i] > 1.0f) fill_rgb[i] = 1.0f;
		}
	}

	/* Border: OpenDroidPDF metadata (app-only) + standard color (/C). */
	bw_obj = pdf_dict_gets(ctx, annot_obj, "OPDBorderWidth");
	if (bw_obj)
	{
		border_width_pt = pdf_to_real(ctx, bw_obj);
		if (border_width_pt < 0.0f) border_width_pt = 0.0f;
		if (border_width_pt > 24.0f) border_width_pt = 24.0f;
		has_border = border_width_pt > 0.01f;
	}
	bd_obj = pdf_dict_gets(ctx, annot_obj, "OPDBorderDashed");
	if (bd_obj)
		border_dashed = (pdf_to_real(ctx, bd_obj) > 0.5f) ? 1 : 0;
	br_obj = pdf_dict_gets(ctx, annot_obj, "OPDBorderRadius");
	if (br_obj)
	{
		border_radius_pt = pdf_to_real(ctx, br_obj);
		if (border_radius_pt < 0.0f) border_radius_pt = 0.0f;
		if (border_radius_pt > 48.0f) border_radius_pt = 48.0f;
	}

	if (has_border)
	{
		c = pdf_dict_gets(ctx, annot_obj, "C");
		if (c && pdf_is_array(ctx, c) && pdf_array_len(ctx, c) >= 3)
		{
			border_rgb[0] = pdf_to_real(ctx, pdf_array_get(ctx, c, 0));
			border_rgb[1] = pdf_to_real(ctx, pdf_array_get(ctx, c, 1));
			border_rgb[2] = pdf_to_real(ctx, pdf_array_get(ctx, c, 2));
			for (int i = 0; i < 3; i++)
			{
				if (border_rgb[i] < 0.0f) border_rgb[i] = 0.0f;
				if (border_rgb[i] > 1.0f) border_rgb[i] = 1.0f;
			}
		}
	}

	/* No fill/border to patch. */
	if (!has_fill && !has_border)
		return;

	ap = pdf_dict_gets(ctx, annot_obj, "AP");
	if (!ap)
	{
		OPD_APLOGI("skip: no /AP on annot");
		return;
	}
	n = pdf_dict_gets(ctx, ap, "N");
	if (!n)
	{
		OPD_APLOGI("skip: no /AP/N on annot");
		return;
	}
	OPD_APLOGI("/AP/N: indirect=%d dict=%d parent=%d", pdf_is_indirect(ctx, n) ? 1 : 0, pdf_is_dict(ctx, n) ? 1 : 0, pdf_obj_parent_num(ctx, n));

	/* Normal appearance can be a stream or a dict of named states. Prefer AS when present. */
	stream_ref = n;
	{
		/*
		 * In MuPDF, an appearance stream resolves to a dict (stream dictionary), so we must
		 * detect "is stream" first before treating /AP/N as a dict-of-states.
		 */
		int n_num = pdf_to_num(ctx, n);
		int n_gen = pdf_to_gen(ctx, n);
		if (n_num <= 0)
			n_num = pdf_obj_parent_num(ctx, n);
		int n_is_stream = (n_num > 0 && pp_pdf_is_stream_compat(ctx, doc, n, n_num, n_gen)) ? 1 : 0;

		if (!n_is_stream && pdf_is_dict(ctx, n))
		{
			pdf_obj *as = pdf_dict_gets(ctx, annot_obj, "AS");
			pdf_obj *st = NULL;
			if (as && pdf_is_name(ctx, as))
				st = pdf_dict_get(ctx, n, as);
			if (!st && pdf_dict_len(ctx, n) > 0)
				st = pdf_dict_get_val(ctx, n, 0);
			if (!st)
				return;
			stream_ref = st;
		}
	}
	OPD_APLOGI("/AP stream_ref: indirect=%d dict=%d parent=%d", pdf_is_indirect(ctx, stream_ref) ? 1 : 0, pdf_is_dict(ctx, stream_ref) ? 1 : 0, pdf_obj_parent_num(ctx, stream_ref));

	num = pdf_to_num(ctx, stream_ref);
	gen = pdf_to_gen(ctx, stream_ref);
	if (num <= 0)
		num = pdf_obj_parent_num(ctx, stream_ref);
	if (num <= 0)
	{
		OPD_APLOGW("skip: /AP has no object number (gen=%d)", gen);
		return;
	}
	if (!pp_pdf_is_stream_compat(ctx, doc, stream_ref, num, gen))
	{
		OPD_APLOGW("skip: /AP %d %d is not a stream", num, gen);
		return;
	}

	stream_dict = pdf_resolve_indirect(ctx, stream_ref);
	if (!stream_dict)
	{
		OPD_APLOGW("skip: failed to resolve /AP stream dict for %d %d", num, gen);
		return;
	}

	/* Use the appearance stream's coordinate system (/BBox) when possible. */
	bbox_obj = pdf_dict_gets(ctx, stream_dict, "BBox");
	if (bbox_obj && pdf_is_array(ctx, bbox_obj) && pdf_array_len(ctx, bbox_obj) >= 4)
	{
		pp_pdf_to_rect_compat(ctx, bbox_obj, &bbox);
	}
	else
	{
		pdf_obj *rect_obj = pdf_dict_gets(ctx, annot_obj, "Rect");
		if (!rect_obj || !pdf_is_array(ctx, rect_obj) || pdf_array_len(ctx, rect_obj) < 4)
			return;
		pp_pdf_to_rect_compat(ctx, rect_obj, &bbox);
	}

	if ((bbox.x1 - bbox.x0) <= 0.5f || (bbox.y1 - bbox.y0) <= 0.5f)
	{
		OPD_APLOGW("skip: invalid bbox for %d %d (%.2f %.2f %.2f %.2f)", num, gen, bbox.x0, bbox.y0, bbox.x1, bbox.y1);
		return;
	}

	/* If the appearance has an opacity ExtGState (MuPDF uses /H), apply it for the fill too. */
	res = pdf_dict_gets(ctx, stream_dict, "Resources");
	if (res)
	{
		extg = pdf_dict_gets(ctx, res, "ExtGState");
		if (extg)
		{
			hgs = pdf_dict_gets(ctx, extg, "H");
			if (hgs)
				has_h_gstate = 1;
		}
	}

	fz_var(orig);
	fz_var(patched);
	fz_try(ctx)
	{
		orig = pp_pdf_load_stream_compat(ctx, doc, stream_ref, num, gen);
		if (!orig)
			fz_throw(ctx, FZ_ERROR_GENERIC, "no appearance stream");

			orig_len = pp_fz_buffer_storage_compat(ctx, orig, &orig_data);
			if (!orig_data || orig_len == 0)
				fz_throw(ctx, FZ_ERROR_GENERIC, "empty appearance stream");

			/* If we've already patched this /AP, replace our prefix instead of stacking patches. */
			{
				static const unsigned char k_marker_new[] = "%OPD_AP_PATCH";
				static const unsigned char k_marker_old[] = "%OPD_BG_FILL";
				static const unsigned char k_end[] = "\nQ\n";
				const unsigned char *m = pp_memmem_compat(orig_data, orig_len, k_marker_new, sizeof(k_marker_new) - 1);
				if (!m)
					m = pp_memmem_compat(orig_data, orig_len, k_marker_old, sizeof(k_marker_old) - 1);
				if (m && (size_t)(m - orig_data) < 64)
				{
					const unsigned char *e = pp_memmem_compat(m, orig_len - (size_t)(m - orig_data), k_end, sizeof(k_end) - 1);
					if (e)
						tail_off = (size_t)(e - orig_data) + (sizeof(k_end) - 1);
				}
			}

			patched = fz_new_buffer(ctx, orig_len + 768);
			pp_fz_buffer_printf_compat(ctx, patched, "q\n%%OPD_AP_PATCH\n");
			if (has_h_gstate)
				pp_fz_buffer_printf_compat(ctx, patched, "/H gs\n");

			if (has_fill)
				pp_fz_buffer_printf_compat(ctx, patched, "%g %g %g rg\n", fill_rgb[0], fill_rgb[1], fill_rgb[2]);
			if (has_border)
			{
				pp_fz_buffer_printf_compat(ctx, patched, "%g %g %g RG\n", border_rgb[0], border_rgb[1], border_rgb[2]);
				pp_fz_buffer_printf_compat(ctx, patched, "%g w\n", border_width_pt);
				if (border_dashed)
				{
					float on = fmaxf(1.0f, border_width_pt * 3.0f);
					float off = fmaxf(1.0f, border_width_pt * 2.0f);
					pp_fz_buffer_printf_compat(ctx, patched, "[%g %g] 0 d\n", on, off);
				}
				else
				{
					pp_fz_buffer_printf_compat(ctx, patched, "[] 0 d\n");
				}
			}

			/* Draw a (possibly rounded) rect for fill/border. Inset stroke by half width to avoid clipping. */
		{
			float inset = has_border ? (border_width_pt * 0.5f) : 0.0f;
			float x0 = bbox.x0 + inset;
			float y0 = bbox.y0 + inset;
			float x1 = bbox.x1 - inset;
			float y1 = bbox.y1 - inset;
			float w = x1 - x0;
			float h = y1 - y0;
			float r = border_radius_pt;
			if (w <= 0.5f || h <= 0.5f)
			{
				/* Fallback to the raw bbox if the inset collapses. */
				x0 = bbox.x0; y0 = bbox.y0; w = bbox.x1 - bbox.x0; h = bbox.y1 - bbox.y0;
				r = 0.0f;
			}

			if (r > 0.0f)
			{
				float max_r = fminf(w, h) * 0.5f;
				if (r > max_r) r = max_r;
				if (r < 0.01f) r = 0.0f;
			}

			if (r <= 0.0f)
			{
				pp_fz_buffer_printf_compat(ctx, patched, "%g %g %g %g re\n", x0, y0, w, h);
			}
			else
			{
				const float k = 0.5522847498f;
				float c = k * r;
				pp_fz_buffer_printf_compat(ctx, patched, "%g %g m\n", x0 + r, y0);
				pp_fz_buffer_printf_compat(ctx, patched, "%g %g l\n", x1 - r, y0);
				pp_fz_buffer_printf_compat(ctx, patched, "%g %g %g %g %g %g c\n",
				                          x1 - r + c, y0, x1, y0 + r - c, x1, y0 + r);
				pp_fz_buffer_printf_compat(ctx, patched, "%g %g l\n", x1, y1 - r);
				pp_fz_buffer_printf_compat(ctx, patched, "%g %g %g %g %g %g c\n",
				                          x1, y1 - r + c, x1 - r + c, y1, x1 - r, y1);
				pp_fz_buffer_printf_compat(ctx, patched, "%g %g l\n", x0 + r, y1);
				pp_fz_buffer_printf_compat(ctx, patched, "%g %g %g %g %g %g c\n",
				                          x0 + r - c, y1, x0, y1 - r + c, x0, y1 - r);
				pp_fz_buffer_printf_compat(ctx, patched, "%g %g l\n", x0, y0 + r);
				pp_fz_buffer_printf_compat(ctx, patched, "%g %g %g %g %g %g c\n",
				                          x0, y0 + r - c, x0 + r - c, y0, x0 + r, y0);
				pp_fz_buffer_printf_compat(ctx, patched, "h\n");
			}

			if (has_fill && has_border)
				pp_fz_buffer_printf_compat(ctx, patched, "B\n");
			else if (has_fill)
				pp_fz_buffer_printf_compat(ctx, patched, "f\n");
			else
				pp_fz_buffer_printf_compat(ctx, patched, "S\n");
		}

			pp_fz_buffer_printf_compat(ctx, patched, "Q\n");
			if (tail_off > 0 && tail_off < orig_len)
				pp_fz_buffer_append_data_compat(ctx, patched, orig_data + tail_off, orig_len - tail_off);
			else
				pp_fz_buffer_append_data_compat(ctx, patched, orig_data, orig_len);

		/*
		 * Update the stream via its indirect ref so MuPDF can reliably resolve and
		 * locate the correct xref entry across API versions.
		 */
#if PP_MUPDF_API_NEW
		pdf_begin_implicit_operation(ctx, doc);
		fz_try(ctx)
		{
			pdf_update_stream(ctx, doc, stream_ref, patched, 0);
			pdf_end_operation(ctx, doc);
		}
		fz_catch(ctx)
		{
			pdf_abandon_operation(ctx, doc);
			fz_rethrow(ctx);
		}
#else
		pdf_update_stream(ctx, doc, stream_ref, patched, 0);
#endif
		OPD_APLOGI("patched /AP stream %d %d (tail_off=%zu)", num, gen, tail_off);
	}
	fz_always(ctx)
	{
		fz_drop_buffer(ctx, orig);
		fz_drop_buffer(ctx, patched);
	}
	fz_catch(ctx)
	{
		/* Best-effort: if patching fails, keep MuPDF-generated /AP unchanged. */
		OPD_APLOGW("patch failed for /AP stream %d %d: %s", num, gen, fz_caught_message(ctx));
	}
}
