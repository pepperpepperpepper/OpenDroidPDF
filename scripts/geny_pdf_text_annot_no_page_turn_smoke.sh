#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "moving a text annotation should not change pages":
# - Open a 2-page PDF via DocumentsUI (content:// URI so Save is available)
# - Add a FreeText annotation on page 1
# - Select it and drag inside the selection box in ways that would normally page-swipe
# - Assert the viewer remains on page 1/2 throughout
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_text_annot_no_page_turn_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_form_nav.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_text_annot_no_page_turn_smoke.pdf}
TOKEN=${TOKEN:-ODPNOPAGETURN}
TOKEN_INPUT=${TOKEN_INPUT:-$TOKEN}
TOKEN_EXPECTED=${TOKEN_EXPECTED:-$TOKEN}
# OCR can miss trailing characters when the default FreeText box is narrow; use a stable prefix.
TOKEN_SEARCH=${TOKEN_SEARCH:-${TOKEN_EXPECTED:0:8}}
PAGE_INDICATOR=${PAGE_INDICATOR:-1/2}

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

_fail_if_fatal_logcat() {
  if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION|Process ${PKG} \\(pid [0-9]+\\) has died"; then
    echo "FAIL: detected crash in logcat" >&2
    adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|AndroidRuntime|${PKG}" | tail -n 260 >&2 || true
    return 1
  fi
  return 0
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

_ocr_token_center_xy() {
  local png="$1"
  local token="$2"
  tesseract "$png" stdout -l eng --psm 6 tsv 2>/dev/null \
    | awk -F'\t' -v tok="$token" 'NR>1 && $1==5 && index($12,tok)>0 { printf "%d %d\n", ($7 + int($9/2)), ($8 + int($10/2)); found=1; exit } END { exit found?0:1 }'
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
  sleep 1.2
  uia_tap_text_contains "$fname" || {
    echo "FAIL: could not select $fname in DocumentsUI search results" >&2
    adb -s "$DEVICE" logcat -d | tail -n 120 >&2
    exit 1
  }

  uia_assert_in_document_view
}

_wait_for_page_indicator() {
  local want="$1"
  for _ in $(seq 1 24); do
    if uia_has_text_contains "$want"; then
      return 0
    fi
    sleep 0.35
  done
  return 1
}

_dismiss_text_dialog_if_present() {
  # If we're still in add-text mode, taps may open the text dialog instead of selecting.
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    uia_tap_any_res_id "android:id/button2" "com.android.internal:id/button2" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
    sleep 0.6
  fi
}

_exit_text_mode_best_effort() {
  # Exit annotation/edit mode so taps can select existing annotations.
  if uia_tap_any_res_id "org.opendroidpdf:id/menu_accept"; then
    sleep 0.6
  fi
  _dismiss_text_dialog_if_present || true
}

echo "[1/7] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/7] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/7] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null
fname="$(basename "$PDF_REMOTE_PATH")"

echo "[4/7] Open PDF via DocumentsUI (persistable grant)"
_open_pdf_via_documentsui "$fname"
sleep 1.0
if ! _wait_for_page_indicator "$PAGE_INDICATOR"; then
  echo "FAIL: expected initial page indicator '$PAGE_INDICATOR' (is this a multi-page PDF?)" >&2
  exit 1
fi

read -r W H < <(_wm_size)
X=$((W / 2))
Y=$((H * 45 / 100))

echo "[5/7] Add a text annotation on page $PAGE_INDICATOR"
uia_enter_add_text_mode || { echo "FAIL: add-text entry point missing" >&2; exit 1; }
sleep 0.6
adb -s "$DEVICE" shell input tap "$X" "$Y"
sleep 0.9
uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || {
  echo "FAIL: text input dialog did not appear" >&2
  adb -s "$DEVICE" logcat -d | tail -n 160 >&2
  exit 1
}
adb -s "$DEVICE" shell input text "$TOKEN_INPUT"
sleep 0.4
if uia_has_res_id "android:id/button1" "com.android.internal:id/button1"; then
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
    echo "FAIL: could not confirm text annotation dialog" >&2
    exit 1
  }
else
  # Inline editor: commit via focus loss (tap outside the editor).
  blank_x=$((W * 9 / 10))
  blank_y=$((H / 5))
  adb -s "$DEVICE" shell input tap "$blank_x" "$blank_y"
  for _ in $(seq 1 15); do
    if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      break
    fi
    sleep 0.25
  done
fi
sleep 2.0
uia_assert_in_document_view
_fail_if_fatal_logcat
_exit_text_mode_best_effort || true
sleep 0.6

echo "[6/7] Select and drag inside selection box; assert page stays $PAGE_INDICATOR"
if ! _wait_for_page_indicator "$PAGE_INDICATOR"; then
  echo "FAIL: page indicator changed unexpectedly before drag" >&2
  exit 1
fi

