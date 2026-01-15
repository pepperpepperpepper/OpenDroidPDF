#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <ctype.h>

#include <pthread.h>
#include <unistd.h>

#include "pp_core.h"

#include <mupdf/fitz.h>
#include <mupdf/pdf.h>

#if defined(FZ_VERSION_MAJOR) && defined(FZ_VERSION_MINOR)
#define PP_DEMO_MUPDF_API_NEW 1
#else
#define PP_DEMO_MUPDF_API_NEW 0
#endif

#if PP_DEMO_MUPDF_API_NEW
#define PP_DEMO_ANNOT_HIGHLIGHT PDF_ANNOT_HIGHLIGHT
#define PP_DEMO_ANNOT_FREE_TEXT PDF_ANNOT_FREE_TEXT
#else
#define PP_DEMO_ANNOT_HIGHLIGHT FZ_ANNOT_HIGHLIGHT
#define PP_DEMO_ANNOT_FREE_TEXT FZ_ANNOT_FREETEXT
#endif

static int
write_ppm_from_rgba(const char *path, int w, int h, const unsigned char *rgba)
{
	FILE *f;
	unsigned char *row;
	int y, x;

	f = fopen(path, "wb");
	if (!f)
		return 0;

	if (fprintf(f, "P6\n%d %d\n255\n", w, h) < 0)
	{
		fclose(f);
		return 0;
	}

	row = (unsigned char *)malloc((size_t)w * 3);
	if (!row)
	{
		fclose(f);
		return 0;
	}

	for (y = 0; y < h; y++)
	{
		const unsigned char *src = rgba + (size_t)y * (size_t)w * 4;
		for (x = 0; x < w; x++)
		{
			row[x * 3 + 0] = src[0];
			row[x * 3 + 1] = src[1];
			row[x * 3 + 2] = src[2];
			src += 4;
		}
		if (fwrite(row, 1, (size_t)w * 3, f) != (size_t)w * 3)
		{
			free(row);
			fclose(f);
			return 0;
		}
	}

	free(row);
	fclose(f);
	return 1;
}

static int
count_nonwhite_rgba(int w, int h, const unsigned char *rgba)
{
	const int thr = 250;
	int count = 0;
	int y, x;
	for (y = 0; y < h; y++)
	{
		const unsigned char *p = rgba + (size_t)y * (size_t)w * 4u;
		for (x = 0; x < w; x++)
		{
			if (p[0] < thr || p[1] < thr || p[2] < thr)
				count++;
			p += 4;
		}
	}
	return count;
}

typedef struct
{
	pp_ctx *ctx;
	pp_doc *doc;
	int page_index;
	int pageW;
	int pageH;
	unsigned char *rgba;
	int stride;
	pp_cookie *cookie;
	int ok;
} render_job;

static void *
render_thread(void *arg)
{
	render_job *job = (render_job *)arg;
	job->ok = pp_render_patch_rgba(job->ctx, job->doc, job->page_index,
	                              job->pageW, job->pageH,
	                              0, 0, job->pageW, job->pageH,
	                              job->rgba, job->stride, job->cookie);
	return NULL;
}

static int
run_cancel_smoke(pp_ctx *ctx, pp_doc *doc, int page_index, int base_w, int base_h)
{
	static const int scales[] = { 4, 6, 8, 10, 12 };
	const size_t max_bytes = 256u * 1024u * 1024u;
	pp_cookie *cookie = NULL;
	int canceled = 0;
	int i;

	cookie = pp_cookie_new(ctx);
	if (!cookie)
		return 0;

	for (i = 0; i < (int)(sizeof(scales) / sizeof(scales[0])); i++)
	{
		int scale = scales[i];
		int w = base_w * scale;
		int h = base_h * scale;
		size_t bytes;
		unsigned char *rgba = NULL;
		pthread_t th;
		render_job job;

		if (w <= 0 || h <= 0)
			continue;
		bytes = (size_t)w * (size_t)h * 4u;
		if (bytes > max_bytes)
			continue;

		rgba = (unsigned char *)malloc(bytes);
		if (!rgba)
			continue;

		pp_cookie_reset(cookie);
		memset(&job, 0, sizeof(job));
		job.ctx = ctx;
		job.doc = doc;
		job.page_index = page_index;
		job.pageW = w;
		job.pageH = h;
		job.rgba = rgba;
		job.stride = w * 4;
		job.cookie = cookie;

		if (pthread_create(&th, NULL, render_thread, &job) != 0)
		{
			free(rgba);
			continue;
		}

		/* Give the render thread a chance to enter MuPDF, then abort. */
		usleep(1000);
		pp_cookie_abort(cookie);

		pthread_join(th, NULL);
		free(rgba);

		if (job.ok == 0 && pp_cookie_aborted(cookie))
		{
			canceled = 1;
			break;
		}
	}

	if (!canceled)
	{
		pp_cookie_drop(ctx, cookie);
		return 0;
	}

	/* After a cancel, rendering must still work. */
	pp_cookie_reset(cookie);
	{
		size_t bytes = (size_t)base_w * (size_t)base_h * 4u;
		unsigned char *rgba = (unsigned char *)malloc(bytes);
		int ok = 0;
		if (!rgba)
		{
			pp_cookie_drop(ctx, cookie);
			return 0;
		}
		ok = pp_render_patch_rgba(ctx, doc, page_index,
		                          base_w, base_h,
		                          0, 0, base_w, base_h,
		                          rgba, base_w * 4, cookie);
		free(rgba);
		pp_cookie_drop(ctx, cookie);
		return ok ? 1 : 0;
	}
}

static long long
pp_demo_object_id_for_pdf_annot(fz_context *ctx, pdf_annot *annot)
{
	if (!ctx || !annot || !annot->obj)
		return -1;
	return ((long long)pdf_to_num(ctx, annot->obj) << 32) | (unsigned int)pdf_to_gen(ctx, annot->obj);
}

