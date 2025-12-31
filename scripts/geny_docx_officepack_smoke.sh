#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for Word (.docx/.doc) behavior when the Office Pack is installed.
#
# Ensures:
# - the main app detects the Office Pack
# - signature verification passes (same signing key)
# - the app binds to the Office Pack service and routes the conversion call
# - the Office Pack converts the docx fixture to a PDF and the main app opens it
# - the rendered output is non-blank (and optionally contains a known token via OCR)
#
# Usage:
#   DEVICE=localhost:<port> \
#   APK=/path/to/OpenDroidPDF-debug.apk \
#   APK_OFFICEPACK=/path/to/officepack-debug.apk \
#   ./scripts/geny_docx_officepack_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
APK_OFFICEPACK=${APK_OFFICEPACK:-/mnt/subtitled/opendroidpdf-android-build/officepack/outputs/apk/debug/officepack-debug.apk}
DOCX_LOCAL=${DOCX_LOCAL:-test_assets/word_with_text.docx}
# Keep smokes independent of MANAGE_EXTERNAL_STORAGE by using app-private storage.
DOCX_REMOTE=${DOCX_REMOTE:-/data/data/org.opendroidpdf/files/word_with_text.docx}
# OCR of rendered output is inherently fuzzy; keep the assertions based on stable
# words present in the fixture rather than an exact token match.
EXPECTED_TOKEN=${EXPECTED_TOKEN:-opendroidpdf-fixture}
ASSERT_OCR=${ASSERT_OCR:-1}

PKG=org.opendroidpdf
PKG_OFFICEPACK=org.opendroidpdf.officepack
ACT=.OpenDroidPDFActivity
DOCX_MIME="application/vnd.openxmlformats-officedocument.wordprocessingml.document"

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null
uia_disable_flaky_ime

if [[ "$ASSERT_OCR" == "1" ]]; then
  if ! command -v tesseract >/dev/null 2>&1; then
    echo "FAIL: tesseract not found (install tesseract-ocr, or run ASSERT_OCR=0)." >&2
    exit 2
  fi
fi

echo "[1/8] Install debug APKs (main + Office Pack)"
if ! adb -s "$DEVICE" install -r "$APK" >/dev/null; then
  echo "  main install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
  adb -s "$DEVICE" install "$APK" >/dev/null
fi

if ! adb -s "$DEVICE" install -r "$APK_OFFICEPACK" >/dev/null; then
  echo "  Office Pack install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG_OFFICEPACK" >/dev/null || true
  adb -s "$DEVICE" install "$APK_OFFICEPACK" >/dev/null
fi

echo "[2/8] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/8] Push docx fixture"
adb -s "$DEVICE" shell "run-as $PKG sh -lc 'mkdir -p files && cat > files/word_with_text.docx'" <"$DOCX_LOCAL"

echo "[4/8] Launch viewer with docx (expect conversion + open derived PDF)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$DOCX_REMOTE" -t "$DOCX_MIME" "$PKG/$ACT" >/dev/null

# Word import runs off-thread; allow a longer window for conversion.
UIA_DOC_VIEW_TIMEOUT_S="${UIA_DOC_VIEW_TIMEOUT_S:-25}"
if ! uia_assert_in_document_view; then
  OUT_PREFIX="${OUT_PREFIX:-tmp_geny_docx_officepack}"
  _uia_dump_to "${OUT_PREFIX}_ui.xml" || true
  adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_fail.png" || true
  echo "FAIL: expected to reach document view after Word import (Office Pack installed)" >&2
  exit 1
fi

echo "[5/8] Assert conversion call routed through Office Pack (logcat)"
if ! adb -s "$DEVICE" logcat -d | rg -q "OfficePackConverter.*convertWordToPdf called"; then
  OUT_PREFIX="${OUT_PREFIX:-tmp_geny_docx_officepack}"
  adb -s "$DEVICE" logcat -d | tail -n 200 >"${OUT_PREFIX}_logcat_tail.txt" || true
  echo "FAIL: expected Office Pack service log entry (conversion not routed?)" >&2
  exit 1
fi

echo "[6/8] Assert rendered output non-blank (screenshot span)"
OUT_PREFIX="${OUT_PREFIX:-tmp_geny_docx_officepack}"
SHOT="${SHOT:-${OUT_PREFIX}_view.png}"
adb -s "$DEVICE" exec-out screencap -p >"$SHOT" || true
python - "$SHOT" <<'PY'
import sys
from PIL import Image

path = sys.argv[1]
im = Image.open(path).convert("L")
data = im.tobytes()
span = max(data) - min(data)
print(f"{path}: {im.size[0]}x{im.size[1]} span={span}")
if span < 10:
    raise SystemExit(f"FAIL: screenshot looks blank/low-contrast (span={span})")
PY

if [[ "$ASSERT_OCR" == "1" ]]; then
  echo "[7/8] OCR content check (fixture words)"
  ocr="$(tesseract "$SHOT" stdout -l eng --psm 6 2>/dev/null | tr -d '\f' | tr -d '\r' | tr '\n' ' ')"
  canon_ocr="$(printf '%s' "$ocr" | tr -cd '[:alnum:]' | tr '[:lower:]' '[:upper:]')"

  # The fixture includes:
  #   "Hello OpenDroidPDF"
  #   "Keyword: opendroidpdf-fixture"
  # OCR often mangles "OpenDroidPDF" itself, but reliably sees HELLO/KEYWORD/FIXTURE.
  for word in HELLO KEYWORD FIXTURE; do
    if ! printf '%s\n' "$canon_ocr" | rg -q "$word"; then
      echo "FAIL: OCR did not find '$word' in screenshot (conversion/render regression?)" >&2
      echo "OCR output: $ocr" >&2
      exit 1
    fi
  done
fi

echo "[8/8] Assert no crash"
if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION" && adb -s "$DEVICE" logcat -d | rg -q "Process: ${PKG}"; then
  echo "FAIL: detected crash in logcat" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2
  exit 1
fi

echo "DOCX Office Pack smoke complete."
