#include "pp_core_internal.h"

static void
pp_drop_cached_page(fz_context *ctx, pp_cached_page *pc)
{
	if (!pc)
		return;
	if (pc->display_list)
		fz_drop_display_list(ctx, pc->display_list);
	pc->display_list = NULL;
	if (pc->page)
		fz_drop_page(ctx, pc->page);
	pc->page = NULL;
	pc->page_index = -1;
	pc->last_used = 0;
	pc->bounds_72 = fz_empty_rect;
}

void
pp_clear_page_cache_locked(fz_context *ctx, pp_doc *doc)
{
	int i;
	if (!doc)
		return;
	for (i = 0; i < (int)(sizeof(doc->pages) / sizeof(doc->pages[0])); i++)
		pp_drop_cached_page(ctx, &doc->pages[i]);
	doc->use_counter = 1;
}

static int
pp_cache_find_index(pp_doc *doc, int page_index)
{
	int i;
	for (i = 0; i < (int)(sizeof(doc->pages) / sizeof(doc->pages[0])); i++)
	{
		if (doc->pages[i].page && doc->pages[i].page_index == page_index)
			return i;
	}
	return -1;
}

static int
pp_cache_choose_slot(pp_doc *doc)
{
	int i;
	int best = 0;
	unsigned int best_used = 0;

	for (i = 0; i < (int)(sizeof(doc->pages) / sizeof(doc->pages[0])); i++)
	{
		pp_cached_page *pc = &doc->pages[i];
		if (!pc->page)
			return i;
		if (i == 0 || pc->last_used < best_used)
		{
			best = i;
			best_used = pc->last_used;
		}
	}
	return best;
}

pp_cached_page *
pp_cache_ensure_page_locked(fz_context *ctx, pp_doc *doc, int page_index)
{
	int idx;
	pp_cached_page *pc;

	idx = pp_cache_find_index(doc, page_index);
	if (idx >= 0)
	{
		pc = &doc->pages[idx];
		pc->last_used = doc->use_counter++;
		return pc;
	}

	idx = pp_cache_choose_slot(doc);
	pc = &doc->pages[idx];
	pp_drop_cached_page(ctx, pc);

	pc->page_index = page_index;
	pc->last_used = doc->use_counter++;
	pc->display_list = NULL;

	pc->page = fz_load_page(ctx, doc->doc, page_index);
	pc->bounds_72 = pp_bound_page_compat(ctx, pc->page);

	return pc;
}

