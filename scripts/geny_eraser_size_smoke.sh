#!/usr/bin/env bash
set -euo pipefail

# Genymotion QA smoke: verify eraser size is reachable from the toolbar and affects erase behavior.
#
# Scenario:
# - Open a PDF (default: test_pdf.pdf)
# - Draw two identical ink "bands" (top and bottom)
# - Switch to eraser mode
# - Set eraser size to MIN and erase across the top band
# - Set eraser size to MAX and erase across the bottom band
# - Assert the MAX erase changes more pixels than the MIN erase
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_eraser_size_smoke.sh
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF.apk PDF_LOCAL=/path/to/foo.pdf ./scripts/geny_eraser_size_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/geny_uia.sh"

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

PDF_LOCAL="${PDF_LOCAL:-${ROOT_DIR}/test_pdf.pdf}"
PDF_REMOTE="${PDF_REMOTE:-/sdcard/Download/odp_eraser_size_smoke.pdf}"

OUTDIR="${OUTDIR:-.}"
mkdir -p "$OUTDIR"
OUT_PREFIX="${OUT_PREFIX:-${OUTDIR}/tmp_geny_eraser_size_smoke}"
BASELINE_PNG="${BASELINE_PNG:-${OUT_PREFIX}_baseline.png}"
AFTER_MIN_PNG="${AFTER_MIN_PNG:-${OUT_PREFIX}_after_min.png}"
AFTER_MAX_PNG="${AFTER_MAX_PNG:-${OUT_PREFIX}_after_max.png}"
LOGCAT_TXT="${LOGCAT_TXT:-${OUT_PREFIX}_logcat.txt}"

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

_bounds_for_rid() {
  local rid="$1"
  local attempts="${UIA_BOUNDS_RETRIES:-10}"
  local sleep_s="${UIA_BOUNDS_RETRY_SLEEP_S:-0.25}"
  local tmp out

  for ((i = 1; i <= attempts; i++)); do
    tmp="$(mktemp)"
    if _uia_dump_to "$tmp" >/dev/null 2>&1; then
      if out="$(python3 - "$tmp" "$rid" <<'PY'
import re, sys, xml.etree.ElementTree as ET

xml_path, rid = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    b = node.attrib.get("bounds", "")
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    if not m:
        continue
    l, t, r, b = map(int, m.groups())
    print(f"{l} {t} {r} {b}")
    raise SystemExit(0)
raise SystemExit(1)
PY
      )"; then
        rm -f "$tmp"
        printf '%s\n' "$out"
        return 0
      fi
    fi
    rm -f "$tmp"
    sleep "$sleep_s"
  done
  return 1
}

_set_eraser_size() {
  local mode="$1" # min|max

  uia_tap_res_id "org.opendroidpdf:id/menu_eraser_size" || {
    echo "FAIL: could not open eraser size dialog (menu_eraser_size)" >&2
    return 1
  }
  sleep 0.6

  local bounds l t r b y x_left x_right
  bounds="$(_bounds_for_rid "org.opendroidpdf:id/eraser_size_seekbar")" || {
    echo "FAIL: could not find eraser size seekbar bounds" >&2
    return 1
  }
  read -r l t r b <<<"$bounds"
  y=$(( (t + b) / 2 ))
  x_left=$(( l + 12 ))
  x_right=$(( r - 12 ))

  if [[ "$mode" == "min" ]]; then
    adb -s "$DEVICE" shell input swipe "$x_right" "$y" "$x_left" "$y" 280 >/dev/null
  elif [[ "$mode" == "max" ]]; then
    adb -s "$DEVICE" shell input swipe "$x_left" "$y" "$x_right" "$y" 280 >/dev/null
  else
    echo "FAIL: invalid eraser size mode '$mode' (expected min|max)" >&2
    return 1
  fi
  sleep 0.4

  # Dismiss dialog.
  adb -s "$DEVICE" shell input keyevent 4 >/dev/null 2>&1 || true
  sleep 0.5
}

_set_pen_size_max() {
  # Best-effort: make the pen thick so eraser-size effects are visible.
  uia_tap_res_id "org.opendroidpdf:id/menu_pen_settings" || {
    echo "FAIL: could not open pen settings dialog (menu_pen_settings)" >&2
    return 1
  }
  sleep 0.7

  local bounds l t r b y x_left x_right
  bounds="$(_bounds_for_rid "org.opendroidpdf:id/pen_size_seekbar")" || {
    echo "FAIL: could not find pen size seekbar bounds" >&2
    return 1
  }
  read -r l t r b <<<"$bounds"
  y=$(( (t + b) / 2 ))
  x_left=$(( l + 12 ))
  x_right=$(( r - 12 ))

  adb -s "$DEVICE" shell input swipe "$x_left" "$y" "$x_right" "$y" 300 >/dev/null
  sleep 0.45

  adb -s "$DEVICE" shell input keyevent 4 >/dev/null 2>&1 || true
  sleep 0.55
}

adb -s "$DEVICE" get-state >/dev/null

APK_REAL="$(_resolve_apk)"
echo "[1/8] Install APK: $APK_REAL"
_install_apk "$APK_REAL"

echo "[2/8] Clear app data + storage perms"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[3/8] Push PDF fixture"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE" >/dev/null

