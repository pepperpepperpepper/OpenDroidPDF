#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "AcroForm widgets feel complete (P0)":
# - Open a PDF with multiple widget types via DocumentsUI (persistable grant)
# - Fill text field, toggle checkbox + radio, select a choice value, and tap a signature field
# - Save in-place
# - Clear app data, reopen, and verify values/states are persisted
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_form_widgets_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_form_widgets.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_form_widgets_smoke.pdf}

TOKEN=${TOKEN:-ODPFORM_WIDGETS_1}
CHOICE_VALUE=${CHOICE_VALUE:-Two}

OUT_PREFIX=${OUT_PREFIX:-/tmp/odp_form_widgets_smoke}
ASSERT_RENDER_OCR=${ASSERT_RENDER_OCR:-1}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_sh_quote_single() {
  # Single-quote a string for /system/bin/sh, escaping embedded single quotes.
  # Produces a token that can be safely embedded in a larger double-quoted command string.
  local s="$1"
  s=${s//\'/\'\"\'\"\'}
  printf "'%s'" "$s"
}

_adb_input_text() {
  # Safely input arbitrary text via Android's 'input text' (handles shell metacharacters).
  local raw="$1"
  # Android 'input text' uses %s for spaces.
  raw=${raw// /%s}
  adb -s "$DEVICE" shell "input text $(_sh_quote_single "$raw")"
}

_fail_if_fatal_logcat() {
  if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION|Process ${PKG} \\(pid [0-9]+\\) has died"; then
    echo "FAIL: detected crash in logcat" >&2
    adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|AndroidRuntime|${PKG}" | tail -n 260 >&2 || true
    return 1
  fi
  return 0
}

_uia_text_for_res_id() {
  local rid="$1"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python - "$tmp" "$rid" <<'PY'
import sys, xml.etree.ElementTree as ET
xml_path, rid = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") == rid:
        print(node.attrib.get("text", ""))
        raise SystemExit(0)
raise SystemExit(1)
PY
  rm -f "$tmp"
}

_uia_bounds_for_res_id() {
  local rid="$1"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python - "$tmp" "$rid" <<'PY'
import re, sys, xml.etree.ElementTree as ET
xml_path, rid = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
pat = re.compile(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    m = pat.match(node.attrib.get("bounds", "") or "")
    if not m:
        continue
    l, t, r, b = map(int, m.groups())
    print(f"{l} {t} {r} {b}")
    raise SystemExit(0)
raise SystemExit(1)
PY
  rm -f "$tmp"
}

_uia_assert_checked_text_eq() {
  local expected="$1"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python - "$tmp" "$expected" <<'PY'
import sys, xml.etree.ElementTree as ET
xml_path, expected = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
for node in tree.iter("node"):
    if node.attrib.get("text", "") != expected:
        continue
    checked = (node.attrib.get("checked", "") or "").lower() == "true"
    selected = (node.attrib.get("selected", "") or "").lower() == "true"
    if checked or selected:
        raise SystemExit(0)
raise SystemExit(1)
PY
  rm -f "$tmp"
}

_open_pdf_via_documentsui() {
  local fname="$1"
  adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
  adb -s "$DEVICE" logcat -c >/dev/null || true
  adb -s "$DEVICE" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT" >/dev/null
  sleep 1.2

  uia_tap_any_res_id "org.opendroidpdf:id/entry_screen_open_document_card_view" || {
    echo "FAIL: could not tap entry-screen open-document card" >&2
    exit 1
  }
  sleep 1.5

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
  _adb_input_text "$fname"

  for _ in $(seq 1 20); do
    if uia_has_text_contains "$fname"; then
      break
    fi
    sleep 0.35
  done
  uia_tap_text_contains "$fname" || {
    echo "FAIL: could not select $fname in DocumentsUI search results" >&2
    echo "Logcat tail:" >&2
    adb -s "$DEVICE" logcat -d | tail -n 180 >&2
    exit 1
  }

  for _ in $(seq 1 20); do
    if uia_has_res_id "org.opendroidpdf:id/document_host_container"; then
      return 0
    fi
    sleep 0.6
  done

  echo "FAIL: document view did not appear after open (fname=$fname)" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2
  return 1
}

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p > "$out_png"
}

_widget_centers_from_highlight() {
  local png="$1"
  python3 - "$png" <<'PY'
from __future__ import annotations

import sys
from PIL import Image, ImageFilter
import numpy as np

path = sys.argv[1]
im = Image.open(path).convert("RGB")
arr = np.array(im)
r = arr[:, :, 0].astype(np.int16)
g = arr[:, :, 1].astype(np.int16)
b = arr[:, :, 2].astype(np.int16)

mask = (g > 150) & (r < 140) & (b < 160) & ((g - np.maximum(r, b)) > 40)
if not mask.any():
    raise SystemExit(1)

factor = 3
mask_small = (mask[::factor, ::factor].astype(np.uint8) * 255)
mask_img = Image.fromarray(mask_small, mode="L")

# The highlight stroke is dashed; dilate enough to connect dashes, but not so much
# that adjacent widgets merge into one component.
mask_img = mask_img.filter(ImageFilter.MaxFilter(11))
mask_bin = (np.array(mask_img) > 0)

h, w = mask_bin.shape
visited = np.zeros((h, w), dtype=np.uint8)

boxes = []
for y in range(h):
    row = mask_bin[y]
    for x in range(w):
        if not row[x] or visited[y, x]:
            continue
        stack = [(x, y)]
        visited[y, x] = 1
        minx = maxx = x
        miny = maxy = y
        count = 0
        while stack:
            cx, cy = stack.pop()
            count += 1
            if cx < minx: minx = cx
            if cx > maxx: maxx = cx
            if cy < miny: miny = cy
            if cy > maxy: maxy = cy

            nx = cx - 1
            if nx >= 0 and mask_bin[cy, nx] and not visited[cy, nx]:
                visited[cy, nx] = 1
                stack.append((nx, cy))
            nx = cx + 1
            if nx < w and mask_bin[cy, nx] and not visited[cy, nx]:
                visited[cy, nx] = 1
                stack.append((nx, cy))
            ny = cy - 1
            if ny >= 0 and mask_bin[ny, cx] and not visited[ny, cx]:
                visited[ny, cx] = 1
                stack.append((cx, ny))
            ny = cy + 1
            if ny < h and mask_bin[ny, cx] and not visited[ny, cx]:
                visited[ny, cx] = 1
                stack.append((cx, ny))

        bw = maxx - minx + 1
        bh = maxy - miny + 1
        if count < 120:
            continue
        if bw < 12 or bh < 12:
            continue
        boxes.append((minx, miny, maxx, maxy, count))

if not boxes:
    raise SystemExit(1)

centers = []
for minx, miny, maxx, maxy, count in boxes:
    cx = int(((minx + maxx) / 2.0) * factor)
    cy = int(((miny + maxy) / 2.0) * factor)
    centers.append((cy, cx, cx, cy))

centers.sort()
for _, __, cx, cy in centers:
    print(f"{cx} {cy}")
PY
}

_wait_for_text_dialog() {
  for _ in $(seq 1 16); do
    if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      return 0
    fi
    sleep 0.35
  done
  return 1
}

_wait_for_text_dialog_quick() {
  # Faster polling used for retry loops.
  local loops="${1:-10}"
  for _ in $(seq 1 "$loops"); do
    if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      return 0
    fi
    sleep 0.25
  done
  return 1
}

_tap_for_text_dialog() {
  local x="$1"
  local y="$2"

  local line w h
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -n "$line" ]]; then
    w="${line%x*}"
    h="${line#*x}"
  else
    w=""
    h=""
  fi

  local -a offsets=(
    "0 0"
    "16 0"
    "-16 0"
    "0 16"
    "0 -16"
    "24 10"
    "-24 10"
  )

  local off dx dy tx ty
  for off in "${offsets[@]}"; do
    dx="${off% *}"
    dy="${off#* }"
    tx=$((x + dx))
    ty=$((y + dy))

    if [[ -n "$w" && -n "$h" ]]; then
      if ((tx < 1)); then tx=1; fi
      if ((ty < 1)); then ty=1; fi
      if ((tx > w - 2)); then tx=$((w - 2)); fi
      if ((ty > h - 2)); then ty=$((h - 2)); fi
    fi

    adb -s "$DEVICE" shell input tap "$tx" "$ty"
    if _wait_for_text_dialog_quick 8; then
      return 0
    fi
  done

  # One last slower wait (some devices need a beat after the tap).
  adb -s "$DEVICE" shell input tap "$x" "$y"
  _wait_for_text_dialog
}

