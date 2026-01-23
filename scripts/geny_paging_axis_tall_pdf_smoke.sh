#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke: verify vertical paging does not break in-page scrolling on tall pages.
#
# What it checks:
# - Set Settings → Display → "Page swipe direction" to Vertical
# - Open a generated 2-page *tall* PDF
# - Pinch-zoom in (so the page becomes scrollable) then swipe-up scrolls within the page
#   (page indicator stays 1/2) and the viewport changes
# - Repeated swipe-up eventually advances to page 2/2 (edge/overscroll)
# - Repeated swipe-down eventually returns to page 1 (after scrolling back to top)
# - Fail fast on crashes (logcat) or dead process
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_paging_axis_tall_pdf_smoke.sh
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF.apk OUT_PREFIX=tmp_paging ./scripts/geny_paging_axis_tall_pdf_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/geny_uia.sh"

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

PDF_REMOTE="${PDF_REMOTE:-/sdcard/Download/odp_tall_paging_smoke.pdf}"
OUT_PREFIX="${OUT_PREFIX:-tmp_geny_paging_axis_tall}"

LOGCAT_TXT="${LOGCAT_TXT:-${OUT_PREFIX}_logcat.txt}"
BEFORE_PNG="${BEFORE_PNG:-${OUT_PREFIX}_before_scroll.png}"
AFTER_SCROLL_PNG="${AFTER_SCROLL_PNG:-${OUT_PREFIX}_after_scroll.png}"
AFTER_PAGE2_PNG="${AFTER_PAGE2_PNG:-${OUT_PREFIX}_after_page2.png}"

export UIA_DOC_VIEW_TIMEOUT_S="${UIA_DOC_VIEW_TIMEOUT_S:-25}"

_resolve_apk() {
  if [[ -n "${APK}" ]]; then
    echo "${APK}"
    return 0
  fi
  local debug_apk="/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk"
  if [[ -f "$debug_apk" ]]; then
    echo "$debug_apk"
    return 0
  fi
  local latest
  latest="$(ls -1 /home/arch/fdroid/repo/org.opendroidpdf_*.apk 2>/dev/null | sort -V | tail -n 1 || true)"
  if [[ -z "${latest}" ]]; then
    echo "FAIL: could not find a default APK (set APK=...)" >&2
    return 1
  fi
  echo "${latest}"
}

_install_apk() {
  local apk="$1"
  local out
  out="$(adb -s "$DEVICE" install -r -t "$apk" 2>&1 || true)"
  if [[ "$out" == *"Success"* ]]; then
    return 0
  fi
  if [[ "$out" == *"INSTALL_FAILED_UPDATE_INCOMPATIBLE"* ]]; then
    adb -s "$DEVICE" uninstall "$PKG" >/dev/null 2>&1 || true
    adb -s "$DEVICE" install -r -t "$apk" >/dev/null
    return 0
  fi
  echo "$out" >&2
  return 1
}

_fail_if_fatal_logcat() {
  local out_txt="$1"
  adb -s "$DEVICE" logcat -d -v time > "$out_txt" || true
  if rg -q "FATAL EXCEPTION|Process ${PKG} \\(pid [0-9]+\\) has died|Fatal signal" "$out_txt"; then
    echo "FAIL: detected crash in logcat ($out_txt)" >&2
    rg -n "FATAL EXCEPTION|AndroidRuntime|${PKG}|Fatal signal" "$out_txt" | tail -n 260 >&2 || true
    return 1
  fi
  return 0
}

_fail_if_process_dead() {
  if ! adb -s "$DEVICE" shell pidof "$PKG" >/dev/null 2>&1; then
    echo "FAIL: ${PKG} process is not running" >&2
    return 1
  fi
  return 0
}

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
  adb -s "$DEVICE" exec-out screencap -p > "$out_png" 2>/dev/null
}

