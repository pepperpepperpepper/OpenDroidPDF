#include "pp_core_pdf_annots_freetext_internal.h"

#include <math.h>
#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <stdlib.h>

char *
opd_pdf_string_dup(fz_context *ctx, pdf_obj *obj)
{
	int len;
	char *out;

	if (!ctx || !obj || !pdf_is_string(ctx, obj))
		return NULL;

	len = pdf_to_str_len(ctx, obj);
	if (len < 0)
		return NULL;

	out = (char *)fz_malloc(ctx, (size_t)len + 1u);
	if (len > 0)
		memcpy(out, pdf_to_str_buf(ctx, obj), (size_t)len);
	out[len] = '\0';
	return out;
}

void
opd_parse_default_appearance(const char *da,
                            char out_font_key[32],
                            float *out_font_size,
                            float out_rgb[3])
{
	char font[32] = {0};
	float size = 12.0f;
	float r = 0.0f, g = 0.0f, b = 0.0f;
	float gray = 0.0f;

	if (out_font_key)
		out_font_key[0] = '\0';
	if (out_font_size)
		*out_font_size = 12.0f;
	if (out_rgb)
	{
		out_rgb[0] = 0.0f;
		out_rgb[1] = 0.0f;
		out_rgb[2] = 0.0f;
	}

	if (!da || !da[0])
	{
		if (out_font_key)
			fz_strlcpy(out_font_key, "Helv", 32);
		return;
	}

	/* Common patterns:
	 *  - "/Helv 12 Tf 0 0 0 rg"
	 *  - "/Helv 12 Tf 0 g"
	 */
	if (sscanf(da, " /%31s %f Tf %f %f %f rg", font, &size, &r, &g, &b) == 5 ||
	    sscanf(da, "/%31s %f Tf %f %f %f rg", font, &size, &r, &g, &b) == 5)
	{
		/* ok */
	}
	else if (sscanf(da, " /%31s %f Tf %f g", font, &size, &gray) == 3 ||
	         sscanf(da, "/%31s %f Tf %f g", font, &size, &gray) == 3)
	{
		r = g = b = gray;
	}
	else
	{
		/* Best-effort: only pick up the font key if we can. */
		if (sscanf(da, " /%31s", font) != 1 && sscanf(da, "/%31s", font) != 1)
			font[0] = '\0';
	}

	if (out_font_key)
	{
		if (font[0])
			fz_strlcpy(out_font_key, font, 32);
		else
			fz_strlcpy(out_font_key, "Helv", 32);
	}

	if (out_font_size)
	{
		if (size > 0.0f && !isnan(size) && !isinf(size))
			*out_font_size = size;
	}

	if (out_rgb)
	{
		if (!isnan(r) && !isinf(r)) out_rgb[0] = r;
		if (!isnan(g) && !isinf(g)) out_rgb[1] = g;
		if (!isnan(b) && !isinf(b)) out_rgb[2] = b;
	}
}

static const char *
opd_strcasestr(const char *hay, const char *needle)
{
	size_t nlen;

	if (!hay || !needle)
		return NULL;
	nlen = strlen(needle);
	if (nlen == 0)
		return hay;

	for (const char *p = hay; *p; p++)
	{
		if (tolower((unsigned char)*p) != tolower((unsigned char)needle[0]))
			continue;
		size_t i = 1;
		for (; i < nlen; i++)
		{
			char hc = p[i];
			if (!hc)
				break;
			if (tolower((unsigned char)hc) != tolower((unsigned char)needle[i]))
				break;
		}
		if (i == nlen)
			return p;
	}

	return NULL;
}

int
opd_ds_has_marker(const char *ds)
{
	return ds && opd_strcasestr(ds, OPD_DS_MARKER) ? 1 : 0;
}

float
opd_ds_float_property(const char *ds, const char *prop, float fallback)
{
	if (!ds || !prop || !prop[0])
		return fallback;

	char needle[64];
	fz_snprintf(needle, (int)sizeof needle, "%s:", prop);
	const char *p = opd_strcasestr(ds, needle);
	if (!p)
		return fallback;
	p += strlen(needle);

	while (*p == ' ' || *p == '\t')
		p++;

	char *end = NULL;
	double v = strtod(p, &end);
	if (!end || end == p)
		return fallback;

	float out = (float)v;
	if (isnan(out) || isinf(out))
		return fallback;
	return out;
}

int
opd_text_style_flags_from_ds(const char *ds)
{
	int flags = 0;
	if (!ds || !ds[0])
		return 0;

	if (opd_strcasestr(ds, "font-weight:bold") || opd_strcasestr(ds, "font-weight:700") || opd_strcasestr(ds, "font-weight:800") || opd_strcasestr(ds, "font-weight:900"))
		flags |= OPD_TEXT_STYLE_BOLD;
	if (opd_strcasestr(ds, "font-style:italic") || opd_strcasestr(ds, "font-style:oblique"))
		flags |= OPD_TEXT_STYLE_ITALIC;
	if (opd_strcasestr(ds, "text-decoration:underline") || opd_strcasestr(ds, "text-decoration: underline") || opd_strcasestr(ds, "underline"))
		flags |= OPD_TEXT_STYLE_UNDERLINE;
	if (opd_strcasestr(ds, "line-through") || opd_strcasestr(ds, "linethrough"))
		flags |= OPD_TEXT_STYLE_STRIKETHROUGH;

	return flags & OPD_TEXT_STYLE_MASK;
}

