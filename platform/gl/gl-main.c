#include "gl-app.h"
#include "odp_state.h"

#include "pp_core.h"

#include "mupdf/pdf.h" /* for pdf specifics and forms */

struct ui ui;
fz_context *ctx = NULL;
GLFWwindow *window = NULL;

#if defined(FZ_VERSION_MAJOR) && defined(FZ_VERSION_MINOR)
#define ODP_MUPDF_API_NEW 1
#else
#define ODP_MUPDF_API_NEW 0
#endif

#if ODP_MUPDF_API_NEW
#define ODP_PDF_ANNOT_INK PDF_ANNOT_INK
#else
#define ODP_PDF_ANNOT_INK FZ_ANNOT_INK
#endif

/* OpenGL capabilities */
static int has_ARB_texture_non_power_of_two = 1;
static GLint max_texture_size = 8192;

static int ui_needs_update = 0;

static void request_quit(void)
{
	if (window)
		glfwSetWindowShouldClose(window, 1);
	else
		exit(0);
}

static void ui_begin(void)
{
	ui_needs_update = 0;
	ui.hot = NULL;
}

static void ui_end(void)
{
	if (!ui.down && !ui.middle && !ui.right)
		ui.active = NULL;
	if (ui_needs_update)
		glfwPostEmptyEvent();
}

static void open_browser(const char *uri)
{
#ifdef _WIN32
	ShellExecuteA(NULL, "open", uri, 0, 0, SW_SHOWNORMAL);
#else
	const char *browser = getenv("BROWSER");
	if (!browser)
	{
#ifdef __APPLE__
		browser = "open";
#else
		browser = "xdg-open";
#endif
	}
	if (fork() == 0)
	{
		execlp(browser, browser, uri, (char*)0);
		fprintf(stderr, "cannot exec '%s'\n", browser);
		exit(0);
	}
#endif
}

const char *ogl_error_string(GLenum code)
{
#define CASE(E) case E: return #E; break
	switch (code)
	{
	/* glGetError */
	CASE(GL_NO_ERROR);
	CASE(GL_INVALID_ENUM);
	CASE(GL_INVALID_VALUE);
	CASE(GL_INVALID_OPERATION);
	CASE(GL_OUT_OF_MEMORY);
	CASE(GL_STACK_UNDERFLOW);
	CASE(GL_STACK_OVERFLOW);
	default: return "(unknown)";
	}
#undef CASE
}

void ogl_assert(fz_context *ctx, const char *msg)
{
	int code = glGetError();
	if (code != GL_NO_ERROR) {
		fz_warn(ctx, "glGetError(%s): %s", msg, ogl_error_string(code));
	}
}

void ui_draw_image(struct texture *tex, float x, float y)
{
	glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
	glEnable(GL_BLEND);
	glBindTexture(GL_TEXTURE_2D, tex->id);
	glEnable(GL_TEXTURE_2D);
	glBegin(GL_TRIANGLE_STRIP);
	{
		glColor4f(1, 1, 1, 1);
		glTexCoord2f(0, tex->t);
		glVertex2f(x + tex->x, y + tex->y + tex->h);
		glTexCoord2f(0, 0);
		glVertex2f(x + tex->x, y + tex->y);
		glTexCoord2f(tex->s, tex->t);
		glVertex2f(x + tex->x + tex->w, y + tex->y + tex->h);
		glTexCoord2f(tex->s, 0);
		glVertex2f(x + tex->x + tex->w, y + tex->y);
	}
	glEnd();
	glDisable(GL_TEXTURE_2D);
	glDisable(GL_BLEND);
}

static const int zoom_list[] = { 18, 24, 36, 54, 72, 96, 120, 144, 180, 216, 288 };

static int zoom_in(int oldres)
{
	int i;
	for (i = 0; i < nelem(zoom_list) - 1; ++i)
		if (zoom_list[i] <= oldres && zoom_list[i+1] > oldres)
			return zoom_list[i+1];
	return zoom_list[i];
}

static int zoom_out(int oldres)
{
	int i;
	for (i = 0; i < nelem(zoom_list) - 1; ++i)
		if (zoom_list[i] < oldres && zoom_list[i+1] >= oldres)
			return zoom_list[i];
	return zoom_list[0];
}

#define MINRES (zoom_list[0])
#define MAXRES (zoom_list[nelem(zoom_list)-1])
#define DEFRES 96

static const char *title = "MuPDF/GL";
static fz_document *doc = NULL;
static fz_page *page = NULL;
static pdf_document *pdf = NULL;
static fz_outline *outline = NULL;
static fz_link *links = NULL;

static int number = 0;

static struct texture page_tex = { 0 };
static int scroll_x = 0, scroll_y = 0;
static int canvas_x = 0, canvas_w = 100;
static int canvas_y = 0, canvas_h = 100;

static struct texture annot_tex[256];
static int annot_count = 0;

static int screen_w = 1, screen_h = 1;

static int oldpage = 0, currentpage = 0;
static float oldzoom = DEFRES, currentzoom = DEFRES;
static float oldrotate = 0, currentrotate = 0;
static fz_matrix page_ctm, page_inv_ctm;

static int isfullscreen = 0;
static int showoutline = 0;
static int showlinks = 0;
static int showsearch = 0;
static int showinfo = 0;

static int history_count = 0;
static int history[256];
static int future_count = 0;
static int future[256];
static int marks[10];

static int search_active = 0;
static struct input search_input = { { 0 }, 0 };
static char *search_needle = 0;
static int search_dir = 1;
static int search_page = -1;
static int search_hit_page = -1;
static int search_hit_count = 0;
static fz_rect search_hit_bbox[500];

void render_page(void);

enum odp_tool_mode
{
	ODP_TOOL_NONE = 0,
	ODP_TOOL_PEN = 1,
	ODP_TOOL_ERASER = 2,
};

typedef struct odp_ink_action
{
	int kind; /* 1=ADD, 2=DELETE */
	int page_index;
	int pageW;
	int pageH;
	long long object_id;
	float color_rgb[3];
	float thickness;
	int arc_count;
	int *arc_counts;
	pp_point *points;
	int point_count;
} odp_ink_action;

#define ODP_UNDO_MAX 64

static enum odp_tool_mode tool_mode = ODP_TOOL_NONE;

static float pen_color_rgb[3] = { 1.0f, 0.0f, 0.0f };
static float pen_thickness = 2.0f; /* PDF user units (points) */

static int pen_drawing = 0;
static pp_point *pen_points = NULL;
static int pen_point_count = 0;
static int pen_point_cap = 0;

static odp_ink_action *undo_stack[ODP_UNDO_MAX];
static int undo_count = 0;
static odp_ink_action *redo_stack[ODP_UNDO_MAX];
static int redo_count = 0;

static unsigned int next_power_of_two(unsigned int n)
{
	--n;
	n |= n >> 1;
	n |= n >> 2;
	n |= n >> 4;
	n |= n >> 8;
	n |= n >> 16;
	return ++n;
}

static void update_title(void)
{
	static char buf[256];
	size_t n = strlen(title);
	if (n > 50)
		sprintf(buf, "...%s - %d / %d", title + n - 50, currentpage + 1, fz_count_pages(ctx, doc));
	else
		sprintf(buf, "%s - %d / %d", title, currentpage + 1, fz_count_pages(ctx, doc));
	glfwSetWindowTitle(window, buf);
}

