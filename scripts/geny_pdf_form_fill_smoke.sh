#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "AcroForm text field fill persists":
# - Push a small AcroForm PDF to /sdcard/Download
# - Open it via DocumentsUI (content:// URI) so Save works
# - Tap the text widget, enter a token, then commit by tapping outside
# - Save in-place
# - Force-stop, reopen, and assert the inline editor shows the saved value
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_form_fill_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_form_text.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_form_fill_smoke.pdf}

TOKEN=${TOKEN:-ODPFORM_SMOKE_1}
TOKEN_INPUT=${TOKEN_INPUT:-$TOKEN}
TOKEN_EXPECTED=${TOKEN_EXPECTED:-$TOKEN}

OUT_PREFIX=${OUT_PREFIX:-/tmp/odp_form_fill_smoke}
ASSERT_RENDER_OCR=${ASSERT_RENDER_OCR:-1}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_sh_quote_single() {
  # Single-quote a string for /system/bin/sh, escaping embedded single quotes.
  # Produces a token that can be safely embedded in a larger double-quoted command string.
  local s="$1"
  s=${s//\'/\'\"\'\"\'}
  printf "'%s'" "$s"
}

_adb_input_text() {
  # Safely input arbitrary text via Android's 'input text' (handles shell metacharacters).
  local raw="$1"
  # Android 'input text' uses %s for spaces.
  raw=${raw// /%s}
  adb -s "$DEVICE" shell "input text $(_sh_quote_single "$raw")"
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

_fail_if_fatal_logcat() {
  if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION|Process ${PKG} \\(pid [0-9]+\\) has died"; then
    echo "FAIL: detected crash in logcat" >&2
    adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|AndroidRuntime|${PKG}" | tail -n 260 >&2 || true
    return 1
  fi
  return 0
}

_uia_tap_rid_text_eq() {
  # Tap a node matching both resource-id and exact text.
  local rid="$1"
  local text="$2"
  local tmp coords
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  coords="$(python - "$tmp" "$rid" "$text" <<'PY'
import re, sys, xml.etree.ElementTree as ET
xml_path, rid, text = sys.argv[1], sys.argv[2], sys.argv[3]
tree = ET.parse(xml_path)
def center(bounds: str):
    m = re.match(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]", bounds or "")
    if not m:
        return None
    l, t, r, b = map(int, m.groups())
    return (l + r) // 2, (t + b) // 2
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    if node.attrib.get("text", "") != text:
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

_uia_text_for_res_id() {
  local rid="$1"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python - "$tmp" "$rid" <<'PY'
import sys, xml.etree.ElementTree as ET
xml_path, rid = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") == rid:
        print(node.attrib.get("text", ""))
        raise SystemExit(0)
raise SystemExit(1)
PY
  rm -f "$tmp"
}

_uia_bounds_for_res_id() {
  local rid="$1"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python - "$tmp" "$rid" <<'PY'
import re, sys, xml.etree.ElementTree as ET
xml_path, rid = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
pat = re.compile(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    m = pat.match(node.attrib.get("bounds", "") or "")
    if not m:
        continue
    l, t, r, b = map(int, m.groups())
    print(f"{l} {t} {r} {b}")
    raise SystemExit(0)
raise SystemExit(1)
PY
  rm -f "$tmp"
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
  _adb_input_text "$fname"

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

_highlight_center_xy() {
  local png="$1"
  python3 - "$png" <<'PY'
from PIL import Image
import sys

path = sys.argv[1]
im = Image.open(path).convert("RGB")
w, h = im.size
px = im.load()

pts = []
for y in range(h):
    for x in range(w):
        r, g, b = px[x, y]
        # Forms highlight paint is a bright green stroke; require green dominance.
        if g > 150 and r < 140 and b < 160 and (g - max(r, b)) > 40:
            pts.append((x, y))

if not pts:
    raise SystemExit(1)

xs = [p[0] for p in pts]
ys = [p[1] for p in pts]
minx, maxx = min(xs), max(xs)
miny, maxy = min(ys), max(ys)
print(f"{(minx + maxx) // 2} {(miny + maxy) // 2}")
PY
}

_tap_text_widget_via_highlight() {
  local tmp_png coords x y
  tmp_png="$(mktemp -t odp_form_hl_XXXXXX).png"
  _screencap_png "$tmp_png"
  coords="$(_highlight_center_xy "$tmp_png" 2>/dev/null || true)"
  rm -f "$tmp_png" || true
  if [[ -z "$coords" ]]; then
    echo "FAIL: could not locate forms highlight rectangle on-screen" >&2
    return 1
  fi
  read -r x y <<<"$coords"
  adb -s "$DEVICE" shell input tap "$x" "$y"
}

_wait_for_text_dialog() {
  for _ in $(seq 1 16); do
    if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      return 0
    fi
    sleep 0.35
  done
  return 1
}

_wait_for_text_editor_gone() {
  for _ in $(seq 1 20); do
    if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      return 0
    fi
    sleep 0.25
  done
  return 1
}

_tap_outside_editor_to_commit() {
  # Prefer tapping just outside the inline editor bounds (avoids tapping the keyboard).
  local bounds l t r b x y
  bounds="$(_uia_bounds_for_res_id "org.opendroidpdf:id/dialog_text_input" 2>/dev/null || true)"
  if [[ -n "$bounds" ]]; then
    read -r l t r b <<<"$bounds"
    x=$((l - 30))
    if [[ "$x" -lt 5 ]]; then x=5; fi
    y=$(((t + b) / 2))
    adb -s "$DEVICE" shell input tap "$x" "$y"
    return 0
  fi

  local w h
  read -r w h <<<"$(_wm_size)"
  adb -s "$DEVICE" shell input tap "$((w / 2))" "$((h / 4))"
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

echo "[5/8] Fill text field"
uia_enable_forms_highlight || true
sleep 0.8
_tap_text_widget_via_highlight
if ! _wait_for_text_dialog; then
  echo "FAIL: text widget editor did not appear after tap" >&2
  adb -s "$DEVICE" logcat -d | tail -n 180 >&2
  exit 1
fi
# Ensure the inline editor has focus before injecting text.
uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || true
_adb_input_text "$TOKEN_INPUT"
sleep 0.4
_tap_outside_editor_to_commit
sleep 0.4
if ! _wait_for_text_editor_gone; then
  echo "FAIL: inline editor did not dismiss after commit" >&2
  exit 1
fi
_fail_if_fatal_logcat

# Sanity check: reopen the editor and confirm the value is present before saving.
_tap_text_widget_via_highlight
if ! _wait_for_text_dialog; then
  echo "FAIL: text widget editor did not appear for pre-save verification" >&2
  exit 1
fi
now="$(_uia_text_for_res_id "org.opendroidpdf:id/dialog_text_input" | tr -d '\r' || true)"
if [[ "$now" != "$TOKEN_EXPECTED" ]]; then
  echo "FAIL: widget value not set before save" >&2
  echo "  got:      '$now'" >&2
  echo "  expected: '$TOKEN_EXPECTED'" >&2
  exit 1
fi
_tap_outside_editor_to_commit || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
sleep 0.4
if ! _wait_for_text_editor_gone; then
  echo "FAIL: inline editor did not dismiss after pre-save verification" >&2
  exit 1
fi
sleep 0.2

echo "[6/8] Save in-place"
uia_save_changes || { echo "FAIL: Save changes entry point missing" >&2; exit 1; }
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 2.5
_fail_if_fatal_logcat

echo "[7/8] Reopen and assert persisted value"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
_open_pdf_via_documentsui "$fname"
sleep 0.9
uia_enable_forms_highlight || true
sleep 0.8
_tap_text_widget_via_highlight
if ! _wait_for_text_dialog; then
  echo "FAIL: text widget editor did not appear after reopen" >&2
  adb -s "$DEVICE" logcat -d | tail -n 180 >&2
  exit 1
fi
saved="$(_uia_text_for_res_id "org.opendroidpdf:id/dialog_text_input" | tr -d '\r' || true)"
if [[ "$saved" != "$TOKEN_EXPECTED" ]]; then
  echo "FAIL: persisted widget value mismatch" >&2
  echo "  got:      '$saved'" >&2
  echo "  expected: '$TOKEN_EXPECTED'" >&2
  exit 1
fi
_tap_outside_editor_to_commit || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
sleep 0.6
_fail_if_fatal_logcat

echo "[8/8] Pull saved PDF back to host (debug artifact)"
SAVED_PDF="${SAVED_PDF:-${OUT_PREFIX}.pdf}"
adb -s "$DEVICE" pull "$PDF_REMOTE_PATH" "$SAVED_PDF" >/dev/null
echo "  wrote $SAVED_PDF"

if [[ "${ASSERT_PDFTOTEXT:-1}" == "1" ]]; then
  if command -v pdftotext >/dev/null 2>&1; then
    echo "[8.25/8] Cross-render check (poppler text extraction) for filled value"
    if ! pdftotext "$SAVED_PDF" - 2>/dev/null | tr -d '\r' | rg -Fq "$TOKEN_EXPECTED"; then
      echo "FAIL: pdftotext did not find token '$TOKEN_EXPECTED' in saved PDF output" >&2
      exit 1
    fi
  else
    echo "WARN: pdftotext not found; skipping cross-render text extraction check" >&2
  fi
fi

if [[ "$ASSERT_RENDER_OCR" == "1" ]]; then
  if command -v pdftoppm >/dev/null 2>&1 && command -v tesseract >/dev/null 2>&1; then
    echo "[8.5/8] Cross-render check (poppler + OCR) for filled value"
    tmpdir="$(mktemp -d -t odp_form_fill_render_XXXXXX)"
    out_prefix="$tmpdir/out"
    pdftoppm -f 1 -l 1 -r 300 -singlefile -png "$SAVED_PDF" "$out_prefix" >/dev/null
    ocr_raw="$(tesseract "${out_prefix}.png" stdout -l eng --psm 6 2>/dev/null | tr -d '\f' | tr -d '\r')"
    ocr_key="$(printf '%s' "$ocr_raw" | tr -cd '[:alnum:]')"
    token_key="$(printf '%s' "$TOKEN_EXPECTED" | tr -cd '[:alnum:]')"
    rm -rf -- "$tmpdir" || true
    if ! printf '%s\n' "$ocr_key" | rg -q "$token_key"; then
      echo "FAIL: OCR did not find token '$TOKEN_EXPECTED' in rendered PDF output" >&2
      echo "  OCR raw: $(printf '%s' "$ocr_raw" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')" >&2
      exit 1
    fi
  else
    echo "WARN: pdftoppm/tesseract not found; skipping cross-render OCR check" >&2
  fi
fi

echo "OK: AcroForm text field value persisted ($TOKEN_EXPECTED)"
