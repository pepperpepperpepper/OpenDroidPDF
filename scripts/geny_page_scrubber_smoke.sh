#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for on-page page scrubber:
# - Installs a local debug APK by default (or APK=... / latest signed F-Droid repo APK)
# - Builds (optionally) a many-page PDF by concatenating a small fixture N times
# - Opens the PDF via DocumentsUI (content:// URI)
# - Scrubs to the end and back using the on-page SeekBar
# - Fails fast on crashes (logcat) or dead process
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_page_scrubber_smoke.sh
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF.apk PDF_LOCAL=/path/to/foo.pdf REPEAT=80 ./scripts/geny_page_scrubber_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/geny_uia.sh"

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

PDF_LOCAL="${PDF_LOCAL:-${ROOT_DIR}/test_pdf.pdf}"
REPEAT="${REPEAT:-60}"
PDF_REMOTE_PATH="${PDF_REMOTE_PATH:-/sdcard/Download/odp_scrub_big.pdf}"
SWIPE_MS="${SWIPE_MS:-260}"

OUTDIR="${OUTDIR:-.}"
mkdir -p "$OUTDIR"
OUT_PREFIX="${OUT_PREFIX:-${OUTDIR}/tmp_geny_page_scrubber_smoke}"
LOGCAT_TXT="${LOGCAT_TXT:-${OUT_PREFIX}_logcat.txt}"

# Optional: capture a screen recording of the scrub gesture for subjective "feel" review.
RECORD_SCRUB="${RECORD_SCRUB:-0}"
RECORD_REMOTE_PATH="${RECORD_REMOTE_PATH:-/sdcard/Download/odp_scrub_record.mp4}"
RECORD_LOCAL_PATH="${RECORD_LOCAL_PATH:-${OUT_PREFIX}_scrub_record.mp4}"
RECORD_TIME_LIMIT_S="${RECORD_TIME_LIMIT_S:-}"
SCREENREC_PID=""

# Optional: print preview latency metrics from logcat (requires debug build).
SCRUB_PREVIEW_METRICS="${SCRUB_PREVIEW_METRICS:-0}"

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
  # Some Genymotion images ship with flaky/system packages that may crash (IME, etc.).
  # Only treat crashes as failures when they involve OpenDroidPDF itself.
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

_summarize_scrub_preview_metrics() {
  local log_txt="$1"
  command -v python >/dev/null 2>&1 || return 0
  python - "$log_txt" <<'PY'
import re, sys

path = sys.argv[1]

def parse(lines):
    out = []
    for line in lines:
        m = re.search(r"ScrubPreview.*show page=(\d+) dtMs=(\d+) cached=(true|false)", line, re.I)
        if not m:
            continue
        out.append((int(m.group(1)), int(m.group(2)), m.group(3).lower() == "true"))
    return out

def pct(values, p):
    if not values:
        return None
    values = sorted(values)
    k = (len(values) - 1) * (p / 100.0)
    f = int(k)
    c = min(f + 1, len(values) - 1)
    if f == c:
        return values[f]
    return round(values[f] + (values[c] - values[f]) * (k - f), 1)

def fmt(values):
    if not values:
        return "n=0"
    values = sorted(values)
    return f"n={len(values)} p50={pct(values,50)}ms p90={pct(values,90)}ms p99={pct(values,99)}ms max={values[-1]}ms"

with open(path, "r", errors="ignore") as f:
    rows = parse(f)

all_dt = [dt for (_, dt, _) in rows]
cached_dt = [dt for (_, dt, cached) in rows if cached]
render_dt = [dt for (_, dt, cached) in rows if not cached]

print("Scrub preview latency (dt thumb->preview):")
print("  all   :", fmt(all_dt))
print("  cached:", fmt(cached_dt))
print("  render:", fmt(render_dt))
PY
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

adb -s "$DEVICE" get-state >/dev/null

# Enable debug preview latency logs (read once during view binding).
if [[ "$SCRUB_PREVIEW_METRICS" == "1" ]]; then
  adb -s "$DEVICE" shell setprop log.tag.ScrubPreview DEBUG >/dev/null 2>&1 || true
fi

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

echo "[6/7] Scrub to end and back"
# Wait until the on-page scrubber is present (multi-page docs only).
for _ in $(seq 1 120); do
  if uia_has_res_id "org.opendroidpdf:id/page_scrubber"; then break; fi
  sleep 0.5
done
uia_has_res_id "org.opendroidpdf:id/page_scrubber" || {
  echo "FAIL: on-page scrubber not found (expected multi-page doc)" >&2
  exit 1
}

bounds="$(_bounds_for_rid "org.opendroidpdf:id/page_scrubber")" || {
  echo "FAIL: could not resolve scrubber bounds (resource-id=org.opendroidpdf:id/page_scrubber)" >&2
  exit 1
}
read -r l t r b <<<"$bounds"
y=$(( (t + b) / 2 ))
x1=$(( l + 10 ))
x2=$(( r - 10 ))

if [[ "$RECORD_SCRUB" == "1" ]]; then
  if [[ -z "$RECORD_TIME_LIMIT_S" ]]; then
    # Give enough time to cover forward+back plus a small buffer.
    RECORD_TIME_LIMIT_S=$(( (SWIPE_MS * 2) / 1000 + 6 ))
    if (( RECORD_TIME_LIMIT_S < 8 )); then RECORD_TIME_LIMIT_S=8; fi
    if (( RECORD_TIME_LIMIT_S > 30 )); then RECORD_TIME_LIMIT_S=30; fi
  fi
  echo "  recording scrub: $RECORD_LOCAL_PATH (time_limit=${RECORD_TIME_LIMIT_S}s)"
  adb -s "$DEVICE" shell rm -f "$RECORD_REMOTE_PATH" >/dev/null 2>&1 || true
  adb -s "$DEVICE" shell screenrecord --time-limit "$RECORD_TIME_LIMIT_S" "$RECORD_REMOTE_PATH" >/dev/null 2>&1 &
  SCREENREC_PID="$!"
  # Let screenrecord start before we begin swiping.
  sleep 0.8
fi

adb -s "$DEVICE" shell input swipe "$x1" "$y" "$x2" "$y" "$SWIPE_MS"
sleep 0.6
_fail_if_process_dead
_screencap_png "${OUT_PREFIX}_after_forward.png"

adb -s "$DEVICE" shell input swipe "$x2" "$y" "$x1" "$y" "$SWIPE_MS"
sleep 0.6
_fail_if_process_dead
_screencap_png "${OUT_PREFIX}_after_back.png"

if [[ -n "${SCREENREC_PID:-}" ]]; then
  wait "$SCREENREC_PID" >/dev/null 2>&1 || true
  adb -s "$DEVICE" pull "$RECORD_REMOTE_PATH" "$RECORD_LOCAL_PATH" >/dev/null 2>&1 || true
fi

echo "[7/7] Check logcat for crashes"
_fail_if_fatal_logcat "$LOGCAT_TXT"

if [[ "$SCRUB_PREVIEW_METRICS" == "1" ]]; then
  _summarize_scrub_preview_metrics "$LOGCAT_TXT" || true
fi

echo "OK: scrubber smoke passed (${OUT_PREFIX}_after_forward.png, ${OUT_PREFIX}_after_back.png)"