void texture_from_pixmap(struct texture *tex, fz_pixmap *pix)
{
	if (!tex->id)
		glGenTextures(1, &tex->id);
	glBindTexture(GL_TEXTURE_2D, tex->id);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

	tex->x = pix->x;
	tex->y = pix->y;
	tex->w = pix->w;
	tex->h = pix->h;

	if (has_ARB_texture_non_power_of_two)
	{
		if (tex->w > max_texture_size || tex->h > max_texture_size)
			fz_warn(ctx, "texture size (%d x %d) exceeds implementation limit (%d)", tex->w, tex->h, max_texture_size);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, tex->w, tex->h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pix->samples);
		tex->s = 1;
		tex->t = 1;
	}
	else
	{
		int w2 = next_power_of_two(tex->w);
		int h2 = next_power_of_two(tex->h);
		if (w2 > max_texture_size || h2 > max_texture_size)
			fz_warn(ctx, "texture size (%d x %d) exceeds implementation limit (%d)", w2, h2, max_texture_size);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w2, h2, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
		glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, tex->w, tex->h, GL_RGBA, GL_UNSIGNED_BYTE, pix->samples);
		tex->s = (float)tex->w / w2;
		tex->t = (float)tex->h / h2;
	}
}

static void
odp_ink_action_free(odp_ink_action *a)
{
	if (!a)
		return;
	free(a->arc_counts);
	free(a->points);
	free(a);
}

static void
odp_stack_clear(odp_ink_action **stack, int *count)
{
	int i;
	if (!stack || !count)
		return;
	for (i = 0; i < *count; i++)
		odp_ink_action_free(stack[i]);
	*count = 0;
}

static void
odp_stack_push(odp_ink_action **stack, int *count, odp_ink_action *a)
{
	if (!stack || !count || !a)
	{
		odp_ink_action_free(a);
		return;
	}
	if (*count >= ODP_UNDO_MAX)
	{
		odp_ink_action_free(stack[0]);
		memmove(stack, stack + 1, sizeof(stack[0]) * (ODP_UNDO_MAX - 1));
		*count = ODP_UNDO_MAX - 1;
	}
	stack[(*count)++] = a;
}

static odp_ink_action *
odp_stack_pop(odp_ink_action **stack, int *count)
{
	if (!stack || !count || *count <= 0)
		return NULL;
	return stack[--(*count)];
}

static int
odp_pen_points_add(float x, float y)
{
	if (pen_point_count >= pen_point_cap)
	{
		int new_cap = pen_point_cap ? pen_point_cap * 2 : 256;
		pp_point *new_points = (pp_point *)realloc(pen_points, (size_t)new_cap * sizeof(pp_point));
		if (!new_points)
			return 0;
		pen_points = new_points;
		pen_point_cap = new_cap;
	}
	pen_points[pen_point_count].x = x;
	pen_points[pen_point_count].y = y;
	pen_point_count++;
	return 1;
}

static int
odp_screen_to_page_pixel(float page_x, float page_y, pp_point *out_p)
{
	float px, py;
	if (!out_p)
		return 0;
	px = (float)ui.x - page_x;
	py = (float)ui.y - page_y;
	if (px < 0 || py < 0 || px >= page_tex.w || py >= page_tex.h)
		return 0;
	out_p->x = px;
	out_p->y = py;
	return 1;
}

static void
odp_pen_commit(void)
{
	int arc_count;
	int arc_counts[1];
	long long object_id = 0;
	odp_ink_action *a = NULL;
	int ok;

	if (!pdf || !fz_has_permission(ctx, doc, FZ_PERMISSION_ANNOTATE))
		return;
	if (pen_point_count < 2)
		return;
	if (page_tex.w <= 0 || page_tex.h <= 0)
		return;

	arc_count = 1;
	arc_counts[0] = pen_point_count;

	ok = pp_pdf_add_ink_annot_mupdf(ctx, doc, page, currentpage,
	                               page_tex.w, page_tex.h,
	                               arc_count, arc_counts,
	                               pen_points, pen_point_count,
	                               pen_color_rgb, pen_thickness,
	                               &object_id);
	if (!ok || object_id == 0)
	{
		fprintf(stderr, "ink: failed to add annotation\n");
		return;
	}

	a = (odp_ink_action *)calloc(1, sizeof(*a));
	if (!a)
		return;
	a->kind = 1;
	a->page_index = currentpage;
	a->pageW = page_tex.w;
	a->pageH = page_tex.h;
	a->object_id = object_id;
	a->color_rgb[0] = pen_color_rgb[0];
	a->color_rgb[1] = pen_color_rgb[1];
	a->color_rgb[2] = pen_color_rgb[2];
	a->thickness = pen_thickness;
	a->arc_count = 1;
	a->arc_counts = (int *)malloc(sizeof(int));
	a->points = (pp_point *)malloc((size_t)pen_point_count * sizeof(pp_point));
	if (!a->arc_counts || !a->points)
	{
		odp_ink_action_free(a);
		return;
	}
	a->arc_counts[0] = pen_point_count;
	memcpy(a->points, pen_points, (size_t)pen_point_count * sizeof(pp_point));
	a->point_count = pen_point_count;

	odp_stack_clear(redo_stack, &redo_count);
	odp_stack_push(undo_stack, &undo_count, a);

	render_page();
	ui_needs_update = 1;
}

static void
odp_erase_at(pp_point at)
{
	pp_pdf_annot_list *list = NULL;
	pp_pdf_annot_info *best = NULL;
	float best_area = 0;
	int ok;

	if (!pdf || !fz_has_permission(ctx, doc, FZ_PERMISSION_ANNOTATE))
		return;
	if (page_tex.w <= 0 || page_tex.h <= 0)
		return;

	ok = pp_pdf_list_annots_mupdf(ctx, doc, page, currentpage, page_tex.w, page_tex.h, &list);
	if (!ok || !list)
		return;

	{
		int i;
		for (i = 0; i < list->count; i++)
		{
			pp_pdf_annot_info *info = &list->items[i];
			float x0 = info->x0 < info->x1 ? info->x0 : info->x1;
			float x1 = info->x0 < info->x1 ? info->x1 : info->x0;
			float y0 = info->y0 < info->y1 ? info->y0 : info->y1;
			float y1 = info->y0 < info->y1 ? info->y1 : info->y0;
			float area = (x1 - x0) * (y1 - y0);
			if (info->type != (int)ODP_PDF_ANNOT_INK)
				continue;
			if (at.x < x0 || at.x > x1 || at.y < y0 || at.y > y1)
				continue;
			if (!best || area < best_area)
			{
				best = info;
				best_area = area;
			}
		}
	}

	if (best && best->object_id != 0 && best->arc_count > 0 && best->arcs)
	{
		odp_ink_action *a = NULL;
		int total_pts = 0;
		int pi = 0;
		int ai;

		for (ai = 0; ai < best->arc_count; ai++)
			total_pts += best->arcs[ai].count;
		if (total_pts > 0)
		{
			a = (odp_ink_action *)calloc(1, sizeof(*a));
			if (a)
			{
				a->kind = 2;
				a->page_index = currentpage;
				a->pageW = page_tex.w;
				a->pageH = page_tex.h;
				a->object_id = best->object_id;
				a->color_rgb[0] = pen_color_rgb[0];
				a->color_rgb[1] = pen_color_rgb[1];
				a->color_rgb[2] = pen_color_rgb[2];
				a->thickness = pen_thickness;
				a->arc_count = best->arc_count;
				a->arc_counts = (int *)malloc((size_t)best->arc_count * sizeof(int));
				a->points = (pp_point *)malloc((size_t)total_pts * sizeof(pp_point));
				if (!a->arc_counts || !a->points)
				{
					odp_ink_action_free(a);
					a = NULL;
				}
				else
				{
					int k;
					for (ai = 0; ai < best->arc_count; ai++)
					{
						a->arc_counts[ai] = best->arcs[ai].count;
						for (k = 0; k < best->arcs[ai].count; k++)
							a->points[pi++] = best->arcs[ai].points[k];
					}
					a->point_count = total_pts;
				}
			}
		}

		if (a)
		{
			int del_ok = pp_pdf_delete_annot_by_object_id_mupdf(ctx, doc, page, currentpage, best->object_id);
			if (del_ok)
			{
				odp_stack_clear(redo_stack, &redo_count);
				odp_stack_push(undo_stack, &undo_count, a);
				render_page();
				ui_needs_update = 1;
			}
			else
			{
				fprintf(stderr, "eraser: failed to delete annotation\n");
				odp_ink_action_free(a);
			}
		}
	}

	pp_pdf_drop_annot_list_mupdf(ctx, list);
}

