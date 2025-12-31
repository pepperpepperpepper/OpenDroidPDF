#ifndef ODP_STATE_H
#define ODP_STATE_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#define ODP_RECENTS_MAX 10

typedef struct odp_viewport_state
{
	int page_index;
	float zoom;
	float rotate;
	int scroll_x;
	int scroll_y;
	float layout_w;
	float layout_h;
	float layout_em;
} odp_viewport_state;

typedef struct odp_recent_entry
{
	char *path_utf8; /* owned; malloc'd */
	long long last_opened_epoch_ms;
	odp_viewport_state viewport;
} odp_recent_entry;

typedef struct odp_recents
{
	int count;
	odp_recent_entry entries[ODP_RECENTS_MAX];
} odp_recents;

int odp_recents_load(odp_recents *out_recents);
int odp_recents_save(const odp_recents *recents);
void odp_recents_clear(odp_recents *recents);

/* Resolve (and ensure) the OpenDroidPDF cache dir (XDG_CACHE_HOME/opendroidpdf or ~/.cache/opendroidpdf). */
int odp_cache_dir(char *out_dir, size_t out_len);

/* Touch/move-to-front the given path in the MRU list. viewport may be NULL to keep the existing one. */
void odp_recents_touch(odp_recents *recents, const char *path_utf8,
                       const odp_viewport_state *viewport,
                       long long now_epoch_ms);

long long odp_now_epoch_ms(void);

#ifdef __cplusplus
}
#endif

#endif
