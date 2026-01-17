#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for AcroForm signature widgets:
# - Generate a temporary PKCS#12 (.pfx) key on the host and push it to /sdcard/Download
# - Open a PDF with a signature widget via DocumentsUI (content:// URI) so Save works
# - Tap the signature widget and complete the signing flow (pick .pfx + enter password)
# - Save in-place, reopen, and ensure the signature widget is now treated as signed
# - Pull the saved PDF back to host and assert it contains a PKCS#7 signature dictionary
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_form_sign_smoke.sh
#
# Requirements (host):
#   - openssl

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}

PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_form_widgets.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_form_sign_smoke.pdf}

ASSERT_RENDERED_SIGNATURE_OCR=${ASSERT_RENDERED_SIGNATURE_OCR:-1}
OCR_TOKEN=${OCR_TOKEN:-OpenDroidPDF}

PFX_NAME=${PFX_NAME:-odp_sign_smoke.pfx}
PFX_PASSWORD=${PFX_PASSWORD:-ODP_SMOKE_PFX_PASS}
PFX_REMOTE_PATH=${PFX_REMOTE_PATH:-/sdcard/Download/${PFX_NAME}}

OUT_PREFIX=${OUT_PREFIX:-/tmp/odp_form_sign_smoke}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null
uia_disable_flaky_ime

if ! command -v openssl >/dev/null 2>&1; then
  echo "FAIL: openssl not found in PATH" >&2
  exit 2
fi
if [[ "${ASSERT_RENDERED_SIGNATURE_OCR}" == "1" ]]; then
  if ! command -v pdftoppm >/dev/null 2>&1; then
    echo "FAIL: pdftoppm not found (install poppler) or set ASSERT_RENDERED_SIGNATURE_OCR=0" >&2
    exit 2
  fi
  if ! command -v tesseract >/dev/null 2>&1; then
    echo "FAIL: tesseract not found (install tesseract-ocr) or set ASSERT_RENDERED_SIGNATURE_OCR=0" >&2
    exit 2
  fi
fi

_fail_if_fatal_logcat() {
  if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION|Process ${PKG} \\(pid [0-9]+\\) has died|Fatal signal"; then
    echo "FAIL: detected crash in logcat" >&2
    adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|AndroidRuntime|${PKG}|Fatal signal" | tail -n 260 >&2 || true
    return 1
  fi
  return 0
}

_make_pfx() {
  local out_pfx="$1"
  local pass="$2"
  local tmpdir
  tmpdir="$(mktemp -d -t odp_pfx_XXXXXX)"
  openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout "$tmpdir/key.pem" \
    -out "$tmpdir/cert.pem" \
    -days 2 \
    -subj "/CN=OpenDroidPDF Smoke" >/dev/null 2>&1
  openssl pkcs12 -export \
    -out "$out_pfx" \
    -inkey "$tmpdir/key.pem" \
    -in "$tmpdir/cert.pem" \
    -passout "pass:${pass}" >/dev/null 2>&1
  rm -rf -- "$tmpdir"
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
    echo "Logcat tail:" >&2
    adb -s "$DEVICE" logcat -d | tail -n 180 >&2
    exit 1
  }

  for _ in $(seq 1 20); do
    if uia_has_res_id "org.opendroidpdf:id/document_host_container"; then
      return 0
    fi
    sleep 0.6
  done

  echo "FAIL: document view did not appear after open (fname=$fname)" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2
  return 1
}

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p > "$out_png"
}

_render_pdf_to_png() {
  local pdf="$1"
  local out_png="$2"
  local tmpdir prefix
  tmpdir="$(mktemp -d -t odp_sign_render_XXXXXX)"
  prefix="$tmpdir/out"
  pdftoppm -f 1 -l 1 -r 300 -singlefile -png "$pdf" "$prefix" >/dev/null
  mv -f -- "${prefix}.png" "$out_png"
  rm -rf -- "$tmpdir"
}

_assert_pdf_renders_token() {
  local pdf="$1"
  local token="$2"
  local out_png="$3"

  _render_pdf_to_png "$pdf" "$out_png"
  local ocr_raw ocr_key token_key
  ocr_raw="$(tesseract "$out_png" stdout -l eng --psm 6 2>/dev/null | tr -d '\f' | tr -d '\r' | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')"
  ocr_key="$(printf '%s' "$ocr_raw" | tr -cd '[:alnum:]')"
  token_key="$(printf '%s' "$token" | tr -cd '[:alnum:]')"
  if [[ -z "$token_key" ]]; then
    echo "FAIL: OCR token key is empty (token='$token')" >&2
    return 1
  fi
  if ! printf '%s' "$ocr_key" | rg -F -q "$token_key"; then
    echo "FAIL: poppler+OCR did not find token '$token' in rendered output" >&2
    echo "  ocr_raw=$ocr_raw" >&2
    echo "  render=$out_png" >&2
    return 1
  fi
  return 0
}

