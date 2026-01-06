#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "FreeText auto-fits/grows after edit (Acrobat-ish)":
# - Open a PDF via DocumentsUI (content:// URI so Save is available)
# - Add a small FreeText annotation
# - Measure the on-screen selection box
# - Edit the FreeText to a much longer string
# - Assert the selection box grows (width and/or height increases)
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_text_annot_autofit_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_with_text.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_text_annot_autofit_smoke.pdf}
TOKEN=${TOKEN:-ODP_AUTOFIT}
TOKEN_INPUT=${TOKEN_INPUT:-$TOKEN}
TOKEN_SEARCH=${TOKEN_SEARCH:-AUTOFIT}
TOKEN_SUFFIX_EDIT=${TOKEN_SUFFIX_EDIT:-_AUTOFIT_0123456789_0123456789_0123456789}
PAGE_INDICATOR=${PAGE_INDICATOR:-1/1}

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

_ocr_token_center_xy() {
  local png="$1"
  local token="$2"
  tesseract "$png" stdout -l eng --psm 6 tsv 2>/dev/null \
    | awk -F'\t' -v tok="$token" '
      BEGIN { gsub(/[^[:alnum:]]/, "", tok); tok=toupper(tok); }
      NR>1 && $1==5 {
        w=$12;
        gsub(/[^[:alnum:]]/, "", w);
        w=toupper(w);
        if (tok != "" && index(w,tok)>0) { printf "%d %d\n", ($7 + int($9/2)), ($8 + int($10/2)); found=1; exit }
      }
      END { exit found?0:1 }'
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
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    uia_tap_any_res_id "android:id/button2" "com.android.internal:id/button2" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
    sleep 0.6
  fi
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
_wait_for_page_indicator "$PAGE_INDICATOR" || {
  echo "FAIL: expected page indicator '$PAGE_INDICATOR'" >&2
  exit 1
}

read -r W H < <(_wm_size)
X=$((W / 2))
Y=$((H * 45 / 100))

echo "[5/6] Add a short FreeText annotation"
uia_tap_any_res_id "org.opendroidpdf:id/menu_add_text_annot" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  uia_tap_text_contains "Add text" || {
    echo "FAIL: add-text action not found" >&2
    exit 1
  }
}
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
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
  echo "FAIL: could not confirm text annotation dialog" >&2
  exit 1
}
sleep 1.4
uia_assert_in_document_view
_fail_if_fatal_logcat

echo "[5.5/6] Capture bbox before edit"
# Exit add-text mode so taps select instead of creating a new annotation.
if uia_tap_any_res_id "org.opendroidpdf:id/menu_accept"; then
  sleep 0.6
fi
_dismiss_text_dialog_if_present || true

# Locate the token on-screen so our selection taps hit the annotation reliably.
token_png=""
X_SEL="$X"
Y_SEL="$Y"
if command -v tesseract >/dev/null 2>&1; then
  token_png="$(mktemp -t odp_text_autofit_token_XXXXXX).png"
  _screencap_png "$token_png"
  if read -r X_SEL Y_SEL < <(_ocr_token_center_xy "$token_png" "$TOKEN_SEARCH" 2>/dev/null); then
    :
  else
    echo "WARN: could not OCR-locate token '$TOKEN_SEARCH'; falling back to center tap coords" >&2
    X_SEL="$X"
    Y_SEL="$Y"
  fi
fi

