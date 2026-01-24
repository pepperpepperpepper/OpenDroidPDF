# geny_pdf_text_annot_steps.sh: entry point for the PDF text-annotation smoke steps.
#
# Intended to be sourced from `scripts/geny_pdf_text_annot_smoke.sh`. Assumes:
# - `set -euo pipefail` is set by the caller
# - `geny_uia.sh` and `geny_pdf_smoke_ocr.sh` are already sourced
# - Required env vars (DEVICE, PKG, ACT, etc) are set by the caller

LIB_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${LIB_DIR}/geny_pdf_text_annot_helpers.sh"
source "${LIB_DIR}/geny_pdf_text_annot_steps_open_create.sh"
source "${LIB_DIR}/geny_pdf_text_annot_steps_move_resize.sh"
source "${LIB_DIR}/geny_pdf_text_annot_steps_style_misc.sh"
source "${LIB_DIR}/geny_pdf_text_annot_steps_save_assert.sh"

geny_pdf_text_annot_smoke_run() {
  _geny_pdf_text_annot_step_open_pdf
  _geny_pdf_text_annot_ensure_on_first_page_best_effort || {
    echo "FAIL: could not ensure page 1 is visible" >&2
    exit 1
  }
  _geny_pdf_text_annot_step_create_and_edit
  _geny_pdf_text_annot_step_move_undo_resize
  _geny_pdf_text_annot_step_style_misc
  _geny_pdf_text_annot_step_save_and_assert
}
