#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke: text-annotation multi-select align/distribute + grouped move.
# Steps (best-effort, OCR-driven):
#   - install app, clear data, push fixture PDF
#   - add three FreeText annotations at distinct positions
#   - build a multi-select set via quick-actions
#   - apply Align left and assert left edges are aligned (within tolerance)
#   - reposition, apply Distribute horizontally and assert even spacing
#   - toggle grouping, drag one item, assert all grouped items move together
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk \
#     ./scripts/geny_pdf_text_annot_multiselect_smoke.sh
#
# Requirements (host):
#   - adb, tesseract

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_with_text.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_text_annot_multiselect.pdf}
PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity
TOKENS=("MULTIAAA" "MULTIBBB" "MULTICCC")
OUT_PREFIX="${OUT_PREFIX:-tmp_geny_pdf_text_annot_multi}"
SCREENSHOT_PNG="${SCREENSHOT_PNG:-${OUT_PREFIX}_ui.png}"

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

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

_tap_doc_fraction() {
  local fx="$1" fy="$2"
  local w h x y
  read -r w h < <(_wm_size)
  x=$((w * fx / 100))
  y=$((h * fy / 100))
  adb -s "$DEVICE" shell input tap "$x" "$y"
}

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p > "$out_png"
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

_ocr_token_center_xy() {
  local png="$1"
  local token="$2"
  tesseract "$png" stdout -l eng --psm 6 tsv 2>/dev/null \
    | awk -F'\t' -v tok="$token" 'NR>1 && $1==5 && index($12,tok)>0 { printf "%d %d\n", ($7 + int($9/2)), ($8 + int($10/2)); found=1; exit } END { exit found?0:1 }'
}

_ocr_token_bbox() {
  local png="$1" token="$2"
  local psm
  for psm in 11 6; do
    if tesseract "$png" stdout -l eng --psm "$psm" -c tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 tsv 2>/dev/null \
      | awk -F'\t' -v tok="$token" 'NR>1 && $1==5 && $12==tok { printf "%d %d %d %d\n", $7, $8, $9, $10; found=1; exit } END { exit found?0:1 }'
    then
      return 0
    fi
  done
  return 1
}

_abs() { local v="$1"; (( v < 0 )) && v=$((-v)); echo "$v"; }

_wm_density() {
  local line dens
  line="$(adb -s "$DEVICE" shell wm density | tr -d '\r' | rg -o '[0-9]+' | head -n1 || true)"
  dens="${line:-420}"
  echo "$dens"
}

_tap_quick_action_button() {
  # Best-effort coordinate tap on a quick-action button by index (0-based) left->right.
  # Inputs: bbox string "x0 y0 x1 y1", button_index
  local bbox="$1" idx="$2"
  local x0 y0 x1 y1 btn_x btn_y width height
  read -r x0 y0 x1 y1 <<<"$bbox"
  width=$((x1 - x0))
  height=$((y1 - y0))
  # Empirical positions from screenshot: quick-action strip spans ~323..627 px and sits ~ (selection top - ~44px)
  btn_y=$((y0 - 44))
  if (( btn_y < 60 )); then btn_y=60; fi
  btn_x=$((x0 + width * (16 + idx*9) / 72))
  adb -s "$DEVICE" shell input tap "$btn_x" "$btn_y"
}

_exit_inline_editor_if_needed() {
  # Inline editor overlays hide quick actions; accept or back out if present.
  local attempt
  for attempt in 1 2 3; do
    if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      return
    fi
    uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || adb -s "$DEVICE" shell input keyevent 111 || adb -s "$DEVICE" shell input keyevent 4
    # Tap a corner of the page to defocus inline editor if needed.
    read -r w h < <(_wm_size)
    adb -s "$DEVICE" shell input tap $((w / 10)) $((h / 6)) || true
    sleep 0.6
  done
}

_exit_creation_mode_if_needed() {
  # After adding text annotations the tool can stay active; exit so quick actions show.
  local attempt
  for attempt in $(seq 1 6); do
    if ! uia_has_res_id "org.opendroidpdf:id/menu_accept"; then
      return
    fi
    uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || adb -s "$DEVICE" shell input keyevent 111 || adb -s "$DEVICE" shell input keyevent 4
    sleep 0.5
    if ! uia_has_res_id "org.opendroidpdf:id/menu_accept"; then
      return
    fi
    if uia_has_res_id "org.opendroidpdf:id/menu_cancel"; then
      uia_tap_any_res_id "org.opendroidpdf:id/menu_cancel" || true
      sleep 0.5
    fi
  done
  # Fallback: send Back twice to leave edit/tool mode.
  adb -s "$DEVICE" shell input keyevent 4
  sleep 0.4
  adb -s "$DEVICE" shell input keyevent 4
  sleep 0.4
  # Last resort: tap toolbar accept/cancel coordinates directly.
  read -r w h < <(_wm_size)
  local y=$((h / 27 + 50)) # roughly status+toolbar height region
  local x_accept=$((w * 9 / 10))
  local x_cancel=$((w * 7 / 10))
  adb -s "$DEVICE" shell input tap "$x_accept" "$y" || true
  sleep 0.3
  adb -s "$DEVICE" shell input tap "$x_cancel" "$y" || true
  sleep 0.5
}

