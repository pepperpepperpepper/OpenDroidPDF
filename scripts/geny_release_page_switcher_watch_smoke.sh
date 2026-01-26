#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for the signed release APK + Navigate & View page switcher:
# - Installs the latest signed org.opendroidpdf_*.apk from the local F-Droid repo (unless APK= is provided)
# - Opens a multi-page PDF via DocumentsUI (content:// URI)
# - Repeatedly switches pages using the Navigate & View prev/next buttons
# - Captures screenshots and fails fast if renders look blank-ish or logcat shows a fatal
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_release_page_switcher_watch_smoke.sh
#   DEVICE=localhost:<port> APK=/path/to/org.opendroidpdf_XXX.apk PDF_LOCAL=/path/to/big.pdf LOOPS=6 ./scripts/geny_release_page_switcher_watch_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/geny_uia.sh"

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

PDF_LOCAL=${PDF_LOCAL:-${ROOT_DIR}/test_pdf.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_release_page_switcher_watch.pdf}

LOOPS=${LOOPS:-4}
WAIT_AFTER_SWITCH_S=${WAIT_AFTER_SWITCH_S:-0.7}
WAIT_PAGE_INDICATOR_S=${WAIT_PAGE_INDICATOR_S:-8}

OUTDIR="${OUTDIR:-.}"
mkdir -p "$OUTDIR"
OUT_PREFIX="${OUT_PREFIX:-${OUTDIR}/tmp_geny_release_page_switcher_watch}"
LOGCAT_TXT="${LOGCAT_TXT:-${OUT_PREFIX}_logcat.txt}"

