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
TOKEN_SUFFIX_EDIT=${TOKEN_SUFFIX_EDIT:-_EDIT}
TOKEN_EDIT=${TOKEN_EDIT:-${TOKEN}${TOKEN_SUFFIX_EDIT}}
ASSERT_ONSCREEN_OCR=${ASSERT_ONSCREEN_OCR:-1}
POST_SAVE_HOME_WAIT_S=${POST_SAVE_HOME_WAIT_S:-90}
POST_EDIT_IDLE_TAP_S=${POST_EDIT_IDLE_TAP_S:-30}

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

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p > "$out_png"
}

_fail_if_fatal_logcat() {
  if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION|Process ${PKG} \\(pid [0-9]+\\) has died"; then
    echo "FAIL: detected crash in logcat" >&2
    adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|AndroidRuntime|${PKG}" | tail -n 260 >&2 || true
    return 1
  fi
  return 0
}

_wait_for_token_onscreen_ocr() {
  local token="$1"
  local timeout_s="${2:-12}"
  local start now
  start="$(date +%s)"
  while true; do
    _screencap_png "$SCREENSHOT_PNG"
    ocr_ui="$(_ocr_png "$SCREENSHOT_PNG" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')"
    if printf '%s\n' "$ocr_ui" | rg -q "$token"; then
      return 0
    fi
    now="$(date +%s)"
    if (( now - start >= timeout_s )); then
      echo "FAIL: in-app screenshot OCR did not find token '$token' within ${timeout_s}s" >&2
      echo "OCR output: $ocr_ui" >&2
      return 1
    fi
    sleep 1.0
  done
}

echo "[1/14] Install APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/14] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/14] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null

echo "[4/14] Launch app and open the PDF via DocumentsUI (content:// URI)"
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

echo "[5/14] Enter add-text mode"
uia_tap_any_res_id "org.opendroidpdf:id/menu_add_text_annot" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  uia_tap_text_contains "Add text" || {
    echo "FAIL: add-text action not found" >&2
    exit 1
  }
}
sleep 0.6

echo "[6/14] Tap page and enter token"
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

uia_assert_in_document_view
_fail_if_fatal_logcat

OUT_PREFIX="${OUT_PREFIX:-tmp_geny_pdf_text_annot}"
SCREENSHOT_PNG="${SCREENSHOT_PNG:-${OUT_PREFIX}_ui.png}"

echo "[7/14] Assert in-app text is visible (screenshot + OCR)"
if [[ "$ASSERT_ONSCREEN_OCR" == "1" ]]; then
  _wait_for_token_onscreen_ocr "$TOKEN" "${UI_OCR_TIMEOUT_S:-12}" || exit 1
  echo "  wrote $SCREENSHOT_PNG"
fi

echo "[8/14] Tap twice to select + edit text annotation and append ${TOKEN_SUFFIX_EDIT}"
_tap_doc_center
sleep 0.35
_tap_doc_center
sleep 0.9
for _ in $(seq 1 10); do
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    break
  fi
  sleep 0.3
done
uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || {
  echo "FAIL: edit text dialog did not appear after tapping the existing annotation" >&2
  adb -s "$DEVICE" logcat -d | tail -n 160 >&2
  exit 1
}
adb -s "$DEVICE" shell input text "$TOKEN_SUFFIX_EDIT"
sleep 0.4
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
  echo "FAIL: could not confirm edited text annotation dialog" >&2
  exit 1
}
sleep 2.0
uia_assert_in_document_view
_fail_if_fatal_logcat

echo "[9/14] Assert edited text is visible (screenshot + OCR)"
if [[ "$ASSERT_ONSCREEN_OCR" == "1" ]]; then
  _wait_for_token_onscreen_ocr "$TOKEN_EDIT" "${UI_OCR_TIMEOUT_S:-12}" || exit 1
  echo "  wrote $SCREENSHOT_PNG"
fi

echo "[10/14] Exit edit mode (show main menu)"
uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || true
sleep 0.8

if [[ "$POST_EDIT_IDLE_TAP_S" != "0" ]]; then
  echo "[10.5/14] Wait ${POST_EDIT_IDLE_TAP_S}s, then tap-to-edit again (catch tap-after-idle crashes)"
  sleep "$POST_EDIT_IDLE_TAP_S"
  _fail_if_fatal_logcat

  _tap_doc_center
  sleep 0.35
  _tap_doc_center
  sleep 0.9
  for _ in $(seq 1 10); do
    if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      break
    fi
    sleep 0.3
  done
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    uia_tap_any_res_id "android:id/button3" "com.android.internal:id/button3" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
    sleep 0.8
    # Canceling the dialog leaves us in Edit mode; return to main so Save is accessible.
    uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || true
    sleep 0.6
  else
    echo "WARN: edit dialog did not appear after tap-after-idle; continuing" >&2
  fi
  _fail_if_fatal_logcat
fi

echo "[11/14] Save in-place"
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

echo "[12/14] Pull saved PDF back to host"
SAVED_PDF="${SAVED_PDF:-${OUT_PREFIX}.pdf}"
adb -s "$DEVICE" pull "$PDF_REMOTE_PATH" "$SAVED_PDF" >/dev/null
echo "  wrote $SAVED_PDF"

echo "[13/14] Render first page and OCR for token"
RENDER_PNG="${RENDER_PNG:-${OUT_PREFIX}_render.png}"
_render_pdf_to_png "$SAVED_PDF" "$RENDER_PNG"
echo "  wrote $RENDER_PNG"

ocr="$(_ocr_png "$RENDER_PNG" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')"
if ! printf '%s\n' "$ocr" | rg -q "$TOKEN_EDIT"; then
  echo "FAIL: OCR did not find token '$TOKEN_EDIT' in rendered output" >&2
  echo "OCR output: $ocr" >&2
  echo "PDF byte scan (first match):" >&2
  rg -a -n "$TOKEN_EDIT" "$SAVED_PDF" | head -n 5 >&2 || true
  exit 1
fi

_fail_if_fatal_logcat

if [[ "$POST_SAVE_HOME_WAIT_S" != "0" ]]; then
  echo "[14/14] Background app and wait ${POST_SAVE_HOME_WAIT_S}s (catch delayed native crashes)"
  adb -s "$DEVICE" shell input keyevent KEYCODE_HOME
  sleep "$POST_SAVE_HOME_WAIT_S"
  _fail_if_fatal_logcat
fi

echo "OK: text annotation rendered and OCR found token ($TOKEN_EDIT)"
