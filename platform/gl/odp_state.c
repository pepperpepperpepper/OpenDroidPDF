#include "odp_state.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>

#ifdef _WIN32
#include <direct.h>
#endif

static char *odp_strdup(const char *s)
{
	size_t n;
	char *p;

	if (!s)
		return NULL;
	n = strlen(s) + 1;
	p = (char *)malloc(n);
	if (!p)
		return NULL;
	memcpy(p, s, n);
	return p;
}

static void odp_entry_free(odp_recent_entry *e)
{
	free(e->path_utf8);
	e->path_utf8 = NULL;
}

void odp_recents_clear(odp_recents *recents)
{
	int i;
	if (!recents)
		return;
	for (i = 0; i < recents->count; ++i)
		odp_entry_free(&recents->entries[i]);
	memset(recents, 0, sizeof(*recents));
}

static int odp_mkdir_one(const char *path)
{
#ifdef _WIN32
	if (_mkdir(path) != 0 && errno != EEXIST)
		return -1;
#else
	if (mkdir(path, 0755) != 0 && errno != EEXIST)
		return -1;
#endif
	return 0;
}

static int odp_mkdir_p(const char *path)
{
	char tmp[2048];
	size_t len;
	size_t i;

	if (!path || !*path)
		return -1;

	len = strlen(path);
	if (len >= sizeof(tmp))
		return -1;

	memcpy(tmp, path, len + 1);

	for (i = 1; i < len; ++i)
	{
		if (tmp[i] == '/')
		{
			tmp[i] = 0;
			if (odp_mkdir_one(tmp) != 0)
				return -1;
			tmp[i] = '/';
		}
	}

	if (odp_mkdir_one(tmp) != 0)
		return -1;
	return 0;
}

static int odp_state_path(char *out_path, size_t out_len)
{
	const char *state_home = getenv("XDG_STATE_HOME");
	const char *home = getenv("HOME");
	char base[2048];
	char dir[2048];

	if (state_home && *state_home)
	{
		if (snprintf(base, sizeof(base), "%s", state_home) >= (int)sizeof(base))
			return -1;
	}
	else if (home && *home)
	{
		if (snprintf(base, sizeof(base), "%s/.local/state", home) >= (int)sizeof(base))
			return -1;
	}
	else
	{
		return -1;
	}

	if (snprintf(dir, sizeof(dir), "%s/opendroidpdf", base) >= (int)sizeof(dir))
		return -1;

	if (odp_mkdir_p(dir) != 0)
		return -1;

	if (snprintf(out_path, out_len, "%s/recents.tsv", dir) >= (int)out_len)
		return -1;

	return 0;
}

int odp_cache_dir(char *out_dir, size_t out_len)
{
	const char *cache_home = getenv("XDG_CACHE_HOME");
	const char *home = getenv("HOME");
	char base[2048];
	char dir[2048];

	if (!out_dir || out_len == 0)
		return -1;

	if (cache_home && *cache_home)
	{
		if (snprintf(base, sizeof(base), "%s", cache_home) >= (int)sizeof(base))
			return -1;
	}
	else if (home && *home)
	{
		if (snprintf(base, sizeof(base), "%s/.cache", home) >= (int)sizeof(base))
			return -1;
	}
	else
	{
		return -1;
	}

	if (snprintf(dir, sizeof(dir), "%s/opendroidpdf", base) >= (int)sizeof(dir))
		return -1;

	if (odp_mkdir_p(dir) != 0)
		return -1;

	if (snprintf(out_dir, out_len, "%s", dir) >= (int)out_len)
		return -1;

	return 0;
}

long long odp_now_epoch_ms(void)
{
#if defined(CLOCK_REALTIME)
	struct timespec ts;
	if (clock_gettime(CLOCK_REALTIME, &ts) == 0)
		return (long long)ts.tv_sec * 1000LL + (long long)(ts.tv_nsec / 1000000L);
#endif
	return (long long)time(NULL) * 1000LL;
}