echo "[4/8] Launch viewer"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2.1
uia_assert_in_document_view

read -r W H < <(_wm_size)
X1=$((W * 22 / 100))
X2=$((W * 78 / 100))

# Two bands, away from the scrubber.
Y_BAND_TOP=$((H * 38 / 100))
Y_BAND_BOTTOM=$((H * 58 / 100))
Y_STEP=$((H * 1 / 100))
BAND_LINES=12

echo "[5/8] Draw two ink bands and commit"
uia_enter_draw_mode || { echo "FAIL: could not enter draw mode" >&2; exit 1; }
sleep 0.7
_set_pen_size_max || true

for i in $(seq 0 $((BAND_LINES - 1))); do
  y=$((Y_BAND_TOP + i * Y_STEP))
  adb -s "$DEVICE" shell input swipe "$X1" "$y" "$X2" "$y" 220
done
for i in $(seq 0 $((BAND_LINES - 1))); do
  y=$((Y_BAND_BOTTOM + i * Y_STEP))
  adb -s "$DEVICE" shell input swipe "$X1" "$y" "$X2" "$y" 220
done
sleep 0.9

uia_tap_res_id "org.opendroidpdf:id/menu_accept" || {
  echo "FAIL: could not commit ink (menu_accept)" >&2
  exit 1
}
sleep 1.6
_fail_if_process_dead
_screencap_png "$BASELINE_PNG"

echo "[6/8] Erase top band with MIN eraser"
# Committing ink can exit annotation mode; re-enter so eraser controls are visible.
uia_enter_draw_mode || { echo "FAIL: could not re-enter draw mode after commit" >&2; exit 1; }
sleep 0.6
uia_tap_res_id "org.opendroidpdf:id/menu_erase" || { echo "FAIL: could not enter eraser mode" >&2; exit 1; }
sleep 0.6
_set_eraser_size "min"

X_MID=$((W / 2))
Y_MIN_1=$((Y_BAND_TOP - 3 * Y_STEP))
Y_MIN_2=$((Y_BAND_TOP + (BAND_LINES + 3) * Y_STEP))
adb -s "$DEVICE" shell input swipe "$X_MID" "$Y_MIN_1" "$X_MID" "$Y_MIN_2" 420
sleep 1.2
_fail_if_process_dead
_screencap_png "$AFTER_MIN_PNG"

echo "[7/8] Erase bottom band with MAX eraser"
_set_eraser_size "max"
Y_MAX_1=$((Y_BAND_BOTTOM - 3 * Y_STEP))
Y_MAX_2=$((Y_BAND_BOTTOM + (BAND_LINES + 3) * Y_STEP))
adb -s "$DEVICE" shell input swipe "$X_MID" "$Y_MAX_1" "$X_MID" "$Y_MAX_2" 420
sleep 1.3
_fail_if_process_dead
_screencap_png "$AFTER_MAX_PNG"

echo "[8/8] Analyze screenshots + check logcat"
python3 - "$BASELINE_PNG" "$AFTER_MIN_PNG" "$AFTER_MAX_PNG" <<'PY'
from PIL import Image, ImageChops
import numpy as np
import sys

baseline = Image.open(sys.argv[1]).convert("RGB")
after_min = Image.open(sys.argv[2]).convert("RGB")
after_max = Image.open(sys.argv[3]).convert("RGB")
if baseline.size != after_min.size or baseline.size != after_max.size:
  raise SystemExit("FAIL: screenshot sizes differ")

w, h = baseline.size

def crop_page(im):
  # Remove status/nav bars and most of the bottom scrubber area.
  return im.crop((int(w * 0.05), int(h * 0.12), int(w * 0.95), int(h * 0.82)))

b0 = crop_page(baseline).convert("L")
b1 = crop_page(after_min).convert("L")
b2 = crop_page(after_max).convert("L")

cw, ch = b0.size

# Regions corresponding to the two ink bands.
roi_top = (int(cw * 0.10), int(ch * 0.28), int(cw * 0.90), int(ch * 0.62))
roi_bottom = (int(cw * 0.10), int(ch * 0.62), int(cw * 0.90), int(ch * 0.95))

def diff_pixels(a, b, roi):
  da = a.crop(roi)
  db = b.crop(roi)
  diff = ImageChops.difference(da, db)
  arr = np.array(diff, dtype=np.uint8)
  return int((arr > 14).sum())

min_changed = diff_pixels(b0, b1, roi_top)
max_changed = diff_pixels(b1, b2, roi_bottom)

print(f"min_changed={min_changed}")
print(f"max_changed={max_changed}")

if min_changed < 150:
  raise SystemExit("FAIL: expected MIN erase to change pixels (too small)")

if max_changed < 3000:
  raise SystemExit("FAIL: expected MAX erase to change pixels (too small)")

if max_changed < int(min_changed * 5.0):
  raise SystemExit("FAIL: expected MAX erase to change more pixels than MIN erase")

print("OK: eraser size changes affect erase footprint")
PY

_fail_if_fatal_logcat "$LOGCAT_TXT"

echo "OK: eraser size smoke passed ($BASELINE_PNG, $AFTER_MIN_PNG, $AFTER_MAX_PNG, $LOGCAT_TXT)"