static pdf_annot *
pp_demo_find_pdf_annot_by_object_id(fz_context *ctx, pdf_page *page, long long object_id)
{
	if (!ctx || !page || object_id < 0)
		return NULL;
	for (pdf_annot *a = pdf_first_annot(ctx, page); a; a = pdf_next_annot(ctx, page, a))
	{
		if (pp_demo_object_id_for_pdf_annot(ctx, a) == object_id)
			return a;
	}
	return NULL;
}

static char *
pp_demo_pdf_string_dup(fz_context *ctx, pdf_obj *obj)
{
	char *buf;
	int len;

	if (!ctx || !obj || !pdf_is_string(ctx, obj))
		return NULL;

	len = pdf_to_str_len(ctx, obj);
	if (len < 0)
		return NULL;

	buf = (char *)malloc((size_t)len + 1u);
	if (!buf)
		return NULL;

	if (len > 0)
		memcpy(buf, pdf_to_str_buf(ctx, obj), (size_t)len);
	buf[len] = '\0';
	return buf;
}

static int
pp_demo_parse_css_float_prop(const char *css, const char *prop_with_colon, float *out_value)
{
	const char *p;
	char *end = NULL;
	float v;

	if (!css || !prop_with_colon || !*prop_with_colon || !out_value)
		return 0;

	p = strstr(css, prop_with_colon);
	if (!p)
		return 0;
	p += strlen(prop_with_colon);
	while (*p && isspace((unsigned char)*p))
		p++;

	v = strtof(p, &end);
	if (!end || end == p)
		return 0;

	*out_value = v;
	return 1;
}