_make_tall_pdf() {
  local out_pdf="$1"
  local tmpdir
  tmpdir="$(mktemp -d /tmp/odp_tall_pdf.XXXXXX)"
  trap 'rm -rf "$tmpdir"' RETURN

  local magick
  if command -v magick >/dev/null 2>&1; then
    magick="magick"
  elif command -v convert >/dev/null 2>&1; then
    magick="convert"
  else
    echo "FAIL: ImageMagick not found (need 'magick' or 'convert')" >&2
    return 1
  fi

  local w=1200
  local h=6000
  local p1_png="$tmpdir/page1.png"
  local p2_png="$tmpdir/page2.png"

  # Add a faint grid so screenshots always contain non-white pixels (helps detect scroll changes).
  local -a grid=()
  local step_y=240
  local step_x=200
  for y in $(seq 0 "$step_y" "$h"); do
    grid+=(-stroke "#d0d0d0" -strokewidth 6 -draw "line 0,${y} ${w},${y}")
  done
  for x in $(seq 0 "$step_x" "$w"); do
    grid+=(-stroke "#d0d0d0" -strokewidth 6 -draw "line ${x},0 ${x},${h}")
  done

  "$magick" -size "${w}x${h}" xc:white \
    "${grid[@]}" \
    -gravity North -fill black -pointsize 92 -annotate +0+220 "PAGE 1 TOP" \
    -gravity Center -fill black -pointsize 96 -annotate +0+0 "PAGE 1 CENTER" \
    -gravity South -fill black -pointsize 92 -annotate +0+220 "PAGE 1 BOTTOM" \
    "$p1_png"

  "$magick" -size "${w}x${h}" xc:white \
    "${grid[@]}" \
    -gravity North -fill black -pointsize 92 -annotate +0+220 "PAGE 2 TOP" \
    -gravity Center -fill black -pointsize 96 -annotate +0+0 "PAGE 2 CENTER" \
    -gravity South -fill black -pointsize 92 -annotate +0+220 "PAGE 2 BOTTOM" \
    "$p2_png"

  "$magick" "$p1_png" "$p2_png" "$out_pdf"
}

_wait_for_visible_content() {
  # Captures screenshots until we see enough non-white pixels in the document area.
  local out_png="$1"
  local attempts="${2:-18}"
  local sleep_s="${3:-0.5}"

  for _ in $(seq 1 "$attempts"); do
    _screencap_png "$out_png"
    if python3 - "$out_png" <<'PY'
from PIL import Image
import numpy as np
import sys

img = Image.open(sys.argv[1]).convert("L")
w, h = img.size
top = int(h * 0.10)
bottom = int(h * 0.80)
crop = img.crop((0, top, w, bottom))
arr = np.array(crop, dtype=np.uint8)

# "Visible content" = anything not near-white.
nonwhite = int((arr < 245).sum())
if nonwhite < 5000:
  raise SystemExit(1)
print(f"OK: visible_content_nonwhite_pixels={nonwhite}")
PY
    then
      return 0
    fi
    sleep "$sleep_s"
  done

  echo "FAIL: document area stayed blank after ${attempts} screenshots ($out_png)" >&2
  return 1
}

_set_page_swipe_direction_vertical() {
  # Opens Settings and selects Vertical for the paging axis list preference.
  uia_tap_any_res_id "org.opendroidpdf:id/menu_settings" || {
    echo "FAIL: could not tap Settings on dashboard" >&2
    return 1
  }
  sleep 0.9

  local found=0
  for _ in $(seq 1 8); do
    if uia_tap_text_contains "Page swipe direction"; then
      found=1
      break
    fi
    # Scroll down a bit inside the Settings list.
    read -r w h < <(_wm_size)
    local x=$((w / 2))
    local y1=$((h * 80 / 100))
    local y2=$((h * 55 / 100))
    adb -s "$DEVICE" shell input swipe "$x" "$y1" "$x" "$y2" 260 >/dev/null
    sleep 0.35
  done
  if [[ "$found" -ne 1 ]]; then
    echo "FAIL: could not find 'Page swipe direction' in Settings" >&2
    return 1
  fi

  sleep 0.5
  uia_tap_text_contains "Vertical" || {
    echo "FAIL: could not select Vertical in paging axis dialog" >&2
    return 1
  }
  sleep 0.5

  # Back to dashboard.
  uia_tap_desc "Navigate up" || adb -s "$DEVICE" shell input keyevent 4 >/dev/null
  sleep 0.6
}

