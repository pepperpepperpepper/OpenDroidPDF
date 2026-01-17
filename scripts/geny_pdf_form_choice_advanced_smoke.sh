#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for advanced choice widgets:
# - Multi-select listbox (setMultiChoiceItems UI, persists after Save)
# - Editable combobox (custom value entry, persists after Save)
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_form_choice_advanced_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_form_choice_advanced.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_form_choice_advanced_smoke.pdf}

MULTI_A=${MULTI_A:-Red}
MULTI_B=${MULTI_B:-Blue}
CUSTOM_VALUE=${CUSTOM_VALUE:-ODP_COMBO_CUSTOM}

OUT_PREFIX=${OUT_PREFIX:-/tmp/odp_form_choice_advanced_smoke}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

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

_uia_tap_rid_text_eq() {
  # Tap a node matching both resource-id and exact text.
  local rid="$1"
  local text="$2"
  local tmp coords
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  coords="$(python - "$tmp" "$rid" "$text" <<'PY'
import re, sys, xml.etree.ElementTree as ET
xml_path, rid, text = sys.argv[1], sys.argv[2], sys.argv[3]
tree = ET.parse(xml_path)
def center(bounds: str):
    m = re.match(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]", bounds or "")
    if not m:
        return None
    l, t, r, b = map(int, m.groups())
    return (l + r) // 2, (t + b) // 2
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    if node.attrib.get("text", "") != text:
        continue
    c = center(node.attrib.get("bounds", ""))
    if not c:
        continue
    print(f"{c[0]} {c[1]}")
    raise SystemExit(0)
raise SystemExit(1)
PY
)" || { rm -f "$tmp"; return 1; }
  rm -f "$tmp"
  set -- $coords
  adb -s "$DEVICE" shell input tap "$1" "$2"
  return 0
}

_uia_tap_rid_text_contains() {
  # Tap a node matching resource-id and where text contains the needle (case-insensitive).
  local rid="$1"
  local needle="$2"
  local tmp coords
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  coords="$(python - "$tmp" "$rid" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
xml_path, rid, needle = sys.argv[1], sys.argv[2], sys.argv[3]
tree = ET.parse(xml_path)
needle_l = needle.lower()
def center(bounds: str):
    m = re.match(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]", bounds or "")
    if not m:
        return None
    l, t, r, b = map(int, m.groups())
    return (l + r) // 2, (t + b) // 2
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    txt = (node.attrib.get("text", "") or "")
    if needle_l not in txt.lower():
        continue
    c = center(node.attrib.get("bounds", ""))
    if not c:
        continue
    print(f"{c[0]} {c[1]}")
    raise SystemExit(0)
raise SystemExit(1)
PY
)" || { rm -f "$tmp"; return 1; }
  rm -f "$tmp"
  set -- $coords
  adb -s "$DEVICE" shell input tap "$1" "$2"
  return 0
}

_open_pdf_via_documentsui_writable() {
  local fname="$1"
  local max_attempts="${2:-5}"
  for attempt in $(seq 1 "$max_attempts"); do
    if ! _open_pdf_via_documentsui "$fname"; then
      echo "WARN: open via DocumentsUI failed; retrying (attempt $attempt/$max_attempts)" >&2
      sleep 1.2
      continue
    fi
    return 0
  done

  echo "FAIL: could not open '$fname' via DocumentsUI" >&2
  adb -s "$DEVICE" logcat -d | tail -n 220 >&2 || true
  return 1
}

