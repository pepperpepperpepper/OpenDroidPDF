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
TOKEN=${TOKEN:-ODPTEXTSMOKE%sWRAP%sTEST}
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
    | awk -F'\t' -v tok="$token" 'NR>1 && $1==5 && index($12,tok)>0 { printf "%d %d\n", ($7 + int($9/2)), ($8 + int($10/2)); found=1; exit } END { exit found?0:1 }'
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

_selection_box_bbox_px() {
  local png="$1"
  python3 - "$png" <<'PY'
from PIL import Image
import sys

im = Image.open(sys.argv[1]).convert("RGBA")
w, h = im.size
px = im.load()

minx = None
miny = None
maxx = None
maxy = None
count = 0

for y in range(h):
  for x in range(w):
    r, g, b, a = px[x, y]
    if a < 200:
      continue
    if b > 150 and g > 100 and r < 210 and b > r + 20:
      count += 1
      minx = x if minx is None else min(minx, x)
      miny = y if miny is None else min(miny, y)
      maxx = x if maxx is None else max(maxx, x)
      maxy = y if maxy is None else max(maxy, y)

if minx is None:
  print("")
else:
  print(f"{minx} {miny} {maxx} {maxy}")
PY
}

_dark_text_height_in_bbox_px() {
  local png="$1"
  local x0="$2"
  local y0="$3"
  local x1="$4"
  local y1="$5"
  python3 - "$png" "$x0" "$y0" "$x1" "$y1" <<'PY'
from PIL import Image
import sys

im = Image.open(sys.argv[1]).convert("RGBA")
w, h = im.size
x0 = max(0, min(w - 1, int(float(sys.argv[2]))))
y0 = max(0, min(h - 1, int(float(sys.argv[3]))))
x1 = max(0, min(w, int(float(sys.argv[4]))))
y1 = max(0, min(h, int(float(sys.argv[5]))))

# Drop the selection border/handles by shrinking the crop a bit.
pad = 10
cx0 = max(0, min(w - 1, x0 + pad))
cy0 = max(0, min(h - 1, y0 + pad))
cx1 = max(cx0 + 1, min(w, x1 - pad))
cy1 = max(cy0 + 1, min(h, y1 - pad))

crop = im.crop((cx0, cy0, cx1, cy1))
px = crop.load()
cw, ch = crop.size

miny = None
maxy = None

# Heuristic: treat near-black pixels as text.
for y in range(ch):
  for x in range(cw):
    r, g, b, a = px[x, y]
    if a < 200:
      continue
    if r < 80 and g < 80 and b < 80:
      miny = y if miny is None else min(miny, y)
      maxy = y if maxy is None else max(maxy, y)

if miny is None or maxy is None:
  print("")
else:
  print(str(maxy - miny))
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
  local token_key token_head suffix_key require_suffix

  # OCR is flaky for underscores/spaces; do a fuzzy match on stripped alphanumerics.
  token_key="$(printf '%s' "$token" | tr -cd '[:alnum:]')"
  token_head="$(printf '%s' "$token_key" | cut -c1-10)"
  suffix_key="$(printf '%s' "$TOKEN_SUFFIX_EDIT" | tr -cd '[:alnum:]')"
  require_suffix=0
  if [[ -n "$suffix_key" && "$token_key" == *"$suffix_key" ]]; then
    require_suffix=1
  fi
  start="$(date +%s)"
  while true; do
    _screencap_png "$SCREENSHOT_PNG"
    ocr_ui="$(_ocr_png "$SCREENSHOT_PNG" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')"
    ocr_key="$(printf '%s' "$ocr_ui" | tr -cd '[:alnum:]')"
    if [[ -n "$token_head" && "$ocr_key" == *"$token_head"* ]]; then
      if (( require_suffix == 0 )) || [[ -n "$suffix_key" && "$ocr_key" == *"$suffix_key"* ]]; then
      return 0
      fi
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

uia_tap_docsui_roots_drawer || {
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
adb -s "$DEVICE" shell input text "$TOKEN_INPUT"
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
  _wait_for_token_onscreen_ocr "$TOKEN_EXPECTED" "${UI_OCR_TIMEOUT_S:-12}" || exit 1
  echo "  wrote $SCREENSHOT_PNG"
fi

read -r TOKEN_X TOKEN_Y < <(_ocr_token_center_xy "$SCREENSHOT_PNG" "$TOKEN_SEARCH" 2>/dev/null || echo "")

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
  # Deselect before OCR so the selection box/handles don't corrupt recognition.
  read -r w h < <(_wm_size)
  blank_x=$((w * 9 / 10))
  blank_y=$((h * 9 / 10))
  adb -s "$DEVICE" shell input tap "$blank_x" "$blank_y"
  sleep 0.7
  _fail_if_fatal_logcat
  _wait_for_token_onscreen_ocr "$TOKEN_EDIT_EXPECTED" "${UI_OCR_TIMEOUT_S:-12}" || exit 1
  echo "  wrote $SCREENSHOT_PNG"
fi

read -r TOKEN_EDIT_X TOKEN_EDIT_Y < <(_ocr_token_center_xy "$SCREENSHOT_PNG" "$TOKEN_EDIT_SEARCH" 2>/dev/null || echo "")

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

# Drag inside the selection box downward to move (Acrobat-style).
read -r bbox_x0 bbox_y0 bbox_x1 bbox_y1 < <(_selection_box_bbox_px "$MOVE_SELECTED_PNG" || echo "")
if [[ -z "${bbox_x0:-}" || -z "${bbox_y0:-}" || -z "${bbox_x1:-}" || -z "${bbox_y1:-}" ]]; then
  echo "FAIL: could not detect selection bbox for move step" >&2
  echo "  screenshot: $MOVE_SELECTED_PNG" >&2
  exit 1
fi

move_x=$(((bbox_x0 + bbox_x1) / 2))
move_y=$(((bbox_y0 + bbox_y1) / 2))
y2=$((y + h / 5))
move_y2=$((move_y + h / 5))
if (( move_y2 > h - 8 )); then move_y2=$((h - 8)); fi
# Use a longer swipe duration and capture mid-gesture to ensure the text preview
# follows the selection box during drag (regression: text "disappears" until drop).
MOVE_MID_PNG="${MOVE_MID_PNG:-${OUT_PREFIX}_move_mid.png}"
adb -s "$DEVICE" shell input swipe "$move_x" "$move_y" "$move_x" "$move_y2" 1200 &
swipe_pid=$!
sleep 0.55
_screencap_png "$MOVE_MID_PNG"
wait "$swipe_pid" || true
sleep 0.6
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

echo "[9.6/14] Assert text stays visible during drag (mid-gesture screenshot)"
read -r mid_x0 mid_y0 mid_x1 mid_y1 < <(_selection_box_bbox_px "$MOVE_MID_PNG" || echo "")
if [[ -z "${mid_x0:-}" || -z "${mid_y0:-}" || -z "${mid_x1:-}" || -z "${mid_y1:-}" ]]; then
  echo "FAIL: could not detect selection bbox in mid-drag screenshot" >&2
  echo "  screenshot: $MOVE_MID_PNG" >&2
  exit 1
fi
mid_text_h="$(_dark_text_height_in_bbox_px "$MOVE_MID_PNG" "$mid_x0" "$mid_y0" "$mid_x1" "$mid_y1" || true)"
if [[ -z "${mid_text_h:-}" || "$mid_text_h" -lt 6 ]]; then
  echo "FAIL: expected dark text inside selection box during drag; got height=${mid_text_h:-<none>}px" >&2
  echo "  screenshot: $MOVE_MID_PNG bbox=($mid_x0,$mid_y0 $mid_x1,$mid_y1)" >&2
  exit 1
fi

echo "[9.7/14] Resize the text annotation via bottom-right handle (assert bbox grows)"
# Re-select to show handles (we deselected for OCR stability above).
adb -s "$DEVICE" shell input tap "$x" "$y2"
sleep 0.7
_fail_if_fatal_logcat

# Corner resize handles are hidden by default; long-press the selected box to enable resize mode.
adb -s "$DEVICE" shell input swipe "$x" "$y2" "$x" "$y2" 800
sleep 0.7
_fail_if_fatal_logcat

RESIZE_SELECTED_PNG="${RESIZE_SELECTED_PNG:-${OUT_PREFIX}_resize_selected.png}"
_screencap_png "$RESIZE_SELECTED_PNG"
read -r bbox_x0 bbox_y0 bbox_x1 bbox_y1 < <(_selection_box_bbox_px "$RESIZE_SELECTED_PNG" || echo "")
if [[ -z "${bbox_x0:-}" || -z "${bbox_y0:-}" || -z "${bbox_x1:-}" || -z "${bbox_y1:-}" ]]; then
  echo "FAIL: could not detect selection bbox for resize step" >&2
  echo "  screenshot: $RESIZE_SELECTED_PNG" >&2
  exit 1
fi

start_rx=$bbox_x1
start_ry=$bbox_y1
end_rx=$((start_rx + w / 10))
end_ry=$((start_ry + h / 12))
if (( end_rx > w - 8 )); then end_rx=$((w - 8)); fi
if (( end_ry > h - 8 )); then end_ry=$((h - 8)); fi

# Drag the bottom-right handle outward to resize.
adb -s "$DEVICE" shell input swipe "$start_rx" "$start_ry" "$end_rx" "$end_ry" 320
sleep 1.2
_fail_if_fatal_logcat

RESIZE_AFTER_PNG="${RESIZE_AFTER_PNG:-${OUT_PREFIX}_resize_after.png}"
_screencap_png "$RESIZE_AFTER_PNG"
read -r bbox2_x0 bbox2_y0 bbox2_x1 bbox2_y1 < <(_selection_box_bbox_px "$RESIZE_AFTER_PNG" || echo "")
if [[ -z "${bbox2_x0:-}" || -z "${bbox2_y0:-}" || -z "${bbox2_x1:-}" || -z "${bbox2_y1:-}" ]]; then
  echo "FAIL: could not detect selection bbox after resize" >&2
  echo "  screenshot: $RESIZE_AFTER_PNG" >&2
  exit 1
fi

before_w=$((bbox_x1 - bbox_x0))
before_h=$((bbox_y1 - bbox_y0))
after_w=$((bbox2_x1 - bbox2_x0))
after_h=$((bbox2_y1 - bbox2_y0))
if (( after_w - before_w < 20 && after_h - before_h < 20 )); then
  echo "FAIL: expected selection bbox to grow after resize (delta >= 20px), got dw=$((after_w-before_w)) dh=$((after_h-before_h))" >&2
  echo "  before: $RESIZE_SELECTED_PNG (w=$before_w h=$before_h) after: $RESIZE_AFTER_PNG (w=$after_w h=$after_h)" >&2
  exit 1
fi

if [[ "$ASSERT_TEXT_WRAP_ON_RESIZE" == "1" ]]; then
  echo "[9.75/14] Resize-wrap regression (shrink width and assert multi-line text)"
  if [[ "$TOKEN_EDIT_EXPECTED" != *" "* ]]; then
    echo "WARN: skipping wrap assertion because token has no spaces (word-wrap won't trigger)" >&2
  else
  before_text_h="$(_dark_text_height_in_bbox_px "$RESIZE_AFTER_PNG" "$bbox2_x0" "$bbox2_y0" "$bbox2_x1" "$bbox2_y1" || true)"

  # Drag the bottom-right handle left to reduce width (keep height roughly stable).
  shrink_start_x=$bbox2_x1
  shrink_start_y=$bbox2_y1
  shrink_end_x=$((bbox2_x1 - w / 4))
  shrink_end_y=$bbox2_y1
  if (( shrink_end_x < bbox2_x0 + 40 )); then shrink_end_x=$((bbox2_x0 + 40)); fi
  if (( shrink_end_x < 8 )); then shrink_end_x=8; fi

  adb -s "$DEVICE" shell input swipe "$shrink_start_x" "$shrink_start_y" "$shrink_end_x" "$shrink_end_y" 360
  sleep 1.2
  _fail_if_fatal_logcat

  WRAP_AFTER_PNG="${WRAP_AFTER_PNG:-${OUT_PREFIX}_wrap_after.png}"
  _screencap_png "$WRAP_AFTER_PNG"

  read -r bbox3_x0 bbox3_y0 bbox3_x1 bbox3_y1 < <(_selection_box_bbox_px "$WRAP_AFTER_PNG" || echo "")
  if [[ -n "${bbox3_x0:-}" && -n "${bbox3_y0:-}" && -n "${bbox3_x1:-}" && -n "${bbox3_y1:-}" ]]; then
    after_text_h="$(_dark_text_height_in_bbox_px "$WRAP_AFTER_PNG" "$bbox3_x0" "$bbox3_y0" "$bbox3_x1" "$bbox3_y1" || true)"
  if [[ -n "${before_text_h:-}" && -n "${after_text_h:-}" ]]; then
      if (( after_text_h - before_text_h < 14 )); then
        echo "FAIL: expected wrapped text to occupy more vertical space after shrinking width (delta >= 14px), got before_h=${before_text_h}px after_h=${after_text_h}px" >&2
        echo "  before: $RESIZE_AFTER_PNG after: $WRAP_AFTER_PNG" >&2
        exit 1
      fi
    else
      echo "WARN: could not detect dark text height for wrap assertion (before=$before_text_h after=$after_text_h)" >&2
    fi
  else
    echo "WARN: could not detect selection bbox after wrap-resize step; skipping wrap assertion" >&2
  fi
  fi
fi

echo "[9.77/14] Fit-to-text action (style dialog) and assert bbox shrinks"
FIT_BEFORE_PNG="${FIT_BEFORE_PNG:-${OUT_PREFIX}_fit_before.png}"
FIT_AFTER_PNG="${FIT_AFTER_PNG:-${OUT_PREFIX}_fit_after.png}"
_screencap_png "$FIT_BEFORE_PNG"
read -r fit_x0 fit_y0 fit_x1 fit_y1 < <(_selection_box_bbox_px "$FIT_BEFORE_PNG" || echo "")
if [[ -z "${fit_x0:-}" || -z "${fit_y0:-}" || -z "${fit_x1:-}" || -z "${fit_y1:-}" ]]; then
  echo "FAIL: could not detect selection bbox before fit-to-text" >&2
  echo "  screenshot: $FIT_BEFORE_PNG" >&2
  exit 1
fi
fit_before_w=$((fit_x1 - fit_x0))
fit_before_h=$((fit_y1 - fit_y0))

uia_tap_any_res_id "org.opendroidpdf:id/menu_text_style" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  uia_tap_text_contains "Style" || {
    echo "FAIL: could not open text style dialog" >&2
    exit 1
  }
}
sleep 0.8

# Best-effort: exercise alignment toggle (should not crash).
uia_tap_any_res_id "org.opendroidpdf:id/text_style_align_center" || true
sleep 0.2

uia_tap_any_res_id "org.opendroidpdf:id/text_style_fit_to_text" || {
  echo "FAIL: could not tap Fit to text" >&2
  exit 1
}
sleep 0.8
adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
sleep 0.8
_fail_if_fatal_logcat

_screencap_png "$FIT_AFTER_PNG"
read -r fit2_x0 fit2_y0 fit2_x1 fit2_y1 < <(_selection_box_bbox_px "$FIT_AFTER_PNG" || echo "")
if [[ -z "${fit2_x0:-}" || -z "${fit2_y0:-}" || -z "${fit2_x1:-}" || -z "${fit2_y1:-}" ]]; then
  # Selection can disappear after dialog interactions; re-tap inside the last-known bbox.
  tap_fit_x=$(((fit_x0 + fit_x1) / 2))
  tap_fit_y=$(((fit_y0 + fit_y1) / 2))
  adb -s "$DEVICE" shell input tap "$tap_fit_x" "$tap_fit_y"
  sleep 0.8
  _screencap_png "$FIT_AFTER_PNG"
  read -r fit2_x0 fit2_y0 fit2_x1 fit2_y1 < <(_selection_box_bbox_px "$FIT_AFTER_PNG" || echo "")
fi
if [[ -z "${fit2_x0:-}" || -z "${fit2_y0:-}" || -z "${fit2_x1:-}" || -z "${fit2_y1:-}" ]]; then
  echo "FAIL: could not detect selection bbox after fit-to-text" >&2
  echo "  screenshot: $FIT_AFTER_PNG" >&2
  exit 1
fi
fit_after_w=$((fit2_x1 - fit2_x0))
fit_after_h=$((fit2_y1 - fit2_y0))
dw_fit=$((fit_before_w - fit_after_w))
dh_fit=$((fit_before_h - fit_after_h))
if (( dw_fit < 20 && dh_fit < 20 )); then
  echo "FAIL: expected bbox to shrink after Fit to text (dw>=20 or dh>=20), got dw=$dw_fit dh=$dh_fit" >&2
  echo "  before: $FIT_BEFORE_PNG (w=$fit_before_w h=$fit_before_h) after: $FIT_AFTER_PNG (w=$fit_after_w h=$fit_after_h)" >&2
  exit 1
fi

echo "[9.8/14] Pinch-zoom + one-finger pan regression (pan outside selection)"
uia_runner_run_test "$UIA_ZOOM_TEST" || exit 1
sleep 1.0
_fail_if_fatal_logcat

PAN_BEFORE_PNG="${PAN_BEFORE_PNG:-${OUT_PREFIX}_panzoom_before.png}"
PAN_AFTER_PNG="${PAN_AFTER_PNG:-${OUT_PREFIX}_panzoom_after.png}"
_screencap_png "$PAN_BEFORE_PNG"

adb -s "$DEVICE" logcat -c >/dev/null || true

read -r w h < <(_wm_size)
sx=$((w / 2))
sy=$((h * 70 / 100))
ex=$sx
ey=$((h * 35 / 100))

# If a selection box is visible, start the pan gesture *outside* it so we validate:
# - pan still works while a text box is selected
# - drag inside the selection moves the annotation (covered earlier)
read -r sel_x0 sel_y0 sel_x1 sel_y1 < <(_selection_box_bbox_px "$PAN_BEFORE_PNG" || echo "")
if [[ -n "${sel_x0:-}" && -n "${sel_y0:-}" && -n "${sel_x1:-}" && -n "${sel_y1:-}" ]]; then
  sx=$((sel_x1 + 60))
  if (( sx > w - 8 )); then sx=$((sel_x0 - 60)); fi
  if (( sx < 8 )); then sx=$((w / 2)); fi
  sy=$(((sel_y0 + sel_y1) / 2))
  if (( sy < h / 10 )); then sy=$((h / 2)); fi
  if (( sy > h - 10 )); then sy=$((h / 2)); fi
  # Swipe up by ~35% of the screen height, clamped to the viewport.
  ex=$sx
  ey=$((sy - (h * 35 / 100)))
  if (( ey < h / 10 )); then ey=$((h / 10)); fi
fi
adb -s "$DEVICE" shell input swipe "$sx" "$sy" "$ex" "$ey" 420
sleep 0.9
_screencap_png "$PAN_AFTER_PNG"

log_tail="$(adb -s "$DEVICE" logcat -d | rg -n "GestureRouter: onScroll" | tail -n 5 || true)"
if [[ -z "$log_tail" ]]; then
  echo "FAIL: expected one-finger pan to hit ReaderView scroll path (GestureRouter: onScroll missing)" >&2
  echo "  wrote $PAN_BEFORE_PNG and $PAN_AFTER_PNG" >&2
  echo "Logcat (tail):" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2
  exit 1
fi
if printf '%s\n' "$log_tail" | rg -q "scrollDisabled=true"; then
  echo "FAIL: one-finger pan reached onScroll but scrollDisabled=true" >&2
  printf '%s\n' "$log_tail" >&2
  exit 1
fi
if adb -s "$DEVICE" logcat -d | rg -q "TextAnnotGesture: start MOVE"; then
  echo "FAIL: pan gesture triggered text MOVE (pan should scroll, not move the annotation)" >&2
  adb -s "$DEVICE" logcat -d | rg -n "TextAnnotGesture: start MOVE" | tail -n 40 >&2 || true
  exit 1
fi
echo "OK: pan gesture reached ReaderView onScroll with scrollEnabled"

_fail_if_fatal_logcat

echo "[10/14] Exit edit mode (show main menu)"
uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || true
sleep 0.8

if [[ "$POST_EDIT_IDLE_TAP_S" != "0" ]]; then
  echo "[10.5/14] Wait ${POST_EDIT_IDLE_TAP_S}s, then tap-to-edit again (catch tap-after-idle crashes)"
  sleep "$POST_EDIT_IDLE_TAP_S"
  _fail_if_fatal_logcat

  adb -s "$DEVICE" shell input tap "$x" "$y2"
  sleep 0.35
  adb -s "$DEVICE" shell input tap "$x" "$y2"
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
token_key="$(printf '%s' "$TOKEN_EDIT_EXPECTED" | tr -cd '[:alnum:]' | cut -c1-10)"
ocr_key="$(printf '%s' "$ocr" | tr -cd '[:alnum:]')"
if ! printf '%s\n' "$ocr_key" | rg -q "$token_key"; then
  # Fall back to a more stable thresholded OCR pass.
  _assert_token_in_rendered_pdf "$RENDER_PNG" "$TOKEN_EDIT_EXPECTED" || {
    echo "  token_key=$token_key" >&2
    echo "  OCR output: $ocr" >&2
    echo "PDF byte scan (first match):" >&2
    rg -a -n "$TOKEN_EDIT_EXPECTED" "$SAVED_PDF" | head -n 5 >&2 || true
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

echo "OK: text annotation rendered and OCR found token ($TOKEN_EDIT_EXPECTED)"
