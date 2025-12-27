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
TOKEN=${TOKEN:-ODPTEXTSMOKE}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

if ! command -v pdftoppm >/dev/null 2>&1; then
  echo "FAIL: pdftoppm not found (install poppler)." >&2
  exit 2
fi
if ! command -v tesseract >/dev/null 2>&1; then
  echo "FAIL: tesseract not found (install tesseract-ocr)." >&2
  exit 2
fi

_wm_size() {
  local line
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

_tap_doc_center() {
  local w h x y
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y=$((h * 45 / 100))
  adb -s "$DEVICE" shell input tap "$x" "$y"
}

_render_pdf_to_png() {
  local pdf="$1"
  local out_png="$2"
  local tmpdir prefix
  tmpdir="$(mktemp -d -t odp_pdf_render_XXXXXX)"
  prefix="$tmpdir/out"
  pdftoppm -f 1 -l 1 -r 300 -singlefile -png "$pdf" "$prefix" >/dev/null
  mv -f -- "${prefix}.png" "$out_png"
  rm -rf -- "$tmpdir"
}

_ocr_png() {
  local png="$1"
  # Keep OCR stable (no fancy layout analysis).
  tesseract "$png" stdout -l eng --psm 6 2>/dev/null | tr -d '\f' | tr -d '\r'
}

echo "[1/10] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/10] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/10] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null

echo "[4/10] Launch app and open the PDF via DocumentsUI (content:// URI)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT" >/dev/null
sleep 1.2

uia_tap_any_res_id "org.opendroidpdf:id/entry_screen_open_document_card_view" || {
  echo "FAIL: could not tap entry-screen open-document card" >&2
  exit 1
}
sleep 1.5

fname="$(basename "$PDF_REMOTE_PATH")"

uia_tap_desc "Show roots" || {
  echo "FAIL: could not open DocumentsUI roots drawer" >&2
  exit 1
}
sleep 0.7
uia_tap_text_contains "Downloads" || {
  echo "FAIL: could not switch DocumentsUI to Downloads root" >&2
  exit 1
}
sleep 0.9

uia_tap_any_res_id "com.android.documentsui:id/option_menu_search" || uia_tap_desc "Search" || {
  echo "FAIL: could not open DocumentsUI search" >&2
  exit 1
}
sleep 0.6
adb -s "$DEVICE" shell input text "$fname"
sleep 1.2
uia_tap_text_contains "$fname" || {
  echo "FAIL: could not select $fname in DocumentsUI search results" >&2
  echo "Logcat tail:" >&2
  adb -s "$DEVICE" logcat -d | tail -n 120 >&2
  exit 1
}

uia_assert_in_document_view

echo "[5/10] Enter add-text mode"
uia_tap_any_res_id "org.opendroidpdf:id/menu_add_text_annot" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  uia_tap_text_contains "Add text" || {
    echo "FAIL: add-text action not found" >&2
    exit 1
  }
}
sleep 0.6

echo "[6/10] Tap page and enter token"
_tap_doc_center
sleep 0.8
for _ in $(seq 1 10); do
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    break
  fi
  sleep 0.3
done
uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || {
  echo "FAIL: text input dialog did not appear" >&2
  adb -s "$DEVICE" logcat -d | tail -n 160 >&2
  exit 1
}
adb -s "$DEVICE" shell input text "$TOKEN"
sleep 0.4
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
  echo "FAIL: could not confirm text annotation dialog" >&2
  exit 1
}
sleep 2.0

echo "[7/10] Save in-place"
if uia_tap_desc "More options"; then
  sleep 0.4
fi
uia_tap_any_res_id "org.opendroidpdf:id/menu_save" || uia_tap_text_contains "Save" || {
  echo "FAIL: Save menu item not found" >&2
  exit 1
}
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 4

echo "[8/10] Pull saved PDF back to host"
OUT_PREFIX="${OUT_PREFIX:-tmp_geny_pdf_text_annot}"
SAVED_PDF="${SAVED_PDF:-${OUT_PREFIX}.pdf}"
adb -s "$DEVICE" pull "$PDF_REMOTE_PATH" "$SAVED_PDF" >/dev/null
echo "  wrote $SAVED_PDF"

echo "[9/10] Render first page and OCR for token"
RENDER_PNG="${RENDER_PNG:-${OUT_PREFIX}_render.png}"
_render_pdf_to_png "$SAVED_PDF" "$RENDER_PNG"
echo "  wrote $RENDER_PNG"

ocr="$(_ocr_png "$RENDER_PNG" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')"
if ! printf '%s\n' "$ocr" | rg -q "$TOKEN"; then
  echo "FAIL: OCR did not find token '$TOKEN' in rendered output" >&2
  echo "OCR output: $ocr" >&2
  echo "PDF byte scan (first match):" >&2
  rg -a -n "$TOKEN" "$SAVED_PDF" | head -n 5 >&2 || true
  exit 1
fi

echo "[10/10] OK: text annotation rendered and OCR found token ($TOKEN)"