static void
odp_undo(void)
{
	odp_ink_action *a = odp_stack_pop(undo_stack, &undo_count);
	fz_page *p = NULL;
	int owns_page = 0;
	int ok = 0;
	long long new_object_id = 0;

	if (!a)
		return;
	if (!pdf || !fz_has_permission(ctx, doc, FZ_PERMISSION_ANNOTATE))
	{
		odp_stack_push(undo_stack, &undo_count, a);
		return;
	}

	if (a->page_index == currentpage)
	{
		p = page;
	}
	else
	{
		p = fz_load_page(ctx, doc, a->page_index);
		owns_page = 1;
	}

	if (a->kind == 2)
		ok = pp_pdf_add_ink_annot_mupdf(ctx, doc, p, a->page_index,
		                               a->pageW, a->pageH,
		                               a->arc_count, a->arc_counts,
		                               a->points, a->point_count,
		                               a->color_rgb, a->thickness,
		                               &new_object_id);
	else
		ok = pp_pdf_delete_annot_by_object_id_mupdf(ctx, doc, p, a->page_index, a->object_id);

	if (owns_page)
		fz_drop_page(ctx, p);

	if (!ok || (a->kind == 2 && new_object_id == 0))
	{
		fprintf(stderr, "undo: failed\n");
		odp_stack_push(undo_stack, &undo_count, a);
		return;
	}

	if (a->kind == 2)
		a->object_id = new_object_id;
	odp_stack_push(redo_stack, &redo_count, a);

	if (a->page_index == currentpage)
	{
		render_page();
		ui_needs_update = 1;
	}
}

static void
odp_redo(void)
{
	odp_ink_action *a = odp_stack_pop(redo_stack, &redo_count);
	fz_page *p = NULL;
	int owns_page = 0;
	long long new_object_id = 0;
	int ok = 0;

	if (!a)
		return;
	if (!pdf || !fz_has_permission(ctx, doc, FZ_PERMISSION_ANNOTATE))
	{
		odp_stack_push(redo_stack, &redo_count, a);
		return;
	}

	if (a->page_index == currentpage)
	{
		p = page;
	}
	else
	{
		p = fz_load_page(ctx, doc, a->page_index);
		owns_page = 1;
	}

	if (a->kind == 2)
		ok = pp_pdf_delete_annot_by_object_id_mupdf(ctx, doc, p, a->page_index, a->object_id);
	else
		ok = pp_pdf_add_ink_annot_mupdf(ctx, doc, p, a->page_index,
		                               a->pageW, a->pageH,
		                               a->arc_count, a->arc_counts,
		                               a->points, a->point_count,
		                               a->color_rgb, a->thickness,
		                               &new_object_id);

	if (owns_page)
		fz_drop_page(ctx, p);

	if (!ok || (a->kind != 2 && new_object_id == 0))
	{
		fprintf(stderr, "redo: failed\n");
		odp_stack_push(redo_stack, &redo_count, a);
		return;
	}

	if (a->kind != 2)
		a->object_id = new_object_id;
	odp_stack_push(undo_stack, &undo_count, a);

	if (a->page_index == currentpage)
	{
		render_page();
		ui_needs_update = 1;
	}
}

void render_page(void)
{
	fz_annot *annot;
	fz_pixmap *pix;

	fz_scale(&page_ctm, currentzoom / 72, currentzoom / 72);
	fz_pre_rotate(&page_ctm, -currentrotate);
	fz_invert_matrix(&page_inv_ctm, &page_ctm);

	fz_drop_page(ctx, page);

	page = fz_load_page(ctx, doc, currentpage);

	fz_drop_link(ctx, links);
	links = NULL;
	links = fz_load_links(ctx, page);

	pix = fz_new_pixmap_from_page_contents(ctx, page, &page_ctm, fz_device_rgb(ctx));
	texture_from_pixmap(&page_tex, pix);
	fz_drop_pixmap(ctx, pix);

	annot_count = 0;
	for (annot = fz_first_annot(ctx, page); annot; annot = fz_next_annot(ctx, page, annot))
	{
		pix = fz_new_pixmap_from_annot(ctx, page, annot, &page_ctm, fz_device_rgb(ctx));
		texture_from_pixmap(&annot_tex[annot_count++], pix);
		fz_drop_pixmap(ctx, pix);
	}

}

static void push_history(void)
{
	if (history_count + 1 >= nelem(history))
	{
		memmove(history, history + 1, sizeof *history * (nelem(history) - 1));
		history[history_count] = currentpage;
	}
	else
	{
		history[history_count++] = currentpage;
	}
}

static void push_future(void)
{
	if (future_count + 1 >= nelem(future))
	{
		memmove(future, future + 1, sizeof *future * (nelem(future) - 1));
		future[future_count] = currentpage;
	}
	else
	{
		future[future_count++] = currentpage;
	}
}

static void clear_future(void)
{
	future_count = 0;
}

static void jump_to_page(int newpage)
{
	newpage = fz_clampi(newpage, 0, fz_count_pages(ctx, doc) - 1);
	clear_future();
	push_history();
	currentpage = newpage;
	push_history();
}

static void pop_history(void)
{
	int here = currentpage;
	push_future();
	while (history_count > 0 && currentpage == here)
		currentpage = history[--history_count];
}

static void pop_future(void)
{
	int here = currentpage;
	push_history();
	while (future_count > 0 && currentpage == here)
		currentpage = future[--future_count];
	push_history();
}

static void do_copy_region(fz_rect *screen_sel, int xofs, int yofs)
{
	fz_buffer *buf;
	fz_rect page_sel;

	xofs -= page_tex.x;
	yofs -= page_tex.y;

	page_sel.x0 = screen_sel->x0 - xofs;
	page_sel.y0 = screen_sel->y0 - yofs;
	page_sel.x1 = screen_sel->x1 - xofs;
	page_sel.y1 = screen_sel->y1 - yofs;

	fz_transform_rect(&page_sel, &page_inv_ctm);

#ifdef _WIN32
	buf = fz_new_buffer_from_page(ctx, page, &page_sel, 1);
#else
	buf = fz_new_buffer_from_page(ctx, page, &page_sel, 0);
#endif
	fz_write_buffer_rune(ctx, buf, 0);
	glfwSetClipboardString(window, (char*)buf->data);
	fz_drop_buffer(ctx, buf);
}

static void ui_label_draw(int x0, int y0, int x1, int y1, const char *text)
{
	glColor4f(1, 1, 1, 1);
	glRectf(x0, y0, x1, y1);
	glColor4f(0, 0, 0, 1);
	ui_draw_string(ctx, x0 + 2, y0 + 2 + ui.baseline, text);
}

static void ui_scrollbar(int x0, int y0, int x1, int y1, int *value, int page_size, int max)
{
	static float saved_top = 0;
	static int saved_ui_y = 0;
	float top;

	int total_h = y1 - y0;
	int thumb_h = fz_maxi(x1 - x0, total_h * page_size / max);
	int avail_h = total_h - thumb_h;

	max -= page_size;

	if (max <= 0)
	{
		*value = 0;
		glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
		glRectf(x0, y0, x1, y1);
		return;
	}

	top = (float) *value * avail_h / max;

	if (ui.down && !ui.active)
	{
		if (ui.x >= x0 && ui.x < x1 && ui.y >= y0 && ui.y < y1)
		{
			if (ui.y < top)
			{
				ui.active = "pgdn";
				*value -= page_size;
			}
			else if (ui.y >= top + thumb_h)
			{
				ui.active = "pgup";
				*value += page_size;
			}
			else
			{
				ui.hot = value;
				ui.active = value;
				saved_top = top;
				saved_ui_y = ui.y;
			}
		}
	}

	if (ui.active == value)
	{
		*value = (saved_top + ui.y - saved_ui_y) * max / avail_h;
	}

	if (*value < 0)
		*value = 0;
	else if (*value > max)
		*value = max;

	top = (float) *value * avail_h / max;

	glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
	glRectf(x0, y0, x1, y1);
	glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
	glRectf(x0, top, x1, top + thumb_h);
}