# Select the annotation and detect the selection bbox.
offsets=(
  "0 0"
  "18 0" "-18 0" "0 18" "0 -18"
  "34 0" "-34 0" "0 34" "0 -34"
  "52 0" "-52 0" "0 52" "0 -52"
  "70 0" "-70 0" "0 70" "0 -70"
)
before_png="$(mktemp -t odp_text_autofit_before_XXXXXX).png"
found_bbox=0
for off in "${offsets[@]}"; do
  dx="${off% *}"
  dy="${off#* }"
  tx=$((X_SEL + dx))
  ty=$((Y_SEL + dy))
  if (( tx < 8 )); then tx=8; fi
  if (( ty < 8 )); then ty=8; fi
  if (( tx > W - 8 )); then tx=$((W - 8)); fi
  if (( ty > H - 8 )); then ty=$((H - 8)); fi

  adb -s "$DEVICE" shell input tap "$tx" "$ty"
  sleep 0.8
  _dismiss_text_dialog_if_present || true
  _screencap_png "$before_png"
  read -r x0 y0 x1 y1 < <(_selection_box_bbox_px "$before_png" || echo "")
  if [[ -n "${x0:-}" && -n "${y0:-}" && -n "${x1:-}" && -n "${y1:-}" ]]; then
    found_bbox=1
    break
  fi

  adb -s "$DEVICE" shell input swipe "$tx" "$ty" "$tx" "$ty" 720
  sleep 0.9
  _dismiss_text_dialog_if_present || true
  _screencap_png "$before_png"
  read -r x0 y0 x1 y1 < <(_selection_box_bbox_px "$before_png" || echo "")
  if [[ -n "${x0:-}" && -n "${y0:-}" && -n "${x1:-}" && -n "${y1:-}" ]]; then
    found_bbox=1
    break
  fi
done

if [[ -z "${x0:-}" || -z "${y0:-}" || -z "${x1:-}" || -z "${y1:-}" ]]; then
  echo "FAIL: could not detect selection bbox before edit" >&2
  echo "  screenshot: $before_png" >&2
  if [[ -n "${token_png:-}" ]]; then
    echo "  token screenshot: $token_png" >&2
  fi
  exit 1
fi
rm -f -- "$token_png" || true
before_w=$((x1 - x0))
before_h=$((y1 - y0))
tap_x=$(((x0 + x1) / 2))
tap_y=$(((y0 + y1) / 2))
tap_safe_y=$((y0 + (before_h * 3 / 4)))
if (( tap_safe_y > y1 - 8 )); then tap_safe_y=$((y1 - 8)); fi

echo "[6/6] Edit to a much longer string; assert bbox grows"
adb -s "$DEVICE" shell input tap "$tap_x" "$tap_safe_y"
sleep 0.35
adb -s "$DEVICE" shell input tap "$tap_x" "$tap_safe_y"
sleep 0.9
for _ in $(seq 1 12); do
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    break
  fi
  sleep 0.25
done
uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || {
  echo "FAIL: edit text dialog did not appear after tapping the existing annotation" >&2
  after_fail_png="$(mktemp -t odp_text_autofit_edit_fail_XXXXXX).png"
  _screencap_png "$after_fail_png" || true
  echo "  wrote $after_fail_png" >&2
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

after_png="$(mktemp -t odp_text_autofit_after_XXXXXX).png"
_screencap_png "$after_png"
read -r x2 y2 x3 y3 < <(_selection_box_bbox_px "$after_png" || echo "")
if [[ -z "${x2:-}" || -z "${y2:-}" || -z "${x3:-}" || -z "${y3:-}" ]]; then
  adb -s "$DEVICE" shell input tap "$tap_x" "$tap_y"
  sleep 0.9
  _dismiss_text_dialog_if_present || true
  _screencap_png "$after_png"
  read -r x2 y2 x3 y3 < <(_selection_box_bbox_px "$after_png" || echo "")
fi
if [[ -z "${x2:-}" || -z "${y2:-}" || -z "${x3:-}" || -z "${y3:-}" ]]; then
  echo "FAIL: could not detect selection bbox after edit" >&2
  echo "  screenshot: $after_png" >&2
  exit 1
fi
after_w=$((x3 - x2))
after_h=$((y3 - y2))

dw=$((after_w - before_w))
dh=$((after_h - before_h))
if (( dw < 14 && dh < 14 )); then
  echo "FAIL: expected bbox to grow after edit (dw>=14 or dh>=14), got dw=$dw dh=$dh" >&2
  echo "  before: $before_png (w=$before_w h=$before_h) after: $after_png (w=$after_w h=$after_h)" >&2
  exit 1
fi

echo "OK: bbox grew after edit (dw=$dw dh=$dh)"
