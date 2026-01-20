#!/usr/bin/env bash
set -euo pipefail

# Runs the Phase 3 (text annotation + markup) Genymotion acceptance smokes in sequence.
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_text_phase3_acceptance.sh
#
# Optional:
#   PDF_LOCAL_TEXT=/path/to/pdf_with_text.pdf
#   PDF_LOCAL_MARKUP=/path/to/list.pdf

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}

PDF_LOCAL_TEXT=${PDF_LOCAL_TEXT:-test_assets/pdf_with_text.pdf}
PDF_LOCAL_MARKUP=${PDF_LOCAL_MARKUP:-$PDF_LOCAL_TEXT}

_run() {
  local name="$1"
  shift
  echo
  echo "==> $name"
  "$@"
}

_run "1/5 FreeText end-to-end" \
  env DEVICE="$DEVICE" APK="$APK" PDF_LOCAL="$PDF_LOCAL_TEXT" \
  ./scripts/geny_pdf_text_annot_smoke.sh

_run "2/5 FreeText background fill persists" \
  env DEVICE="$DEVICE" APK="$APK" PDF_LOCAL="$PDF_LOCAL_TEXT" \
  ./scripts/geny_pdf_text_annot_background_smoke.sh

_run "3/5 FreeText multi-select align/distribute" \
  env DEVICE="$DEVICE" APK="$APK" PDF_LOCAL="$PDF_LOCAL_TEXT" \
  ./scripts/geny_pdf_text_annot_multiselect_smoke.sh

_run "4/5 FreeText auto-fit after edit" \
  env DEVICE="$DEVICE" APK="$APK" PDF_LOCAL="$PDF_LOCAL_TEXT" \
  ./scripts/geny_pdf_text_annot_autofit_smoke.sh

_run "5/5 Markup highlight/delete + underline positioning" \
  env DEVICE="$DEVICE" APK="$APK" PDF_LOCAL="$PDF_LOCAL_MARKUP" \
  ./scripts/geny_pdf_text_markup_smoke.sh

echo
echo "OK: Phase 3 text acceptance smokes complete"