static int
run_freetext_rich_smoke(const char *input_path, int page_index, const char *out_pdf)
{
	const char *k_rc_marker = "ODP_RICH_SMOKE";
	const char *rc_xml =
	        "<body xmlns=\"http://www.w3.org/1999/xhtml\"><p>ODP_RICH_SMOKE</p></body>";
	const float initial_line_height = 1.45f;
	const float initial_indent_pt = 24.0f;
	const float updated_line_height = 1.60f;
	const float updated_indent_pt = 18.0f;

	fz_context *ctx = NULL;
	fz_document *doc = NULL;
	pdf_document *pdf = NULL;
	pdf_page *page = NULL;
	long long object_id = -1;
	int ok = 0;

	if (!input_path || !*input_path || !out_pdf || !*out_pdf)
		return 0;

	ctx = fz_new_context(NULL, NULL, FZ_STORE_UNLIMITED);
	if (!ctx)
	{
		fprintf(stderr, "pp_demo: freetext-rich smoke failed to create MuPDF context\n");
		return 0;
	}
	fz_register_document_handlers(ctx);

	fz_try(ctx)
	{
		int page_count;
		fz_rect bounds;
		int pageW, pageH;
		pp_point rect[2];
		float color1[3] = { 0.0f, 0.0f, 1.0f };
		float color2[3] = { 0.0f, 1.0f, 0.0f };
		char ds[256];

		doc = fz_open_document(ctx, input_path);
		if (!doc)
			fz_throw(ctx, FZ_ERROR_GENERIC, "failed to open: %s", input_path);

		pdf = pdf_specifics(ctx, doc);
		if (!pdf)
			fz_throw(ctx, FZ_ERROR_GENERIC, "not a PDF: %s", input_path);

		page_count = fz_count_pages(ctx, doc);
		if (page_count <= 0)
			fz_throw(ctx, FZ_ERROR_GENERIC, "failed to count pages");
		if (page_index < 0 || page_index >= page_count)
			fz_throw(ctx, FZ_ERROR_GENERIC, "page out of range: %d (0..%d)", page_index, page_count - 1);

		page = pdf_load_page(ctx, pdf, page_index);
		if (!page)
			fz_throw(ctx, FZ_ERROR_GENERIC, "failed to load page %d", page_index);

		pdf_bound_page(ctx, page, &bounds);
		pageW = (int)((bounds.x1 - bounds.x0) + 0.5f);
		pageH = (int)((bounds.y1 - bounds.y0) + 0.5f);
		if (pageW < 1) pageW = 1;
		if (pageH < 1) pageH = 1;

		rect[0].x = 0.20f * (float)pageW;
		rect[0].y = 0.22f * (float)pageH;
		rect[1].x = 0.80f * (float)pageW;
		rect[1].y = 0.34f * (float)pageH;

		snprintf(ds, sizeof(ds),
		         "font: 12pt Helvetica; color:#000000; line-height:%g; text-indent:%gpt;",
		         (double)initial_line_height,
		         (double)initial_indent_pt);

		if (!pp_pdf_add_annot_mupdf(ctx, doc, (fz_page *)page, page_index,
		                           pageW, pageH,
		                           PP_DEMO_ANNOT_FREE_TEXT,
		                           rect, 2,
		                           color1, 1.0f,
		                           "pp_demo rich smoke",
		                           &object_id) || object_id < 0)
		{
			fz_throw(ctx, FZ_ERROR_GENERIC, "failed to add FreeText annotation");
		}

		{
			pdf_annot *annot = pp_demo_find_pdf_annot_by_object_id(ctx, page, object_id);
			pdf_obj *ds_obj;
			pdf_obj *rc_obj;
			char *ds_s = NULL;
			char *rc_s = NULL;
			float got_line_height = 0.0f;
			float got_indent_pt = 0.0f;

			if (!annot)
				fz_throw(ctx, FZ_ERROR_GENERIC, "failed to locate created annotation");

			pdf_dict_puts_drop(ctx, annot->obj, "RC", pdf_new_string(ctx, pdf, rc_xml, (int)strlen(rc_xml)));
			pdf_dict_puts_drop(ctx, annot->obj, "DS", pdf_new_string(ctx, pdf, ds, (int)strlen(ds)));
			pdf_update_annot(ctx, pdf, annot);

			rc_obj = pdf_dict_gets(ctx, annot->obj, "RC");
			ds_obj = pdf_dict_gets(ctx, annot->obj, "DS");
			rc_s = pp_demo_pdf_string_dup(ctx, rc_obj);
			ds_s = pp_demo_pdf_string_dup(ctx, ds_obj);

			if (!rc_s || !strstr(rc_s, k_rc_marker))
				fz_throw(ctx, FZ_ERROR_GENERIC, "expected /RC marker missing");
			if (!ds_s)
				fz_throw(ctx, FZ_ERROR_GENERIC, "expected /DS missing");
			if (!pp_demo_parse_css_float_prop(ds_s, "line-height:", &got_line_height))
				fz_throw(ctx, FZ_ERROR_GENERIC, "failed to parse line-height from /DS");
			if (!pp_demo_parse_css_float_prop(ds_s, "text-indent:", &got_indent_pt))
				fz_throw(ctx, FZ_ERROR_GENERIC, "failed to parse text-indent from /DS");
			if (got_line_height < initial_line_height - 0.01f || got_line_height > initial_line_height + 0.01f)
				fz_throw(ctx, FZ_ERROR_GENERIC, "line-height mismatch after seed (%g != %g)", (double)got_line_height, (double)initial_line_height);
			if (got_indent_pt < initial_indent_pt - 0.05f || got_indent_pt > initial_indent_pt + 0.05f)
				fz_throw(ctx, FZ_ERROR_GENERIC, "text-indent mismatch after seed (%g != %g)", (double)got_indent_pt, (double)initial_indent_pt);

			free(ds_s);
			free(rc_s);
		}

		/* Style update should preserve /RC and the paragraph props we seeded into /DS. */
		if (!pp_pdf_update_freetext_style_by_object_id_mupdf(ctx, doc, (fz_page *)page, page_index,
		                                                     object_id, 18.0f, color2))
		{
			fz_throw(ctx, FZ_ERROR_GENERIC, "update FreeText style failed");
		}

		{
			pdf_annot *annot = pp_demo_find_pdf_annot_by_object_id(ctx, page, object_id);
			pdf_obj *ds_obj;
			pdf_obj *rc_obj;
			char *ds_s = NULL;
			char *rc_s = NULL;
			float got_line_height = 0.0f;
			float got_indent_pt = 0.0f;

			if (!annot)
				fz_throw(ctx, FZ_ERROR_GENERIC, "annotation disappeared after style update");
			rc_obj = pdf_dict_gets(ctx, annot->obj, "RC");
			ds_obj = pdf_dict_gets(ctx, annot->obj, "DS");
			rc_s = pp_demo_pdf_string_dup(ctx, rc_obj);
			ds_s = pp_demo_pdf_string_dup(ctx, ds_obj);

			if (!rc_s || !strstr(rc_s, k_rc_marker))
				fz_throw(ctx, FZ_ERROR_GENERIC, "/RC marker missing after style update");
			if (!ds_s)
				fz_throw(ctx, FZ_ERROR_GENERIC, "/DS missing after style update");
			if (!pp_demo_parse_css_float_prop(ds_s, "line-height:", &got_line_height))
				fz_throw(ctx, FZ_ERROR_GENERIC, "failed to parse line-height after style update");
			if (!pp_demo_parse_css_float_prop(ds_s, "text-indent:", &got_indent_pt))
				fz_throw(ctx, FZ_ERROR_GENERIC, "failed to parse text-indent after style update");
			if (got_line_height < initial_line_height - 0.01f || got_line_height > initial_line_height + 0.01f)
				fz_throw(ctx, FZ_ERROR_GENERIC, "line-height changed on style update (%g != %g)", (double)got_line_height, (double)initial_line_height);
			if (got_indent_pt < initial_indent_pt - 0.05f || got_indent_pt > initial_indent_pt + 0.05f)
				fz_throw(ctx, FZ_ERROR_GENERIC, "text-indent changed on style update (%g != %g)", (double)got_indent_pt, (double)initial_indent_pt);

			free(ds_s);
			free(rc_s);
		}

		/* Paragraph update should update /DS and still preserve /RC. */
		if (!pp_pdf_update_freetext_paragraph_by_object_id_mupdf(ctx, doc, (fz_page *)page, page_index,
		                                                         object_id, updated_line_height, updated_indent_pt))
		{
			fz_throw(ctx, FZ_ERROR_GENERIC, "update FreeText paragraph failed");
		}

		{
			pdf_annot *annot = pp_demo_find_pdf_annot_by_object_id(ctx, page, object_id);
			pdf_obj *ds_obj;
			pdf_obj *rc_obj;
			char *ds_s = NULL;
			char *rc_s = NULL;
			float got_line_height = 0.0f;
			float got_indent_pt = 0.0f;

			if (!annot)
				fz_throw(ctx, FZ_ERROR_GENERIC, "annotation disappeared after paragraph update");
			rc_obj = pdf_dict_gets(ctx, annot->obj, "RC");
			ds_obj = pdf_dict_gets(ctx, annot->obj, "DS");
			rc_s = pp_demo_pdf_string_dup(ctx, rc_obj);
			ds_s = pp_demo_pdf_string_dup(ctx, ds_obj);

			if (!rc_s || !strstr(rc_s, k_rc_marker))
				fz_throw(ctx, FZ_ERROR_GENERIC, "/RC marker missing after paragraph update");
			if (!ds_s)
				fz_throw(ctx, FZ_ERROR_GENERIC, "/DS missing after paragraph update");
			if (!pp_demo_parse_css_float_prop(ds_s, "line-height:", &got_line_height))
				fz_throw(ctx, FZ_ERROR_GENERIC, "failed to parse line-height after paragraph update");
			if (!pp_demo_parse_css_float_prop(ds_s, "text-indent:", &got_indent_pt))
				fz_throw(ctx, FZ_ERROR_GENERIC, "failed to parse text-indent after paragraph update");
			if (got_line_height < updated_line_height - 0.01f || got_line_height > updated_line_height + 0.01f)
				fz_throw(ctx, FZ_ERROR_GENERIC, "line-height mismatch after paragraph update (%g != %g)", (double)got_line_height, (double)updated_line_height);
			if (got_indent_pt < updated_indent_pt - 0.05f || got_indent_pt > updated_indent_pt + 0.05f)
				fz_throw(ctx, FZ_ERROR_GENERIC, "text-indent mismatch after paragraph update (%g != %g)", (double)got_indent_pt, (double)updated_indent_pt);

			free(ds_s);
			free(rc_s);
		}

		/* Updating the contents should drop /RC (convert rich -> plain), per our edit semantics. */
		if (!pp_pdf_update_annot_contents_by_object_id_mupdf(ctx, doc, (fz_page *)page, page_index,
		                                                     object_id, "pp_demo rich smoke (plain)"))
		{
			fz_throw(ctx, FZ_ERROR_GENERIC, "update FreeText contents failed");
		}

		{
			pdf_annot *annot = pp_demo_find_pdf_annot_by_object_id(ctx, page, object_id);
			pdf_obj *rc_obj;
			if (!annot)
				fz_throw(ctx, FZ_ERROR_GENERIC, "annotation disappeared after contents update");
			rc_obj = pdf_dict_gets(ctx, annot->obj, "RC");
			if (rc_obj)
				fz_throw(ctx, FZ_ERROR_GENERIC, "expected /RC removed after contents update");
		}

		if (!pp_pdf_save_as_mupdf(ctx, doc, out_pdf))
			fz_throw(ctx, FZ_ERROR_GENERIC, "failed to save output: %s", out_pdf);

		/* Reopen and ensure paragraph props persisted and /RC is still absent. */
		pdf_drop_page(ctx, page);
		page = NULL;
		fz_drop_document(ctx, doc);
		doc = NULL;
		pdf = NULL;

		doc = fz_open_document(ctx, out_pdf);
		if (!doc)
			fz_throw(ctx, FZ_ERROR_GENERIC, "failed to reopen saved PDF: %s", out_pdf);
		pdf = pdf_specifics(ctx, doc);
		if (!pdf)
			fz_throw(ctx, FZ_ERROR_GENERIC, "reopened document is not PDF");
		page = pdf_load_page(ctx, pdf, page_index);
		if (!page)
			fz_throw(ctx, FZ_ERROR_GENERIC, "failed to load page after reopen");

		{
			pdf_annot *annot = pp_demo_find_pdf_annot_by_object_id(ctx, page, object_id);
			pdf_obj *ds_obj;
			pdf_obj *rc_obj;
			char *ds_s = NULL;
			float got_line_height = 0.0f;
			float got_indent_pt = 0.0f;

			if (!annot)
				fz_throw(ctx, FZ_ERROR_GENERIC, "failed to locate annotation after reopen");
			rc_obj = pdf_dict_gets(ctx, annot->obj, "RC");
			if (rc_obj)
				fz_throw(ctx, FZ_ERROR_GENERIC, "unexpected /RC present after reopen");

			ds_obj = pdf_dict_gets(ctx, annot->obj, "DS");
			ds_s = pp_demo_pdf_string_dup(ctx, ds_obj);
			if (!ds_s)
				fz_throw(ctx, FZ_ERROR_GENERIC, "expected /DS missing after reopen");
			if (!pp_demo_parse_css_float_prop(ds_s, "line-height:", &got_line_height) ||
			    !pp_demo_parse_css_float_prop(ds_s, "text-indent:", &got_indent_pt))
			{
				free(ds_s);
				fz_throw(ctx, FZ_ERROR_GENERIC, "failed to parse paragraph props after reopen");
			}
			free(ds_s);

			if (got_line_height < updated_line_height - 0.01f || got_line_height > updated_line_height + 0.01f)
				fz_throw(ctx, FZ_ERROR_GENERIC, "line-height mismatch after reopen (%g != %g)", (double)got_line_height, (double)updated_line_height);
			if (got_indent_pt < updated_indent_pt - 0.05f || got_indent_pt > updated_indent_pt + 0.05f)
				fz_throw(ctx, FZ_ERROR_GENERIC, "text-indent mismatch after reopen (%g != %g)", (double)got_indent_pt, (double)updated_indent_pt);
		}

		ok = 1;
	}
	fz_catch(ctx)
	{
		fprintf(stderr, "pp_demo: freetext-rich smoke failed: %s\n", fz_caught_message(ctx));
		ok = 0;
	}

	if (page) pdf_drop_page(ctx, page);
	if (doc) fz_drop_document(ctx, doc);
	fz_drop_context(ctx);
	return ok;
}

