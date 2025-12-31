#ifndef ODP_WORD_IMPORT_H
#define ODP_WORD_IMPORT_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Convert a Word document (.doc/.docx) to a cached PDF using LibreOffice (soffice).
 *
 * - out_doc_id is the stable content id (sha256:*).
 * - out_pdf_path is the derived PDF path under the OpenDroidPDF cache dir.
 *
 * Returns 1 on success, 0 on failure.
 */
int odp_word_import_to_cached_pdf(const char *word_path,
                                  char *out_pdf_path, size_t out_pdf_len,
                                  char *out_doc_id, size_t out_doc_id_len,
                                  char *out_err, size_t out_err_len);

#ifdef __cplusplus
}
#endif

#endif

