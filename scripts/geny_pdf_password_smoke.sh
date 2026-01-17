#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for password-protected PDFs:
# - Push an encrypted PDF to /sdcard/Download
# - Open it via DocumentsUI (content:// URI)
# - Assert a password dialog appears, enter password, and verify the document opens
# - Draw + accept a stroke, Save in-place
# - Reopen (again via DocumentsUI), enter password again
# - Pull the saved PDF back to host and assert it renders differently vs baseline
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_password_smoke.sh
#
# Requirements (host):
#   - pdftoppm (poppler)
#   - python3 + pillow

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_password_user.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_password_smoke.pdf}
PASSWORD=${PASSWORD:-test}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null
uia_disable_flaky_ime

if ! command -v pdftoppm >/dev/null 2>&1; then
  echo "FAIL: pdftoppm not found (install poppler)." >&2
  exit 2
fi
python3 - <<'PY' >/dev/null
import PIL  # noqa: F401
PY

_wm_size() {
  local line
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

_draw_swipe() {
  local w h x1 y1 x2 y2 dur
  read -r w h < <(_wm_size)
  x1=$((w * 2 / 10))
  x2=$((w * 8 / 10))
  y1=$((h * 55 / 100))
  y2=$((h * 58 / 100))
  dur=${1:-280}
  adb -s "$DEVICE" shell input swipe "$x1" "$y1" "$x2" "$y2" "$dur"
}

_wait_for_any_alert_dialog() {
  for _ in $(seq 1 24); do
    if uia_has_res_id "android:id/alertTitle"; then
      return 0
    fi
    sleep 0.25
  done
  return 1
}

_wait_for_no_alert_dialog() {
  for _ in $(seq 1 32); do
    if ! uia_has_res_id "android:id/alertTitle"; then
      return 0
    fi
    sleep 0.25
  done
  return 1
}

_uia_tap_first_class() {
  local klass="$1"
  local tmp coords
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  coords="$(python3 - "$tmp" "$klass" <<'PY'
import re, sys, xml.etree.ElementTree as ET
xml_path, klass = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)

def center(bounds: str):
    m = re.match(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]", bounds or "")
    if not m:
        return None
    l, t, r, b = map(int, m.groups())
    return (l + r) // 2, (t + b) // 2

for node in tree.iter("node"):
    if (node.attrib.get("class", "") or "") != klass:
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
}

_enter_password_and_wait_open() {
  local password="$1"
  if ! _wait_for_any_alert_dialog; then
    echo "FAIL: password dialog did not appear" >&2
    adb -s "$DEVICE" logcat -d | tail -n 200 >&2 || true
    exit 1
  fi

  # Tap the password field and type.
  _uia_tap_first_class "android.widget.EditText" || true
  adb -s "$DEVICE" shell input text "$password"
  sleep 0.3
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
    echo "FAIL: could not press OK on password dialog" >&2
    exit 1
  }

  if ! _wait_for_no_alert_dialog; then
    echo "FAIL: password dialog did not dismiss (wrong password?)" >&2
    exit 1
  fi

  # Wait for the document UI.
  for _ in $(seq 1 20); do
    if uia_has_res_id "org.opendroidpdf:id/doc_view"; then
      break
    fi
    sleep 0.25
  done
  uia_assert_in_document_view
}

_render_pdf_to_png_pw() {
  local pdf="$1"
  local out_png="$2"
  local upw="$3"
  local tmpdir prefix
  tmpdir="$(mktemp -d -t odp_pdf_render_XXXXXX)"
  prefix="$tmpdir/out"
  pdftoppm -upw "$upw" -f 1 -l 1 -r 140 -singlefile -png "$pdf" "$prefix" >/dev/null
  mv -f -- "${prefix}.png" "$out_png"
  rm -rf -- "$tmpdir"
}

