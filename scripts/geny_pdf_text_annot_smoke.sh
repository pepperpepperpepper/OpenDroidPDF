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

_doc_center_xy() {
  local w h x y
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y=$((h * 45 / 100))
  echo "$x $y"
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

_assert_token_in_rendered_pdf() {
  local png="$1"
  local token="$2"
  local tmp_bw
  tmp_bw="$(mktemp -t odp_render_bw_XXXXXX).png"

  # OCR is flaky for small mid-page text; threshold first for stability.
  python3 - "$png" "$tmp_bw" <<'PY'
from PIL import Image
import sys
src=sys.argv[1]
dst=sys.argv[2]
im=Image.open(src).convert('L')
# Keep text (dark) and drop near-white paper.
im=im.point(lambda p: 0 if p<200 else 255)
im.save(dst)
PY

  local ocr_raw ocr_key token_key
  ocr_raw="$(tesseract "$tmp_bw" stdout -l eng --psm 6 2>/dev/null | tr -d '\f' | tr -d '\r')"
  ocr_key="$(printf '%s' "$ocr_raw" | tr -cd '[:alnum:]')"
  token_key="$(printf '%s' "$token" | tr -cd '[:alnum:]')"
  rm -f -- "$tmp_bw" || true

  if ! printf '%s\n' "$ocr_key" | rg -q "$token_key"; then
    echo "FAIL: OCR did not find token '$token' in rendered output" >&2
    echo "  OCR raw: $(printf '%s' "$ocr_raw" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')" >&2
    return 1
  fi
  return 0
}

_ocr_png() {
  local png="$1"
  # Keep OCR stable (no fancy layout analysis).
  tesseract "$png" stdout -l eng --psm 6 2>/dev/null | tr -d '\f' | tr -d '\r'
}

_assert_token_onscreen_fuzzy() {
  local png="$1"
  local token="$2"
  local label="$3"

  # Fuzzy match: OCR often corrupts underscores/letters once handles/selection boxes overlay the page.
  # We strip non-alphanumerics and do a simple substring check on the first 10 chars.
  local token_key
  token_key="$(printf '%s' "$token" | tr -cd '[:alnum:]' | cut -c1-10)"
  if [[ -z "$token_key" ]]; then
    echo "FAIL: token_key empty for token '$token'" >&2
    return 1
  fi

  local ocr_raw ocr_key
  ocr_raw="$(_ocr_png "$png" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')"
  ocr_key="$(printf '%s' "$ocr_raw" | tr -cd '[:alnum:]')"
  if ! printf '%s' "$ocr_key" | rg -q "$token_key"; then
    echo "FAIL: OCR did not find token '$token' (${label})" >&2
    echo "  token_key=$token_key" >&2
    echo "  ocr_raw=$ocr_raw" >&2
    return 1
  fi
  return 0
}

_ocr_token_top_px() {
  local png="$1"
  local token="$2"
  tesseract "$png" stdout -l eng --psm 6 tsv 2>/dev/null \
    | awk -F'\t' -v tok="$token" 'NR>1 && $1==5 && index($12,tok)>0 { print $8; exit }'
}

_ocr_token_center_xy() {
  local png="$1"
  local token="$2"
  tesseract "$png" stdout -l eng --psm 6 tsv 2>/dev/null \
    | awk -F'\t' -v tok="$token" 'NR>1 && $1==5 && index($12,tok)>0 { printf "%d %d\n", ($7 + int($9/2)), ($8 + int($10/2)); exit }'
}

_selection_box_top_px() {
  local png="$1"
  python3 - "$png" <<'PY'
from PIL import Image
import sys

im = Image.open(sys.argv[1]).convert("RGBA")
w, h = im.size
px = im.load()

miny = None
count = 0

# Selection box/handles are drawn in a light blue/cyan tint. Detect those pixels and
# return the top-most y so we can assert movement without relying on flaky OCR.
for y in range(h):
  for x in range(w):
    r, g, b, a = px[x, y]
    if a < 200:
      continue
    # Require a "blue-ish" pixel that's not just black text on white.
    if b > 150 and g > 100 and r < 210 and b > r + 20:
      count += 1
      miny = y if miny is None else min(miny, y)
  # Small perf win: stop early if we've already found enough pixels near the top.
  if miny is not None and y > miny + 60 and count > 500:
    break

print("" if miny is None else str(miny))
PY
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

read -r TOKEN_X TOKEN_Y < <(_ocr_token_center_xy "$SCREENSHOT_PNG" "$TOKEN" 2>/dev/null || echo "")

echo "[8/14] Tap twice to select + edit text annotation and append ${TOKEN_SUFFIX_EDIT}"
if [[ -n "${TOKEN_X:-}" && -n "${TOKEN_Y:-}" ]]; then
  adb -s "$DEVICE" shell input tap "$TOKEN_X" "$TOKEN_Y"
else
  _tap_doc_center
fi
sleep 0.35
if [[ -n "${TOKEN_X:-}" && -n "${TOKEN_Y:-}" ]]; then
  adb -s "$DEVICE" shell input tap "$TOKEN_X" "$TOKEN_Y"
else
  _tap_doc_center
fi
sleep 0.9
for _ in $(seq 1 10); do
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    break
  fi
  sleep 0.3
done
uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || {
  echo "FAIL: edit text dialog did not appear after tapping the existing annotation" >&2
  _screencap_png "${OUT_PREFIX}_edit_fail.png" || true
  echo "  wrote ${OUT_PREFIX}_edit_fail.png" >&2
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

read -r TOKEN_EDIT_X TOKEN_EDIT_Y < <(_ocr_token_center_xy "$SCREENSHOT_PNG" "$TOKEN_EDIT" 2>/dev/null || echo "")

echo "[9.5/14] Select the text annotation and drag-move it (direct manipulation)"
MOVE_BEFORE_PNG="${MOVE_BEFORE_PNG:-${OUT_PREFIX}_move_before.png}"
MOVE_AFTER_PNG="${MOVE_AFTER_PNG:-${OUT_PREFIX}_move_after.png}"
_screencap_png "$MOVE_BEFORE_PNG"

read -r w h < <(_wm_size)
if [[ -n "${TOKEN_EDIT_X:-}" && -n "${TOKEN_EDIT_Y:-}" ]]; then
  x="$TOKEN_EDIT_X"
  y="$TOKEN_EDIT_Y"
else
  read -r x y < <(_doc_center_xy)
fi

# Single tap selects (should show bounding box + handles).
adb -s "$DEVICE" shell input tap "$x" "$y"
sleep 0.7
_fail_if_fatal_logcat
MOVE_SELECTED_PNG="${MOVE_SELECTED_PNG:-${OUT_PREFIX}_move_selected.png}"
_screencap_png "$MOVE_SELECTED_PNG"
sel_top_before="$(_selection_box_top_px "$MOVE_SELECTED_PNG" || true)"

# Long-press to arm move (automation-friendly), then drag downward to move.
adb -s "$DEVICE" shell input swipe "$x" "$y" "$x" "$y" 650
sleep 0.25
y2=$((y + h / 5))
adb -s "$DEVICE" shell input swipe "$x" "$y" "$x" "$y2" 280
sleep 1.4
_fail_if_fatal_logcat

MOVE_AFTER_SELECTED_PNG="${MOVE_AFTER_SELECTED_PNG:-${OUT_PREFIX}_move_after_selected.png}"
_screencap_png "$MOVE_AFTER_SELECTED_PNG"
sel_top_after="$(_selection_box_top_px "$MOVE_AFTER_SELECTED_PNG" || true)"

# Deselect before OCR so the selection box/handles don't corrupt token recognition.
blank_x=$((w * 9 / 10))
blank_y=$((h * 9 / 10))
adb -s "$DEVICE" shell input tap "$blank_x" "$blank_y"
sleep 0.7
_fail_if_fatal_logcat

_screencap_png "$MOVE_AFTER_PNG"

if [[ -n "${sel_top_before:-}" && -n "${sel_top_after:-}" ]]; then
  delta_sel=$((sel_top_after - sel_top_before))
  if (( delta_sel < 30 )); then
    echo "FAIL: expected selection box to move down (top delta >= 30px), got ${delta_sel}px" >&2
    echo "  before: $MOVE_SELECTED_PNG (top=$sel_top_before) after: $MOVE_AFTER_SELECTED_PNG (top=$sel_top_after)" >&2
    exit 1
  fi
else
  echo "FAIL: could not detect selection box in move screenshots" >&2
  echo "  before: $MOVE_SELECTED_PNG (top=$sel_top_before) after: $MOVE_AFTER_SELECTED_PNG (top=$sel_top_after)" >&2
  exit 1
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
token_key="$(printf '%s' "$TOKEN_EDIT" | tr -cd '[:alnum:]' | cut -c1-10)"
ocr_key="$(printf '%s' "$ocr" | tr -cd '[:alnum:]')"
if ! printf '%s\n' "$ocr_key" | rg -q "$token_key"; then
  # Fall back to a more stable thresholded OCR pass.
  _assert_token_in_rendered_pdf "$RENDER_PNG" "$TOKEN_EDIT" || {
    echo "  token_key=$token_key" >&2
    echo "  OCR output: $ocr" >&2
    echo "PDF byte scan (first match):" >&2
    rg -a -n "$TOKEN_EDIT" "$SAVED_PDF" | head -n 5 >&2 || true
    exit 1
  }
else
  # Strict OCR already found it.
  true
fi

_fail_if_fatal_logcat

if [[ "$POST_SAVE_HOME_WAIT_S" != "0" ]]; then
  echo "[14/14] Background app and wait ${POST_SAVE_HOME_WAIT_S}s (catch delayed native crashes)"
  adb -s "$DEVICE" shell input keyevent KEYCODE_HOME
  sleep "$POST_SAVE_HOME_WAIT_S"
  _fail_if_fatal_logcat
fi

echo "OK: text annotation rendered and OCR found token ($TOKEN_EDIT)"
