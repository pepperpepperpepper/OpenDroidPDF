#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "PDF mark-up text (highlight/underline) still works end-to-end":
# - Push a text PDF to /sdcard/Download
# - Open it via DocumentsUI (content:// URI so Save stays available)
# - Create 2 highlights and delete them (regression: 2nd delete blanks page)
# - Create an underline and assert it appears near the selected text (regression: underline far below)
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_text_markup_smoke.sh
#   PDF_LOCAL=/path/to/list.pdf ./scripts/geny_pdf_text_markup_smoke.sh
#
# Requirements (host):
#   - python3 + pillow (PIL)

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_with_text.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_text_markup_smoke.pdf}
OUT_PREFIX="${OUT_PREFIX:-tmp_geny_pdf_text_markup}"

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

# Tunables:
DARK_PIXEL_MIN_COUNT=${DARK_PIXEL_MIN_COUNT:-200}
ASSERT_UNDERLINE_NEAR_SELECTION=${ASSERT_UNDERLINE_NEAR_SELECTION:-1}
UNDERLINE_MAX_DY_PX=${UNDERLINE_MAX_DY_PX:-260}

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null
python3 - <<'PY' >/dev/null 2>&1 || {
import PIL  # noqa: F401
PY
  echo "FAIL: python3 Pillow (PIL) not available; install it (e.g. 'python3 -m pip install pillow')." >&2
  exit 2
}

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p >"$out_png"
}

_pick_dark_text_xy() {
  local png="$1"
  local ystart="${2:-200}"
  local yend="${3:-900}"
  python3 - "$png" "$ystart" "$yend" <<'PY'
import sys
from PIL import Image

path = sys.argv[1]
ystart = int(sys.argv[2])
yend = int(sys.argv[3])

img = Image.open(path).convert("RGB")
w, h = img.size
px = img.load()

ystart = max(0, min(h, ystart))
yend = max(0, min(h, yend))
if yend <= ystart:
    yend = h

best = None
best_score = 10**9
count = 0

step = 2
xend = min(w, int(w * 0.80))
for y in range(ystart, yend, step):
    for x in range(0, xend, step):
        r, g, b = px[x, y]
        # Dark pixel (text) on light background.
        if r < 80 and g < 80 and b < 80:
            count += 1
            score = r + g + b
            if score < best_score:
                best_score = score
                best = (x, y)

if best is None:
    raise SystemExit(2)

print(f"{best[0]} {best[1]} {count}")
PY
}

_count_dark_text_pixels() {
  local png="$1"
  local ystart="${2:-200}"
  local yend="${3:-0}"
  python3 - "$png" "$ystart" "$yend" <<'PY'
import sys
from PIL import Image

path = sys.argv[1]
ystart = int(sys.argv[2])
yend = int(sys.argv[3])

img = Image.open(path).convert("RGB")
w, h = img.size
px = img.load()

ystart = max(0, min(h, ystart))
yend = h if yend <= 0 else max(0, min(h, yend))

count = 0
step = 2
xend = min(w, int(w * 0.92))
for y in range(ystart, yend, step):
    for x in range(0, xend, step):
        r, g, b = px[x, y]
        if r < 80 and g < 80 and b < 80:
            count += 1
print(count)
PY
}

_diff_bbox() {
  local before_png="$1"
  local after_png="$2"
  local skip_top="${3:-180}"
  python3 - "$before_png" "$after_png" "$skip_top" <<'PY'
import sys
from PIL import Image

before_path, after_path, skip_top = sys.argv[1], sys.argv[2], int(sys.argv[3])

a = Image.open(before_path).convert("RGB")
b = Image.open(after_path).convert("RGB")
if a.size != b.size:
    raise SystemExit(2)
w, h = a.size
pa = a.load()
pb = b.load()

minx = miny = None
maxx = maxy = None
changed = 0

step = 2
skip_top = max(0, min(h, skip_top))

for y in range(skip_top, h, step):
    for x in range(0, w, step):
        ar, ag, ab = pa[x, y]
        br, bg, bb = pb[x, y]
        if abs(ar - br) + abs(ag - bg) + abs(ab - bb) < 60:
            continue
        changed += 1
        minx = x if minx is None else min(minx, x)
        miny = y if miny is None else min(miny, y)
        maxx = x if maxx is None else max(maxx, x)
        maxy = y if maxy is None else max(maxy, y)

if minx is None:
    print("")
else:
    print(f"{minx} {miny} {maxx} {maxy} {changed}")
PY
}