static int measure_outline_height(fz_outline *node)
{
	int h = 0;
	while (node)
	{
		h += ui.lineheight;
		if (node->down)
			h += measure_outline_height(node->down);
		node = node->next;
	}
	return h;
}

static int do_outline_imp(fz_outline *node, int end, int x0, int x1, int x, int y)
{
	int h = 0;
	int p = currentpage;
	int n = end;

	while (node)
	{
		if (node->dest.kind == FZ_LINK_GOTO)
		{
			p = node->dest.ld.gotor.page;

			if (ui.x >= x0 && ui.x < x1 && ui.y >= y + h && ui.y < y + h + ui.lineheight)
			{
				ui.hot = node;
				if (!ui.active && ui.down)
				{
					ui.active = node;
					jump_to_page(p);
					ui_needs_update = 1; /* we changed the current page, so force a redraw */
				}
			}

			n = end;
			if (node->next && node->next->dest.kind == FZ_LINK_GOTO)
			{
				n = node->next->dest.ld.gotor.page;
			}
			if (currentpage == p || (currentpage > p && currentpage < n))
			{
				glColor4f(0.9f, 0.9f, 0.9f, 1.0f);
				glRectf(x0, y + h, x1, y + h + ui.lineheight);
			}
		}

		glColor4f(0, 0, 0, 1);
		ui_draw_string(ctx, x, y + h + ui.baseline, node->title);
		h += ui.lineheight;
		if (node->down)
			h += do_outline_imp(node->down, n, x0, x1, x + ui.lineheight, y + h);

		node = node->next;
	}
	return h;
}

static void do_outline(fz_outline *node, int outline_w)
{
	static char *id = "outline";
	static int outline_scroll_y = 0;
	static int saved_outline_scroll_y = 0;
	static int saved_ui_y = 0;

	int outline_h;
	int total_h;

	outline_w -= ui.lineheight;
	outline_h = screen_h;
	total_h = measure_outline_height(outline);

	if (ui.x >= 0 && ui.x < outline_w && ui.y >= 0 && ui.y < outline_h)
	{
		ui.hot = id;
		if (!ui.active && ui.middle)
		{
			ui.active = id;
			saved_ui_y = ui.y;
			saved_outline_scroll_y = outline_scroll_y;
		}
	}

	if (ui.active == id)
		outline_scroll_y = saved_outline_scroll_y + (saved_ui_y - ui.y) * 5;

	if (ui.hot == id)
		outline_scroll_y -= ui.scroll_y * ui.lineheight * 3;

	ui_scrollbar(outline_w, 0, outline_w+ui.lineheight, outline_h, &outline_scroll_y, outline_h, total_h);

	glScissor(0, 0, outline_w, outline_h);
	glEnable(GL_SCISSOR_TEST);

	glColor4f(1, 1, 1, 1);
	glRectf(0, 0, outline_w, outline_h);

	do_outline_imp(outline, fz_count_pages(ctx, doc), 0, outline_w, 10, -outline_scroll_y);

	glDisable(GL_SCISSOR_TEST);
}

static void do_links(fz_link *link, int xofs, int yofs)
{
	fz_rect r;
	float x, y;

	x = ui.x;
	y = ui.y;

	xofs -= page_tex.x;
	yofs -= page_tex.y;

	glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
	glEnable(GL_BLEND);

	while (link)
	{
		r = link->rect;
		fz_transform_rect(&r, &page_ctm);

		if (x >= xofs + r.x0 && x < xofs + r.x1 && y >= yofs + r.y0 && y < yofs + r.y1)
		{
			ui.hot = link;
			if (!ui.active && ui.down)
				ui.active = link;
		}

		if (ui.hot == link || showlinks)
		{
			if (ui.active == link && ui.hot == link)
				glColor4f(0, 0, 1, 0.4f);
			else if (ui.hot == link)
				glColor4f(0, 0, 1, 0.2f);
			else
				glColor4f(0, 0, 1, 0.1f);
			glRectf(xofs + r.x0, yofs + r.y0, xofs + r.x1, yofs + r.y1);
		}

		if (ui.active == link && !ui.down)
		{
			if (ui.hot == link)
			{
				if (link->dest.kind == FZ_LINK_GOTO)
					jump_to_page(link->dest.ld.gotor.page);
				else if (link->dest.kind == FZ_LINK_URI)
					open_browser(link->dest.ld.uri.uri);
			}
			ui_needs_update = 1;
		}

		link = link->next;
	}

	glDisable(GL_BLEND);
}

static void do_page_selection(int x0, int y0, int x1, int y1)
{
	static fz_rect sel;

	if (ui.x >= x0 && ui.x < x1 && ui.y >= y0 && ui.y < y1)
	{
		ui.hot = &sel;
		if (!ui.active && ui.right)
		{
			ui.active = &sel;
			sel.x0 = sel.x1 = ui.x;
			sel.y0 = sel.y1 = ui.y;
		}
	}

	if (ui.active == &sel)
	{
		sel.x1 = ui.x;
		sel.y1 = ui.y;

		glBlendFunc(GL_ONE_MINUS_DST_COLOR, GL_ZERO); /* invert destination color */
		glEnable(GL_BLEND);

		glColor4f(1, 1, 1, 1);
		glRectf(sel.x0, sel.y0, sel.x1 + 1, sel.y1 + 1);

		glDisable(GL_BLEND);
	}

	if (ui.active == &sel && !ui.right)
	{
		do_copy_region(&sel, x0, y0);
		ui_needs_update = 1;
	}
}

static void do_search_hits(int xofs, int yofs)
{
	fz_rect r;
	int i;

	xofs -= page_tex.x;
	yofs -= page_tex.y;

	glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
	glEnable(GL_BLEND);

	for (i = 0; i < search_hit_count; ++i)
	{
		r = search_hit_bbox[i];

		fz_transform_rect(&r, &page_ctm);

		glColor4f(1, 0, 0, 0.4f);
		glRectf(xofs + r.x0, yofs + r.y0, xofs + r.x1, yofs + r.y1);
	}

	glDisable(GL_BLEND);
}

static void do_forms(float xofs, float yofs)
{
	pdf_ui_event event;
	fz_point p;
	int i;

	for (i = 0; i < annot_count; ++i)
		ui_draw_image(&annot_tex[i], xofs - page_tex.x, yofs - page_tex.y);

	if (!pdf || search_active || tool_mode != ODP_TOOL_NONE)
		return;

	p.x = xofs - page_tex.x + ui.x;
	p.y = yofs - page_tex.y + ui.y;
	fz_transform_point(&p, &page_inv_ctm);

	if (ui.down && !ui.active)
	{
		event.etype = PDF_EVENT_TYPE_POINTER;
		event.event.pointer.pt = p;
		event.event.pointer.ptype = PDF_POINTER_DOWN;
		if (pdf_pass_event(ctx, pdf, (pdf_page*)page, &event))
		{
			if (pdf->focus)
				ui.active = do_forms;
			pdf_update_page(ctx, pdf, (pdf_page*)page);
			render_page();
			ui_needs_update = 1;
		}
	}
	else if (ui.active == do_forms && !ui.down)
	{
		ui.active = NULL;
		event.etype = PDF_EVENT_TYPE_POINTER;
		event.event.pointer.pt = p;
		event.event.pointer.ptype = PDF_POINTER_UP;
		if (pdf_pass_event(ctx, pdf, (pdf_page*)page, &event))
		{
			pdf_update_page(ctx, pdf, (pdf_page*)page);
			render_page();
			ui_needs_update = 1;
		}
	}
}