# Locate the token on-screen so our selection taps hit the annotation reliably.
# (Best-effort; if OCR isn't available or is flaky, fall back to the center tap coords.)
token_png=""
X_SEL="$X"
Y_SEL="$Y"
if command -v tesseract >/dev/null 2>&1; then
  token_png="$(mktemp -t odp_text_no_page_turn_token_XXXXXX).png"
  _screencap_png "$token_png"
  if read -r X_SEL Y_SEL < <(_ocr_token_center_xy "$token_png" "$TOKEN_SEARCH" 2>/dev/null); then
    :
  else
    echo "WARN: could not OCR-locate token '$TOKEN_SEARCH'; falling back to center tap coords" >&2
    X_SEL="$X"
    Y_SEL="$Y"
  fi
fi

# Tap to select and locate the selection box.
sel_png="$(mktemp -t odp_text_no_page_turn_sel_XXXXXX).png"
bbox=""
offsets=(
  "0 0"
  "18 0" "-18 0" "0 18" "0 -18"
  "34 0" "-34 0" "0 34" "0 -34"
  "52 0" "-52 0" "0 52" "0 -52"
  "70 0" "-70 0" "0 70" "0 -70"
)

for off in "${offsets[@]}"; do
  dx="${off% *}"
  dy="${off#* }"
  tx=$((X_SEL + dx))
  ty=$((Y_SEL + dy))
  if (( tx < 8 )); then tx=8; fi
  if (( ty < 8 )); then ty=8; fi
  if (( tx > W - 8 )); then tx=$((W - 8)); fi
  if (( ty > H - 8 )); then ty=$((H - 8)); fi

  # 1) Tap-select
  adb -s "$DEVICE" shell input tap "$tx" "$ty"
  sleep 0.8
  _dismiss_text_dialog_if_present || true
  _screencap_png "$sel_png"
  bbox="$(_selection_box_bbox_px "$sel_png" || true)"
  if [[ -n "$bbox" ]]; then
    break
  fi

  # 2) Long-press select
  adb -s "$DEVICE" shell input swipe "$tx" "$ty" "$tx" "$ty" 720
  sleep 0.9
  _dismiss_text_dialog_if_present || true
  _screencap_png "$sel_png"
  bbox="$(_selection_box_bbox_px "$sel_png" || true)"
  if [[ -n "$bbox" ]]; then
    break
  fi
done
if [[ -z "$bbox" ]]; then
  echo "FAIL: could not detect selection box around text annotation" >&2
  echo "  screenshot: $sel_png" >&2
  if [[ -n "${token_png:-}" ]]; then
    echo "  token screenshot: $token_png" >&2
  fi
  exit 1
fi
rm -f -- "$token_png" || true
read -r x0 y0 x1 y1 <<<"$bbox"
cx=$(((x0 + x1) / 2))
cy=$(((y0 + y1) / 2))

_assert_still_on_page() {
  if ! _wait_for_page_indicator "$PAGE_INDICATOR"; then
    echo "FAIL: page indicator changed (expected '$PAGE_INDICATOR')" >&2
    exit 1
  fi
}

_assert_still_on_page

# Recompute bbox before each swipe so we always start inside it (prior swipes may move the annot).
_recenter_to_selection_or_fail() {
  local png bbox
  png="$(mktemp -t odp_text_no_page_turn_center_XXXXXX).png"
  _screencap_png "$png"
  bbox="$(_selection_box_bbox_px "$png" || true)"
  rm -f -- "$png" || true
  if [[ -z "$bbox" ]]; then
    echo "FAIL: selection box disappeared before swipe" >&2
    exit 1
  fi
  read -r x0 y0 x1 y1 <<<"$bbox"
  cx=$(((x0 + x1) / 2))
  cy=$(((y0 + y1) / 2))
}

# Horizontal swipe inside selection (common page-turn gesture).
_recenter_to_selection_or_fail
dx=$((W / 2))
end_x=$((cx + dx))
if (( end_x > W - 8 )); then end_x=$((W - 8)); fi
adb -s "$DEVICE" shell input swipe "$cx" "$cy" "$end_x" "$cy" 360
sleep 1.1
_fail_if_fatal_logcat
_assert_still_on_page

# Reverse horizontal swipe inside selection.
_recenter_to_selection_or_fail
end_x=$((cx - dx))
if (( end_x < 8 )); then end_x=8; fi
adb -s "$DEVICE" shell input swipe "$cx" "$cy" "$end_x" "$cy" 360
sleep 1.1
_fail_if_fatal_logcat
_assert_still_on_page

# Vertical swipe inside selection (common continuous-scroll page advance).
# Re-center first so the swipe begins inside the selection box.
_recenter_to_selection_or_fail
dy=$((H / 2))
end_y=$((cy + dy))
if (( end_y > H - 8 )); then end_y=$((H - 8)); fi
adb -s "$DEVICE" shell input swipe "$cx" "$cy" "$cx" "$end_y" 420
sleep 1.1
_fail_if_fatal_logcat
_assert_still_on_page

echo "[6.9/7] Tap bottom-right margin while text annotation is/was selected; assert page stays $PAGE_INDICATOR"
_recenter_to_selection_or_fail
tap_x=$((W - 6))
tap_y=$((H - 6))
adb -s "$DEVICE" shell input tap "$tap_x" "$tap_y"
sleep 1.0
_fail_if_fatal_logcat
_assert_still_on_page

rm -f -- "$sel_png" || true

echo "[7/7] OK"
echo "OK: dragging a text annotation does not change pages ($PAGE_INDICATOR)"