_resolve_apk() {
  if [[ -n "${APK}" ]]; then
    echo "${APK}"
    return 0
  fi
  local latest
  latest="$(ls -1 /home/arch/fdroid/repo/org.opendroidpdf_*.apk 2>/dev/null | sort -V | tail -n 1 || true)"
  if [[ -z "${latest}" ]]; then
    echo "FAIL: could not find /home/arch/fdroid/repo/org.opendroidpdf_*.apk (set APK=...)" >&2
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
  adb -s "$DEVICE" logcat -d > "$out_txt" || true
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

_pdf_page_count() {
  local pdf="$1"
  if ! command -v pdfinfo >/dev/null 2>&1; then
    echo "FAIL: pdfinfo not found; install poppler-utils or set PAGE_COUNT=..." >&2
    return 1
  fi
  local pages
  pages="$(pdfinfo "$pdf" 2>/dev/null | awk '/^Pages:/ {print $2; exit}' || true)"
  if [[ -z "$pages" ]]; then
    echo "FAIL: could not parse page count from pdfinfo for $pdf" >&2
    return 1
  fi
  echo "$pages"
}

_assert_nonblank_png() {
  local png="$1"
  python - "$png" <<'PY'
import sys
from PIL import Image

path = sys.argv[1]
img = Image.open(path).convert("RGB")
w, h = img.size

# Heuristic: fail fast on “blank-ish” renders (white flash) without false-failing on
# light/low-contrast pages.
#
# We sample the central content region (skip toolbar/status + outer margins) and treat a
# render as “non-blank” if it has either:
#  - enough dark pixels (text/ink), OR
#  - enough chroma (colored content), OR
#  - enough edges (contrast transitions).
ystart = int(h * 0.12)
yend = int(h * 0.96)
xstart = int(w * 0.06)
xend = int(w * 0.94)

step = 3
colored = 0
dark = 0
edges = 0
samples = 0

MIN_COLORED = 300
MIN_DARK = 120
MIN_EDGES = 900

px = img.load()
for y in range(ystart, yend, step):
    prev_l = None
    for x in range(xstart, xend, step):
        r, g, b = px[x, y]
        l = 0.2126 * r + 0.7152 * g + 0.0722 * b  # perceived luminance
        samples += 1
        if max(r, g, b) - min(r, g, b) > 25:
            colored += 1
        if l < 80:
            dark += 1
        if prev_l is not None and abs(l - prev_l) > 18:
            edges += 1
        prev_l = l
        if colored >= MIN_COLORED or dark >= MIN_DARK or edges >= MIN_EDGES:
            raise SystemExit(0)

raise SystemExit(
    f"FAIL: render looks blank-ish: colored={colored}, dark={dark}, edges={edges}, samples={samples}, size={w}x{h}"
)
PY
}

_page_indicator_text() {
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python - "$tmp" <<'PY'
import sys, xml.etree.ElementTree as ET

xml_path = sys.argv[1]
rid = "org.opendroidpdf:id/page_indicator"
tree = ET.parse(xml_path)
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") == rid:
        print(node.attrib.get("text", ""))
        raise SystemExit(0)
print("")
PY
  rm -f "$tmp"
}

_wait_for_page_indicator_contains() {
  local needle="$1"
  local timeout_s="$2"
  local start now text
  start="$(date +%s)"
  while true; do
    text="$(_page_indicator_text 2>/dev/null || true)"
    if [[ "$text" == *"$needle"* ]]; then
      return 0
    fi
    now="$(date +%s)"
    if (( now - start >= timeout_s )); then
      echo "FAIL: page indicator did not contain '$needle' after ${timeout_s}s (got: '$text')" >&2
      return 1
    fi
    sleep 0.25
  done
}

_page_indicator_fragment() {
  local page_one_based="$1"
  local total_pages="$2"
  printf '%s / %s' "$page_one_based" "$total_pages"
}

_tap_next_page() {
  uia_open_navigate_view_sheet || return 1
  uia_tap_any_res_id "org.opendroidpdf:id/navigate_view_page_next" || return 1
  adb -s "$DEVICE" shell input keyevent 4 >/dev/null || true
  return 0
}

_tap_prev_page() {
  uia_open_navigate_view_sheet || return 1
  uia_tap_any_res_id "org.opendroidpdf:id/navigate_view_page_prev" || return 1
  adb -s "$DEVICE" shell input keyevent 4 >/dev/null || true
  return 0
}

adb -s "$DEVICE" get-state >/dev/null

PAGE_COUNT="${PAGE_COUNT:-$(_pdf_page_count "$PDF_LOCAL")}"
if [[ "$PAGE_COUNT" -le 1 ]]; then
  echo "FAIL: expected a multi-page PDF (pageCount=$PAGE_COUNT): $PDF_LOCAL" >&2
  exit 1
fi

APK_REAL="$(_resolve_apk)"
echo "[1/8] Install APK: $APK_REAL"
adb -s "$DEVICE" uninstall "$PKG" >/dev/null 2>&1 || true
adb -s "$DEVICE" install -r "$APK_REAL" >/dev/null

echo "[2/8] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/8] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null

echo "[4/8] Launch app and open the PDF via DocumentsUI (content:// URI)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT" >/dev/null
sleep 1.1

# On a fresh install, the dashboard may show a non-clickable "No recent documents" card.
# The reliable entry point is the toolbar action.
uia_tap_any_res_id "org.opendroidpdf:id/menu_open_document" || {
  echo "FAIL: could not tap toolbar Open document button" >&2
  exit 1
}
sleep 1.2

fname="$(basename "$PDF_REMOTE_PATH")"

# Many images launch DocumentsUI directly in Downloads; try to tap the pushed file directly first.
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

if uia_has_text_contains "$fname"; then
  uia_tap_text_contains "$fname" || {
    echo "FAIL: could not select $fname in DocumentsUI file list" >&2
    _fail_if_fatal_logcat "$LOGCAT_TXT" || true
    exit 1
  }
else
  uia_tap_any_res_id "com.android.documentsui:id/option_menu_search" || uia_tap_desc "Search" || {
    echo "FAIL: could not open DocumentsUI search" >&2
    exit 1
  }
  sleep 0.5
  adb -s "$DEVICE" shell input text "$fname"
  for _ in $(seq 1 20); do
    if uia_has_text_contains "$fname"; then
      break
    fi
    sleep 0.35
  done
  uia_tap_text_contains "$fname" || {
    echo "FAIL: could not select $fname in DocumentsUI search results" >&2
    _fail_if_fatal_logcat "$LOGCAT_TXT" || true
    exit 1
  }
fi

uia_assert_in_document_view
_fail_if_fatal_logcat "$LOGCAT_TXT"

echo "[5/8] Wait for initial page indicator"
_wait_for_page_indicator_contains "$(_page_indicator_fragment 1 "$PAGE_COUNT")" "$WAIT_PAGE_INDICATOR_S"

echo "[6/8] Switch pages via Navigate & View (loops=${LOOPS}, pages=${PAGE_COUNT})"
for ((iter = 1; iter <= LOOPS; iter++)); do
  echo "  loop $iter/$LOOPS: forward"
  for ((p = 2; p <= PAGE_COUNT; p++)); do
    _tap_next_page || { echo "FAIL: could not tap next page" >&2; exit 1; }
    _wait_for_page_indicator_contains "$(_page_indicator_fragment "$p" "$PAGE_COUNT")" "$WAIT_PAGE_INDICATOR_S"
    sleep "$WAIT_AFTER_SWITCH_S"
    shot="${OUT_PREFIX}_iter${iter}_page${p}.png"
    _screencap_png "$shot" || true
    _assert_nonblank_png "$shot"
    _fail_if_fatal_logcat "$LOGCAT_TXT"
    _fail_if_process_dead
  done

  echo "  loop $iter/$LOOPS: backward"
  for ((p = PAGE_COUNT - 1; p >= 1; p--)); do
    _tap_prev_page || { echo "FAIL: could not tap previous page" >&2; exit 1; }
    _wait_for_page_indicator_contains "$(_page_indicator_fragment "$p" "$PAGE_COUNT")" "$WAIT_PAGE_INDICATOR_S"
    sleep "$WAIT_AFTER_SWITCH_S"
    shot="${OUT_PREFIX}_iter${iter}_page${p}.png"
    _screencap_png "$shot" || true
    _assert_nonblank_png "$shot"
    _fail_if_fatal_logcat "$LOGCAT_TXT"
    _fail_if_process_dead
  done
done

echo "[7/8] Capture final artifacts"
_screencap_png "${OUT_PREFIX}_final.png" || true
_fail_if_fatal_logcat "$LOGCAT_TXT"

echo "[8/8] OK: page-switcher smoke passed"
echo "  screenshots: ${OUT_PREFIX}_iter*_page*.png, ${OUT_PREFIX}_final.png"
echo "  logcat:      $LOGCAT_TXT"