static void toggle_fullscreen(void)
{
#if 0
	static int oldw = 100, oldh = 100, oldx = 0, oldy = 0;

	if (!isfullscreen)
	{
		oldw = glutGet(GLUT_WINDOW_WIDTH);
		oldh = glutGet(GLUT_WINDOW_HEIGHT);
		oldx = glutGet(GLUT_WINDOW_X);
		oldy = glutGet(GLUT_WINDOW_Y);
		glutFullScreen();
		isfullscreen = 1;
	}
	else
	{
		glutPositionWindow(oldx, oldy);
		glutReshapeWindow(oldw, oldh);
		isfullscreen = 0;
	}
#endif
}

static void shrinkwrap(void)
{
	int w = page_tex.w + canvas_x;
	int h = page_tex.h + canvas_y;
	if (isfullscreen)
		toggle_fullscreen();
	glfwSetWindowSize(window, w, h);
}

static void toggle_outline(void)
{
	if (outline)
	{
		showoutline = !showoutline;
		if (showoutline)
			canvas_x = ui.lineheight * 16;
		else
			canvas_x = 0;
		if (canvas_w == page_tex.w && canvas_h == page_tex.h)
			shrinkwrap();
	}
}

static void auto_zoom_w(void)
{
	currentzoom = fz_clamp(currentzoom * canvas_w / (float)page_tex.w, MINRES, MAXRES);
}

static void auto_zoom_h(void)
{
	currentzoom = fz_clamp(currentzoom * canvas_h / (float)page_tex.h, MINRES, MAXRES);
}

static void auto_zoom(void)
{
	float page_a = (float) page_tex.w / page_tex.h;
	float screen_a = (float) canvas_w / canvas_h;
	if (page_a > screen_a)
		auto_zoom_w();
	else
		auto_zoom_h();
}

static void smart_move_backward(void)
{
	if (scroll_y <= 0)
	{
		if (scroll_x <= 0)
		{
			if (currentpage - 1 >= 0)
			{
				scroll_x = page_tex.w;
				scroll_y = page_tex.h;
				currentpage -= 1;
			}
		}
		else
		{
			scroll_y = page_tex.h;
			scroll_x -= canvas_w * 9 / 10;
		}
	}
	else
	{
		scroll_y -= canvas_h * 9 / 10;
	}
}

static void smart_move_forward(void)
{
	if (scroll_y + canvas_h >= page_tex.h)
	{
		if (scroll_x + canvas_w >= page_tex.w)
		{
			if (currentpage + 1 < fz_count_pages(ctx, doc))
			{
				scroll_x = 0;
				scroll_y = 0;
				currentpage += 1;
			}
		}
		else
		{
			scroll_y = 0;
			scroll_x += canvas_w * 9 / 10;
		}
	}
	else
	{
		scroll_y += canvas_h * 9 / 10;
	}
}

static void do_app(void)
{
	if (ui.key == KEY_F4 && ui.mod == GLFW_MOD_ALT)
	{
		request_quit();
		return;
	}

	if (ui.down || ui.middle || ui.right || ui.key)
		showinfo = 0;

	if (!ui.focus && ui.key)
	{
		switch (ui.key)
		{
		case KEY_ESCAPE:
			if (tool_mode != ODP_TOOL_NONE)
			{
				tool_mode = ODP_TOOL_NONE;
				pen_drawing = 0;
				pen_point_count = 0;
				ui_needs_update = 1;
			}
			break;
		case 'q':
			request_quit();
			break;
		case 'p':
			if (tool_mode == ODP_TOOL_PEN)
			{
				tool_mode = ODP_TOOL_NONE;
				pen_drawing = 0;
				pen_point_count = 0;
				ui_needs_update = 1;
				break;
			}
			if (!pdf || !fz_has_permission(ctx, doc, FZ_PERMISSION_ANNOTATE))
			{
				fprintf(stderr, "pen: document is not annotatable\n");
				break;
			}
			tool_mode = ODP_TOOL_PEN;
			pen_drawing = 0;
			pen_point_count = 0;
			ui_needs_update = 1;
			break;
		case 'e':
			if (tool_mode == ODP_TOOL_ERASER)
			{
				tool_mode = ODP_TOOL_NONE;
				ui_needs_update = 1;
				break;
			}
			if (!pdf || !fz_has_permission(ctx, doc, FZ_PERMISSION_ANNOTATE))
			{
				fprintf(stderr, "eraser: document is not annotatable\n");
				break;
			}
			tool_mode = ODP_TOOL_ERASER;
			pen_drawing = 0;
			pen_point_count = 0;
			ui_needs_update = 1;
			break;
		case KEY_CTL_Z:
			if (ui.mod & GLFW_MOD_SHIFT)
				odp_redo();
			else
				odp_undo();
			break;
		case KEY_CTL_Y:
			odp_redo();
			break;
		case 'm':
			if (number == 0)
				push_history();
			else if (number > 0 && number < nelem(marks))
				marks[number] = currentpage;
			break;
		case 't':
			if (number == 0)
			{
				if (history_count > 0)
					pop_history();
			}
			else if (number > 0 && number < nelem(marks))
			{
				jump_to_page(marks[number]);
			}
			break;
		case 'T':
			if (number == 0)
			{
				if (future_count > 0)
					pop_future();
			}
			break;
		case 'N':
			search_dir = -1;
			if (search_hit_page == currentpage)
				search_page = currentpage + search_dir;
			else
				search_page = currentpage;
			if (search_page >= 0 && search_page < fz_count_pages(ctx, doc))
			{
				search_hit_page = -1;
				if (search_needle)
					search_active = 1;
			}
			break;
		case 'n':
			search_dir = 1;
			if (search_hit_page == currentpage)
				search_page = currentpage + search_dir;
			else
				search_page = currentpage;
			if (search_page >= 0 && search_page < fz_count_pages(ctx, doc))
			{
				search_hit_page = -1;
				if (search_needle)
					search_active = 1;
			}
			break;
		case KEY_F3:
		case KEY_CTL_G:
			if (ui.mod & GLFW_MOD_SHIFT)
				search_dir = -1;
			else
				search_dir = 1;
			if (search_hit_page == currentpage)
				search_page = currentpage + search_dir;
			else
				search_page = currentpage;
			if (search_page >= 0 && search_page < fz_count_pages(ctx, doc))
			{
				search_hit_page = -1;
				if (search_needle)
					search_active = 1;
			}
			break;
		case KEY_CTL_F:
			if (ui.mod & GLFW_MOD_SHIFT)
				search_dir = -1;
			else
				search_dir = 1;
			showsearch = 1;
			search_input.p = search_input.text;
			search_input.q = search_input.end;
			break;
		case 'f': toggle_fullscreen(); break;
		case 'w': shrinkwrap(); break;
		case 'o': toggle_outline(); break;
		case 'W': auto_zoom_w(); break;
		case 'H': auto_zoom_h(); break;
		case 'Z': auto_zoom(); break;
		case 'z': currentzoom = number > 0 ? number : DEFRES; break;
		case '<': currentpage -= 10 * fz_maxi(number, 1); break;
		case '>': currentpage += 10 * fz_maxi(number, 1); break;
		case ',': currentpage -= fz_maxi(number, 1); break;
		case '.': currentpage += fz_maxi(number, 1); break;
		case 'b': number = fz_maxi(number, 1); while (number--) smart_move_backward(); break;
		case ' ': number = fz_maxi(number, 1); while (number--) smart_move_forward(); break;
		case 'g': jump_to_page(number - 1); break;
		case 'G': jump_to_page(fz_count_pages(ctx, doc) - 1); break;
		case '+': currentzoom = zoom_in(currentzoom); break;
		case '-': currentzoom = zoom_out(currentzoom); break;
		case '[': currentrotate += 90; break;
		case ']': currentrotate -= 90; break;
		case 'l': showlinks = !showlinks; break;
		case 'i': showinfo = !showinfo; break;
		case '/': search_dir = 1; showsearch = 1; search_input.p = search_input.text; search_input.q = search_input.end; break;
		case '?': search_dir = -1; showsearch = 1; search_input.p = search_input.text; search_input.q = search_input.end; break;
		case KEY_UP: scroll_y -= 10; break;
		case KEY_DOWN: scroll_y += 10; break;
		case KEY_LEFT: scroll_x -= 10; break;
		case KEY_RIGHT: scroll_x += 10; break;
		case KEY_PAGE_UP: currentpage -= fz_maxi(number, 1); number = 0; break;
		case KEY_PAGE_DOWN: currentpage += fz_maxi(number, 1); number = 0; break;
		}

		if (ui.key >= '0' && ui.key <= '9')
			number = number * 10 + ui.key - '0';
		else
			number = 0;

		currentpage = fz_clampi(currentpage, 0, fz_count_pages(ctx, doc) - 1);
		currentzoom = fz_clamp(currentzoom, MINRES, MAXRES);
		while (currentrotate < 0) currentrotate += 360;
		while (currentrotate >= 360) currentrotate -= 360;

		if (search_hit_page != currentpage)
			search_hit_page = -1; /* clear highlights when navigating */

		ui_needs_update = 1;

		ui.key = 0; /* we ate the key event, so zap it */
	}
}

