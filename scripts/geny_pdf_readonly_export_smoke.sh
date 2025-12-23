#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "PDF sidecar mode" (read-only PDF):
# - Open a PDF from a read-only filesystem path (forces sidecar annotations)
# - Draw + accept (persist sidecar ink)
# - Share (export) and assert the exported PDF is NOT flattened (size heuristic)
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_readonly_export_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_with_text.pdf}
PDF_REMOTE=${PDF_REMOTE:-/data/local/tmp/odp_readonly.pdf}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

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

_draw_swipe() {
  local w h x1 y1 x2 y2 dur
  read -r w h < <(_wm_size)
  x1=$((w * 2 / 10))
  x2=$((w * 8 / 10))
  y1=$((h * 35 / 100))
  y2=$((h * 55 / 100))
  dur=${1:-240}
  adb -s "$DEVICE" shell input swipe "$x1" "$y1" "$x2" "$y2" "$dur"
}

_list_tmpfiles() {
  adb -s "$DEVICE" shell run-as "$PKG" ls -1 cache/tmpfiles 2>/dev/null | tr -d '\r' | sort || true
}

_newest_tmpfile() {
  adb -s "$DEVICE" shell run-as "$PKG" ls -1t cache/tmpfiles 2>/dev/null | tr -d '\r' | head -n 1 || true
}

_pull_app_tmpfile() {
  local remote_rel="$1"
  local out_path="$2"
  # Prefer exec-out for binary-safe transfer.
  if adb -s "$DEVICE" exec-out run-as "$PKG" cat "$remote_rel" >"$out_path" 2>/dev/null; then
    return 0
  fi
  # Fallback: adb shell (best-effort).
  adb -s "$DEVICE" shell run-as "$PKG" cat "$remote_rel" >"$out_path"
}

echo "[1/7] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/7] Clear app data (fresh sidecar DB + tmpfiles)"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/7] Push PDF to a read-only path"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE" >/dev/null
adb -s "$DEVICE" shell chmod 444 "$PDF_REMOTE" >/dev/null || true

echo "[4/7] Launch viewer with read-only PDF (forces sidecar mode)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[5/7] Draw + accept (persist sidecar ink)"
uia_tap_any_res_id "org.opendroidpdf:id/draw_image_button" "org.opendroidpdf:id/menu_draw"
sleep 0.4
_draw_swipe 260
sleep 0.5
uia_tap_any_res_id "org.opendroidpdf:id/accept_image_button" "org.opendroidpdf:id/menu_accept"
sleep 1.0

echo "[6/7] Share (export) and assert non-flattened output"
before="$(mktemp -t geny_readonly_pdf_before_XXXXXX.txt)"
after="$(mktemp -t geny_readonly_pdf_after_XXXXXX.txt)"
exported_local="$(mktemp -t geny_readonly_export_XXXXXX.pdf)"
cleanup() { rm -f -- "$before" "$after" "$exported_local" 2>/dev/null || true; }
trap cleanup EXIT

_list_tmpfiles >"$before"

if uia_tap_desc "More options"; then
  sleep 0.4
  uia_tap_any_res_id "org.opendroidpdf:id/menu_share" || uia_tap_text_contains "Share" || true
fi

# Chooser may appear; back out to keep the run stable.
sleep 3
adb -s "$DEVICE" shell input keyevent 4 >/dev/null || true
sleep 1

_list_tmpfiles >"$after"
new_file="$(comm -13 "$before" "$after" | tail -n 1 || true)"
if [[ -z "$new_file" ]]; then
  # Fallback: use newest file if comm couldn't determine a delta (best-effort).
  new_file="$(_newest_tmpfile)"
fi
if [[ -z "$new_file" ]]; then
  echo "FAIL: no exported PDF found in cache/tmpfiles" >&2
  exit 1
fi

orig_size="$(adb -s "$DEVICE" shell stat -c %s \"$PDF_REMOTE\" | tr -d '\r' || echo 0)"
export_size="$(adb -s "$DEVICE" shell run-as \"$PKG\" stat -c %s \"cache/tmpfiles/$new_file\" | tr -d '\r' || echo 0)"

echo "  exported: cache/tmpfiles/$new_file"
echo "  sizes: original=${orig_size}B exported=${export_size}B"

if [[ "$orig_size" -gt 0 ]] && [[ "$export_size" -gt 0 ]]; then
  # Heuristic: flattened export rasterizes the page, producing a much larger file.
  # Keep the threshold generous to avoid false positives on small PDFs.
  if (( export_size > orig_size * 100 )); then
    echo "FAIL: exported PDF is unexpectedly large; likely flattened (embed export failed)" >&2
    echo "Logcat tail:" >&2
    adb -s "$DEVICE" logcat -d | tail -n 80 >&2
    exit 1
  fi
fi

# Stronger check than size heuristics: embedded export should preserve extractable text.
if command -v pdftotext >/dev/null; then
  _pull_app_tmpfile "cache/tmpfiles/$new_file" "$exported_local"
  if ! pdftotext "$exported_local" - 2>/dev/null | rg -F "quick brown fox" >/dev/null; then
    echo "FAIL: exported PDF appears flattened (pdftotext did not find expected text)" >&2
    echo "Logcat tail:" >&2
    adb -s "$DEVICE" logcat -d | tail -n 80 >&2
    exit 1
  fi
else
  echo "WARN: pdftotext not found; relying on size heuristic only" >&2
fi

echo "[7/7] Done"
echo "Smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