_open_pdf_via_documentsui() {
  local fname="$1"
  adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
  adb -s "$DEVICE" logcat -c >/dev/null || true
  adb -s "$DEVICE" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT" >/dev/null
  sleep 1.2

  uia_tap_any_res_id "org.opendroidpdf:id/entry_screen_open_document_card_view" || {
    echo "FAIL: could not tap entry-screen open-document card" >&2
    return 1
  }
  sleep 1.5

  uia_tap_docsui_roots_drawer || {
    echo "FAIL: could not open DocumentsUI roots drawer" >&2
    return 1
  }
  sleep 0.7
  uia_tap_text_contains "Downloads" || {
    echo "FAIL: could not switch DocumentsUI to Downloads root" >&2
    return 1
  }
  sleep 0.9

  # Use DocumentsUI search so a single tap returns the document immediately (avoids "select + confirm" UIs).
  uia_tap_any_res_id "com.android.documentsui:id/option_menu_search" || uia_tap_desc "Search" || {
    echo "FAIL: could not open DocumentsUI search" >&2
    return 1
  }
  sleep 0.6
  adb -s "$DEVICE" shell input text "$fname"
  sleep 1.0

  for _ in $(seq 1 20); do
    if uia_has_text_contains "$fname"; then
      break
    fi
    sleep 0.35
  done
  uia_tap_text_contains "$fname" || {
    echo "FAIL: could not select '$fname' in DocumentsUI search results" >&2
    adb -s "$DEVICE" logcat -d | tail -n 180 >&2 || true
    return 1
  }

  # Wait for our document view to appear.
  for _ in $(seq 1 20); do
    if uia_has_res_id "org.opendroidpdf:id/document_host_container"; then
      return 0
    fi
    sleep 0.6
  done

  echo "FAIL: document view did not appear after open (fname=$fname)" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2 || true
  return 1
}

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p > "$out_png"
}

_enable_forms_highlight_or_die() {
  uia_enable_forms_highlight || {
    echo "FAIL: could not enable Forms highlight (Navigate & View)" >&2
    exit 1
  }
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
        boxes.append((minx, miny, maxx, maxy))

if not boxes:
    raise SystemExit(1)

centers = []
for minx, miny, maxx, maxy in boxes:
    cx = int(((minx + maxx) / 2.0) * factor)
    cy = int(((miny + maxy) / 2.0) * factor)
    centers.append((cy, cx))

centers.sort()
for cy, cx in centers:
    print(f"{cx} {cy}")
PY
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

_wait_for_text_input() {
  for _ in $(seq 1 16); do
    if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      return 0
    fi
    sleep 0.35
  done
  return 1
}

_tap_save() {
  uia_save_changes || { echo "FAIL: Save changes entry point missing" >&2; exit 1; }
}

echo "[1/8] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/8] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/8] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null
fname="$(basename "$PDF_REMOTE_PATH")"
sleep 0.4

echo "[4/8] Open PDF via DocumentsUI (persistable grant)"
_open_pdf_via_documentsui_writable "$fname"
sleep 1.0

echo "[5/8] Enable Forms highlight + locate widgets"
_enable_forms_highlight_or_die
centers=()
last_hl_png="${OUT_PREFIX}_hl_last.png"
for _attempt in $(seq 1 22); do
  _screencap_png "$last_hl_png"
  mapfile -t centers < <(_widget_centers_from_highlight "$last_hl_png" 2>/dev/null || true)
  if [[ "${#centers[@]}" -ge 2 ]]; then
    break
  fi
  sleep 0.35
done
if [[ "${#centers[@]}" -lt 2 ]]; then
  echo "FAIL: expected 2 highlighted widgets, got ${#centers[@]}" >&2
  if [[ -f "$last_hl_png" ]]; then
    cp "$last_hl_png" "${OUT_PREFIX}_fail_highlight.png" || true
    echo "  wrote ${OUT_PREFIX}_fail_highlight.png" >&2
  fi
  exit 1
fi
rm -f "$last_hl_png" || true

# By screen order (top -> bottom): multi-select listbox, then editable combobox.
read -r multi_x multi_y <<<"${centers[0]}"
read -r combo_x combo_y <<<"${centers[1]}"

echo "[6/8] Set multi-select listbox + editable combobox"
adb -s "$DEVICE" shell input tap "$multi_x" "$multi_y"
if ! _wait_for_any_alert_dialog; then
  echo "FAIL: multi-select dialog did not appear" >&2
  exit 1
fi
uia_tap_text_contains "$MULTI_A"
sleep 0.25
uia_tap_text_contains "$MULTI_B"
sleep 0.25
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
  echo "FAIL: could not confirm multi-select choices (OK)" >&2
  exit 1
}
sleep 0.8

adb -s "$DEVICE" shell input tap "$combo_x" "$combo_y"
if ! _wait_for_text_input; then
  echo "FAIL: editable combobox dialog did not appear" >&2
  exit 1
