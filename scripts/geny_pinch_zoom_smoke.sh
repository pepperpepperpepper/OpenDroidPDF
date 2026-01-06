#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke: open a PDF and repeatedly pinch-zoom in.
#
# Uses the UIAutomator2 runner APK (instrumentation) to perform multi-touch.
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pinch_zoom_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/thirdparty_PrZo.pdf}
PDF_REMOTE=${PDF_REMOTE:-/sdcard/Download/odp_zoom_smoke.pdf}
PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

SRC_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

export UIA_DOC_VIEW_TIMEOUT_S="${UIA_DOC_VIEW_TIMEOUT_S:-25}"

source "$SRC_DIR/geny_uia.sh"

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

echo "[1/6] Ensure UIAutomator2 runner installed"
uia_runner_ensure_installed

echo "[2/6] Install debug APK (clear data + perms)"
_install_out="$(adb -s "$DEVICE" install -r "$APK" 2>&1 || true)"
if [[ "$_install_out" != *"Success"* ]]; then
  if [[ "$_install_out" == *"INSTALL_FAILED_UPDATE_INCOMPATIBLE"* ]]; then
    echo "[2/6] Signature mismatch; uninstalling $PKG and retrying install"
    adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
    adb -s "$DEVICE" install -r "$APK" >/dev/null
  else
    echo "$_install_out" >&2
    exit 1
  fi
fi

adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[3/6] Push sample PDF"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE" >/dev/null

echo "[4/6] Launch viewer with sample PDF"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[5/6] Run pinch-zoom test (progressive zoom-in)"
uia_runner_run_test "org.opendroidpdf.uia.ZoomPinchTest#testProgressiveZoomInDoesNotCrash"

echo "[5.5/6] Assert one-finger pan changes viewport (screenshot diff)"
PAN_BEFORE_PNG="${PAN_BEFORE_PNG:-tmp_geny_pinch_pan_before.png}"
PAN_AFTER_PNG="${PAN_AFTER_PNG:-tmp_geny_pinch_pan_after.png}"
adb -s "$DEVICE" exec-out screencap -p > "$PAN_BEFORE_PNG"

read -r w h < <(_wm_size)
sx=$((w / 2))
sy=$((h * 70 / 100))
ex=$sx
ey=$((h * 30 / 100))
adb -s "$DEVICE" shell input swipe "$sx" "$sy" "$ex" "$ey" 420
sleep 0.9
adb -s "$DEVICE" exec-out screencap -p > "$PAN_AFTER_PNG"

python3 - "$PAN_BEFORE_PNG" "$PAN_AFTER_PNG" <<'PY'
from PIL import Image, ImageChops
import numpy as np
import sys

before = Image.open(sys.argv[1]).convert("RGB")
after = Image.open(sys.argv[2]).convert("RGB")
if before.size != after.size:
  raise SystemExit("FAIL: screenshot sizes differ")

w, h = before.size

# Ignore status/nav bars but keep full width (content may be near the left edge after zoom).
top = int(h * 0.10)
bottom = int(h * 0.92)
b_full = before.crop((0, top, w, bottom)).convert("L")
a_full = after.crop((0, top, w, bottom)).convert("L")

# Focus the diff on where there is actual page content (non-white pixels) to avoid false
# negatives when the visible content occupies only a narrow strip of the viewport.
b_arr = np.array(b_full)
a_arr = np.array(a_full)
content = (b_arr < 250) | (a_arr < 250)
ys, xs = np.where(content)
if xs.size == 0:
  raise SystemExit("FAIL: expected non-white content in screenshot crop")

min_x, max_x = int(xs.min()), int(xs.max())
min_y, max_y = int(ys.min()), int(ys.max())
margin = 12
min_x = max(0, min_x - margin)
max_x = min(b_full.size[0], max_x + margin)
min_y = max(0, min_y - margin)
max_y = min(b_full.size[1], max_y + margin)

b = b_full.crop((min_x, min_y, max_x, max_y))
a = a_full.crop((min_x, min_y, max_x, max_y))

diff = ImageChops.difference(b, a)
d_arr = np.array(diff, dtype=np.uint8)
changed = int((d_arr > 14).sum())
total = int(d_arr.size)
ratio = changed / float(total)

if ratio < 0.0005:
  raise SystemExit(f"FAIL: expected pan to change viewport (changed_ratio={ratio:.4f})")
print(f"OK: pan changed viewport (changed_ratio={ratio:.4f})")
PY

echo "[6/6] Logcat tail"
adb -s "$DEVICE" logcat -d | tail -n 120

echo "Pinch zoom smoke complete."