_wait_for_text_editor_gone() {
  for _ in $(seq 1 20); do
    if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      return 0
    fi
    sleep 0.25
  done
  return 1
}

_tap_outside_editor_to_commit() {
  # Prefer tapping just outside the inline editor bounds (avoids tapping the keyboard).
  local bounds l t r b x y
  bounds="$(_uia_bounds_for_res_id "org.opendroidpdf:id/dialog_text_input" 2>/dev/null || true)"
  if [[ -n "$bounds" ]]; then
    read -r l t r b <<<"$bounds"
    x=$((l - 30))
    if [[ "$x" -lt 5 ]]; then x=5; fi
    y=$(((t + b) / 2))
    adb -s "$DEVICE" shell input tap "$x" "$y"
    return 0
  fi

  local line w h
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    adb -s "$DEVICE" shell input keyevent KEYCODE_BACK
    return 0
  fi
  w="${line%x*}"
  h="${line#*x}"
  adb -s "$DEVICE" shell input tap "$((w / 2))" "$((h / 4))"
}

_wait_for_any_alert_dialog() {
  for _ in $(seq 1 16); do
    if uia_has_res_id "android:id/alertTitle"; then
      return 0
    fi
    sleep 0.35
  done
  return 1
}

_assert_mark_present_near() {
  local png="$1"
  local cx="$2"
  local cy="$3"
  python3 - "$png" "$cx" "$cy" <<'PY'
from __future__ import annotations

import sys
from PIL import Image

path, cx_s, cy_s = sys.argv[1], sys.argv[2], sys.argv[3]
cx, cy = int(cx_s), int(cy_s)

im = Image.open(path).convert("L")
w, h = im.size

half = 42
left = max(0, cx - half)
top = max(0, cy - half)
right = min(w, cx + half)
bottom = min(h, cy + half)

crop = im.crop((left, top, right, bottom))

# Ignore the border by trimming an inner margin; the \"X\" mark lives inside.
inner_margin = 10
if crop.size[0] <= inner_margin * 2 or crop.size[1] <= inner_margin * 2:
    raise SystemExit(1)
crop = crop.crop((inner_margin, inner_margin, crop.size[0] - inner_margin, crop.size[1] - inner_margin))

px = crop.load()
dark = 0
total = crop.size[0] * crop.size[1]
for y in range(crop.size[1]):
    for x in range(crop.size[0]):
        if px[x, y] < 110:
            dark += 1

# Off-state is mostly white; on-state has two diagonals. Threshold is conservative.
if dark < max(25, total // 90):
    raise SystemExit(1)
PY
}

echo "[1/9] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/9] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/9] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null
fname="$(basename "$PDF_REMOTE_PATH")"

echo "[4/9] Open PDF via DocumentsUI (persistable grant)"
_open_pdf_via_documentsui "$fname"
sleep 1.0

echo "[5/9] Enable Forms highlight + locate widgets"
uia_enable_forms_highlight || true
sleep 0.4
centers=()
last_hl_png="${OUT_PREFIX}_hl_last.png"
for _attempt in $(seq 1 22); do
  _screencap_png "$last_hl_png"
  mapfile -t centers < <(_widget_centers_from_highlight "$last_hl_png" 2>/dev/null || true)
  if [[ "${#centers[@]}" -ge 6 ]]; then
    break
  fi
  sleep 0.35
done
if [[ "${#centers[@]}" -lt 6 ]]; then
  echo "FAIL: expected >= 6 widget highlight regions, got ${#centers[@]}" >&2
  if [[ -f "$last_hl_png" ]]; then
    cp "$last_hl_png" "${OUT_PREFIX}_fail_highlight.png" || true
    echo "  wrote ${OUT_PREFIX}_fail_highlight.png" >&2
  fi
  exit 1
fi
rm -f "$last_hl_png" || true

read -r name_xy <<<"${centers[0]}"
read -r agree_xy <<<"${centers[1]}"
read -r radio_a_xy <<<"${centers[2]}"
read -r choice_xy <<<"${centers[4]}"
read -r sig_xy <<<"${centers[5]}"

name_x="${name_xy% *}"; name_y="${name_xy#* }"
agree_x="${agree_xy% *}"; agree_y="${agree_xy#* }"
radio_x="${radio_a_xy% *}"; radio_y="${radio_a_xy#* }"
choice_x="${choice_xy% *}"; choice_y="${choice_xy#* }"
sig_x="${sig_xy% *}"; sig_y="${sig_xy#* }"

echo "[6/9] Interact with widgets (text + checkbox + radio + choice + signature)"

# Text field
if ! _tap_for_text_dialog "$name_x" "$name_y"; then
  echo "FAIL: text widget editor did not appear after tap" >&2
  adb -s "$DEVICE" logcat -d | tail -n 180 >&2
  exit 1
fi
# Ensure the inline editor has focus before injecting text.
uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || true
_adb_input_text "$TOKEN"
sleep 0.4
_tap_outside_editor_to_commit
sleep 0.4
if ! _wait_for_text_editor_gone; then
  echo "FAIL: inline editor did not dismiss after text commit" >&2
  exit 1
fi
sleep 0.4
_fail_if_fatal_logcat

# Sanity check: reopen the editor and confirm the value is present before saving.
if ! _tap_for_text_dialog "$name_x" "$name_y"; then
  echo "FAIL: text widget editor did not appear for pre-save verification" >&2
  exit 1
fi
now=""
for _ in $(seq 1 12); do
  now="$(_uia_text_for_res_id "org.opendroidpdf:id/dialog_text_input" | tr -d '\r' || true)"
  if [[ "$now" == "$TOKEN" ]]; then
    break
  fi
  sleep 0.25
done
if [[ "$now" != "$TOKEN" ]]; then
  echo "FAIL: widget value not set before save" >&2
  echo "  got:      '$now'" >&2
  echo "  expected: '$TOKEN'" >&2
  exit 1
fi
_tap_outside_editor_to_commit || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
sleep 0.4
if ! _wait_for_text_editor_gone; then
  echo "FAIL: inline editor did not dismiss after pre-save verification" >&2
  exit 1
fi
sleep 0.2

# Checkbox: expect the X mark to appear inside the box.
pre_chk="$(mktemp -t odp_form_chk_pre_XXXXXX).png"
post_chk="$(mktemp -t odp_form_chk_post_XXXXXX).png"
_screencap_png "$pre_chk"
adb -s "$DEVICE" shell input tap "$agree_x" "$agree_y"
sleep 0.8
_screencap_png "$post_chk"
if ! _assert_mark_present_near "$post_chk" "$agree_x" "$agree_y"; then
  echo "FAIL: checkbox mark not detected after toggle" >&2
  mv "$pre_chk" "${OUT_PREFIX}_chk_pre.png" || true
  mv "$post_chk" "${OUT_PREFIX}_chk_post.png" || true
  exit 1
fi
rm -f "$pre_chk" "$post_chk" || true
_fail_if_fatal_logcat

# Radio: select A (visual X mark indicates selection in our fixture appearance).
pre_radio="$(mktemp -t odp_form_radio_pre_XXXXXX).png"
post_radio="$(mktemp -t odp_form_radio_post_XXXXXX).png"
_screencap_png "$pre_radio"
adb -s "$DEVICE" shell input tap "$radio_x" "$radio_y"
sleep 0.8
_screencap_png "$post_radio"
if ! _assert_mark_present_near "$post_radio" "$radio_x" "$radio_y"; then
  echo "FAIL: radio mark not detected after toggle" >&2
  mv "$pre_radio" "${OUT_PREFIX}_radio_pre.png" || true
  mv "$post_radio" "${OUT_PREFIX}_radio_post.png" || true
  exit 1
fi
rm -f "$pre_radio" "$post_radio" || true
_fail_if_fatal_logcat

# Choice: pick a value.
adb -s "$DEVICE" shell input tap "$choice_x" "$choice_y"
sleep 0.8
uia_tap_text_contains "$CHOICE_VALUE" || {
  echo "FAIL: could not select choice '$CHOICE_VALUE' in dialog" >&2
  exit 1
}
sleep 1.2
_fail_if_fatal_logcat

# Signature: ensure we detect the field and show some dialog, then cancel out.
adb -s "$DEVICE" shell input tap "$sig_x" "$sig_y"
if ! _wait_for_any_alert_dialog; then
  echo "FAIL: signature interaction did not show an alert dialog" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2
  exit 1
fi
uia_tap_any_res_id "android:id/button2" "com.android.internal:id/button2" || uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
sleep 0.8
_fail_if_fatal_logcat

echo "[7/9] Save in-place"
uia_save_changes || { echo "FAIL: Save changes entry point missing" >&2; exit 1; }
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 2.6
_fail_if_fatal_logcat

echo "[8/9] Reopen and verify persisted state"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
_open_pdf_via_documentsui "$fname"
sleep 0.9
uia_enable_forms_highlight || true
sleep 0.8

tmp_png="$(mktemp -t odp_form_widgets_hl2_XXXXXX).png"
centers2=()
last_hl_png2="${OUT_PREFIX}_hl_last_reopen.png"
for _attempt in $(seq 1 22); do
  _screencap_png "$last_hl_png2"
  mapfile -t centers2 < <(_widget_centers_from_highlight "$last_hl_png2" 2>/dev/null || true)
  if [[ "${#centers2[@]}" -ge 6 ]]; then
    break
  fi
  sleep 0.35
done
rm -f "$tmp_png" || true
if [[ "${#centers2[@]}" -lt 6 ]]; then
  echo "FAIL: expected >= 6 widget highlight regions after reopen, got ${#centers2[@]}" >&2
  if [[ -f "${last_hl_png2:-}" ]]; then
    cp "$last_hl_png2" "${OUT_PREFIX}_fail_highlight_reopen.png" || true
    echo "  wrote ${OUT_PREFIX}_fail_highlight_reopen.png" >&2
  fi
  exit 1
fi
rm -f "$last_hl_png2" || true
read -r name_xy2 <<<"${centers2[0]}"
read -r agree_xy2 <<<"${centers2[1]}"
read -r radio_xy2 <<<"${centers2[2]}"
read -r choice_xy2 <<<"${centers2[4]}"
read -r sig_xy2 <<<"${centers2[5]}"

name_x2="${name_xy2% *}"; name_y2="${name_xy2#* }"
agree_x2="${agree_xy2% *}"; agree_y2="${agree_xy2#* }"
radio_x2="${radio_xy2% *}"; radio_y2="${radio_xy2#* }"
choice_x2="${choice_xy2% *}"; choice_y2="${choice_xy2#* }"
sig_x2="${sig_xy2% *}"; sig_y2="${sig_xy2#* }"

# Text value persisted (inline editor shows saved value)
if ! _tap_for_text_dialog "$name_x2" "$name_y2"; then
  echo "FAIL: text widget editor did not appear after reopen" >&2
  exit 1
fi
saved=""
for _ in $(seq 1 12); do
  saved="$(_uia_text_for_res_id "org.opendroidpdf:id/dialog_text_input" | tr -d '\r' || true)"
  if [[ "$saved" == "$TOKEN" ]]; then
    break
  fi
  sleep 0.25
done
if [[ "$saved" != "$TOKEN" ]]; then
  echo "FAIL: persisted text value mismatch (got '$saved', expected '$TOKEN')" >&2
  exit 1
fi
_tap_outside_editor_to_commit || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
sleep 0.4
if ! _wait_for_text_editor_gone; then
  echo "FAIL: inline editor did not dismiss after reopen verification" >&2
  exit 1
fi
sleep 0.2

# Checkbox/radio marks persisted (visual check)
chk_png="$(mktemp -t odp_form_chk_persist_XXXXXX).png"
_screencap_png "$chk_png"
_assert_mark_present_near "$chk_png" "$agree_x2" "$agree_y2" || { mv "$chk_png" "${OUT_PREFIX}_chk_persist_fail.png" || true; echo "FAIL: checkbox mark missing after reopen" >&2; exit 1; }
_assert_mark_present_near "$chk_png" "$radio_x2" "$radio_y2" || { mv "$chk_png" "${OUT_PREFIX}_radio_persist_fail.png" || true; echo "FAIL: radio mark missing after reopen" >&2; exit 1; }
rm -f "$chk_png" || true

# Choice persisted: dialog should open with the prior selection checked.
adb -s "$DEVICE" shell input tap "$choice_x2" "$choice_y2"
sleep 0.8
if ! _uia_assert_checked_text_eq "$CHOICE_VALUE"; then
  echo "FAIL: choice dialog did not show '$CHOICE_VALUE' as checked after reopen" >&2
  exit 1
fi
uia_tap_any_res_id "android:id/button2" "com.android.internal:id/button2" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
sleep 0.6

# Signature field still interactive.
adb -s "$DEVICE" shell input tap "$sig_x2" "$sig_y2"
if ! _wait_for_any_alert_dialog; then
  echo "FAIL: signature interaction did not show an alert dialog after reopen" >&2
  exit 1
fi
uia_tap_any_res_id "android:id/button2" "com.android.internal:id/button2" || uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
sleep 0.7

echo "[9/9] Pull saved PDF back to host (debug artifact)"
SAVED_PDF="${SAVED_PDF:-${OUT_PREFIX}.pdf}"
adb -s "$DEVICE" pull "$PDF_REMOTE_PATH" "$SAVED_PDF" >/dev/null
echo "  wrote $SAVED_PDF"

if [[ "$ASSERT_RENDER_OCR" == "1" ]]; then
  if command -v pdftoppm >/dev/null 2>&1 && command -v tesseract >/dev/null 2>&1; then
    echo "[9.5/9] Cross-render check (poppler + OCR) for filled text value"
    tmpdir="$(mktemp -d -t odp_form_widgets_render_XXXXXX)"
    out_prefix="$tmpdir/out"
    pdftoppm -f 1 -l 1 -r 300 -singlefile -png "$SAVED_PDF" "$out_prefix" >/dev/null
    ocr_raw="$(tesseract "${out_prefix}.png" stdout -l eng --psm 6 2>/dev/null | tr -d '\f' | tr -d '\r')"
    ocr_key="$(printf '%s' "$ocr_raw" | tr -cd '[:alnum:]')"
    token_key="$(printf '%s' "$TOKEN" | tr -cd '[:alnum:]')"
    rm -rf -- "$tmpdir" || true
    if ! printf '%s\n' "$ocr_key" | rg -q "$token_key"; then
      echo "FAIL: OCR did not find token '$TOKEN' in rendered PDF output" >&2
      echo "  OCR raw: $(printf '%s' "$ocr_raw" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')" >&2
      exit 1
    fi
  else
    echo "WARN: pdftoppm/tesseract not found; skipping cross-render OCR check" >&2
  fi
fi

echo "OK: AcroForm widgets persisted (text/checkbox/radio/choice) and signature field prompts"
