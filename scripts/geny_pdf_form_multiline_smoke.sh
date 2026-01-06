#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for AcroForm multiline text widget rendering fidelity:
# - Uses a multiline text field fixture
# - Enters a token containing a literal backslash (requires correct PDF string escaping)
# - Saves, reopens, and asserts the value is visible to third-party tooling (poppler pdftotext)
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_form_multiline_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}

PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_form_multiline.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_form_multiline_smoke.pdf}

TOKEN=${TOKEN:-'ODP_MULTILINE\\SMOKE'}
TOKEN_INPUT=${TOKEN_INPUT:-$TOKEN}
TOKEN_EXPECTED=${TOKEN_EXPECTED:-$TOKEN}

# The base fill smoke already runs an OCR check; for this test we prefer deterministic
# poppler text extraction (pdftotext) so OCR is off by default.
ASSERT_RENDER_OCR=${ASSERT_RENDER_OCR:-0}
ASSERT_PDFTOTEXT=${ASSERT_PDFTOTEXT:-1}

export DEVICE APK PDF_LOCAL PDF_REMOTE_PATH TOKEN TOKEN_INPUT TOKEN_EXPECTED ASSERT_RENDER_OCR ASSERT_PDFTOTEXT

exec "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_pdf_form_fill_smoke.sh"
