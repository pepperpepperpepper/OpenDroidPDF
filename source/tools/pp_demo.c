#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <pthread.h>
#include <unistd.h>

#include "pp_core.h"

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

int main(int argc, char **argv)
{
	const char *input_path;
	int page_index = 0;
	const char *output_path = "out.ppm";
	int patch_mode = 0;
	int cancel_smoke = 0;
	int ink_smoke = 0;
	const char *ink_out_pdf = NULL;
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
		fprintf(stderr, "usage: pp_demo <file> [page_index] [out.ppm] [--patch x y w h [buffer_w]] [--cancel-smoke] [--ink-smoke <out.pdf>]\n");
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