void odp_recents_touch(odp_recents *recents, const char *path_utf8,
                       const odp_viewport_state *viewport,
                       long long now_epoch_ms)
{
	int i, j;
	odp_recent_entry moved;

	if (!recents || !path_utf8 || !*path_utf8)
		return;

	memset(&moved, 0, sizeof moved);

	for (i = 0; i < recents->count; ++i)
	{
		if (recents->entries[i].path_utf8 && strcmp(recents->entries[i].path_utf8, path_utf8) == 0)
		{
			moved = recents->entries[i];
			for (j = i; j + 1 < recents->count; ++j)
				recents->entries[j] = recents->entries[j + 1];
			--recents->count;
			break;
		}
	}

	if (!moved.path_utf8)
	{
		moved.path_utf8 = odp_strdup(path_utf8);
		if (!moved.path_utf8)
			return;
		memset(&moved.viewport, 0, sizeof moved.viewport);
		moved.last_opened_epoch_ms = 0;
	}

	if (viewport)
		moved.viewport = *viewport;
	moved.last_opened_epoch_ms = now_epoch_ms;

	if (recents->count == ODP_RECENTS_MAX)
	{
		odp_entry_free(&recents->entries[ODP_RECENTS_MAX - 1]);
		--recents->count;
	}

	for (j = recents->count; j > 0; --j)
		recents->entries[j] = recents->entries[j - 1];
	recents->entries[0] = moved;
	++recents->count;
}

int odp_recents_save(const odp_recents *recents)
{
	char path[2048];
	FILE *f;
	int i;

	if (!recents)
		return -1;
	if (odp_state_path(path, sizeof(path)) != 0)
		return -1;

	f = fopen(path, "w");
	if (!f)
		return -1;

	for (i = 0; i < recents->count; ++i)
	{
		const odp_recent_entry *e = &recents->entries[i];
		const odp_viewport_state *v = &e->viewport;
		if (!e->path_utf8)
			continue;
		fprintf(f,
		        "%lld\t%d\t%.9g\t%.9g\t%d\t%d\t%.9g\t%.9g\t%.9g\t%s\n",
		        e->last_opened_epoch_ms,
		        v->page_index,
		        v->zoom,
		        v->rotate,
		        v->scroll_x,
		        v->scroll_y,
		        v->layout_w,
		        v->layout_h,
		        v->layout_em,
		        e->path_utf8);
	}

	fclose(f);
	return 0;
}

static char *odp_next_field(char **inout_p)
{
	char *p = *inout_p;
	char *tab;

	if (!p)
		return NULL;

	tab = strchr(p, '\t');
	if (tab)
	{
		*tab = 0;
		*inout_p = tab + 1;
	}
	else
	{
		*inout_p = NULL;
	}

	return p;
}

static int odp_parse_line(char *line, odp_recent_entry *out_entry)
{
	odp_viewport_state v;
	long long epoch_ms = 0;
	char *p;
	char *path;
	char *field;

	memset(&v, 0, sizeof v);

	p = line;

	field = odp_next_field(&p);
	if (!field) return -1;
	epoch_ms = atoll(field);

	field = odp_next_field(&p);
	if (!field) return -1;
	v.page_index = atoi(field);

	field = odp_next_field(&p);
	if (!field) return -1;
	v.zoom = (float)atof(field);

	field = odp_next_field(&p);
	if (!field) return -1;
	v.rotate = (float)atof(field);

	field = odp_next_field(&p);
	if (!field) return -1;
	v.scroll_x = atoi(field);

	field = odp_next_field(&p);
	if (!field) return -1;
	v.scroll_y = atoi(field);

	field = odp_next_field(&p);
	if (!field) return -1;
	v.layout_w = (float)atof(field);

	field = odp_next_field(&p);
	if (!field) return -1;
	v.layout_h = (float)atof(field);

	field = odp_next_field(&p);
	if (!field) return -1;
	v.layout_em = (float)atof(field);

	path = p;

	if (!path || !*path)
		return -1;

	/* Trim trailing newline(s). */
	{
		size_t n = strlen(path);
		while (n > 0 && (path[n - 1] == '\n' || path[n - 1] == '\r'))
		{
			path[n - 1] = 0;
			--n;
		}
	}

	out_entry->path_utf8 = odp_strdup(path);
	if (!out_entry->path_utf8)
		return -1;
	out_entry->last_opened_epoch_ms = epoch_ms;
	out_entry->viewport = v;
	return 0;
}

int odp_recents_load(odp_recents *out_recents)
{
	char path[2048];
	FILE *f;
	char line[4096];
	int loaded = 0;

	if (!out_recents)
		return -1;

	memset(out_recents, 0, sizeof(*out_recents));

	if (odp_state_path(path, sizeof(path)) != 0)
		return -1;

	f = fopen(path, "r");
	if (!f)
		return 0; /* missing state is OK */

	while (fgets(line, sizeof(line), f))
	{
		odp_recent_entry e;
		memset(&e, 0, sizeof e);

		if (out_recents->count >= ODP_RECENTS_MAX)
			break;

		if (odp_parse_line(line, &e) == 0)
		{
			out_recents->entries[out_recents->count++] = e;
			++loaded;
		}
	}

	fclose(f);
	(void)loaded;
	return 0;
}