_read_page_indicator() {
  local rid="org.opendroidpdf:id/page_indicator"
  local tmp out
  tmp="$(mktemp)"
  _uia_dump_to "$tmp" >/dev/null 2>&1 || { rm -f "$tmp"; return 1; }
  out="$(python3 - "$tmp" "$rid" <<'PY'
import sys, xml.etree.ElementTree as ET

xml_path, rid = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") == rid:
        print(node.attrib.get("text", "") or "")
        raise SystemExit(0)
raise SystemExit(1)
PY
  )" || { rm -f "$tmp"; return 1; }
  rm -f "$tmp"
  printf '%s\n' "$out"
}

_page_num_from_indicator() {
  local text="$1"
  python3 - "$text" <<'PY'
import re, sys
s = sys.argv[1]
m = re.search(r"(\d+)\s*/\s*(\d+)", s)
if not m:
  sys.exit(1)
print(m.group(1))
PY
}

adb -s "$DEVICE" get-state >/dev/null

echo "[0/7] Ensure UIAutomator2 runner installed"
uia_runner_ensure_installed

APK_REAL="$(_resolve_apk)"
echo "[1/7] Install APK: $APK_REAL"
_install_apk "$APK_REAL"

echo "[2/7] Clear app data + storage perms"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[3/7] Build tall PDF fixture"
TMP_PDF="$(mktemp /tmp/odp_tall_paging_smoke.XXXXXX.pdf)"
_make_tall_pdf "$TMP_PDF"

echo "[4/7] Push tall PDF to device"
adb -s "$DEVICE" push "$TMP_PDF" "$PDF_REMOTE" >/dev/null
rm -f "$TMP_PDF"

echo "[5/7] Set paging axis to Vertical"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT" >/dev/null
sleep 1.1
_set_page_swipe_direction_vertical

echo "[6/7] Open tall PDF and validate in-page scroll vs page switch"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2.0
uia_assert_in_document_view

# Wait for page indicator to populate.
for _ in $(seq 1 80); do
  if uia_has_res_id "org.opendroidpdf:id/page_indicator"; then break; fi
  sleep 0.3
done
uia_has_res_id "org.opendroidpdf:id/page_indicator" || {
  echo "FAIL: page indicator not found" >&2
  exit 1
}

indicator="$(_read_page_indicator || true)"
page="$(_page_num_from_indicator "$indicator" 2>/dev/null || true)"
if [[ -z "$page" ]]; then
  echo "FAIL: could not parse page indicator text: '$indicator'" >&2
  exit 1
fi
if [[ "$page" != "1" ]]; then
  echo "FAIL: expected to start on page 1, got indicator='$indicator'" >&2
  exit 1
fi

echo "[6.2/7] Pinch-zoom in so in-page scrolling is possible"
if ! uia_runner_run_test "org.opendroidpdf.uia.ZoomPinchTest#testPinchOutOnlyDoesNotCrash"; then
  _screencap_png "${OUT_PREFIX}_zoom_fail.png" || true
  adb -s "$DEVICE" logcat -d > "${OUT_PREFIX}_zoom_fail_logcat.txt" 2>/dev/null || true
  exit 1
fi
sleep 1.2

# Move toward the top edge (safe on page 1: no prev page).
adb -s "$DEVICE" shell input swipe 280 280 280 980 720 >/dev/null 2>&1 || true
sleep 0.6
adb -s "$DEVICE" shell input swipe 280 280 280 980 720 >/dev/null 2>&1 || true
sleep 0.6

_wait_for_visible_content "$BEFORE_PNG" 20 0.5

read -r w h < <(_wm_size)
sx=$((w / 2))
sy=$((h * 78 / 100))
ex=$sx
ey=$((h * 30 / 100))