static int do_info_line(int x, int y, char *label, char *text)
{
	char buf[512];
	snprintf(buf, sizeof buf, "%s: %s", label, text);
	ui_draw_string(ctx, x, y, buf);
	return y + ui.lineheight;
}

static void do_info(void)
{
	char buf[256];

	int x = canvas_x + 4 * ui.lineheight;
	int y = canvas_y + 4 * ui.lineheight;
	int w = canvas_w - 8 * ui.lineheight;
	int h = 7 * ui.lineheight;

	glBegin(GL_TRIANGLE_STRIP);
	{
		glColor4f(0.9f, 0.9f, 0.9f, 1.0f);
		glVertex2f(x, y);
		glVertex2f(x, y + h);
		glVertex2f(x + w, y);
		glVertex2f(x + w, y + h);
	}
	glEnd();

	x += ui.lineheight;
	y += ui.lineheight + ui.baseline;

	glColor4f(0, 0, 0, 1);
	if (fz_lookup_metadata(ctx, doc, FZ_META_INFO_TITLE, buf, sizeof buf) > 0)
		y = do_info_line(x, y, "Title", buf);
	if (fz_lookup_metadata(ctx, doc, FZ_META_INFO_AUTHOR, buf, sizeof buf) > 0)
		y = do_info_line(x, y, "Author", buf);
	if (fz_lookup_metadata(ctx, doc, FZ_META_FORMAT, buf, sizeof buf) > 0)
		y = do_info_line(x, y, "Format", buf);
	if (fz_lookup_metadata(ctx, doc, FZ_META_ENCRYPTION, buf, sizeof buf) > 0)
		y = do_info_line(x, y, "Encryption", buf);
	if (pdf_specifics(ctx, doc))
	{
		buf[0] = 0;
		if (fz_has_permission(ctx, doc, FZ_PERMISSION_PRINT))
			fz_strlcat(buf, "print, ", sizeof buf);
		if (fz_has_permission(ctx, doc, FZ_PERMISSION_COPY))
			fz_strlcat(buf, "copy, ", sizeof buf);
		if (fz_has_permission(ctx, doc, FZ_PERMISSION_EDIT))
			fz_strlcat(buf, "edit, ", sizeof buf);
		if (fz_has_permission(ctx, doc, FZ_PERMISSION_ANNOTATE))
			fz_strlcat(buf, "annotate, ", sizeof buf);
		if (strlen(buf) > 2)
			buf[strlen(buf)-2] = 0;
		else
			fz_strlcat(buf, "none", sizeof buf);
		y = do_info_line(x, y, "Permissions", buf);
	}
}

static void do_canvas(void)
{
	static int saved_scroll_x = 0;
	static int saved_scroll_y = 0;
	static int saved_ui_x = 0;
	static int saved_ui_y = 0;
	static int prev_down = 0;

	float x, y;
	pp_point p;

	if (oldpage != currentpage || oldzoom != currentzoom || oldrotate != currentrotate)
	{
		render_page();
		update_title();
		oldpage = currentpage;
		oldzoom = currentzoom;
		oldrotate = currentrotate;
	}

	if (ui.x >= canvas_x && ui.x < canvas_x + canvas_w && ui.y >= canvas_y && ui.y < canvas_y + canvas_h)
	{
		ui.hot = doc;
		if (!ui.active && ui.middle)
		{
			ui.active = doc;
			saved_scroll_x = scroll_x;
			saved_scroll_y = scroll_y;
			saved_ui_x = ui.x;
			saved_ui_y = ui.y;
		}
	}

	if (ui.hot == doc)
	{
		scroll_x -= ui.scroll_x * ui.lineheight * 3;
		scroll_y -= ui.scroll_y * ui.lineheight * 3;
	}

	if (ui.active == doc)
	{
		scroll_x = saved_scroll_x + saved_ui_x - ui.x;
		scroll_y = saved_scroll_y + saved_ui_y - ui.y;
	}

	if (page_tex.w <= canvas_w)
	{
		scroll_x = 0;
		x = canvas_x + (canvas_w - page_tex.w) / 2;
	}
	else
	{
		scroll_x = fz_clamp(scroll_x, 0, page_tex.w - canvas_w);
		x = canvas_x - scroll_x;
	}

	if (page_tex.h <= canvas_h)
	{
		scroll_y = 0;
		y = canvas_y + (canvas_h - page_tex.h) / 2;
	}
	else
	{
		scroll_y = fz_clamp(scroll_y, 0, page_tex.h - canvas_h);
		y = canvas_y - scroll_y;
	}

	ui_draw_image(&page_tex, x - page_tex.x, y - page_tex.y);

	do_forms(x, y);

	if (!search_active)
	{
		if (tool_mode == ODP_TOOL_NONE)
			do_links(links, x, y);
		do_page_selection(x, y, x+page_tex.w, y+page_tex.h);
		if (search_hit_page == currentpage && search_hit_count > 0)
			do_search_hits(x, y);
	}

	if (tool_mode == ODP_TOOL_ERASER)
	{
		if (!prev_down && ui.down && !ui.focus && odp_screen_to_page_pixel(x, y, &p))
		{
			ui.active = &tool_mode;
			odp_erase_at(p);
		}
	}
	else if (tool_mode == ODP_TOOL_PEN)
	{
		int i;
		if (!prev_down && ui.down && !ui.focus && odp_screen_to_page_pixel(x, y, &p))
		{
			ui.active = &tool_mode;
			pen_drawing = 1;
			pen_point_count = 0;
			odp_pen_points_add(p.x, p.y);
		}
		else if (pen_drawing && ui.down && ui.active == &tool_mode && odp_screen_to_page_pixel(x, y, &p))
		{
			if (pen_point_count <= 0)
			{
				odp_pen_points_add(p.x, p.y);
			}
			else
			{
				pp_point last = pen_points[pen_point_count - 1];
				float dx = p.x - last.x;
				float dy = p.y - last.y;
				if (dx * dx + dy * dy >= 1.0f)
					odp_pen_points_add(p.x, p.y);
			}
		}
		else if (pen_drawing && prev_down && !ui.down && ui.active == &tool_mode)
		{
			odp_pen_commit();
			pen_drawing = 0;
			pen_point_count = 0;
		}

		if (pen_drawing && pen_point_count > 1)
		{
			float px = pen_thickness * currentzoom / 72.0f;
			if (px < 1.0f) px = 1.0f;
			glLineWidth(px);
			glColor4f(pen_color_rgb[0], pen_color_rgb[1], pen_color_rgb[2], 1.0f);
			glBegin(GL_LINE_STRIP);
			for (i = 0; i < pen_point_count; i++)
				glVertex2f(x + pen_points[i].x, y + pen_points[i].y);
			glEnd();
			glLineWidth(1.0f);
		}
	}

	prev_down = ui.down;
}

