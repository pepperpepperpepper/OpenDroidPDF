#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "Export/share includes latest text annotation edits":
# - Open a PDF from app-private storage (stable file:// open)
# - Add a FreeText text annotation
# - Immediately export ("Share a copy")
# - Pull the exported PDF from cache/tmpfiles
# - OCR the exported PDF render to assert the text is present
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_export_latest_text_annot_smoke.sh
#
# Requirements (host):
#   - pdftoppm (poppler)
#   - tesseract

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}"
PDF_LOCAL="${PDF_LOCAL:-test_assets/pdf_with_text.pdf}"
TOKEN="${TOKEN:-ODP_EXPORT_ANNOT_TOKEN_ABC123}"

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

OUTDIR="${OUTDIR:-.}"
mkdir -p "$OUTDIR"
OUT_PREFIX="${OUT_PREFIX:-${OUTDIR}/tmp_geny_export_latest_text_annot}"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/geny_uia.sh"

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
  # Avoid the bottom nav bar + scrubber area by tapping slightly above center.
  y=$((h * 35 / 100))
  adb -s "$DEVICE" shell input tap "$x" "$y"
}

_list_tmp_pdfs() {
  # Exports may land either directly under cache/tmpfiles (sidecar/flatten exports) or
  # under cache/tmpfiles/<n>/... (core.export() tmp rotation).
  adb -s "$DEVICE" shell run-as "$PKG" sh -c 'find cache/tmpfiles -maxdepth 2 -type f -name "*.pdf" 2>/dev/null' \
    | tr -d '\r' | sort || true
}

_newest_tmp_pdf() {
  adb -s "$DEVICE" shell run-as "$PKG" sh -c 'ls -1t $(find cache/tmpfiles -maxdepth 2 -type f -name "*.pdf" 2>/dev/null) 2>/dev/null | head -n 1' \
    | tr -d '\r' || true
}

_pull_app_file() {
  local remote_rel="$1"
  local out_path="$2"
  adb -s "$DEVICE" exec-out run-as "$PKG" cat "$remote_rel" >"$out_path"
}

echo "[1/7] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/7] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/7] Stage fixture PDF in app-private storage"
APP_PRIVATE_REL_PATH="files/odp_export_latest_text_annot_smoke.pdf"
adb -s "$DEVICE" shell "run-as $PKG sh -lc 'mkdir -p \"$(dirname "$APP_PRIVATE_REL_PATH")\" && cat > \"$APP_PRIVATE_REL_PATH\"'" <"$PDF_LOCAL"

echo "[4/7] Launch viewer with app-private file:// URI"
PDF_APP_PRIVATE="/data/data/${PKG}/${APP_PRIVATE_REL_PATH}"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W \
  -a android.intent.action.VIEW \
  -d "file://$PDF_APP_PRIVATE" \
  -t application/pdf \
  -n "$PKG/$ACT" >/dev/null
sleep 2.0
uia_assert_in_document_view

echo "[5/7] Add text annotation (token=$TOKEN)"
uia_enter_add_text_mode || { echo "FAIL: could not enter add-text mode" >&2; exit 1; }
sleep 0.6
_tap_doc_center
sleep 0.8

for _ in $(seq 1 20); do
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    break
  fi
  sleep 0.3
done
uia_has_res_id "org.opendroidpdf:id/dialog_text_input" || {
  echo "FAIL: text input UI did not appear" >&2
  adb -s "$DEVICE" logcat -d | tail -n 160 >&2
  exit 1
}

if uia_has_res_id "android:id/button1" "com.android.internal:id/button1"; then
  uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || true
fi
adb -s "$DEVICE" shell input text "$TOKEN"
sleep 0.4

if uia_has_res_id "android:id/button1" "com.android.internal:id/button1"; then
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
    echo "FAIL: could not confirm text annotation dialog" >&2
    exit 1
  }
else
  # Inline editor: commit via focus loss (tap outside).
  read -r w h < <(_wm_size)
  adb -s "$DEVICE" shell input tap $((w / 2)) $((h / 5))
fi
sleep 0.2

echo "[6/7] Share a copy and pull exported PDF"
before="$(mktemp -t geny_export_latest_text_before_XXXXXX.txt)"
after="$(mktemp -t geny_export_latest_text_after_XXXXXX.txt)"
exported_local="${EXPORT_PDF:-${OUT_PREFIX}_export.pdf}"
exported_render_prefix="${EXPORT_RENDER_PREFIX:-${OUT_PREFIX}_export_render}"

cleanup() {
  rm -f -- "$before" "$after" 2>/dev/null || true
}
trap cleanup EXIT

_list_tmp_pdfs >"$before"

uia_open_export_sheet || { echo "FAIL: could not open Export sheet" >&2; exit 1; }
uia_tap_any_res_id "org.opendroidpdf:id/export_action_share_copy" || uia_tap_text_contains "Share a copy" || {
  echo "FAIL: could not trigger Share a copy from Export sheet" >&2
  exit 1
}

# Chooser may appear; back out to keep the run stable.
sleep 3
adb -s "$DEVICE" shell input keyevent 4 >/dev/null || true

new_file=""
for _ in $(seq 1 60); do
  _list_tmp_pdfs >"$after"
  new_file="$(comm -13 "$before" "$after" | tail -n 1 || true)"
  if [[ -n "$new_file" ]]; then
    break
  fi
  sleep 0.5
done
if [[ -z "$new_file" ]]; then
  new_file="$(_newest_tmp_pdf)"
fi
if [[ -z "$new_file" ]]; then
  echo "FAIL: no exported PDF found in cache/tmpfiles" >&2
  exit 1
fi

echo "  exported: $new_file"
_pull_app_file "$new_file" "$exported_local"
if [[ ! -s "$exported_local" ]]; then
  echo "FAIL: exported file empty: $exported_local" >&2
  exit 1
fi

echo "[6.5/7] OCR exported PDF render and assert token is present"
pdftoppm -png -f 1 -singlefile "$exported_local" "$exported_render_prefix" >/dev/null 2>&1 || {
  echo "FAIL: pdftoppm failed to render exported PDF" >&2
  exit 1
}
exported_png="${exported_render_prefix}.png"
if [[ ! -f "$exported_png" ]]; then
  echo "FAIL: expected rendered PNG at $exported_png" >&2
  exit 1
fi

ocr_out="$(tesseract "$exported_png" stdout -l eng --psm 6 2>/dev/null || true)"
if ! printf '%s\n' "$ocr_out" | rg -q -i -F "$TOKEN"; then
  echo "FAIL: OCR did not find token '$TOKEN' in exported PDF render" >&2
  echo "OCR output (tail):" >&2
  printf '%s\n' "$ocr_out" | tail -n 40 >&2 || true
  echo "Logcat tail:" >&2
  adb -s "$DEVICE" logcat -d | tail -n 120 >&2
  exit 1
fi

echo "[7/7] Done"
echo "OK: export includes latest text annotation (token found via OCR)"