_bbox_for_token() {
  local token="$1"
  local shot bbox fx fy b0 b1 b2 b3
  shot="$(mktemp -t odp_multi_bbox_XXXXXX).png"
  _screencap_png "$shot"
  if read -r b0 b1 b2 b3 < <(_ocr_token_bbox "$shot" "$token" 2>/dev/null); then
    echo "$b0 $b1 $((b0+b2)) $((b1+b3))"
    return 0
  fi
  # Retry after tapping the original placement to bring into view.
  case "$token" in
    "MULTIAAA") fx=30 fy=42 ;;
    "MULTIBBB") fx=72 fy=46 ;;
    "MULTICCC") fx=50 fy=70 ;;
    *) fx=50 fy=50 ;;
  esac
  _tap_doc_fraction "$fx" "$fy"
  sleep 0.8
  _screencap_png "$shot"
  if read -r b0 b1 b2 b3 < <(_ocr_token_bbox "$shot" "$token" 2>/dev/null); then
    echo "$b0 $b1 $((b0+b2)) $((b1+b3))"
    return 0
  fi
  # Persist failing screenshot for debugging.
  local fail_path="${OUT_PREFIX}_${token}_bbox_fail.png"
  cp "$shot" "$fail_path" 2>/dev/null || true
  echo "FAIL: OCR could not locate token '$token' onscreen (saved $fail_path)" >&2
  return 1
}

_center_from_bbox() {
  local bbox="$1"
  read -r x0 y0 x1 y1 <<<"$bbox"
  echo $((((x0 + x1) / 2))) $((((y0 + y1) / 2)))
}

_add_text_at() {
  local token="$1" fx="$2" fy="$3"
  uia_tap_any_res_id "org.opendroidpdf:id/menu_add_text_annot" || {
    if uia_tap_desc "More options"; then sleep 0.4; fi
    uia_tap_text_contains "Add text" || {
      echo "FAIL: could not enter add-text mode" >&2
      exit 1
    }
  }
  sleep 0.6
  _tap_doc_fraction "$fx" "$fy"
  for _ in $(seq 1 12); do
    if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then break; fi
    sleep 0.25
  done
  if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    echo "FAIL: text input UI did not appear for token '$token'" >&2
    exit 1
  fi
  if uia_has_res_id "android:id/button1" "com.android.internal:id/button1"; then
    uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || true
  fi
  adb -s "$DEVICE" shell input text "$token"
  sleep 0.3
  if uia_has_res_id "android:id/button1" "com.android.internal:id/button1"; then
    uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
  else
    # Inline editor: dismiss via focus loss.
    read -r w h < <(_wm_size)
    adb -s "$DEVICE" shell input tap $((w * 9 / 10)) $((h / 5))
    for _ in $(seq 1 10); do
      if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then break; fi
      sleep 0.2
    done
  fi
  sleep 1.2
}

_add_to_multiselect() {
  local token="$1"
  local bbox="$(_bbox_for_token "$token")"
  local token_center_x token_center_y
  read -r token_center_x token_center_y <<<"$(_center_from_bbox "$bbox")"
  # Ensure selection is active by tapping the center.
  adb -s "$DEVICE" shell input tap "$token_center_x" "$token_center_y" || true
  sleep 0.4
  _debug_broadcast "org.opendroidpdf.DEBUG_TEXT_MULTI_ADD"
  sleep 0.6
}

_debug_broadcast() {
  local action="$1"
  adb -s "$DEVICE" shell am broadcast --receiver-foreground \
    -n "$PKG/.app.debug.DebugActionsReceiver" \
    -a "$action" -p "$PKG" >/dev/null 2>&1 || true
}

_apply_action() {
  local label="$1"
  case "$label" in
    "Align left") _debug_broadcast "org.opendroidpdf.DEBUG_TEXT_MULTI_ALIGN_LEFT" ;;
    "Distribute horizontally") _debug_broadcast "org.opendroidpdf.DEBUG_TEXT_MULTI_DISTRIBUTE_H" ;;
    *) echo "FAIL: unknown action '$label'" >&2; exit 1 ;;
  esac
  sleep 1.0
}

_assert_aligned_left() {
  local line
  line="$(adb -s "$DEVICE" logcat -d -s OpenDroidPDF/Debug | tac | awk '/text-multi-apply-post ALIGN_LEFT/ {print; exit}')" || true
  if [[ -z "$line" ]]; then
    echo "FAIL: no ALIGN_LEFT apply log found" >&2
    return 1
  fi
  LINE="$line" python3 - <<'PY'
import os,re,sys
line=os.environ["LINE"]
bounds=re.findall(r"b=([0-9.]+),([0-9.]+),([0-9.]+),([0-9.]+)", line)
if len(bounds)<2:
    sys.exit("FAIL: unable to parse bounds from log")
lefts=[float(b[0]) for b in bounds]
diff=max(lefts)-min(lefts)
if diff>8.0:
    sys.exit(f"FAIL: left edges not aligned (range={diff:.1f}; lefts={lefts})")
print(f"OK: aligned-left range={diff:.1f}")
PY
}