fi
before_combo="$(_uia_text_for_res_id "org.opendroidpdf:id/dialog_text_input" 2>/dev/null || true)"
adb -s "$DEVICE" shell input text "$CUSTOM_VALUE"
sleep 0.25
after_combo="$(_uia_text_for_res_id "org.opendroidpdf:id/dialog_text_input" 2>/dev/null || true)"
if [[ "$after_combo" != "$CUSTOM_VALUE" ]]; then
  echo "FAIL: could not input editable combobox value" >&2
  echo "  before: '$before_combo'" >&2
  echo "  after:  '$after_combo'" >&2
  exit 1
fi
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
  echo "FAIL: could not confirm editable combo value (OK)" >&2
  exit 1
}
sleep 0.8
_fail_if_fatal_logcat

# Verify the value applied before saving (avoid false positives where input worked but the widget update didn't).
adb -s "$DEVICE" shell input tap "$combo_x" "$combo_y"
if ! _wait_for_text_input; then
  echo "FAIL: editable combobox dialog did not re-open for verification" >&2
  exit 1
fi
got_now="$(_uia_text_for_res_id "org.opendroidpdf:id/dialog_text_input" 2>/dev/null || true)"
if [[ "$got_now" != "$CUSTOM_VALUE" ]]; then
  echo "FAIL: editable combobox value was not applied (before save)" >&2
  echo "  expected: '$CUSTOM_VALUE'" >&2
  echo "  got:      '$got_now'" >&2
  exit 1
fi
uia_tap_any_res_id "android:id/button2" "com.android.internal:id/button2" || uia_tap_text_contains "Cancel" || true
sleep 0.6

echo "[7/8] Save in-place"
uia_save_changes || { echo "FAIL: Save changes entry point missing" >&2; exit 1; }
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 2.6
_fail_if_fatal_logcat

echo "[8/8] Reopen and verify persisted state"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
_open_pdf_via_documentsui_writable "$fname"
sleep 1.0
_enable_forms_highlight_or_die

centers=()
last_hl_png="${OUT_PREFIX}_hl_last_reopen.png"
for _attempt in $(seq 1 22); do
  _screencap_png "$last_hl_png"
  mapfile -t centers < <(_widget_centers_from_highlight "$last_hl_png" 2>/dev/null || true)
  if [[ "${#centers[@]}" -ge 2 ]]; then
    break
  fi
  sleep 0.35
done
if [[ "${#centers[@]}" -lt 2 ]]; then
  echo "FAIL: expected 2 highlighted widgets after reopen, got ${#centers[@]}" >&2
  if [[ -f "$last_hl_png" ]]; then
    cp "$last_hl_png" "${OUT_PREFIX}_fail_highlight_reopen.png" || true
    echo "  wrote ${OUT_PREFIX}_fail_highlight_reopen.png" >&2
  fi
  exit 1
fi
rm -f "$last_hl_png" || true
read -r multi_x multi_y <<<"${centers[0]}"
read -r combo_x combo_y <<<"${centers[1]}"

adb -s "$DEVICE" shell input tap "$multi_x" "$multi_y"
if ! _wait_for_any_alert_dialog; then
  echo "FAIL: multi-select dialog did not appear after reopen" >&2
  exit 1
fi
_uia_assert_checked_text_eq "$MULTI_A" || { echo "FAIL: expected '$MULTI_A' checked after reopen" >&2; exit 1; }
_uia_assert_checked_text_eq "$MULTI_B" || { echo "FAIL: expected '$MULTI_B' checked after reopen" >&2; exit 1; }
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 0.5

adb -s "$DEVICE" shell input tap "$combo_x" "$combo_y"
if ! _wait_for_text_input; then
  echo "FAIL: editable combobox dialog did not appear after reopen" >&2
  exit 1
fi
got="$(_uia_text_for_res_id "org.opendroidpdf:id/dialog_text_input" 2>/dev/null || true)"
if [[ "$got" != "$CUSTOM_VALUE" ]]; then
  echo "FAIL: expected editable combobox value '$CUSTOM_VALUE' after reopen, got '$got'" >&2
  exit 1
fi
uia_tap_any_res_id "android:id/button2" "com.android.internal:id/button2" || uia_tap_text_contains "Cancel" || true

echo "OK: advanced choice widgets persisted (multi-select + editable combobox)"
