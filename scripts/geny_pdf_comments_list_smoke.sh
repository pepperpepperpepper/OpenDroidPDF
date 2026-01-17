#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "Comments list + next/prev navigation (PDF)":
# - Open a writable PDF via DocumentsUI (content:// URI so Save stays available)
# - Create 2 FreeText comments at distinct vertical positions
# - Open Comments list, tap an entry to jump/select
# - Use Next/Previous comment navigation and assert the selection box moves
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_comments_list_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_form_nav.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_comments_list_smoke.pdf}
TOKEN1=${TOKEN1:-ODPCOMMENT1}
TOKEN2=${TOKEN2:-ODPCOMMENT2}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_wm_size() {
  local line
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
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

for y in range(h):
  for x in range(w):
    r, g, b, a = px[x, y]
    if a < 200:
      continue
    # Selection box/handles are drawn in a light blue/cyan tint.
    if b > 150 and g > 100 and r < 210 and b > r + 20:
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
  adb -s "$DEVICE" shell input text "$fname"

  for _ in $(seq 1 20); do
    if uia_has_text_contains "$fname"; then
      break
    fi
    sleep 0.35
  done
  uia_tap_text_contains "$fname" || {
    echo "FAIL: could not select $fname in DocumentsUI search results" >&2
    exit 1
  }

  uia_assert_in_document_view
}

_tap_overflow_then_text() {
  local text="$1"
  uia_tap_desc "More options" || return 1
  sleep 0.4
  uia_tap_text_contains "$text"
}

_tap_menu_comments() {
  uia_open_annotations_list
}

_tap_menu_comment_next() {
  uia_tap_any_res_id "org.opendroidpdf:id/menu_comment_next" && return 0
  _tap_overflow_then_text "Next"
}

_tap_menu_comment_prev() {
  uia_tap_any_res_id "org.opendroidpdf:id/menu_comment_previous" && return 0
  _tap_overflow_then_text "Previous"
}

_add_text_comment_at() {
  local x="$1"
  local y="$2"
  local token="$3"

  uia_enter_add_text_mode || { echo "FAIL: add text entry point missing" >&2; exit 1; }
  sleep 0.5
  adb -s "$DEVICE" shell input tap "$x" "$y"

  for _ in $(seq 1 20); do
    if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      break
    fi
    sleep 0.25
  done
  uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || true
  adb -s "$DEVICE" shell input text "$token"
  sleep 0.2
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
    echo "FAIL: could not confirm text dialog" >&2
    exit 1
  }
  sleep 0.9
}

echo "[1/6] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/6] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/6] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null
fname="$(basename "$PDF_REMOTE_PATH")"

echo "[4/6] Open PDF via DocumentsUI (persistable grant)"
_open_pdf_via_documentsui "$fname"
sleep 1.0

echo "[5/6] Create two comments"
read -r w h < <(_wm_size)
cx=$((w * 5 / 10))
y_top=$((h * 30 / 100))
y_bot=$((h * 70 / 100))
_add_text_comment_at "$cx" "$y_top" "$TOKEN1"
_add_text_comment_at "$cx" "$y_bot" "$TOKEN2"

echo "[6/6] Comments list -> jump -> next/prev"
_tap_menu_comments || { echo "FAIL: could not open Comments list" >&2; exit 1; }
sleep 0.8

for _ in $(seq 1 20); do
  if uia_has_text_contains "$TOKEN1"; then
    break
  fi
  sleep 0.25
done
uia_tap_text_contains "$TOKEN1" || { echo "FAIL: could not tap comment entry for $TOKEN1" >&2; exit 1; }
sleep 0.9

shot1="$(mktemp -t odp_comments1_XXXXXX).png"
shot2="$(mktemp -t odp_comments2_XXXXXX).png"
shot3="$(mktemp -t odp_comments3_XXXXXX).png"
_screencap_png "$shot1"
bbox1="$(_selection_box_bbox_px "$shot1" || true)"
if [[ -z "$bbox1" ]]; then
  echo "FAIL: selection box not detected after jump-to ($TOKEN1)" >&2
  echo "  screenshot: $shot1" >&2
  exit 1
fi
read -r _x1 y1 _x2 _y2 <<<"$bbox1"

_tap_menu_comment_next || { echo "FAIL: could not navigate to Next comment" >&2; exit 1; }
sleep 1.1
_screencap_png "$shot2"
bbox2="$(_selection_box_bbox_px "$shot2" || true)"
if [[ -z "$bbox2" ]]; then
  echo "FAIL: selection box not detected after Next comment" >&2
  echo "  screenshot: $shot2" >&2
  exit 1
fi
read -r _x1 y2 _x2 _y2 <<<"$bbox2"

if (( y2 <= y1 + 40 )); then
  echo "FAIL: expected selection to move downward after Next comment (y1=$y1 y2=$y2)" >&2
  echo "  screenshot1: $shot1" >&2
  echo "  screenshot2: $shot2" >&2
  exit 1
fi

_tap_menu_comment_prev || { echo "FAIL: could not navigate to Previous comment" >&2; exit 1; }
sleep 1.1
_screencap_png "$shot3"
bbox3="$(_selection_box_bbox_px "$shot3" || true)"
if [[ -z "$bbox3" ]]; then
  echo "FAIL: selection box not detected after Previous comment" >&2
  echo "  screenshot: $shot3" >&2
  exit 1
fi
read -r _x1 y3 _x2 _y2 <<<"$bbox3"

dy=$(( y3 > y1 ? y3 - y1 : y1 - y3 ))
if (( dy > 60 )); then
  echo "FAIL: expected Previous to return near original selection (y1=$y1 y3=$y3 dy=$dy)" >&2
  echo "  screenshot1: $shot1" >&2
  echo "  screenshot3: $shot3" >&2
  exit 1
fi

echo "OK: Comments list jump + next/prev navigation works (PDF)"