_widget_centers_from_highlight() {
  local png="$1"
  python3 - "$png" <<'PY'
from __future__ import annotations

import sys
from PIL import Image, ImageFilter
import numpy as np

path = sys.argv[1]
im = Image.open(path).convert("RGB")
arr = np.array(im)
r = arr[:, :, 0].astype(np.int16)
g = arr[:, :, 1].astype(np.int16)
b = arr[:, :, 2].astype(np.int16)

# Widget highlight paint is a bright green dashed stroke.
mask = (g > 150) & (r < 140) & (b < 160) & ((g - np.maximum(r, b)) > 40)
if not mask.any():
    raise SystemExit(1)

factor = 3
mask_small = (mask[::factor, ::factor].astype(np.uint8) * 255)
mask_img = Image.fromarray(mask_small, mode="L")

# Connect dashes enough to form stable components.
mask_img = mask_img.filter(ImageFilter.MaxFilter(11))
mask_bin = (np.array(mask_img) > 0)

h, w = mask_bin.shape
visited = np.zeros((h, w), dtype=np.uint8)

boxes = []
for y in range(h):
    row = mask_bin[y]
    for x in range(w):
        if not row[x] or visited[y, x]:
            continue
        stack = [(x, y)]
        visited[y, x] = 1
        minx = maxx = x
        miny = maxy = y
        count = 0
        while stack:
            cx, cy = stack.pop()
            count += 1
            if cx < minx: minx = cx
            if cx > maxx: maxx = cx
            if cy < miny: miny = cy
            if cy > maxy: maxy = cy

            nx = cx - 1
            if nx >= 0 and mask_bin[cy, nx] and not visited[cy, nx]:
                visited[cy, nx] = 1
                stack.append((nx, cy))
            nx = cx + 1
            if nx < w and mask_bin[cy, nx] and not visited[cy, nx]:
                visited[cy, nx] = 1
                stack.append((nx, cy))
            ny = cy - 1
            if ny >= 0 and mask_bin[ny, cx] and not visited[ny, cx]:
                visited[ny, cx] = 1
                stack.append((cx, ny))
            ny = cy + 1
            if ny < h and mask_bin[ny, cx] and not visited[ny, cx]:
                visited[ny, cx] = 1
                stack.append((cx, ny))

        bw = maxx - minx + 1
        bh = maxy - miny + 1
        if count < 120:
            continue
        if bw < 12 or bh < 12:
            continue
        boxes.append((minx, miny, maxx, maxy))

if not boxes:
    raise SystemExit(1)

centers = []
for minx, miny, maxx, maxy in boxes:
    cx = int(((minx + maxx) / 2.0) * factor)
    cy = int(((miny + maxy) / 2.0) * factor)
    centers.append((cy, cx))

centers.sort()
for cy, cx in centers:
    print(f"{cx} {cy}")
PY
}

_signature_xy_from_forms_highlight() {
  local last_png centers=()
  last_png="$(mktemp -t odp_form_sign_hl_XXXXXX).png"
  for _attempt in $(seq 1 22); do
    _screencap_png "$last_png"
    mapfile -t centers < <(_widget_centers_from_highlight "$last_png" 2>/dev/null || true)
    if [[ "${#centers[@]}" -ge 1 ]]; then
      break
    fi
    sleep 0.35
  done
  if [[ "${#centers[@]}" -lt 1 ]]; then
    cp "$last_png" "${OUT_PREFIX}_fail_highlight.png" || true
    rm -f "$last_png" || true
    echo "FAIL: could not locate any forms highlight rectangles on-screen" >&2
    echo "  wrote ${OUT_PREFIX}_fail_highlight.png" >&2
    return 1
  fi
  rm -f "$last_png" || true

  # Pick the lowest (max y) highlight region; in our fixture that's the signature widget.
  local best_x="" best_y=-1
  local c x y
  for c in "${centers[@]}"; do
    x="${c% *}"
    y="${c#* }"
    if (( y > best_y )); then
      best_y="$y"
      best_x="$x"
    fi
  done
  if [[ -z "$best_x" ]] || (( best_y < 0 )); then
    echo "FAIL: could not select a signature highlight region" >&2
    return 1
  fi
  echo "$best_x $best_y"
}

_wait_for_any_alert_dialog() {
  for _ in $(seq 1 20); do
    if uia_has_res_id "android:id/alertTitle"; then
      return 0
    fi
    sleep 0.25
  done
  return 1
}

