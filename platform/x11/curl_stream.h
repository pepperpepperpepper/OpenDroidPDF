#ifndef MUPDF_CURL_STREAM_H
#define MUPDF_CURL_STREAM_H

#include "mupdf/fitz.h"

#ifdef __cplusplus
extern "C" {
#endif

/* Create an fz_stream that progressively fetches data from a URL via libcurl. */
fz_stream *fz_stream_from_curl(fz_context *ctx, char *filename, void (*more_data)(void *, int), void *more_data_arg);

#ifdef __cplusplus
}
#endif

#endif