_assert_renders_differ() {
  local before="$1"
  local after="$2"
  python3 - "$before" "$after" <<'PY'
import sys
from PIL import Image

before_path, after_path = sys.argv[1], sys.argv[2]
a = Image.open(before_path).convert("RGB")
b = Image.open(after_path).convert("RGB")
if a.size != b.size:
    raise SystemExit(f"FAIL: render size mismatch: {a.size} vs {b.size}")

w, h = a.size
ystart = int(h * 0.12)

pa = a.load()
pb = b.load()

step = 3
changed = 0
new_dark = 0
threshold = 90  # sum(|dr|,|dg|,|db|)
need = 120
need_dark = 35

for y in range(ystart, h, step):
    for x in range(0, w, step):
        r1, g1, b1 = pa[x, y]
        r2, g2, b2 = pb[x, y]
        if abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2) > threshold:
            changed += 1
        if (r1 >= 245 and g1 >= 245 and b1 >= 245) and (r2 < 80 and g2 < 80 and b2 < 80):
            new_dark += 1
        if changed > need and new_dark > need_dark:
            break
    if changed > need and new_dark > need_dark:
        break

if changed <= need or new_dark <= need_dark:
    raise SystemExit(
        f"FAIL: saved-PDF render didn't show embedded marks (changed={changed}, need>{need}, new_dark={new_dark}, need_dark>{need_dark}, size={w}x{h})"
    )

print(f"OK: saved-PDF render shows embedded marks (changed={changed}, new_dark={new_dark}, size={w}x{h})")
PY
}

echo "[1/10] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/10] Push password-protected PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null
fname="$(basename "$PDF_REMOTE_PATH")"

echo "[3/10] Open via DocumentsUI (expect password prompt)"
_open_pdf_via_documentsui "$fname"
sleep 0.9
_enter_password_and_wait_open "$PASSWORD"

echo "[4/10] Draw + accept"
uia_open_annotate_sheet || { echo "FAIL: could not open Annotate sheet" >&2; exit 1; }
uia_tap_any_res_id "org.opendroidpdf:id/annotate_action_draw" || uia_tap_text_contains "Draw" || {
  echo "FAIL: draw action not found in Annotate sheet" >&2
  exit 1
}
sleep 0.6
_draw_swipe 300
sleep 0.5
uia_tap_any_res_id "org.opendroidpdf:id/accept_image_button" "org.opendroidpdf:id/menu_accept" || {
  echo "FAIL: accept button not found after drawing" >&2
  exit 1
}
sleep 1.4

echo "[5/10] Save in-place"
uia_open_navigate_view_sheet || { echo "FAIL: could not open Navigate & View sheet" >&2; exit 1; }
uia_tap_any_res_id "org.opendroidpdf:id/navigate_view_action_save" || uia_tap_text_contains "Save changes" || {
  echo "FAIL: Save changes action not found in Navigate & View sheet" >&2
  exit 1
}
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 3.6

echo "[6/10] Reopen via DocumentsUI (expect password prompt again)"
_open_pdf_via_documentsui "$fname"
sleep 0.9
_enter_password_and_wait_open "$PASSWORD"

echo "[7/10] Best-effort: Share flattened PDF (then back)"
uia_open_export_sheet_advanced || true
if uia_tap_any_res_id "org.opendroidpdf:id/export_action_share_flattened" || uia_tap_text_contains "Share flattened"; then
  sleep 1.8
  adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
  sleep 0.8
fi

echo "[8/10] Pull saved PDF back to host"
OUT_PREFIX="${OUT_PREFIX:-tmp_geny_pdf_password}"
SAVED_PDF="${SAVED_PDF:-${OUT_PREFIX}.pdf}"
adb -s "$DEVICE" pull "$PDF_REMOTE_PATH" "$SAVED_PDF" >/dev/null
echo "  wrote $SAVED_PDF"

echo "[9/10] Render baseline + saved (with password) and assert the output differs"
BASE_PNG="${BASE_PNG:-${OUT_PREFIX}_before.png}"
AFTER_PNG="${AFTER_PNG:-${OUT_PREFIX}_after.png}"
_render_pdf_to_png_pw "$PDF_LOCAL" "$BASE_PNG" "$PASSWORD"
_render_pdf_to_png_pw "$SAVED_PDF" "$AFTER_PNG" "$PASSWORD"
echo "  wrote $BASE_PNG"
echo "  wrote $AFTER_PNG"
_assert_renders_differ "$BASE_PNG" "$AFTER_PNG"

echo "[10/10] Logcat fatal check"
if adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" >/dev/null; then
  echo "FAIL: detected fatal logcat entries" >&2
  adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" | tail -n 40 >&2
  exit 1
fi

echo "OK: password-protected PDF open + save + reopen passed"