_move_token_to_fraction() {
  local token="$1" fx="$2" fy="$3"
  local bbox center_x center_y w h tx ty
  bbox="$(_bbox_for_token "$token")" || return 1
  read -r center_x center_y < <(_center_from_bbox "$bbox")
  read -r w h < <(_wm_size)
  tx=$((w * fx / 100))
  ty=$((h * fy / 100))
  adb -s "$DEVICE" shell input swipe "$center_x" "$center_y" "$tx" "$ty" 520
  sleep 0.9
}

_assert_distributed_horizontal() {
  local line
  line="$(adb -s "$DEVICE" logcat -d -s OpenDroidPDF/Debug | tac | awk '/text-multi-apply-post DISTRIBUTE_HORIZONTAL/ {print; exit}')" || true
  if [[ -z "$line" ]]; then
    echo "FAIL: no DISTRIBUTE_HORIZONTAL apply log found" >&2
    return 1
  fi
  LINE="$line" python3 - <<'PY'
import os,re,sys
line=os.environ["LINE"]
bounds=re.findall(r"b=([0-9.]+),([0-9.]+),([0-9.]+),([0-9.]+)", line)
if len(bounds)<3:
    sys.exit("FAIL: unable to parse bounds from log")
centers=[(float(l)+float(r))/2.0 for l,_,r,_ in bounds]
centers.sort()
d1=centers[1]-centers[0]
d2=centers[2]-centers[1]
delta=abs(d1-d2)
if d1<24 or d2<24 or delta>18:
    sys.exit(f"FAIL: horizontal distribution uneven (d1={d1:.1f}px d2={d2:.1f}px delta={delta:.1f}px centers={centers})")
print(f"OK: distributed horizontally (d1={d1:.1f}px d2={d2:.1f}px delta={delta:.1f}px)")
PY
}

_record_centers() {
  local -n out_arr=$1
  out_arr=()
  local bbox cx cy
  for tok in "${TOKENS[@]}"; do
    bbox="$(_bbox_for_token "$tok")" || return 1
    read -r cx cy < <(_center_from_bbox "$bbox")
    out_arr+=("$cx,$cy")
  done
}

_assert_group_move() {
  # Toggle grouping on and use debug nudge to translate the set.
  _debug_broadcast "org.opendroidpdf.DEBUG_TEXT_MULTI_TOGGLE_GROUP"
  sleep 0.4
  _debug_broadcast "org.opendroidpdf.DEBUG_TEXT_MULTI_NUDGE"
  sleep 0.8

  local line
  line="$(adb -s "$DEVICE" logcat -d -s OpenDroidPDF/Debug | tac | awk '/text-multi-debug-translate/ {print; exit}')" || true
  if [[ -z "$line" ]]; then
    echo "FAIL: no debug-translate log captured" >&2
    return 1
  fi
  LINE="$line" python3 - <<'PY'
import os,re,sys,math
line=os.environ["LINE"]
m=re.search(r"dx=([\-0-9.]+) dy=([\-0-9.]+)", line)
bounds=re.findall(r"b=([0-9.]+),([0-9.]+),([0-9.]+),([0-9.]+)", line)
if not m or len(bounds)<2:
    sys.exit("FAIL: could not parse debug translate log")
dx=float(m.group(1)); dy=float(m.group(2))
mag=math.hypot(dx,dy)
if mag < 15.0:
    sys.exit(f"FAIL: grouped move delta too small (dx={dx:.1f} dy={dy:.1f})")
print(f"OK: grouped move applied (dx={dx:.1f}, dy={dy:.1f}, items={len(bounds)})")
PY
}

echo "[1/9] Install APK"
adb -s "$DEVICE" install -r -t "$APK" >/dev/null

echo "[2/9] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/9] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null

echo "[4/9] Launch app and open PDF via DocumentsUI"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT" >/dev/null
sleep 1.2

uia_tap_any_res_id "org.opendroidpdf:id/entry_screen_open_document_card_view" || {
  echo "FAIL: could not tap entry-screen open-document card" >&2
  exit 1
}
sleep 1.3

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
  exit 1
}

uia_assert_in_document_view

echo "[5/9] Add three text annotations"
_add_text_at "${TOKENS[0]}" 30 42
_add_text_at "${TOKENS[1]}" 72 46
_add_text_at "${TOKENS[2]}" 50 70
uia_assert_in_document_view

echo "[6/9] Build multi-select set"
_exit_creation_mode_if_needed
_add_to_multiselect "${TOKENS[0]}"
_add_to_multiselect "${TOKENS[1]}"
_add_to_multiselect "${TOKENS[2]}"

echo "[7/9] Align left and assert"
_apply_action "Align left"
_assert_aligned_left

echo "[8/9] Distribute horizontally and assert spacing"
_apply_action "Distribute horizontally"
_assert_distributed_horizontal

echo "[9/9] Group + drag move"
_assert_group_move

echo "OK: multi-select align/distribute/group smoke passed"
