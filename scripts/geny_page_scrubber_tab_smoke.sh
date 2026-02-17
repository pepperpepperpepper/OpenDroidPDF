#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for the Acrobat-style edge page scrubber tab:
# - Installs a local debug APK by default (or APK=... / latest signed F-Droid repo APK)
# - Builds (optionally) a many-page PDF by concatenating a small fixture N times
# - Opens the PDF via DocumentsUI (content:// URI)
# - Drags the right-edge scrubber tab to the end and back
# - Verifies the page indicator reaches first/last page
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_page_scrubber_tab_smoke.sh
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF.apk REPEAT=80 ./scripts/geny_page_scrubber_tab_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/geny_uia.sh"

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

PDF_LOCAL="${PDF_LOCAL:-${ROOT_DIR}/test_pdf.pdf}"
REPEAT="${REPEAT:-60}"
PDF_REMOTE_PATH="${PDF_REMOTE_PATH:-/sdcard/Download/odp_scrub_big.pdf}"

OUTDIR="${OUTDIR:-.}"
mkdir -p "$OUTDIR"
OUT_PREFIX="${OUT_PREFIX:-${OUTDIR}/tmp_geny_page_scrubber_tab_smoke}"
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

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p > "$out_png"
}

_fail_if_fatal_logcat() {
  local out_txt="$1"
  adb -s "$DEVICE" logcat -d -v time > "$out_txt" || true
  if rg -q "Process ${PKG} \\(pid [0-9]+\\) has died|Process: ${PKG}|Fatal signal.*${PKG}|>>> ${PKG} <<<" "$out_txt" \
    && rg -q "FATAL EXCEPTION|Process ${PKG} \\(pid [0-9]+\\) has died|Fatal signal|>>> ${PKG} <<<" "$out_txt"; then
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

_ensure_many_page_pdf() {
  local src_pdf="$1"
  local repeat="$2"
  local out_pdf="$3"

  if [[ "$repeat" -le 1 ]]; then
    cp -f "$src_pdf" "$out_pdf"
    return 0
  fi

  if ! command -v pdfunite >/dev/null 2>&1; then
    echo "WARN: pdfunite not found; using single PDF (set REPEAT=1)" >&2
    cp -f "$src_pdf" "$out_pdf"
    return 0
  fi

  local -a args=()
  for _ in $(seq 1 "$repeat"); do args+=("$src_pdf"); done
  pdfunite "${args[@]}" "$out_pdf"
}

_bounds_for_rid() {
  local rid="$1"
  local attempts="${UIA_BOUNDS_RETRIES:-8}"
  local sleep_s="${UIA_BOUNDS_RETRY_SLEEP_S:-0.25}"
  local tmp out

  for ((i = 1; i <= attempts; i++)); do
    tmp="$(mktemp)"
    if _uia_dump_to "$tmp" >/dev/null 2>&1; then
      if out="$(python - "$tmp" "$rid" <<'PY'
import re, sys, xml.etree.ElementTree as ET

xml_path, rid = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    b = node.attrib.get("bounds", "")
    m = re.match(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]", b)
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

_text_for_rid() {
  local rid="$1"
  local attempts="${UIA_TEXT_RETRIES:-6}"
  local sleep_s="${UIA_TEXT_RETRY_SLEEP_S:-0.25}"
  local tmp out

  for ((i = 1; i <= attempts; i++)); do
    tmp="$(mktemp)"
    if _uia_dump_to "$tmp" >/dev/null 2>&1; then
      if out="$(python - "$tmp" "$rid" <<'PY'
import sys, xml.etree.ElementTree as ET

xml_path, rid = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    print(node.attrib.get("text", "") or "")
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

adb -s "$DEVICE" get-state >/dev/null

APK_REAL="$(_resolve_apk)"
echo "[1/7] Install APK: $APK_REAL"
adb -s "$DEVICE" uninstall "$PKG" >/dev/null 2>&1 || true
adb -s "$DEVICE" install -r -t "$APK_REAL" >/dev/null

echo "[2/7] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/7] Build fixture PDF (repeat=$REPEAT)"
TMP_PDF="$(mktemp /tmp/odp_scrub_big.XXXXXX.pdf)"
_ensure_many_page_pdf "$PDF_LOCAL" "$REPEAT" "$TMP_PDF"

echo "[4/7] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$TMP_PDF" "$PDF_REMOTE_PATH" >/dev/null
rm -f "$TMP_PDF"

echo "[5/7] Launch app and open the PDF via DocumentsUI"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT" >/dev/null
sleep 1.1

uia_tap_any_res_id "org.opendroidpdf:id/menu_open_document" || {
  echo "FAIL: could not tap dashboard Open document button" >&2
  exit 1
}
sleep 1.2

fname="$(basename "$PDF_REMOTE_PATH")"
if ! uia_has_text_contains "$fname"; then
  uia_tap_docsui_roots_drawer || {
    echo "FAIL: could not open DocumentsUI roots drawer" >&2
    exit 1
  }
  sleep 0.6
  uia_tap_text_contains "Downloads" || {
    echo "FAIL: could not switch DocumentsUI to Downloads root" >&2
    exit 1
  }
  sleep 0.8
fi

uia_tap_text_contains "$fname" || {
  echo "FAIL: could not select $fname in DocumentsUI file list" >&2
  _fail_if_fatal_logcat "$LOGCAT_TXT" || true
  exit 1
}

uia_assert_in_document_view

echo "[6/7] Drag edge scrubber tab to end and back"
for _ in $(seq 1 120); do
  if uia_has_res_id "org.opendroidpdf:id/page_scrubber_tab"; then break; fi
  sleep 0.5
done
uia_has_res_id "org.opendroidpdf:id/page_scrubber_tab" || {
  echo "FAIL: edge scrubber tab not found (expected multi-page doc + chrome visible)" >&2
  exit 1
}

tab_bounds="$(_bounds_for_rid "org.opendroidpdf:id/page_scrubber_tab")" || {
  echo "FAIL: could not resolve tab bounds (resource-id=org.opendroidpdf:id/page_scrubber_tab)" >&2
  exit 1
}
read -r l t r b <<<"$tab_bounds"
x=$(( (l + r) / 2 ))
y_start=$(( (t + b) / 2 ))

size="$(adb -s "$DEVICE" shell wm size 2>/dev/null | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
if [[ -z "$size" ]]; then
  echo "FAIL: could not resolve screen size via wm size" >&2
  exit 1
fi
w="${size%x*}"
h="${size#*x}"
y_top=$(( h * 6 / 100 ))
y_bottom=$(( h * 94 / 100 ))

adb -s "$DEVICE" shell input swipe "$x" "$y_start" "$x" "$y_bottom" 520
sleep 0.5
_fail_if_process_dead
_screencap_png "${OUT_PREFIX}_after_down.png"

indicator_txt="$(_text_for_rid "org.opendroidpdf:id/page_indicator" || true)"
if [[ -z "$indicator_txt" ]]; then
  echo "FAIL: could not read page indicator text after scrub down" >&2
  exit 1
fi
if ! [[ "$indicator_txt" =~ ^[[:space:]]*([0-9]+)[[:space:]]*/[[:space:]]*([0-9]+)[[:space:]]*$ ]]; then
  echo "FAIL: unexpected page indicator format after scrub down: '$indicator_txt'" >&2
  exit 1
fi
cur="${BASH_REMATCH[1]}"
total="${BASH_REMATCH[2]}"
if [[ "$cur" != "$total" ]]; then
  echo "FAIL: expected last page after scrub down, got: '$indicator_txt'" >&2
  exit 1
fi

adb -s "$DEVICE" shell input swipe "$x" "$y_bottom" "$x" "$y_top" 520
sleep 0.5
_fail_if_process_dead
_screencap_png "${OUT_PREFIX}_after_up.png"

indicator_txt="$(_text_for_rid "org.opendroidpdf:id/page_indicator" || true)"
if [[ -z "$indicator_txt" ]]; then
  echo "FAIL: could not read page indicator text after scrub up" >&2
  exit 1
fi
if ! [[ "$indicator_txt" =~ ^[[:space:]]*([0-9]+)[[:space:]]*/[[:space:]]*([0-9]+)[[:space:]]*$ ]]; then
  echo "FAIL: unexpected page indicator format after scrub up: '$indicator_txt'" >&2
  exit 1
fi
cur="${BASH_REMATCH[1]}"
total="${BASH_REMATCH[2]}"
if [[ "$cur" != "1" ]]; then
  echo "FAIL: expected first page after scrub up, got: '$indicator_txt'" >&2
  exit 1
fi

echo "[7/7] Check logcat for crashes"
_fail_if_fatal_logcat "$LOGCAT_TXT"

echo "OK: edge scrubber tab smoke passed (${OUT_PREFIX}_after_down.png, ${OUT_PREFIX}_after_up.png)"