# First swipe should scroll within the tall page, not advance pages.
adb -s "$DEVICE" shell input swipe "$sx" "$sy" "$ex" "$ey" 1100
sleep 0.9
_fail_if_process_dead

indicator2="$(_read_page_indicator || true)"
page2="$(_page_num_from_indicator "$indicator2" 2>/dev/null || true)"
if [[ "$page2" != "1" ]]; then
  _screencap_png "$AFTER_SCROLL_PNG" || true
  _fail_if_fatal_logcat "$LOGCAT_TXT" || true
  echo "FAIL: swipe-up advanced pages too early (indicator='$indicator2')" >&2
  exit 1
fi

_wait_for_visible_content "$AFTER_SCROLL_PNG" 10 0.4

python3 - "$BEFORE_PNG" "$AFTER_SCROLL_PNG" <<'PY'
from PIL import Image, ImageChops
import numpy as np
import sys

before = Image.open(sys.argv[1]).convert("RGB")
after = Image.open(sys.argv[2]).convert("RGB")
if before.size != after.size:
  raise SystemExit("FAIL: screenshot sizes differ")
w, h = before.size

# Ignore status/nav + the bottom scrubber container.
top = int(h * 0.10)
bottom = int(h * 0.80)
b = before.crop((0, top, w, bottom)).convert("L")
a = after.crop((0, top, w, bottom)).convert("L")

diff = ImageChops.difference(b, a)
d_arr = np.array(diff, dtype=np.uint8)
changed = int((d_arr > 14).sum())
total = int(d_arr.size)
ratio = changed / float(total)
if ratio < 0.0005:
  raise SystemExit(f"FAIL: expected in-page scroll to change viewport (changed_ratio={ratio:.4f})")
print(f"OK: in-page scroll changed viewport (changed_ratio={ratio:.4f})")
PY

# Now keep swiping up until we reach page 2 (edge/overscroll should allow switching).
reached=0
sy_long=$((h * 78 / 100))
ey_long=$((h * 22 / 100))
for _ in $(seq 1 40); do
  adb -s "$DEVICE" shell input swipe "$sx" "$sy_long" "$ex" "$ey_long" 320
  sleep 0.40
  _fail_if_process_dead
  ind="$(_read_page_indicator || true)"
  p="$(_page_num_from_indicator "$ind" 2>/dev/null || true)"
  if [[ "$p" == "2" ]]; then
    reached=1
    break
  fi
done

if [[ "$reached" -ne 1 ]]; then
  _fail_if_fatal_logcat "$LOGCAT_TXT" || true
  echo "FAIL: never advanced to page 2 after repeated swipes (last_indicator='$ind')" >&2
  exit 1
fi

_screencap_png "$AFTER_PAGE2_PNG"

# Swipe down repeatedly until we return to page 1 (we may need to scroll to top first).
sy_down=$((h * 22 / 100))
ey_down=$((h * 78 / 100))
back=0
for _ in $(seq 1 40); do
  adb -s "$DEVICE" shell input swipe "$sx" "$sy_down" "$sx" "$ey_down" 320
  sleep 0.40
  _fail_if_process_dead
  ind_back="$(_read_page_indicator || true)"
  p_back="$(_page_num_from_indicator "$ind_back" 2>/dev/null || true)"
  if [[ "$p_back" == "1" ]]; then
    back=1
    break
  fi
done

if [[ "$back" -ne 1 ]]; then
  _fail_if_fatal_logcat "$LOGCAT_TXT" || true
  echo "FAIL: expected swipe-down to return to page 1 (indicator='$ind_back')" >&2
  exit 1
fi

echo "[7/7] Check logcat for crashes"
_fail_if_fatal_logcat "$LOGCAT_TXT"

echo "OK: vertical paging tall-page smoke passed ($BEFORE_PNG, $AFTER_SCROLL_PNG, $AFTER_PAGE2_PNG, $LOGCAT_TXT)"
