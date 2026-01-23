#!/usr/bin/env bash
set -euo pipefail

# Genymotion QA smoke: detect persistent "white box" (blank tile) artifacts after zoom/pan.
#
# Strategy:
# - Generate a single-page PDF with a *non-white* colorful background (so blank tiles stand out)
# - Open it in OpenDroidPDF
# - Pinch-zoom in (UIAutomator2 runner)
# - Pan around and capture screenshots
# - Assert the central viewport region does not contain large near-white areas
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_hq_flicker_smoke.sh
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF.apk ./scripts/geny_hq_flicker_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/geny_uia.sh"

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

PDF_REMOTE="${PDF_REMOTE:-/sdcard/Download/odp_hq_flicker_smoke.pdf}"
OUT_PREFIX="${OUT_PREFIX:-tmp_geny_hq_flicker}"
LOGCAT_TXT="${LOGCAT_TXT:-${OUT_PREFIX}_logcat.txt}"

NEAR_WHITE_RATIO_MAX="${NEAR_WHITE_RATIO_MAX:-0.02}"

python3 - <<'PY' >/dev/null 2>&1 || {
import PIL  # noqa: F401
PY
  echo "FAIL: python3 Pillow (PIL) not available (install pillow)." >&2
  exit 2
}

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

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p > "$out_png" 2>/dev/null
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

_make_fixture_pdf() {
  local out_pdf="$1"
  local tmpdir
  tmpdir="$(mktemp -d /tmp/odp_hq_flicker.XXXXXX)"
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

  local w=1800
  local h=2400
  local png="$tmpdir/page.png"

  # Gradient + saturated blocks: the page should have almost no pure-white pixels.
  "$magick" -size "${w}x${h}" gradient:"#0a1a2a-#2a0a1a" \
    -fill "#00ff88" -draw "rectangle 120,180 1680,560" \
    -fill "#ff4444" -draw "rectangle 120,720 1680,1100" \
    -fill "#4477ff" -draw "rectangle 120,1260 1680,1640" \
    -stroke "#000000" -strokewidth 14 -fill none -draw "rectangle 80,120 1720,2280" \
    -gravity North -fill "#ffffff" -pointsize 84 -annotate +0+140 "HQ FLICKER TEST" \
    -gravity South -fill "#ffffff" -pointsize 72 -annotate +0+160 "If you see white boxes, HQ patching is broken" \
    "$png"

  "$magick" "$png" "$out_pdf"
}

_assert_no_white_boxes() {
  local png="$1"
  local label="$2"
  local ratio_max="$3"
  python3 - "$png" "$label" "$ratio_max" <<'PY'
from PIL import Image
import numpy as np
import sys

png, label, ratio_max = sys.argv[1], sys.argv[2], float(sys.argv[3])
im = Image.open(png).convert("RGB")
w, h = im.size

# Crop away status/action bars and bottom scrubber area; keep central region.
top = int(h * 0.14)
bottom = int(h * 0.80)
left = int(w * 0.10)
right = int(w * 0.90)
crop = im.crop((left, top, right, bottom))
arr = np.array(crop, dtype=np.uint8)

# Near-white pixels (blank tiles tend to be pure/near-white).
near_white = (arr[:, :, 0] > 245) & (arr[:, :, 1] > 245) & (arr[:, :, 2] > 245)
ratio = float(near_white.mean())

print(f"{label}: near_white_ratio={ratio:.4f} (max={ratio_max:.4f})")
if ratio > ratio_max:
    raise SystemExit(1)
PY
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

adb -s "$DEVICE" get-state >/dev/null

echo "[0/6] Ensure UIAutomator2 runner installed"
uia_runner_ensure_installed

APK_REAL="$(_resolve_apk)"
echo "[1/6] Install APK: $APK_REAL"
_install_apk "$APK_REAL"

echo "[2/6] Clear app data + storage perms"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[3/6] Build and push fixture PDF"
TMP_PDF="$(mktemp /tmp/odp_hq_flicker.XXXXXX.pdf)"
_make_fixture_pdf "$TMP_PDF"
adb -s "$DEVICE" push "$TMP_PDF" "$PDF_REMOTE" >/dev/null
rm -f "$TMP_PDF"

echo "[4/6] Launch viewer"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2.2
uia_assert_in_document_view

echo "[5/6] Pinch-zoom in (progressive)"
uia_runner_run_test "org.opendroidpdf.uia.ZoomPinchTest#testPinchOutOnlyDoesNotCrash"
sleep 0.9

echo "[5.5/6] Pan around + assert no white tiles"
read -r W H < <(_wm_size)
cx=$((W / 2))
cy=$((H / 2))

_step() {
  local name="$1"
  shift
  "$@"
  sleep 0.7
  local png="${OUT_PREFIX}_${name}.png"
  _screencap_png "$png"
  if ! _assert_no_white_boxes "$png" "$name" "$NEAR_WHITE_RATIO_MAX"; then
    adb -s "$DEVICE" logcat -d > "$LOGCAT_TXT" 2>/dev/null || true
    echo "FAIL: detected near-white artifact in $png (see $LOGCAT_TXT)" >&2
    exit 1
  fi
}

_step "baseline" true
_step "pan_left"  adb -s "$DEVICE" shell input swipe "$((W * 70 / 100))" "$cy" "$((W * 30 / 100))" "$cy" 420
_step "pan_right" adb -s "$DEVICE" shell input swipe "$((W * 30 / 100))" "$cy" "$((W * 70 / 100))" "$cy" 420
_step "pan_up"    adb -s "$DEVICE" shell input swipe "$cx" "$((H * 70 / 100))" "$cx" "$((H * 35 / 100))" 420
_step "pan_down"  adb -s "$DEVICE" shell input swipe "$cx" "$((H * 35 / 100))" "$cx" "$((H * 70 / 100))" 420

echo "[6/6] Check logcat for crashes"
_fail_if_fatal_logcat "$LOGCAT_TXT"

echo "OK: HQ flicker smoke passed (${OUT_PREFIX}_*.png, $LOGCAT_TXT)"