static void run_main_loop(void)
{
	glViewport(0, 0, screen_w, screen_h);
	glClearColor(0.3f, 0.3f, 0.3f, 1.0f);
	glClear(GL_COLOR_BUFFER_BIT);

	glMatrixMode(GL_PROJECTION);
	glLoadIdentity();
	glOrtho(0, screen_w, screen_h, 0, -1, 1);

	glMatrixMode(GL_MODELVIEW);
	glLoadIdentity();

	ui_begin();

	if (search_active)
	{
		float start_time = glfwGetTime();

		if (ui.key == KEY_ESCAPE)
			search_active = 0;

		/* ignore events during search */
		ui.key = ui.mod = 0;
		ui.down = ui.middle = ui.right = 0;

		while (glfwGetTime() < start_time + 0.2)
		{
			search_hit_count = fz_search_page_number(ctx, doc, search_page, search_needle,
					search_hit_bbox, nelem(search_hit_bbox));
			if (search_hit_count)
			{
				search_active = 0;
				search_hit_page = search_page;
				jump_to_page(search_hit_page);
				break;
			}
			else
			{
				search_page += search_dir;
				if (search_page < 0 || search_page == fz_count_pages(ctx, doc))
				{
					search_active = 0;
					break;
				}
			}
		}

		/* keep searching later */
		if (search_active)
			ui_needs_update = 1;
	}

	do_app();

	canvas_w = screen_w - canvas_x;
	canvas_h = screen_h - canvas_y;

	do_canvas();

	if (showinfo)
		do_info();

	if (showoutline)
		do_outline(outline, canvas_x);

	if (showsearch)
	{
		int state = ui_input(canvas_x, 0, canvas_x + canvas_w, ui.lineheight+4, &search_input);
		if (state == -1)
		{
			ui.focus = NULL;
			showsearch = 0;
		}
		else if (state == 1)
		{
			ui.focus = NULL;
			showsearch = 0;
			search_page = -1;
			if (search_needle)
			{
				fz_free(ctx, search_needle);
				search_needle = NULL;
			}
			if (search_input.end > search_input.text)
			{
				search_needle = fz_strdup(ctx, search_input.text);
				search_active = 1;
				search_page = currentpage;
			}
		}
		ui_needs_update = 1;
	}

	if (!showsearch && !search_active)
	{
		if (tool_mode == ODP_TOOL_PEN)
			ui_label_draw(canvas_x, 0, canvas_x + canvas_w, ui.lineheight+4,
			              "Pen: drag to draw. Ctrl+Z undo, Ctrl+Shift+Z redo, Esc exit.");
		else if (tool_mode == ODP_TOOL_ERASER)
			ui_label_draw(canvas_x, 0, canvas_x + canvas_w, ui.lineheight+4,
			              "Eraser: click ink to delete. Ctrl+Z undo, Ctrl+Shift+Z redo, Esc exit.");
	}

	if (search_active)
	{
		char buf[256];
		sprintf(buf, "Searching page %d of %d.", search_page + 1, fz_count_pages(ctx, doc));
		ui_label_draw(canvas_x, 0, canvas_x + canvas_w, ui.lineheight+4, buf);
	}

	ui_end();

	glfwSwapBuffers(window);

	ogl_assert(ctx, "swap buffers");
}

static void on_char(GLFWwindow *window, unsigned int key, int mod)
{
	ui.key = key;
	ui.mod = mod;
	run_main_loop();
	ui.key = ui.mod = 0;
}

static void on_key(GLFWwindow *window, int special, int scan, int action, int mod)
{
	if (action == GLFW_PRESS || action == GLFW_REPEAT)
	{
		ui.key = 0;
		switch (special)
		{
#ifndef GLFW_MUPDF_FIXES
		/* regular control characters: ^A, ^B, etc. */
		default:
			if (special >= 'A' && special <= 'Z' && (mod & GLFW_MOD_CONTROL))
				ui.key = KEY_CTL_A + special - 'A';
			break;

		/* regular control characters: escape, enter, backspace, tab */
		case GLFW_KEY_ESCAPE: ui.key = KEY_ESCAPE; break;
		case GLFW_KEY_ENTER: ui.key = KEY_ENTER; break;
		case GLFW_KEY_BACKSPACE: ui.key = KEY_BACKSPACE; break;
		case GLFW_KEY_TAB: ui.key = KEY_TAB; break;
#endif
		case GLFW_KEY_INSERT: ui.key = KEY_INSERT; break;
		case GLFW_KEY_DELETE: ui.key = KEY_DELETE; break;
		case GLFW_KEY_RIGHT: ui.key = KEY_RIGHT; break;
		case GLFW_KEY_LEFT: ui.key = KEY_LEFT; break;
		case GLFW_KEY_DOWN: ui.key = KEY_DOWN; break;
		case GLFW_KEY_UP: ui.key = KEY_UP; break;
		case GLFW_KEY_PAGE_UP: ui.key = KEY_PAGE_UP; break;
		case GLFW_KEY_PAGE_DOWN: ui.key = KEY_PAGE_DOWN; break;
		case GLFW_KEY_HOME: ui.key = KEY_HOME; break;
		case GLFW_KEY_END: ui.key = KEY_END; break;
		case GLFW_KEY_F1: ui.key = KEY_F1; break;
		case GLFW_KEY_F2: ui.key = KEY_F2; break;
		case GLFW_KEY_F3: ui.key = KEY_F3; break;
		case GLFW_KEY_F4: ui.key = KEY_F4; break;
		case GLFW_KEY_F5: ui.key = KEY_F5; break;
		case GLFW_KEY_F6: ui.key = KEY_F6; break;
		case GLFW_KEY_F7: ui.key = KEY_F7; break;
		case GLFW_KEY_F8: ui.key = KEY_F8; break;
		case GLFW_KEY_F9: ui.key = KEY_F9; break;
		case GLFW_KEY_F10: ui.key = KEY_F10; break;
		case GLFW_KEY_F11: ui.key = KEY_F11; break;
		case GLFW_KEY_F12: ui.key = KEY_F12; break;
		}
		if (ui.key)
		{
			ui.mod = mod;
			run_main_loop();
			ui.key = ui.mod = 0;
		}
	}
}

static void on_mouse_button(GLFWwindow *window, int button, int action, int mod)
{
	switch (button)
	{
	case GLFW_MOUSE_BUTTON_LEFT: ui.down = (action == GLFW_PRESS); break;
	case GLFW_MOUSE_BUTTON_MIDDLE: ui.middle = (action == GLFW_PRESS); break;
	case GLFW_MOUSE_BUTTON_RIGHT: ui.right = (action == GLFW_PRESS); break;
	}

	run_main_loop();
}

static void on_mouse_motion(GLFWwindow *window, double x, double y)
{
	ui.x = x;
	ui.y = y;
	ui_needs_update = 1;
}

static void on_scroll(GLFWwindow *window, double x, double y)
{
	ui.scroll_x = x;
	ui.scroll_y = y;
	run_main_loop();
	ui.scroll_x = ui.scroll_y = 0;
}

static void on_reshape(GLFWwindow *window, int w, int h)
{
	showinfo = 0;
	screen_w = w;
	screen_h = h;
	ui_needs_update = 1;
}

static void on_display(GLFWwindow *window)
{
	ui_needs_update = 1;
}

static void on_error(int error, const char *msg)
{
	fprintf(stderr, "gl error %d: %s\n", error, msg);
}

static void usage(void)
{
	fprintf(stderr, "usage: mupdf [options] document [page]\n");
	fprintf(stderr, "\t-p -\tpassword\n");
	fprintf(stderr, "\t-r -\tresolution\n");
	fprintf(stderr, "\t-W -\tpage width for EPUB layout\n");
	fprintf(stderr, "\t-H -\tpage height for EPUB layout\n");
	fprintf(stderr, "\t-S -\tfont size for EPUB layout\n");
	fprintf(stderr, "\t-U -\tuser style sheet for EPUB layout\n");
	fprintf(stderr, "\t-R -\topen Nth recent document (1=most recent)\n");
	fprintf(stderr, "\t-L\tlist recent documents and exit\n");
	exit(1);
}

