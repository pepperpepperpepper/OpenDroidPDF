#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "PDF FreeText annotations actually work end-to-end":
# - Push a writable PDF to /sdcard/Download
# - Launch OpenDroidPDF with a content:// DocumentsUI Uri
# - Enter "Add text" mode, tap the page, enter a token, confirm
# - Save in-place
# - Pull the saved PDF back to host and OCR the first page to ensure the token renders
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_text_annot_smoke.sh
#
# Requirements (host):
#   - pdftoppm (poppler)
#   - tesseract

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_with_text.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_text_annot_smoke.pdf}
# Use a long, multi-word token so width shrink forces wrapping deterministically.
TOKEN=${TOKEN:-WRAPCHECK%sLONG%sTOKEN%sMULTILINE}
# adb `input text` encodes spaces as "%s". Allow the input token to differ from the expected
# rendered token so the smoke can use multi-word phrases without breaking OCR matching.
TOKEN_INPUT=${TOKEN_INPUT:-$TOKEN}
TOKEN_EXPECTED=${TOKEN_EXPECTED:-${TOKEN//%s/ }}
# OCR TSV emits words; use the first word for center-point discovery.
TOKEN_SEARCH=${TOKEN_SEARCH:-${TOKEN_EXPECTED%% *}}
TOKEN_SUFFIX_EDIT=${TOKEN_SUFFIX_EDIT:-_EDIT}
TOKEN_EDIT=${TOKEN_EDIT:-${TOKEN}${TOKEN_SUFFIX_EDIT}}
TOKEN_EDIT_EXPECTED=${TOKEN_EDIT_EXPECTED:-${TOKEN_EXPECTED}${TOKEN_SUFFIX_EDIT}}
TOKEN_EDIT_SEARCH=${TOKEN_EDIT_SEARCH:-${TOKEN_EDIT_EXPECTED%% *}}
ASSERT_ONSCREEN_OCR=${ASSERT_ONSCREEN_OCR:-1}
ASSERT_TEXT_WRAP_ON_RESIZE=${ASSERT_TEXT_WRAP_ON_RESIZE:-1}
POST_SAVE_HOME_WAIT_S=${POST_SAVE_HOME_WAIT_S:-90}
POST_EDIT_IDLE_TAP_S=${POST_EDIT_IDLE_TAP_S:-30}
UIA_ZOOM_TEST=${UIA_ZOOM_TEST:-org.opendroidpdf.uia.ZoomPinchTest#testPinchOutOnlyDoesNotCrash}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/geny_uia.sh"
source "$SCRIPT_DIR/lib/geny_pdf_smoke_ocr.sh"
source "$SCRIPT_DIR/lib/geny_pdf_text_annot_steps.sh"

adb -s "$DEVICE" get-state >/dev/null

if ! command -v pdftoppm >/dev/null 2>&1; then
  echo "FAIL: pdftoppm not found (install poppler)." >&2
  exit 2
fi
if ! command -v tesseract >/dev/null 2>&1; then
  echo "FAIL: tesseract not found (install tesseract-ocr)." >&2
  exit 2
fi

geny_pdf_text_annot_smoke_run

