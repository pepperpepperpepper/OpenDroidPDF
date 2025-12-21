#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for EPUB layout mismatch UX + export guard:
# - Open a sample EPUB
# - Create a sidecar note (records annotated layout)
# - Change reading settings (layout-affecting) so annotations are hidden
# - Assert the mismatch banner is shown and Share/Print are blocked (no export file)
# - Tap "Switch" to return to annotated layout and assert Share exports again
#
# Usage:
#   DEVICE=localhost:42865 APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_layout_mismatch_smoke.sh

DEVICE=${DEVICE:-localhost:42865}
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/hello.epub}
EPUB_REMOTE=${EPUB_REMOTE:-/sdcard/Download/hello.epub}

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

_uia_bounds_for_rid() {
  local rid="$1"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python - "$tmp" "$rid" <<'PY'
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
    l, t, r, bt = map(int, m.groups())
    print(l, t, r, bt)
    raise SystemExit(0)
raise SystemExit(1)
PY
  rm -f "$tmp"
}

_tap_seekbar_ratio() {
  # Tap on a seekbar at a given horizontal ratio (0..1) to change its value.
  local rid="$1"
  local ratio="${2:-0.85}"
  local l t r b w x y
  read -r l t r b < <(_uia_bounds_for_rid "$rid")
  w=$((r - l))
  if (( w <= 0 )); then
    echo "FAIL: seekbar bounds invalid for $rid" >&2
    return 1
  fi
  x=$((l + (w * 85 / 100)))
  y=$(((t + b) / 2))
  adb -s "$DEVICE" shell input tap "$x" "$y"
}

_list_tmpfiles() {
  adb -s "$DEVICE" shell run-as "$PKG" ls -1 cache/tmpfiles 2>/dev/null | tr -d '\r' | sort || true
}

echo "[1/9] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/9] Clear app data (fresh sidecar DB + tmpfiles)"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/9] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[4/9] Push sample EPUB"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null

echo "[5/9] Launch viewer with sample EPUB"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[6/9] Create a sidecar note (records annotated layout)"
uia_tap_any_res_id "org.opendroidpdf:id/menu_add_text_annot" || uia_tap_desc "Add text" || { echo "FAIL: add text not found" >&2; exit 1; }
sleep 0.5
read -r w h < <(_wm_size)
adb -s "$DEVICE" shell input tap "$((w * 5 / 10))" "$((h * 45 / 100))"
sleep 0.8

echo "[7/9] Change reading settings (layout-affecting) to trigger mismatch"
uia_tap_desc "More options"
sleep 0.4
uia_tap_text_contains "Reading settings" || { echo "FAIL: Reading settings missing" >&2; exit 1; }
sleep 0.8

# Increase font size seekbar to force a relayout.
_tap_seekbar_ratio "org.opendroidpdf:id/reflow_seek_font_size" 0.85 || { echo "FAIL: could not adjust font size seekbar" >&2; exit 1; }
sleep 0.3
uia_tap_text_contains "Apply" || { echo "FAIL: Apply not found" >&2; exit 1; }
sleep 3

# Assert the mismatch banner is present.
uia_has_text_contains "Annotations are hidden" || uia_has_text_contains "different layout" || {
  echo "FAIL: expected layout mismatch banner after relayout" >&2
  echo "Logcat tail:" >&2
  adb -s "$DEVICE" logcat -d | tail -n 80 >&2
  exit 1
}

echo "[8/9] Assert Share/Print are blocked under mismatch (no export file)"
before="$(mktemp -t geny_epub_mismatch_before_XXXXXX.txt)"
after="$(mktemp -t geny_epub_mismatch_after_XXXXXX.txt)"
cleanup() { rm -f -- "$before" "$after" 2>/dev/null || true; }
trap cleanup EXIT

_list_tmpfiles >"$before"

uia_tap_desc "More options"
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/menu_share" || uia_tap_text_contains "Share" || true
sleep 2

_list_tmpfiles >"$after"
new_file="$(comm -13 "$before" "$after" | tail -n 1 || true)"
if [[ -n "$new_file" ]]; then
  echo "FAIL: expected Share to be blocked under mismatch, but export file appeared: $new_file" >&2
  exit 1
fi

echo "  OK: no share export created while mismatch banner active"

_list_tmpfiles >"$before"
uia_tap_desc "More options"
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/menu_print" || uia_tap_text_contains "Print" || true
sleep 2

_list_tmpfiles >"$after"
new_file="$(comm -13 "$before" "$after" | tail -n 1 || true)"
if [[ -n "$new_file" ]]; then
  echo "FAIL: expected Print to be blocked under mismatch, but export file appeared: $new_file" >&2
  exit 1
fi

echo "  OK: no print export created while mismatch banner active"

echo "[9/9] Switch back to annotated layout and verify Share exports"
# Close overflow menu only if it is still open (avoid backing out of the document view).
if uia_has_text_contains "Contents" || uia_has_text_contains "Reading settings" || uia_has_text_contains "Go to page"; then
  adb -s "$DEVICE" shell input keyevent 4 >/dev/null || true
  sleep 0.5
fi
uia_assert_in_document_view
uia_tap_any_res_id "org.opendroidpdf:id/snackbar_action" "com.google.android.material:id/snackbar_action" || uia_tap_text_contains "Switch" || {
  echo "FAIL: switch action not found on mismatch banner" >&2
  exit 1
}
sleep 3

_list_tmpfiles >"$before"
uia_tap_desc "More options"
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/menu_share" || uia_tap_text_contains "Share" || { echo "FAIL: Share not found" >&2; exit 1; }
# Wait for export to complete (flatten export can be slow on emulators).
new_file=""
for _ in $(seq 1 15); do
  sleep 1
  _list_tmpfiles >"$after"
  new_file="$(comm -13 "$before" "$after" | tail -n 1 || true)"
  if [[ -n "$new_file" ]]; then
    break
  fi
done
if [[ -z "$new_file" ]]; then
  echo "FAIL: expected export file after switching back to annotated layout" >&2
  echo "Logcat tail:" >&2
  adb -s "$DEVICE" logcat -d | tail -n 80 >&2
  exit 1
fi

# Chooser may appear; back out to keep the device stable.
if uia_has_text_contains "Share with"; then
  adb -s "$DEVICE" shell input keyevent 4 >/dev/null || true
fi

echo "  exported: cache/tmpfiles/$new_file"
echo "Smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