static const char *
opd_full_font_name_from_key(const char *font_key)
{
	if (!font_key || !font_key[0])
		return "Helvetica";
	if (!strcmp(font_key, "TiRo") || !strncmp(font_key, "Times", 5))
		return "Times-Roman";
	if (!strcmp(font_key, "Cour") || !strncmp(font_key, "Cour", 4) || !strncmp(font_key, "Couri", 5))
		return "Courier";
	if (!strcmp(font_key, "Symb"))
		return "Symbol";
	if (!strcmp(font_key, "ZaDb"))
		return "ZapfDingbats";
	return "Helvetica";
}

void
opd_rgb_from_default_appearance(float out_rgb[3], int n, const float color[4])
{
	if (!out_rgb)
		return;
	out_rgb[0] = 0.0f;
	out_rgb[1] = 0.0f;
	out_rgb[2] = 0.0f;
	if (!color) return;
	if (n <= 0) return;
	if (n == 1)
	{
		float g = color[0];
		out_rgb[0] = g;
		out_rgb[1] = g;
		out_rgb[2] = g;
		return;
	}
	if (n == 3)
	{
		out_rgb[0] = color[0];
		out_rgb[1] = color[1];
		out_rgb[2] = color[2];
		return;
	}
	if (n >= 4)
	{
		float c = color[0], m = color[1], y = color[2], k = color[3];
		float r = 1.0f - fminf(1.0f, c + k);
		float g = 1.0f - fminf(1.0f, m + k);
		float b = 1.0f - fminf(1.0f, y + k);
		out_rgb[0] = r;
		out_rgb[1] = g;
		out_rgb[2] = b;
		return;
	}
}

void
opd_build_freetext_ds(char *out, size_t out_len,
                      const char *font_key,
                      float font_size,
                      const float rgb[3],
                      int alignment,
                      int style_flags,
                      float line_height,
                      float text_indent_pt)
{
	const char *align = "left";
	const char *weight = (style_flags & OPD_TEXT_STYLE_BOLD) ? "bold" : "normal";
	const char *style = (style_flags & OPD_TEXT_STYLE_ITALIC) ? "italic" : "normal";
	char deco_buf[64];
	const char *deco = "none";
	int r = 0, g = 0, b = 0;

	if (!out || out_len == 0)
		return;

	if (alignment == 1) align = "center";
	else if (alignment == 2) align = "right";

	if (rgb)
	{
		float rf = rgb[0], gf = rgb[1], bf = rgb[2];
		if (rf < 0.0f) rf = 0.0f;
		if (rf > 1.0f) rf = 1.0f;
		if (gf < 0.0f) gf = 0.0f;
		if (gf > 1.0f) gf = 1.0f;
		if (bf < 0.0f) bf = 0.0f;
		if (bf > 1.0f) bf = 1.0f;
		r = (int)lroundf(rf * 255.0f);
		g = (int)lroundf(gf * 255.0f);
		b = (int)lroundf(bf * 255.0f);
		if (r < 0) r = 0;
		if (r > 255) r = 255;
		if (g < 0) g = 0;
		if (g > 255) g = 255;
		if (b < 0) b = 0;
		if (b > 255) b = 255;
	}

	deco_buf[0] = 0;
	if (style_flags & (OPD_TEXT_STYLE_UNDERLINE | OPD_TEXT_STYLE_STRIKETHROUGH))
	{
		size_t off = 0;
		if (style_flags & OPD_TEXT_STYLE_UNDERLINE)
		{
			const char *u = "underline";
			size_t ul = strlen(u);
			if (off + ul + 1 < sizeof(deco_buf))
			{
				memcpy(deco_buf + off, u, ul);
				off += ul;
				deco_buf[off] = 0;
			}
		}
		if (style_flags & OPD_TEXT_STYLE_STRIKETHROUGH)
		{
			const char *s = "line-through";
			size_t sl = strlen(s);
			if (off > 0 && off + 1 < sizeof(deco_buf))
			{
				deco_buf[off++] = ' ';
				deco_buf[off] = 0;
			}
			if (off + sl + 1 < sizeof(deco_buf))
			{
				memcpy(deco_buf + off, s, sl);
				off += sl;
				deco_buf[off] = 0;
			}
		}
		if (deco_buf[0])
			deco = deco_buf;
	}

	if (isnan(line_height) || isinf(line_height))
		line_height = OPD_DEFAULT_LINE_HEIGHT;
	if (line_height < 0.5f) line_height = OPD_DEFAULT_LINE_HEIGHT;
	if (line_height > 5.0f) line_height = 5.0f;

	if (isnan(text_indent_pt) || isinf(text_indent_pt))
		text_indent_pt = OPD_DEFAULT_TEXT_INDENT_PT;
	if (text_indent_pt < -144.0f) text_indent_pt = -144.0f;
	if (text_indent_pt > 144.0f) text_indent_pt = 144.0f;

	fz_snprintf(out, (int)out_len,
	            "%s;font-family:%s;font-size:%gpt;line-height:%g;text-indent:%gpt;color:#%02x%02x%02x;text-align:%s;font-weight:%s;font-style:%s;text-decoration:%s;",
	            OPD_DS_MARKER,
	            opd_full_font_name_from_key(font_key),
	            font_size,
	            line_height,
	            text_indent_pt,
	            r, g, b,
	            align,
	            weight,
	            style,
	            deco);
}