int main(int argc, char **argv)
{
	const char *input_path;
	int page_index = 0;
	const char *output_path = "out.ppm";
	int patch_mode = 0;
		int cancel_smoke = 0;
		int ink_smoke = 0;
		const char *ink_out_pdf = NULL;
		int annot_smoke = 0;
		const char *annot_out_pdf = NULL;
		int freetext_rich_smoke = 0;
		const char *freetext_rich_out_pdf = NULL;
		int flatten_smoke = 0;
		const char *flatten_out_pdf = NULL;
		int text_smoke = 0;
		const char *text_smoke_substring = NULL;
		int widget_smoke = 0;
		const char *widget_out_pdf = NULL;
	int patch_x = 0;
	int patch_y = 0;
	int patch_w = 0;
	int patch_h = 0;
	int buffer_w = 0;
	pp_ctx *ctx = NULL;
	pp_doc *doc = NULL;
	float page_w = 0;
	float page_h = 0;
	int out_w = 0;
	int out_h = 0;
	unsigned char *rgba = NULL;
	size_t rgba_size;
	int page_count;

		if (argc < 2)
		{
			fprintf(stderr, "usage: pp_demo <file> [page_index] [out.ppm] [--patch x y w h [buffer_w]] [--cancel-smoke] [--ink-smoke <out.pdf>] [--annot-smoke <out.pdf>] [--freetext-rich-smoke <out.pdf>] [--flatten-smoke <out.pdf>] [--text-smoke <substring>] [--widget-smoke <out.pdf>]\n");
			return 2;
		}

	input_path = argv[1];
	if (argc >= 3)
		page_index = atoi(argv[2]);
	if (argc >= 4)
		output_path = argv[3];

	for (int i = 4; i < argc; i++)
	{
		if (!argv[i])
			continue;

		if (strcmp(argv[i], "--cancel-smoke") == 0)
		{
			cancel_smoke = 1;
			continue;
		}
		if (strcmp(argv[i], "--ink-smoke") == 0)
		{
			if (i + 1 >= argc)
			{
				fprintf(stderr, "pp_demo: --ink-smoke requires an output PDF path\n");
				return 2;
			}
			ink_smoke = 1;
			ink_out_pdf = argv[i + 1];
			i += 1;
			continue;
		}
			if (strcmp(argv[i], "--annot-smoke") == 0)
			{
				if (i + 1 >= argc)
				{
					fprintf(stderr, "pp_demo: --annot-smoke requires an output PDF path\n");
					return 2;
				}
				annot_smoke = 1;
				annot_out_pdf = argv[i + 1];
				i += 1;
				continue;
			}
			if (strcmp(argv[i], "--freetext-rich-smoke") == 0)
			{
				if (i + 1 >= argc)
				{
					fprintf(stderr, "pp_demo: --freetext-rich-smoke requires an output PDF path\n");
					return 2;
				}
				freetext_rich_smoke = 1;
				freetext_rich_out_pdf = argv[i + 1];
				i += 1;
				continue;
			}
			if (strcmp(argv[i], "--flatten-smoke") == 0)
			{
				if (i + 1 >= argc)
				{
					fprintf(stderr, "pp_demo: --flatten-smoke requires an output PDF path\n");
					return 2;
				}
				flatten_smoke = 1;
				flatten_out_pdf = argv[i + 1];
				i += 1;
				continue;
			}
				if (strcmp(argv[i], "--text-smoke") == 0)
				{
				if (i + 1 >= argc)
				{
				fprintf(stderr, "pp_demo: --text-smoke requires an expected substring\n");
				return 2;
			}
			text_smoke = 1;
			text_smoke_substring = argv[i + 1];
				i += 1;
				continue;
			}
			if (strcmp(argv[i], "--widget-smoke") == 0)
			{
				if (i + 1 >= argc)
				{
					fprintf(stderr, "pp_demo: --widget-smoke requires an output PDF path\n");
					return 2;
				}
				widget_smoke = 1;
				widget_out_pdf = argv[i + 1];
				i += 1;
				continue;
			}
			if (strcmp(argv[i], "--patch") == 0)
			{
			if (i + 4 >= argc)
			{
				fprintf(stderr, "pp_demo: --patch requires x y w h\n");
				return 2;
			}
			patch_mode = 1;
			patch_x = atoi(argv[i + 1]);
			patch_y = atoi(argv[i + 2]);
			patch_w = atoi(argv[i + 3]);
			patch_h = atoi(argv[i + 4]);
			if (i + 5 < argc)
			{
				/* Optional buffer_w (must be a number). */
				char *end = NULL;
				long v = strtol(argv[i + 5], &end, 10);
				if (end && *end == '\0')
				{
					buffer_w = (int)v;
					i++;
				}
			}
			i += 4;
			continue;
		}

		fprintf(stderr, "pp_demo: unknown arg: %s\n", argv[i]);
		return 2;
	}

	if (freetext_rich_smoke)
	{
		int ok = run_freetext_rich_smoke(input_path, page_index, freetext_rich_out_pdf);
		if (!ok)
			return 1;
		printf("freetext-rich smoke OK (wrote %s)\n", freetext_rich_out_pdf);
		return 0;
	}

	ctx = pp_new();
	if (!ctx)
	{
		fprintf(stderr, "pp_demo: failed to create context\n");
		return 1;
	}

	doc = pp_open(ctx, input_path);
	if (!doc)
	{
		fprintf(stderr, "pp_demo: failed to open document: %s\n", input_path);
		pp_drop(ctx);
		return 1;
	}

	page_count = pp_count_pages(ctx, doc);
	if (page_count <= 0)
	{
		fprintf(stderr, "pp_demo: failed to count pages\n");
		pp_close(ctx, doc);
		pp_drop(ctx);
		return 1;
	}
	if (page_index < 0 || page_index >= page_count)
	{
		fprintf(stderr, "pp_demo: page out of range: %d (0..%d)\n", page_index, page_count - 1);
		pp_close(ctx, doc);
		pp_drop(ctx);
		return 1;
	}

		if (text_smoke)
		{
		char *text = NULL;
		int ok = pp_page_text_utf8(ctx, doc, page_index, &text);
		if (!ok || !text)
		{
			fprintf(stderr, "pp_demo: text-smoke failed to extract text\n");
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}
		if (!text_smoke_substring || !strstr(text, text_smoke_substring))
		{
			fprintf(stderr, "pp_demo: text-smoke missing substring: %s\n",
			        text_smoke_substring ? text_smoke_substring : "(null)");
			pp_free_string(ctx, text);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}

		pp_free_string(ctx, text);
		pp_close(ctx, doc);
		pp_drop(ctx);
			return 0;
		}

		if (widget_smoke)
		{
			const char *expected = "pp_core widget smoke";
			char *value = NULL;

			if (!widget_out_pdf || !*widget_out_pdf)
			{
				fprintf(stderr, "pp_demo: --widget-smoke missing output PDF path\n");
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 2;
			}

			if (!pp_pdf_widget_set_text_utf8(ctx, doc, page_index, 0, expected))
			{
				fprintf(stderr, "pp_demo: widget smoke failed to set widget[0] text\n");
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			if (!pp_pdf_save_as(ctx, doc, widget_out_pdf))
			{
				fprintf(stderr, "pp_demo: widget smoke failed to save: %s\n", widget_out_pdf);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			pp_close(ctx, doc);
			doc = pp_open(ctx, widget_out_pdf);
			if (!doc)
			{
				fprintf(stderr, "pp_demo: widget smoke failed to reopen: %s\n", widget_out_pdf);
				pp_drop(ctx);
				return 1;
			}

			if (!pp_pdf_widget_get_value_utf8(ctx, doc, page_index, 0, &value) || !value)
			{
				fprintf(stderr, "pp_demo: widget smoke failed to read widget[0] text\n");
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			if (strcmp(value, expected) != 0)
			{
				fprintf(stderr, "pp_demo: widget smoke mismatch (got=%s expected=%s)\n", value, expected);
				pp_free_string(ctx, value);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			pp_free_string(ctx, value);
			pp_close(ctx, doc);
			pp_drop(ctx);
			printf("widget smoke OK (wrote %s)\n", widget_out_pdf);
			return 0;
		}

		if (!pp_page_size(ctx, doc, page_index, &page_w, &page_h) || page_w <= 0 || page_h <= 0)
		{
			fprintf(stderr, "pp_demo: failed to get page size\n");
			pp_close(ctx, doc);
		pp_drop(ctx);
		return 1;
	}

	out_w = (int)(page_w + 0.5f);
	out_h = (int)(page_h + 0.5f);
	if (out_w < 1)
		out_w = 1;
	if (out_h < 1)
		out_h = 1;

	if (cancel_smoke)
	{
		int ok = run_cancel_smoke(ctx, doc, page_index, out_w, out_h);
		pp_close(ctx, doc);
		pp_drop(ctx);
		if (!ok)
		{
			fprintf(stderr, "pp_demo: cancel smoke failed\n");
			return 1;
		}
		printf("cancel smoke OK\n");
		return 0;
	}

		if (ink_smoke)
		{
			int ok = 0;
			int before_nonwhite;
			int after_nonwhite;
			long long object_id = -1;
			pp_point pts[48];
			int arc_counts[1];
			float color[3] = { 1.0f, 0.0f, 0.0f };
			size_t bytes;

				if (!ink_out_pdf || !*ink_out_pdf)
				{
					fprintf(stderr, "pp_demo: --ink-smoke missing output PDF path\n");
					pp_close(ctx, doc);
					pp_drop(ctx);
					return 2;
				}

				bytes = (size_t)out_w * (size_t)out_h * 4u;
				rgba = (unsigned char *)malloc(bytes);
				if (!rgba)
				{
					fprintf(stderr, "pp_demo: failed to allocate %zu bytes\n", bytes);
					pp_close(ctx, doc);
					pp_drop(ctx);
					return 1;
				}

				if (!pp_render_page_rgba(ctx, doc, page_index, out_w, out_h, rgba))
				{
					fprintf(stderr, "pp_demo: baseline render failed\n");
					free(rgba);
					pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}
			before_nonwhite = count_nonwhite_rgba(out_w, out_h, rgba);

			/* Draw a simple diagonal stroke in page-pixel space. */
			for (int p = 0; p < (int)(sizeof(pts) / sizeof(pts[0])); p++)
			{
				float t = (float)p / (float)((int)(sizeof(pts) / sizeof(pts[0])) - 1);
				pts[p].x = (0.15f + 0.7f * t) * (float)out_w;
				pts[p].y = (0.20f + 0.6f * t) * (float)out_h;
			}
			arc_counts[0] = (int)(sizeof(pts) / sizeof(pts[0]));

			if (!pp_pdf_add_ink_annot(ctx, doc, page_index,
			                         out_w, out_h,
			                         1, arc_counts,
			                         pts, arc_counts[0],
			                         color, 3.0f,
			                         &object_id))
			{
				fprintf(stderr, "pp_demo: pp_pdf_add_ink_annot failed\n");
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			if (!pp_pdf_save_as(ctx, doc, ink_out_pdf))
			{
				fprintf(stderr, "pp_demo: pp_pdf_save_as failed: %s\n", ink_out_pdf);
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			pp_close(ctx, doc);
			doc = pp_open(ctx, ink_out_pdf);
			if (!doc)
			{
				fprintf(stderr, "pp_demo: failed to reopen saved PDF: %s\n", ink_out_pdf);
				free(rgba);
				pp_drop(ctx);
				return 1;
			}

			if (!pp_render_page_rgba(ctx, doc, page_index, out_w, out_h, rgba))
			{
				fprintf(stderr, "pp_demo: post-save render failed\n");
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}
			after_nonwhite = count_nonwhite_rgba(out_w, out_h, rgba);

			if (!write_ppm_from_rgba(output_path, out_w, out_h, rgba))
			{
				fprintf(stderr, "pp_demo: failed to write output: %s\n", output_path);
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			ok = after_nonwhite > before_nonwhite + 200;
			if (!ok)
			{
				fprintf(stderr, "pp_demo: ink smoke failed (before_nonwhite=%d after_nonwhite=%d object_id=%lld)\n",
				        before_nonwhite, after_nonwhite, object_id);
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			printf("ink smoke OK (before_nonwhite=%d after_nonwhite=%d object_id=%lld) wrote %s and %s\n",
			       before_nonwhite, after_nonwhite, object_id, ink_out_pdf, output_path);
			free(rgba);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 0;
		}

		if (flatten_smoke)
		{
			int ok = 0;
			int before_nonwhite;
			int after_nonwhite;
			long long object_id = -1;
			pp_point pts[48];
			int arc_counts[1];
			float color[3] = { 1.0f, 0.0f, 0.0f };
			size_t bytes;

			if (!flatten_out_pdf || !*flatten_out_pdf)
			{
				fprintf(stderr, "pp_demo: --flatten-smoke missing output PDF path\n");
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 2;
			}

			bytes = (size_t)out_w * (size_t)out_h * 4u;
			rgba = (unsigned char *)malloc(bytes);
			if (!rgba)
			{
				fprintf(stderr, "pp_demo: failed to allocate %zu bytes\n", bytes);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			if (!pp_render_page_rgba(ctx, doc, page_index, out_w, out_h, rgba))
			{
				fprintf(stderr, "pp_demo: baseline render failed\n");
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}
			before_nonwhite = count_nonwhite_rgba(out_w, out_h, rgba);

			/* Draw a simple diagonal stroke in page-pixel space. */
			for (int p = 0; p < (int)(sizeof(pts) / sizeof(pts[0])); p++)
			{
				float t = (float)p / (float)((int)(sizeof(pts) / sizeof(pts[0])) - 1);
				pts[p].x = (0.15f + 0.7f * t) * (float)out_w;
				pts[p].y = (0.20f + 0.6f * t) * (float)out_h;
			}
			arc_counts[0] = (int)(sizeof(pts) / sizeof(pts[0]));

			if (!pp_pdf_add_ink_annot(ctx, doc, page_index,
			                         out_w, out_h,
			                         1, arc_counts,
			                         pts, arc_counts[0],
			                         color, 3.0f,
			                         &object_id))
			{
				fprintf(stderr, "pp_demo: pp_pdf_add_ink_annot failed\n");
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			if (!pp_export_flattened_pdf(ctx, doc, flatten_out_pdf, 150))
			{
				fprintf(stderr, "pp_demo: flatten export failed: %s\n", flatten_out_pdf);
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			pp_close(ctx, doc);
			doc = pp_open(ctx, flatten_out_pdf);
			if (!doc)
			{
				fprintf(stderr, "pp_demo: failed to reopen exported PDF: %s\n", flatten_out_pdf);
				free(rgba);
				pp_drop(ctx);
				return 1;
			}

			if (!pp_render_page_rgba(ctx, doc, page_index, out_w, out_h, rgba))
			{
				fprintf(stderr, "pp_demo: post-export render failed\n");
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}
			after_nonwhite = count_nonwhite_rgba(out_w, out_h, rgba);

			if (!write_ppm_from_rgba(output_path, out_w, out_h, rgba))
			{
				fprintf(stderr, "pp_demo: failed to write output: %s\n", output_path);
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			ok = after_nonwhite > before_nonwhite + 200;
			if (!ok)
			{
				fprintf(stderr, "pp_demo: flatten smoke failed (before_nonwhite=%d after_nonwhite=%d object_id=%lld)\n",
				        before_nonwhite, after_nonwhite, object_id);
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}

			printf("flatten smoke OK (before_nonwhite=%d after_nonwhite=%d object_id=%lld) wrote %s and %s\n",
			       before_nonwhite, after_nonwhite, object_id, flatten_out_pdf, output_path);
			free(rgba);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 0;
		}

		if (annot_smoke)
		{
			int ok = 0;
			int before_nonwhite;
		int after_nonwhite;
		long long object_id1 = -1;
		long long object_id2 = -1;
		float highlight_color[3] = { 1.0f, 1.0f, 0.0f };
		float text_color[3] = { 1.0f, 0.0f, 0.0f };
		pp_point quad[4];
		pp_point rect[2];
		size_t bytes;

		if (!annot_out_pdf || !*annot_out_pdf)
		{
			fprintf(stderr, "pp_demo: --annot-smoke missing output PDF path\n");
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 2;
		}

		bytes = (size_t)out_w * (size_t)out_h * 4u;
		rgba = (unsigned char *)malloc(bytes);
		if (!rgba)
		{
			fprintf(stderr, "pp_demo: failed to allocate %zu bytes\n", bytes);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}

		if (!pp_render_page_rgba(ctx, doc, page_index, out_w, out_h, rgba))
		{
			fprintf(stderr, "pp_demo: baseline render failed\n");
			free(rgba);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}
		before_nonwhite = count_nonwhite_rgba(out_w, out_h, rgba);

		/* Highlight quad in page-pixel space (use the same ordering the Android glue expects). */
		quad[0].x = 0.20f * (float)out_w; quad[0].y = 0.18f * (float)out_h; /* ul */
		quad[1].x = 0.80f * (float)out_w; quad[1].y = 0.18f * (float)out_h; /* ur */
		quad[2].x = 0.80f * (float)out_w; quad[2].y = 0.28f * (float)out_h; /* lr */
		quad[3].x = 0.20f * (float)out_w; quad[3].y = 0.28f * (float)out_h; /* ll */

		if (!pp_pdf_add_annot(ctx, doc, page_index,
		                      out_w, out_h,
		                      PP_DEMO_ANNOT_HIGHLIGHT,
		                      quad, 4,
		                      highlight_color, 0.69f,
		                      "",
		                      &object_id1))
		{
			fprintf(stderr, "pp_demo: pp_pdf_add_annot(highlight) failed\n");
			free(rgba);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}

		/* Free text box. */
		rect[0].x = 0.22f * (float)out_w; rect[0].y = 0.32f * (float)out_h;
		rect[1].x = 0.78f * (float)out_w; rect[1].y = 0.42f * (float)out_h;

		if (!pp_pdf_add_annot(ctx, doc, page_index,
		                      out_w, out_h,
		                      PP_DEMO_ANNOT_FREE_TEXT,
		                      rect, 2,
		                      text_color, 1.0f,
		                      "pp_core annot smoke",
		                      &object_id2))
		{
			fprintf(stderr, "pp_demo: pp_pdf_add_annot(free_text) failed\n");
			free(rgba);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}

		/* Ensure enumeration sees at least the annots we added. */
		{
			pp_pdf_annot_list *list = NULL;
			if (!pp_pdf_list_annots(ctx, doc, page_index, out_w, out_h, &list) || !list)
			{
				fprintf(stderr, "pp_demo: pp_pdf_list_annots failed\n");
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}
			if (list->count < 2)
			{
				fprintf(stderr, "pp_demo: annot smoke expected >=2 annots, got %d\n", list->count);
				pp_pdf_drop_annot_list(ctx, list);
				free(rgba);
				pp_close(ctx, doc);
				pp_drop(ctx);
				return 1;
			}
			pp_pdf_drop_annot_list(ctx, list);
		}

		if (!pp_pdf_save_as(ctx, doc, annot_out_pdf))
		{
			fprintf(stderr, "pp_demo: pp_pdf_save_as failed: %s\n", annot_out_pdf);
			free(rgba);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}

		pp_close(ctx, doc);
		doc = pp_open(ctx, annot_out_pdf);
		if (!doc)
		{
			fprintf(stderr, "pp_demo: failed to reopen saved PDF: %s\n", annot_out_pdf);
			free(rgba);
			pp_drop(ctx);
			return 1;
		}

		if (!pp_render_page_rgba(ctx, doc, page_index, out_w, out_h, rgba))
		{
			fprintf(stderr, "pp_demo: post-save render failed\n");
			free(rgba);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}
		after_nonwhite = count_nonwhite_rgba(out_w, out_h, rgba);

		if (!write_ppm_from_rgba(output_path, out_w, out_h, rgba))
		{
			fprintf(stderr, "pp_demo: failed to write output: %s\n", output_path);
			free(rgba);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}

		ok = after_nonwhite > before_nonwhite + 200;
		if (!ok)
		{
			fprintf(stderr, "pp_demo: annot smoke failed (before_nonwhite=%d after_nonwhite=%d ids=%lld,%lld)\n",
			        before_nonwhite, after_nonwhite, object_id1, object_id2);
			free(rgba);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}

		printf("annot smoke OK (before_nonwhite=%d after_nonwhite=%d ids=%lld,%lld) wrote %s and %s\n",
		       before_nonwhite, after_nonwhite, object_id1, object_id2, annot_out_pdf, output_path);
		free(rgba);
		pp_close(ctx, doc);
		pp_drop(ctx);
		return 0;
	}

		if (patch_mode)
		{
			if (patch_w <= 0 || patch_h <= 0)
			{
			fprintf(stderr, "pp_demo: invalid patch size %dx%d\n", patch_w, patch_h);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}
		if (buffer_w <= 0)
			buffer_w = patch_w;
		rgba_size = (size_t)buffer_w * (size_t)patch_h * 4;
	}
	else
	{
		rgba_size = (size_t)out_w * (size_t)out_h * 4;
	}
	rgba = (unsigned char *)malloc(rgba_size);
	if (!rgba)
	{
		fprintf(stderr, "pp_demo: failed to allocate %zu bytes\n", rgba_size);
		pp_close(ctx, doc);
		pp_drop(ctx);
		return 1;
	}

	if (patch_mode)
	{
		if (!pp_render_patch_rgba(ctx, doc, page_index,
		                          out_w, out_h,
		                          patch_x, patch_y, patch_w, patch_h,
		                          rgba, buffer_w * 4, NULL))
		{
			fprintf(stderr, "pp_demo: patch render failed\n");
			free(rgba);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}
		if (!write_ppm_from_rgba(output_path, buffer_w, patch_h, rgba))
		{
			fprintf(stderr, "pp_demo: failed to write output: %s\n", output_path);
			free(rgba);
			pp_close(ctx, doc);
			pp_drop(ctx);
			return 1;
		}
		printf("wrote %s (patch %dx%d @%d,%d buffer %dx%d) [%s]\n",
		       output_path, patch_w, patch_h, patch_x, patch_y, buffer_w, patch_h, pp_format(ctx, doc));
		free(rgba);
		pp_close(ctx, doc);
		pp_drop(ctx);
		return 0;
	}

	if (!pp_render_page_rgba(ctx, doc, page_index, out_w, out_h, rgba))
	{
		fprintf(stderr, "pp_demo: render failed\n");
		free(rgba);
		pp_close(ctx, doc);
		pp_drop(ctx);
		return 1;
	}

	if (!write_ppm_from_rgba(output_path, out_w, out_h, rgba))
	{
		fprintf(stderr, "pp_demo: failed to write output: %s\n", output_path);
		free(rgba);
		pp_close(ctx, doc);
		pp_drop(ctx);
		return 1;
	}

	printf("wrote %s (%dx%d) [%s]\n", output_path, out_w, out_h, pp_format(ctx, doc));

	free(rgba);
	pp_close(ctx, doc);
	pp_drop(ctx);
	return 0;
}
