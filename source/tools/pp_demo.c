#include <stdio.h>
#include <stdlib.h>
#include <string.h>

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

int main(int argc, char **argv)
{
	const char *input_path;
	int page_index = 0;
	const char *output_path = "out.ppm";
	int patch_mode = 0;
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
		fprintf(stderr, "usage: pp_demo <file> [page_index] [out.ppm] [--patch x y w h [buffer_w]]\n");
		return 2;
	}

	input_path = argv[1];
	if (argc >= 3)
		page_index = atoi(argv[2]);
	if (argc >= 4)
		output_path = argv[3];
	if (argc >= 5 && argv[4] && strcmp(argv[4], "--patch") == 0)
	{
		if (argc < 9)
		{
			fprintf(stderr, "pp_demo: --patch requires x y w h\n");
			return 2;
		}
		patch_mode = 1;
		patch_x = atoi(argv[5]);
		patch_y = atoi(argv[6]);
		patch_w = atoi(argv[7]);
		patch_h = atoi(argv[8]);
		if (argc >= 10)
			buffer_w = atoi(argv[9]);
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