_fail_if_fatal_logcat() {
  if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION|Process ${PKG} \\(pid [0-9]+\\) has died"; then
    echo "FAIL: detected crash in logcat" >&2
    adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|AndroidRuntime|${PKG}" | tail -n 260 >&2 || true
    return 1
  fi
  return 0
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
  uia_tap_text_contains "Downloads" || uia_tap_text_contains "Download" || {
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

  for _ in $(seq 1 25); do
    if uia_has_text_contains "$fname"; then
      break
    fi
    sleep 0.25
  done
  if ! uia_tap_text_contains "$fname"; then
    # Some DocumentsUI variants don't include the filename text in the row; try tapping the thumbnail.
    uia_tap_any_res_id "com.android.documentsui:id/thumbnail" || uia_tap_any_res_id "com.android.documentsui:id/icon_mime" || {
      echo "FAIL: could not select $fname in DocumentsUI search results" >&2
      exit 1
    }
  fi

  # Some picker variants require hitting an "Open" / checkmark action.
  uia_tap_any_res_id "com.android.documentsui:id/action_menu_open" || \
  uia_tap_any_res_id "com.android.documentsui:id/open" || \
  uia_tap_desc "Open" || true

  uia_assert_in_document_view
}

_long_press_xy() {
  local x="$1"
  local y="$2"
  local duration_ms="${3:-1500}"
  adb -s "$DEVICE" shell input swipe "$x" "$y" "$x" "$y" "$duration_ms"
}

_tap_menu_action_or_text() {
  local rid="$1"
  local text="$2"
  uia_tap_any_res_id "$rid" && return 0
  uia_tap_text_contains "$text" && return 0
  if uia_tap_desc "More options"; then
    sleep 0.4
  fi
  uia_tap_text_contains "$text"
}

echo "[1/8] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/8] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/8] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null
fname="$(basename "$PDF_REMOTE_PATH")"

echo "[4/8] Open PDF via DocumentsUI (persistable grant)"
_open_pdf_via_documentsui "$fname"
sleep 1.0

echo "[5/8] Choose text coordinates (for 2 highlights)"
SHOT_BEFORE="${OUT_PREFIX}_before.png"
_screencap_png "$SHOT_BEFORE"
echo "  wrote $SHOT_BEFORE" >&2

read -r x1 y1 dark1 < <(_pick_dark_text_xy "$SHOT_BEFORE" 200 700 || echo "")
if [[ -z "${x1:-}" || -z "${y1:-}" ]]; then
  echo "FAIL: could not locate dark text pixels (upper region) for selection" >&2
  exit 1
fi
if (( dark1 < DARK_PIXEL_MIN_COUNT )); then
  echo "FAIL: not enough dark text pixels in upper region (count=$dark1, min=$DARK_PIXEL_MIN_COUNT)" >&2
  exit 1
fi

read -r x2 y2 dark2 < <(_pick_dark_text_xy "$SHOT_BEFORE" 700 1500 || echo "")
if [[ -z "${x2:-}" || -z "${y2:-}" || "$dark2" == "0" ]]; then
  echo "WARN: could not locate dark text pixels in lower region; highlight-delete 2x test will reuse the first coordinate" >&2
  x2="$x1"
  y2="$y1"
fi

echo "  selection1: $x1,$y1 (dark_count=$dark1)" >&2
echo "  selection2: $x2,$y2 (dark_count=${dark2:-unknown})" >&2

echo "[6/8] Create 2 highlights, then delete them (regression: 2nd delete blanks page)"
for pass in 1 2; do
  if [[ "$pass" == "1" ]]; then x="$x1"; y="$y1"; else x="$x2"; y="$y2"; fi
  _long_press_xy "$x" "$y" 1500
  sleep 1.0
  _tap_menu_action_or_text "org.opendroidpdf:id/menu_highlight" "Highlight" || {
    echo "FAIL: could not find Highlight action after long-press selection" >&2
    exit 1
  }
  sleep 0.9
  _fail_if_fatal_logcat
  # Exit selection mode if action-mode items are still visible.
  if uia_has_res_id "org.opendroidpdf:id/menu_highlight"; then
    adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
    sleep 0.35
  fi
done

for pass in 1 2; do
  if [[ "$pass" == "1" ]]; then x="$x1"; y="$y1"; else x="$x2"; y="$y2"; fi
  adb -s "$DEVICE" shell input tap "$x" "$y"
  sleep 0.9
  _tap_menu_action_or_text "org.opendroidpdf:id/menu_delete_annotation" "Delete" || {
    echo "FAIL: could not find Delete action for selected highlight" >&2
    exit 1
  }
  sleep 0.6
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
  sleep 1.0
  _fail_if_fatal_logcat

  SHOT_AFTER_DEL="${OUT_PREFIX}_after_delete_${pass}.png"
  _screencap_png "$SHOT_AFTER_DEL"
  echo "  wrote $SHOT_AFTER_DEL" >&2

  dark_after="$(_count_dark_text_pixels "$SHOT_AFTER_DEL" 200 0 || echo 0)"
  if [[ -z "$dark_after" ]]; then dark_after=0; fi
  if (( dark_after < DARK_PIXEL_MIN_COUNT )); then
    echo "FAIL: page looks blank/white after delete #${pass} (dark_count=$dark_after, min=$DARK_PIXEL_MIN_COUNT)" >&2
    exit 1
  fi
done

echo "[7/8] Underline selection should appear near selected text (regression: underline far below)"
SHOT_UNDERLINE_BEFORE="${OUT_PREFIX}_underline_before.png"
SHOT_UNDERLINE_AFTER="${OUT_PREFIX}_underline_after.png"
_screencap_png "$SHOT_UNDERLINE_BEFORE"
echo "  wrote $SHOT_UNDERLINE_BEFORE" >&2

read -r ux uy darku < <(_pick_dark_text_xy "$SHOT_UNDERLINE_BEFORE" 200 700 || echo "")
if [[ -z "${ux:-}" || -z "${uy:-}" ]]; then
  echo "FAIL: could not locate dark text pixels (upper region) for underline selection" >&2
  exit 1
fi
if (( darku < DARK_PIXEL_MIN_COUNT )); then
  echo "FAIL: not enough dark text pixels in upper region (count=$darku, min=$DARK_PIXEL_MIN_COUNT)" >&2
  exit 1
fi

_long_press_xy "$ux" "$uy" 1500
sleep 1.0
_tap_menu_action_or_text "org.opendroidpdf:id/menu_underline" "Underline" || {
  echo "FAIL: could not find Underline action after long-press selection" >&2
  exit 1
}
sleep 0.9
_fail_if_fatal_logcat

# Exit selection mode if still active to stabilize screenshots.
if uia_has_res_id "org.opendroidpdf:id/menu_underline"; then
  adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
  sleep 0.35
fi

_screencap_png "$SHOT_UNDERLINE_AFTER"
echo "  wrote $SHOT_UNDERLINE_AFTER" >&2

diff_bbox="$(_diff_bbox "$SHOT_UNDERLINE_BEFORE" "$SHOT_UNDERLINE_AFTER" 180 || true)"
echo "  selection=$ux,$uy diff_bbox=${diff_bbox:-<none>}" >&2
if [[ "$ASSERT_UNDERLINE_NEAR_SELECTION" == "1" ]]; then
  if [[ -z "${diff_bbox:-}" ]]; then
    echo "FAIL: underline did not produce visible screen diff (no bbox)" >&2
    exit 1
  fi
  read -r dx0 dy0 dx1 dy1 dcount < <(echo "$diff_bbox")
  dcy=$(((dy0 + dy1) / 2))
  dy=$((dcy - uy))
  abs_dy="${dy#-}"
  if (( abs_dy > UNDERLINE_MAX_DY_PX )); then
    echo "FAIL: underline appears far from selection (abs(dy)=${abs_dy}px > ${UNDERLINE_MAX_DY_PX}px)" >&2
    echo "  selection_y=$uy diff_center_y=$dcy bbox=[$dx0,$dy0]-[$dx1,$dy1] changed=$dcount" >&2
    exit 1
  fi
fi

echo "[8/8] Save changes"
uia_save_changes || { echo "FAIL: could not trigger Save changes" >&2; exit 1; }
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 1.5
_fail_if_fatal_logcat

underline_check="disabled"
if [[ "$ASSERT_UNDERLINE_NEAR_SELECTION" == "1" ]]; then
  underline_check="enabled"
fi
echo "OK: markup highlight/delete stable; underline positioning check ${underline_check}"
