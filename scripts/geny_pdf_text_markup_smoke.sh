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
# Keep smokes independent of MANAGE_EXTERNAL_STORAGE by defaulting to app-private storage.
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/data/data/org.opendroidpdf/files/odp_text_markup_smoke.pdf}
OUT_PREFIX="${OUT_PREFIX:-tmp_geny_pdf_text_markup}"

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

# Tunables:
DARK_PIXEL_MIN_COUNT=${DARK_PIXEL_MIN_COUNT:-200}
ASSERT_UNDERLINE_NEAR_SELECTION=${ASSERT_UNDERLINE_NEAR_SELECTION:-1}
UNDERLINE_MAX_DY_PX=${UNDERLINE_MAX_DY_PX:-260}
ASSERT_MARKUP_NEAR_SELECTION=${ASSERT_MARKUP_NEAR_SELECTION:-1}
MARKUP_MAX_DY_PX=${MARKUP_MAX_DY_PX:-260}
RUN_STRIKEOUT_PLACEMENT_CHECK=${RUN_STRIKEOUT_PLACEMENT_CHECK:-1}
RUN_ZOOM_MARKUP_CHECK=${RUN_ZOOM_MARKUP_CHECK:-0}
UIA_ZOOM_TEST=${UIA_ZOOM_TEST:-org.opendroidpdf.uia.ZoomPinchTest#testPinchOutOnlyDoesNotCrash}

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

count = 0

# Avoid selecting UI chrome (nav bar) or margins that may not hit selectable text.
nav_pad = 120
yend = min(yend, max(ystart + 1, h - nav_pad))

cx = w / 2.0
cy = (ystart + yend) / 2.0

best = None
best_dist = 10**18
best_dark = 10**9

step = 2
xstart = max(60, int(w * 0.10))
xend = min(w, int(w * 0.92))
for y in range(ystart, yend, step):
    for x in range(xstart, xend, step):
        r, g, b = px[x, y]
        # Dark pixel (text) on light background.
        if r < 80 and g < 80 and b < 80:
            count += 1
            dark = r + g + b
            dist = (x - cx) ** 2 + (y - cy) ** 2
            if dist < best_dist or (dist == best_dist and dark < best_dark):
                best_dist = dist
                best_dark = dark
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
nav_pad = 120
yend = h if yend <= 0 else max(0, min(h, yend))
yend = min(yend, max(ystart + 1, h - nav_pad))

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

step = 1
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

_long_press_xy() {
  local x="$1"
  local y="$2"
  local duration_ms="${3:-1500}"
  adb -s "$DEVICE" shell input swipe "$x" "$y" "$x" "$y" "$duration_ms"
}

_find_quick_actions_bar_near_xyxy() {
  # UIAutomator does not reliably expose some in-document quick-action popups (they can be
  # rendered in a PopupWindow without an accessibility tree). Detect the overlay bar by
  # screenshot analysis and return bounds: x0 y0 x1 y1.
  local png="$1"
  local sx="$2"
  local sy="$3"
  python3 - "$png" "$sx" "$sy" <<'PY'
from collections import deque
from PIL import Image
import sys

png = sys.argv[1]
sx = int(sys.argv[2])
sy = int(sys.argv[3])

im = Image.open(png).convert("RGBA")
w, h = im.size
px = im.load()

top_pad = int(h * 0.12)
bot_pad = int(h * 0.12)

def is_candidate(r, g, b, a):
    if a < 200:
        return False
    # Low saturation (grey-ish), excludes red selection boxes etc.
    if abs(r - g) > 50 or abs(r - b) > 50 or abs(g - b) > 50:
        return False
    # Exclude near-white background and near-black text.
    if r > 245 and g > 245 and b > 245:
        return False
    if r < 35 and g < 35 and b < 35:
        return False
    return True

mask = [[False] * w for _ in range(h)]
for y in range(top_pad, max(top_pad + 1, h - bot_pad)):
    row = mask[y]
    for x in range(w):
        r, g, b, a = px[x, y]
        if is_candidate(r, g, b, a):
            row[x] = True

visited = [[False] * w for _ in range(h)]
cands = []

for y in range(top_pad, max(top_pad + 1, h - bot_pad)):
    for x in range(w):
        if not mask[y][x] or visited[y][x]:
            continue
        q = deque([(x, y)])
        visited[y][x] = True
        minx = maxx = x
        miny = maxy = y
        count = 0
        while q:
            cx, cy = q.popleft()
            count += 1
            if cx < minx:
                minx = cx
            if cx > maxx:
                maxx = cx
            if cy < miny:
                miny = cy
            if cy > maxy:
                maxy = cy
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = cx + dx, cy + dy
                if 0 <= nx < w and 0 <= ny < h and not visited[ny][nx] and mask[ny][nx]:
                    visited[ny][nx] = True
                    q.append((nx, ny))

        bw = maxx - minx + 1
        bh = maxy - miny + 1

        # Heuristic bounds for the quick-actions bar.
        if count < 1200:
            continue
        if bw < 70 or bw > int(w * 0.95):
            continue
        if bh < 26 or bh > 140:
            continue

        cx = (minx + maxx) / 2.0
        cy = (miny + maxy) / 2.0
        dist = (cx - sx) ** 2 + (cy - sy) ** 2
        # Prefer closer bars; tie-breaker prefers larger components.
        cands.append((dist, -count, minx, miny, maxx, maxy))

if not cands:
    raise SystemExit(1)

cands.sort()
_, _, l, t, r, b = cands[0]
print(l, t, r, b)
PY
}

_tap_quick_actions_bar_delete_near() {
  local sx="$1"
  local sy="$2"
  local png="${OUT_PREFIX}_quick_actions.png"
  _screencap_png "$png"
  local l t r b w x y
  if ! read -r l t r b < <(_find_quick_actions_bar_near_xyxy "$png" "$sx" "$sy" 2>/dev/null); then
    return 1
  fi
  w=$((r - l))
  # Right-most button in the small bar is "Delete".
  x=$((l + (w * 75 / 100)))
  y=$(((t + b) / 2))
  adb -s "$DEVICE" shell input tap "$x" "$y"
  return 0
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

_wait_for_res_id() {
  local rid="$1"
  local timeout_s="${2:-4}"
  local deadline=$((SECONDS + timeout_s))
  while (( SECONDS < deadline )); do
    if uia_has_res_id "$rid"; then
      return 0
    fi
    sleep 0.2
  done
  return 1
}

_markup_check_near_selection_y() {
  local label="$1"
  local rid="$2"
  local text="$3"
  local ystart="$4"
  local yend="$5"
  local max_dy_px="$6"
  local assert_near="${7:-1}"

  local before after
  before="${OUT_PREFIX}_${label}_before.png"
  after="${OUT_PREFIX}_${label}_after.png"

  _screencap_png "$before"
  echo "  wrote $before" >&2

  local sx sy dark
  read -r sx sy dark < <(_pick_dark_text_xy "$before" "$ystart" "$yend" || echo "")
  if [[ -z "${sx:-}" || -z "${sy:-}" ]]; then
    echo "FAIL: could not locate dark text pixels for ${label} selection" >&2
    return 1
  fi
  if (( dark < DARK_PIXEL_MIN_COUNT )); then
    echo "FAIL: not enough dark text pixels for ${label} selection (count=$dark, min=$DARK_PIXEL_MIN_COUNT)" >&2
    return 1
  fi

  _long_press_xy "$sx" "$sy" 1500
  sleep 0.4
  if ! _wait_for_res_id "$rid" 6; then
    echo "FAIL: selection action mode did not appear after long-press (${label})" >&2
    return 1
  fi
  _tap_menu_action_or_text "$rid" "$text" || {
    echo "FAIL: could not find ${text} action after long-press selection (${label})" >&2
    return 1
  }
  sleep 0.9
  _fail_if_fatal_logcat

  # Exit selection mode if still active to stabilize screenshots.
  if uia_has_res_id "$rid"; then
    uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
    sleep 0.35
  fi

  _screencap_png "$after"
  echo "  wrote $after" >&2

  local diff_bbox
  diff_bbox="$(_diff_bbox "$before" "$after" 180 || true)"
  echo "  selection=$sx,$sy diff_bbox=${diff_bbox:-<none>}" >&2

  if [[ "$assert_near" == "1" ]]; then
    if [[ -z "${diff_bbox:-}" ]]; then
      echo "FAIL: markup did not produce visible screen diff (no bbox) (${label})" >&2
      return 1
    fi
    local dx0 dy0 dx1 dy1 dcount dcy dy abs_dy
    read -r dx0 dy0 dx1 dy1 dcount < <(echo "$diff_bbox")
    dcy=$(((dy0 + dy1) / 2))
    dy=$((dcy - sy))
    abs_dy="${dy#-}"
    if (( abs_dy > max_dy_px )); then
      echo "FAIL: markup appears far from selection (${label}) (abs(dy)=${abs_dy}px > ${max_dy_px}px)" >&2
      echo "  selection_y=$sy diff_center_y=$dcy bbox=[$dx0,$dy0]-[$dx1,$dy1] changed=$dcount" >&2
      return 1
    fi
  fi

  return 0
}

_markup_check_contains_selection() {
  local label="$1"
  local rid="$2"
  local text="$3"
  local ystart="$4"
  local yend="$5"
  local margin_px="${6:-60}"
  local assert_contains="${7:-1}"

  local before after
  before="${OUT_PREFIX}_${label}_before.png"
  after="${OUT_PREFIX}_${label}_after.png"

  _screencap_png "$before"
  echo "  wrote $before" >&2

  local sx sy dark
  read -r sx sy dark < <(_pick_dark_text_xy "$before" "$ystart" "$yend" || echo "")
  if [[ -z "${sx:-}" || -z "${sy:-}" ]]; then
    echo "FAIL: could not locate dark text pixels for ${label} selection" >&2
    return 1
  fi
  if (( dark < DARK_PIXEL_MIN_COUNT )); then
    echo "FAIL: not enough dark text pixels for ${label} selection (count=$dark, min=$DARK_PIXEL_MIN_COUNT)" >&2
    return 1
  fi

  _long_press_xy "$sx" "$sy" 1500
  sleep 0.4
  if ! _wait_for_res_id "$rid" 6; then
    echo "FAIL: selection action mode did not appear after long-press (${label})" >&2
    return 1
  fi
  _tap_menu_action_or_text "$rid" "$text" || {
    echo "FAIL: could not find ${text} action after long-press selection (${label})" >&2
    return 1
  }
  sleep 0.9
  _fail_if_fatal_logcat

  # Exit selection mode if still active to stabilize screenshots.
  if uia_has_res_id "$rid"; then
    uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
    sleep 0.35
  fi

  _screencap_png "$after"
  echo "  wrote $after" >&2

  local diff_bbox
  diff_bbox="$(_diff_bbox "$before" "$after" 180 || true)"
  echo "  selection=$sx,$sy diff_bbox=${diff_bbox:-<none>}" >&2

  if [[ "$assert_contains" == "1" ]]; then
    if [[ -z "${diff_bbox:-}" ]]; then
      echo "FAIL: markup did not produce visible screen diff (no bbox) (${label})" >&2
      return 1
    fi
    local dx0 dy0 dx1 dy1 dcount
    read -r dx0 dy0 dx1 dy1 dcount < <(echo "$diff_bbox")
    if (( sx < dx0 - margin_px || sx > dx1 + margin_px || sy < dy0 - margin_px || sy > dy1 + margin_px )); then
      echo "FAIL: markup bbox does not cover selection (${label}) (margin=${margin_px}px)" >&2
      echo "  selection=$sx,$sy bbox=[$dx0,$dy0]-[$dx1,$dy1] changed=$dcount" >&2
      return 1
    fi
  fi

  return 0
}

echo "[1/11] Install debug APK"
_install_out="$(adb -s "$DEVICE" install -r "$APK" 2>&1 || true)"
if [[ "$_install_out" != *"Success"* ]]; then
  if [[ "$_install_out" == *"INSTALL_FAILED_UPDATE_INCOMPATIBLE"* ]]; then
    echo "[1/11] Signature mismatch; uninstalling $PKG and retrying install" >&2
    adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
    adb -s "$DEVICE" install -r "$APK" >/dev/null
  else
    printf '%s\n' "$_install_out" >&2
    exit 1
  fi
fi

echo "[2/11] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/11] Stage fixture PDF"
if [[ "$PDF_REMOTE_PATH" == "/data/data/${PKG}/"* ]]; then
  rel="${PDF_REMOTE_PATH#/data/data/${PKG}/}"
  adb -s "$DEVICE" shell "run-as $PKG sh -lc 'mkdir -p \"$(dirname "$rel")\" && cat > \"${rel}\"'" <"$PDF_LOCAL"
else
  adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null
fi

echo "[4/11] Launch viewer with file:// PDF"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE_PATH" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view || { echo "FAIL: did not enter document view" >&2; exit 1; }
sleep 1.0

echo "[5/11] Choose text coordinates (for 2 highlights)"
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

hx1="$x1"; hy1="$y1"
hx2="$x2"; hy2="$y2"

echo "[6/11] Create 2 highlights, then delete them (regression: 2nd delete blanks page)"
for pass in 1 2; do
	  if [[ "$pass" == "1" ]]; then x="$hx1"; y="$hy1"; else x="$hx2"; y="$hy2"; fi
	  _long_press_xy "$x" "$y" 1500
	  sleep 0.4
	  if ! _wait_for_res_id "org.opendroidpdf:id/menu_highlight" 6; then
	    if [[ "$pass" == "1" ]]; then fx="$hx2"; fy="$hy2"; else fx="$hx1"; fy="$hy1"; fi
	    echo "WARN: selection did not activate at $x,$y; retrying at $fx,$fy" >&2
	    x="$fx"; y="$fy"
	    _long_press_xy "$x" "$y" 1500
	    sleep 0.4
	    if ! _wait_for_res_id "org.opendroidpdf:id/menu_highlight" 6; then
	      echo "FAIL: selection action mode did not appear after long-press selection" >&2
	      exit 1
	    fi
	    if [[ "$pass" == "1" ]]; then hx1="$x"; hy1="$y"; else hx2="$x"; hy2="$y"; fi
	  fi
	  _tap_menu_action_or_text "org.opendroidpdf:id/menu_highlight" "Highlight" || {
	    echo "FAIL: could not find Highlight action after long-press selection" >&2
	    exit 1
	  }
  sleep 0.9
  _fail_if_fatal_logcat
  # Exit selection mode if action-mode items are still visible.
  if uia_has_res_id "org.opendroidpdf:id/menu_highlight"; then
    uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
    sleep 0.35
  fi
done

for pass in 1 2; do
  if [[ "$pass" == "1" ]]; then x="$hx1"; y="$hy1"; else x="$hx2"; y="$hy2"; fi
  # Ensure we're not still in text-selection mode.
  if uia_has_res_id "org.opendroidpdf:id/menu_highlight"; then
    uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
    sleep 0.5
  fi

  # Tap the highlight to select the annotation; a small overlay bar (edit/delete) should appear.
  adb -s "$DEVICE" shell input tap "$x" "$y"
  sleep 0.7
  adb -s "$DEVICE" shell input tap "$x" "$y"
  sleep 0.9

  # Prefer toolbar delete if present; otherwise use the in-document quick-actions overlay.
  if ! uia_tap_any_res_id "org.opendroidpdf:id/menu_delete_annotation"; then
    if ! _tap_quick_actions_bar_delete_near "$x" "$y"; then
      fail_png="${OUT_PREFIX}_delete_fail_${pass}.png"
      fail_xml="${OUT_PREFIX}_delete_fail_${pass}.xml"
      _screencap_png "$fail_png" || true
      adb -s "$DEVICE" shell uiautomator dump /sdcard/__odp_markup_del.xml >/dev/null 2>&1 || true
      adb -s "$DEVICE" exec-out cat /sdcard/__odp_markup_del.xml >"$fail_xml" 2>/dev/null || true
      echo "FAIL: could not trigger delete for selected highlight (wrote $fail_png and $fail_xml)" >&2
      exit 1
    fi
  fi
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

echo "[7/11] Underline selection should appear near selected text (regression: underline far below)"
if ! _markup_check_near_selection_y \
  "underline" \
  "org.opendroidpdf:id/menu_underline" \
  "Underline" \
  200 \
  700 \
  "$UNDERLINE_MAX_DY_PX" \
  "$ASSERT_UNDERLINE_NEAR_SELECTION"; then
  _markup_check_near_selection_y \
    "underline_retry" \
    "org.opendroidpdf:id/menu_underline" \
    "Underline" \
    700 \
    1500 \
    "$UNDERLINE_MAX_DY_PX" \
    "$ASSERT_UNDERLINE_NEAR_SELECTION"
fi

if [[ "$RUN_STRIKEOUT_PLACEMENT_CHECK" == "1" ]]; then
  echo "[8/11] Strikeout selection should appear near selected text"
  if ! _markup_check_near_selection_y \
    "strike" \
    "org.opendroidpdf:id/menu_strikeout" \
    "Strikeout" \
    200 \
    700 \
    "$MARKUP_MAX_DY_PX" \
    "$ASSERT_MARKUP_NEAR_SELECTION"; then
    _markup_check_near_selection_y \
      "strike_retry" \
      "org.opendroidpdf:id/menu_strikeout" \
      "Strikeout" \
      700 \
      1500 \
      "$MARKUP_MAX_DY_PX" \
      "$ASSERT_MARKUP_NEAR_SELECTION"
  fi
else
  echo "[8/11] Strikeout placement check skipped (RUN_STRIKEOUT_PLACEMENT_CHECK=0)"
fi

if [[ "$RUN_ZOOM_MARKUP_CHECK" == "1" ]]; then
  echo "[9/11] Pinch-zoom in (UIAutomator2 runner)"
  if ! uia_runner_run_test "$UIA_ZOOM_TEST"; then
    zoom_fail_png="${OUT_PREFIX}_zoom_fail.png"
    _screencap_png "$zoom_fail_png" || true
    echo "FAIL: zoom test failed; wrote $zoom_fail_png" >&2
    exit 1
  fi
  sleep 1.2
  _fail_if_fatal_logcat

  echo "[10/11] Zoomed-in markup placement checks (highlight/underline/strikeout)"
  _markup_check_contains_selection \
    "highlight_zoom" \
    "org.opendroidpdf:id/menu_highlight" \
    "Highlight" \
    200 \
    1500 \
    80 \
    "$ASSERT_MARKUP_NEAR_SELECTION"

  if ! _markup_check_near_selection_y \
    "underline_zoom" \
    "org.opendroidpdf:id/menu_underline" \
    "Underline" \
    700 \
    1500 \
    "$UNDERLINE_MAX_DY_PX" \
    "$ASSERT_UNDERLINE_NEAR_SELECTION"; then
    _markup_check_near_selection_y \
      "underline_zoom_retry" \
      "org.opendroidpdf:id/menu_underline" \
      "Underline" \
      200 \
      900 \
      "$UNDERLINE_MAX_DY_PX" \
      "$ASSERT_UNDERLINE_NEAR_SELECTION"
  fi

  if ! _markup_check_near_selection_y \
    "strike_zoom" \
    "org.opendroidpdf:id/menu_strikeout" \
    "Strikeout" \
    700 \
    1500 \
    "$MARKUP_MAX_DY_PX" \
    "$ASSERT_MARKUP_NEAR_SELECTION"; then
    _markup_check_near_selection_y \
      "strike_zoom_retry" \
      "org.opendroidpdf:id/menu_strikeout" \
      "Strikeout" \
      200 \
      900 \
      "$MARKUP_MAX_DY_PX" \
      "$ASSERT_MARKUP_NEAR_SELECTION"
  fi
else
  echo "[9/11] Zoomed markup placement checks skipped (RUN_ZOOM_MARKUP_CHECK=0)"
fi

echo "[11/11] Save changes"
uia_save_changes || { echo "FAIL: could not trigger Save changes" >&2; exit 1; }
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 1.5
_fail_if_fatal_logcat

underline_check="disabled"
if [[ "$ASSERT_UNDERLINE_NEAR_SELECTION" == "1" ]]; then
  underline_check="enabled"
fi
strike_check="skipped"
if [[ "$RUN_STRIKEOUT_PLACEMENT_CHECK" == "1" ]]; then
  strike_check="disabled"
  if [[ "$ASSERT_MARKUP_NEAR_SELECTION" == "1" ]]; then
    strike_check="enabled"
  fi
fi
zoom_check="skipped"
if [[ "$RUN_ZOOM_MARKUP_CHECK" == "1" ]]; then
  zoom_check="disabled"
  if [[ "$ASSERT_MARKUP_NEAR_SELECTION" == "1" ]]; then
    zoom_check="enabled"
  fi
fi
echo "OK: markup highlight/delete stable; underline check ${underline_check}; strikeout check ${strike_check}; zoom checks ${zoom_check}"