_wait_for_no_alert_dialog() {
  for _ in $(seq 1 30); do
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
  coords="$(python - "$tmp" "$klass" <<'PY'
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

echo "[1/9] Install debug APK"
if ! adb -s "$DEVICE" install -r "$APK" >/dev/null; then
  echo "  install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
  adb -s "$DEVICE" install "$APK" >/dev/null
fi

echo "[2/9] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/9] Generate PKCS#12 key and push to Downloads"
pfx_local="$(mktemp -t odp_sign_smoke_XXXXXX).pfx"
_make_pfx "$pfx_local" "$PFX_PASSWORD"
adb -s "$DEVICE" push "$pfx_local" "$PFX_REMOTE_PATH" >/dev/null
rm -f "$pfx_local" || true

echo "[4/9] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null

fname="$(basename "$PDF_REMOTE_PATH")"

echo "[5/9] Open PDF via DocumentsUI (persistable grant)"
_open_pdf_via_documentsui "$fname"
sleep 0.9

uia_enable_forms_highlight || true
sleep 0.9

read -r sig_x sig_y < <(_signature_xy_from_forms_highlight)

echo "[6/9] Sign the signature widget (pick .pfx + password)"
adb -s "$DEVICE" shell input tap "$sig_x" "$sig_y"
if ! _wait_for_any_alert_dialog; then
  echo "FAIL: signature signing dialog did not appear" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2
  exit 1
fi

# "Select certificate and sign?" dialog -> OK
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
  echo "FAIL: could not confirm signing dialog (OK)" >&2
  exit 1
}
sleep 0.9

# DocumentsUI picker: select the pushed .pfx from Downloads.
uia_tap_docsui_roots_drawer || {
  echo "FAIL: could not open DocumentsUI roots drawer for key picker" >&2
  exit 1
}
sleep 0.7
uia_tap_text_contains "Downloads" || {
  echo "FAIL: could not switch DocumentsUI to Downloads root for key picker" >&2
  exit 1
}
sleep 0.9

uia_tap_any_res_id "com.android.documentsui:id/option_menu_search" || uia_tap_desc "Search" || {
  echo "FAIL: could not open DocumentsUI search for key picker" >&2
  exit 1
}
sleep 0.6
adb -s "$DEVICE" shell input text "$PFX_NAME"

for _ in $(seq 1 20); do
  if uia_has_text_contains "$PFX_NAME"; then
    break
  fi
  sleep 0.35
done
uia_tap_text_contains "$PFX_NAME" || {
  echo "FAIL: could not select $PFX_NAME in DocumentsUI key picker results" >&2
  exit 1
}
sleep 0.9

# Password prompt -> enter password -> Sign
if ! _wait_for_any_alert_dialog; then
  echo "FAIL: password dialog did not appear after selecting key file" >&2
  exit 1
fi

_uia_tap_first_class "android.widget.EditText" || true
adb -s "$DEVICE" shell input text "$PFX_PASSWORD"
sleep 0.3
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
  echo "FAIL: could not press Sign on password dialog" >&2
  exit 1
}

if ! _wait_for_no_alert_dialog; then
  echo "FAIL: password dialog did not dismiss after signing" >&2
  exit 1
fi
sleep 1.4
_fail_if_fatal_logcat

echo "[7/9] Save in-place"
uia_save_changes || { echo "FAIL: Save changes entry point missing" >&2; exit 1; }
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 2.6
_fail_if_fatal_logcat

echo "[8/9] Reopen and ensure the signature widget is now signed"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
_open_pdf_via_documentsui "$fname"
sleep 0.9
uia_enable_forms_highlight || true
sleep 0.9

read -r sig_x2 sig_y2 < <(_signature_xy_from_forms_highlight)
adb -s "$DEVICE" shell input tap "$sig_x2" "$sig_y2"
if ! _wait_for_any_alert_dialog; then
  echo "FAIL: tapping signature widget after reopen did not show a dialog" >&2
  exit 1
fi

if uia_has_text_contains "Select certificate and sign"; then
  echo "FAIL: signature widget still appears unsigned after signing/reopen" >&2
  exit 1
fi

# Dismiss whatever report dialog we got.
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
sleep 0.6

echo "[9/9] Pull saved PDF back to host and assert signature dictionary exists"
SAVED_PDF="${SAVED_PDF:-${OUT_PREFIX}.pdf}"
adb -s "$DEVICE" pull "$PDF_REMOTE_PATH" "$SAVED_PDF" >/dev/null
echo "  wrote $SAVED_PDF"

if ! rg -a -q "/ByteRange\\s*\\[" "$SAVED_PDF"; then
  echo "FAIL: saved PDF does not contain /ByteRange (signature not written?)" >&2
  exit 1
fi
if ! rg -a -q "/SubFilter\\s*/adbe\\.pkcs7\\.detached" "$SAVED_PDF"; then
  echo "FAIL: saved PDF does not contain SubFilter adbe.pkcs7.detached" >&2
  exit 1
fi
if ! rg -a -q "/Contents\\s*<" "$SAVED_PDF"; then
  echo "FAIL: saved PDF does not contain /Contents <...> (signature contents missing?)" >&2
  exit 1
fi

if [[ "${ASSERT_RENDERED_SIGNATURE_OCR}" == "1" ]]; then
  echo "[9.5/9] Cross-render check (poppler + OCR) for signature appearance"
  RENDER_PNG="${RENDER_PNG:-${OUT_PREFIX}_render.png}"
  _assert_pdf_renders_token "$SAVED_PDF" "$OCR_TOKEN" "$RENDER_PNG"
  echo "  render: $RENDER_PNG"
fi

echo "OK: signature widget signed and persisted (PKCS#7 present in PDF)"