#ifdef _MSC_VER
int main_utf8(int argc, char **argv)
#else
int main(int argc, char **argv)
#endif
{
	char filename[2048];
	char *password = "";
	float layout_w = 450;
	float layout_h = 600;
	float layout_em = 12;
	char *layout_css = NULL;
	int zoom_from_cli = 0;
	int layout_w_from_cli = 0;
	int layout_h_from_cli = 0;
	int layout_em_from_cli = 0;
	int list_recents = 0;
	int recent_index = -1; /* 0-based */
	int restore_viewport = 0;
	odp_viewport_state restored = {0};
	odp_recents recents = {0};
	int c;

	odp_recents_load(&recents);

	while ((c = fz_getopt(argc, argv, "p:r:W:H:S:U:R:L")) != -1)
	{
		switch (c)
		{
		default: usage(); break;
		case 'p': password = fz_optarg; break;
		case 'r': currentzoom = fz_atof(fz_optarg); zoom_from_cli = 1; break;
		case 'W': layout_w = fz_atof(fz_optarg); layout_w_from_cli = 1; break;
		case 'H': layout_h = fz_atof(fz_optarg); layout_h_from_cli = 1; break;
		case 'S': layout_em = fz_atof(fz_optarg); layout_em_from_cli = 1; break;
		case 'U': layout_css = fz_optarg; break;
		case 'R': recent_index = atoi(fz_optarg) - 1; break;
		case 'L': list_recents = 1; break;
		}
	}

	if (list_recents)
	{
		int i;
		for (i = 0; i < recents.count; ++i)
			printf("%d\t%s\n", i + 1, recents.entries[i].path_utf8 ? recents.entries[i].path_utf8 : "");
		odp_recents_clear(&recents);
		return 0;
	}

	if (fz_optind < argc)
	{
		if (recent_index >= 0)
			usage();
		fz_strlcpy(filename, argv[fz_optind], sizeof filename);
	}
	else
	{
#ifdef _WIN32
		win_install();
		if (!win_open_file(filename, sizeof filename))
		{
			odp_recents_clear(&recents);
			exit(0);
		}
#else
		if (recents.count <= 0)
			usage();
		if (recent_index < 0)
			recent_index = 0;
		if (recent_index >= recents.count)
		{
			fprintf(stderr, "No such recent entry: %d\n", recent_index + 1);
			odp_recents_clear(&recents);
			exit(1);
		}
		if (!recents.entries[recent_index].path_utf8 || !*recents.entries[recent_index].path_utf8)
		{
			fprintf(stderr, "Recent entry %d has no path.\n", recent_index + 1);
			odp_recents_clear(&recents);
			exit(1);
		}
		fz_strlcpy(filename, recents.entries[recent_index].path_utf8, sizeof filename);
		restored = recents.entries[recent_index].viewport;
		restore_viewport = 1;

		if (!zoom_from_cli && restored.zoom > 0)
			currentzoom = restored.zoom;
		if (!layout_w_from_cli && restored.layout_w > 0)
			layout_w = restored.layout_w;
		if (!layout_h_from_cli && restored.layout_h > 0)
			layout_h = restored.layout_h;
		if (!layout_em_from_cli && restored.layout_em > 0)
			layout_em = restored.layout_em;
#endif
	}

	title = strrchr(filename, '/');
	if (!title)
		title = strrchr(filename, '\\');
	if (title)
		++title;
	else
		title = filename;

	memset(&ui, 0, sizeof ui);

	search_input.p = search_input.text;
	search_input.q = search_input.p;
	search_input.end = search_input.p;

	if (!glfwInit()) {
		fprintf(stderr, "cannot initialize glfw\n");
		exit(1);
	}

	glfwSetErrorCallback(on_error);

	window = glfwCreateWindow(800, 1000, filename, NULL, NULL);
	if (!window) {
		fprintf(stderr, "cannot create glfw window\n");
		exit(1);
	}

	glfwMakeContextCurrent(window);

	ctx = fz_new_context(NULL, NULL, 0);
	fz_register_document_handlers(ctx);

	if (layout_css)
	{
		fz_buffer *buf = fz_read_file(ctx, layout_css);
		fz_write_buffer_byte(ctx, buf, 0);
		fz_set_user_css(ctx, (char*)buf->data);
		fz_drop_buffer(ctx, buf);
	}

	has_ARB_texture_non_power_of_two = glfwExtensionSupported("GL_ARB_texture_non_power_of_two");
	if (!has_ARB_texture_non_power_of_two)
		fz_warn(ctx, "OpenGL implementation does not support non-power of two texture sizes");

	glGetIntegerv(GL_MAX_TEXTURE_SIZE, &max_texture_size);

	ui.fontsize = 15;
	ui.baseline = 14;
	ui.lineheight = 18;

	ui_init_fonts(ctx, ui.fontsize);

	doc = fz_open_document(ctx, filename);
	if (fz_needs_password(ctx, doc))
	{
		if (!fz_authenticate_password(ctx, doc, password))
		{
			fprintf(stderr, "Invalid password.\n");
			exit(1);
		}
	}

	outline = fz_load_outline(ctx, doc);
	pdf = pdf_specifics(ctx, doc);
	if (pdf)
		pdf_enable_js(ctx, pdf);

	fz_layout_document(ctx, doc, layout_w, layout_h, layout_em);

	if (restore_viewport)
	{
		currentpage = fz_clampi(restored.page_index, 0, fz_count_pages(ctx, doc) - 1);
		currentrotate = restored.rotate;
		scroll_x = restored.scroll_x;
		scroll_y = restored.scroll_y;

		currentzoom = fz_clamp(currentzoom, MINRES, MAXRES);
		while (currentrotate < 0) currentrotate += 360;
		while (currentrotate >= 360) currentrotate -= 360;
	}

	render_page();
	update_title();
	shrinkwrap();

	glfwSetFramebufferSizeCallback(window, on_reshape);
	glfwSetCursorPosCallback(window, on_mouse_motion);
	glfwSetMouseButtonCallback(window, on_mouse_button);
	glfwSetScrollCallback(window, on_scroll);
	glfwSetCharModsCallback(window, on_char);
	glfwSetKeyCallback(window, on_key);
	glfwSetWindowRefreshCallback(window, on_display);

	glfwGetFramebufferSize(window, &screen_w, &screen_h);

	ui_needs_update = 1;

	while (!glfwWindowShouldClose(window))
	{
		glfwWaitEvents();
		if (ui_needs_update)
			run_main_loop();
	}

	{
		odp_viewport_state vp;
		memset(&vp, 0, sizeof vp);
		vp.page_index = currentpage;
		vp.zoom = currentzoom;
		vp.rotate = currentrotate;
		vp.scroll_x = scroll_x;
		vp.scroll_y = scroll_y;
		vp.layout_w = layout_w;
		vp.layout_h = layout_h;
		vp.layout_em = layout_em;

		odp_recents_touch(&recents, filename, &vp, odp_now_epoch_ms());
		odp_recents_save(&recents);
		odp_recents_clear(&recents);
	}

	odp_stack_clear(undo_stack, &undo_count);
	odp_stack_clear(redo_stack, &redo_count);
	free(pen_points);
	pen_points = NULL;
	pen_point_count = 0;
	pen_point_cap = 0;

	ui_finish_fonts(ctx);

	fz_drop_link(ctx, links);
	fz_drop_page(ctx, page);
	fz_drop_document(ctx, doc);
	fz_drop_context(ctx);

	return 0;
}

#ifdef _MSC_VER
int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nShowCmd)
{
	int argc;
	LPWSTR *wargv = CommandLineToArgvW(GetCommandLineW(), &argc);
	char **argv = fz_argv_from_wargv(argc, wargv);
	int ret = main_utf8(argc, argv);
	fz_free_argv(argc, argv);
	return ret;
}
#endif
