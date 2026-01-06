#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke: import a per-document sidecar annotations bundle (JSON) and assert
# the imported annotations are visible via a re-export.
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_sidecar_bundle_import_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/hello.epub}
EPUB_REMOTE=${EPUB_REMOTE:-/sdcard/Download/hello.epub}
ARTIFACT_DIR=${ARTIFACT_DIR:-generated/geny_sidecar_bundle_import}
NOTE_TEXT=${NOTE_TEXT:-ODP_SIDECAR_IMPORT}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null
mkdir -p "$ARTIFACT_DIR"

_wm_size() {
  # Prints: "<w> <h>"
  local line
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | grep -Eo '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

_documentsui_dump_artifact() {
  local label="$1"
  local out="$ARTIFACT_DIR/documentsui_${label}_$(date +%s).xml"
  _uia_dump_to "$out" || true
  echo "  [artifact] wrote $out" >&2
}

_documentsui_tap_clickable_row_by_text() {
  local name="$1"
  local tmp coords
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  set +e
  coords="$(python - "$tmp" "$name" <<'PY'
import re, sys, xml.etree.ElementTree as ET

xml_path, needle = sys.argv[1], sys.argv[2]
needle_l = (needle or "").lower()
tree = ET.parse(xml_path)

def rect(bounds: str):
    m = re.match(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]", bounds or "")
    if not m:
        return None
    return tuple(map(int, m.groups()))

def center(r):
    l, t, rr, b = r
    return (l + rr) // 2, (t + b) // 2

def is_true(v):
    return (v or "").lower() == "true"

title_point = None
title_rids = {"android:id/title", "com.android.documentsui:id/title", "android:id/text1"}
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") not in title_rids:
        continue
    txt = (node.attrib.get("text", "") or "")
    if needle_l not in txt.lower():
        continue
    r = rect(node.attrib.get("bounds", ""))
    if not r:
        continue
    title_point = center(r)
    break

if not title_point:
    raise SystemExit(1)

x, y = title_point

# Find the smallest enabled+clickable container containing the title point.
best = None
best_area = None
for node in tree.iter("node"):
    r = rect(node.attrib.get("bounds", ""))
    if not r:
        continue
    l, t, rr, b = r
    if not (l <= x <= rr and t <= y <= b):
        continue
    if not is_true(node.attrib.get("clickable")):
        continue
    if not is_true(node.attrib.get("enabled")):
        continue
    area = max(1, (rr - l) * (b - t))
    if best is None or area < best_area:
        best = r
        best_area = area

if not best:
    raise SystemExit(3)

cx, cy = center(best)
print(f"{cx} {cy}")
raise SystemExit(0)
PY
)"
  py_rc=$?
  set -e
  if [[ "$py_rc" -ne 0 ]]; then
    if [[ "${DOCSUI_DEBUG:-0}" == "1" ]]; then
      echo "  [docsui] tap_row '$name' failed (rc=$py_rc)" >&2
    fi
    rm -f "$tmp"
    return 1
  fi
  rm -f "$tmp"
  set -- $coords
  adb -s "$DEVICE" shell input tap "$1" "$2"
  return 0
}

_documentsui_switch_to_downloads() {
  if uia_has_text_contains "Downloads" && ! uia_has_res_id "com.android.documentsui:id/drawer_roots"; then
    return 0
  fi

  for _ in $(seq 1 10); do
    if ! uia_has_res_id "com.android.documentsui:id/drawer_roots"; then
      uia_tap_docsui_roots_drawer || true
      sleep 0.7
    fi

    if uia_has_res_id "com.android.documentsui:id/drawer_roots"; then
      if _documentsui_tap_clickable_row_by_text "Downloads"; then
        sleep 0.8
      fi
    fi

    if ! uia_has_res_id "com.android.documentsui:id/drawer_roots" && uia_has_text_contains "Downloads"; then
      return 0
    fi
    sleep 0.6
  done

  _documentsui_dump_artifact "switch_to_downloads_fail"
  return 1
}

echo "[1/10] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/10] Clear app data + grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[3/10] Push sample EPUB"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null

echo "[4/10] Launch viewer with sample EPUB"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[5/10] Create a sidecar note (tap-to-add)"
uia_tap_any_res_id "org.opendroidpdf:id/menu_add_text_annot" || uia_tap_desc "Add text" || { echo "FAIL: add text not found" >&2; exit 1; }
sleep 0.4
read -r w h < <(_wm_size)
adb -s "$DEVICE" shell input tap "$((w * 5 / 10))" "$((h * 45 / 100))"
sleep 0.8
# Some builds prompt for note text immediately (shared "text annotation" dialog).
for _ in $(seq 1 10); do
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    break
  fi
  sleep 0.2
done
if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
  uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || true
  adb -s "$DEVICE" shell input text "$NOTE_TEXT"
  sleep 0.2
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || { echo "FAIL: could not confirm note text dialog" >&2; exit 1; }
  sleep 0.8
fi

echo "[6/10] Export annotations bundle"
adb -s "$DEVICE" exec-out run-as "$PKG" sh -c 'rm -f cache/tmpfiles/*_annotations_*.json 2>/dev/null || true'
uia_tap_desc "More options"
sleep 0.4
uia_tap_text_contains "Export annotations" || { echo "FAIL: Export annotations menu item not found" >&2; exit 1; }
sleep 1.2
adb -s "$DEVICE" shell input keyevent 4
sleep 0.4

OUT_JSON="${OUT_JSON:-$ARTIFACT_DIR/sidecar_bundle_export.json}"
latest="$(adb -s "$DEVICE" exec-out run-as "$PKG" sh -c 'ls -t cache/tmpfiles 2>/dev/null | grep -E "_annotations_.*\\.json$" | head -n 1' | tr -d '\r')"
if [[ -z "$latest" ]]; then
  echo "FAIL: no exported *_annotations_*.json found under cache/tmpfiles" >&2
  exit 1
fi
adb -s "$DEVICE" exec-out run-as "$PKG" cat "cache/tmpfiles/$latest" >"$OUT_JSON"
echo "  wrote $OUT_JSON"

echo "[7/10] Reset app data and push bundle for import"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null

IMPORT_NAME="${IMPORT_NAME:-odpi.json}"
IMPORT_TITLE_MATCH="${IMPORT_TITLE_MATCH:-odpi}"
IMPORT_REMOTE="/sdcard/Download/$IMPORT_NAME"
adb -s "$DEVICE" push "$OUT_JSON" "$IMPORT_REMOTE" >/dev/null
adb -s "$DEVICE" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$IMPORT_REMOTE" >/dev/null 2>&1 || true

echo "[8/10] Relaunch viewer"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[9/10] Import annotations bundle (debug intent hook; avoids DocumentsUI flakiness)"
adb -s "$DEVICE" shell am start -a org.opendroidpdf.DEBUG_IMPORT_SIDECAR_BUNDLE \
  -d "file://$IMPORT_REMOTE" \
  --ez org.opendroidpdf.EXTRA_FORCE_IMPORT true \
  -f 0x20000000 \
  "$PKG/$ACT" >/dev/null
sleep 3.0
uia_assert_in_document_view

echo "[10/10] Re-export and assert annotations exist"
adb -s "$DEVICE" exec-out run-as "$PKG" sh -c 'rm -f cache/tmpfiles/*_annotations_*.json 2>/dev/null || true'
uia_tap_desc "More options"
sleep 0.4
uia_tap_text_contains "Export annotations" || { echo "FAIL: Export annotations menu item not found after import" >&2; exit 1; }
sleep 1.2
adb -s "$DEVICE" shell input keyevent 4
sleep 0.4

OUT_JSON_AFTER="${OUT_JSON_AFTER:-$ARTIFACT_DIR/sidecar_bundle_after_import.json}"
latest2="$(adb -s "$DEVICE" exec-out run-as "$PKG" sh -c 'ls -t cache/tmpfiles 2>/dev/null | grep -E "_annotations_.*\\.json$" | head -n 1' | tr -d '\r')"
if [[ -z "$latest2" ]]; then
  echo "FAIL: no exported *_annotations_*.json found after import" >&2
  exit 1
fi
adb -s "$DEVICE" exec-out run-as "$PKG" cat "cache/tmpfiles/$latest2" >"$OUT_JSON_AFTER"
echo "  wrote $OUT_JSON_AFTER"

python - "$OUT_JSON_AFTER" <<'PY'
import json, sys
path = sys.argv[1]
with open(path, "rb") as f:
    data = json.loads(f.read().decode("utf-8"))
assert data.get("format") == "opendroidpdf-sidecar"
notes = data.get("notes") or []
assert len(notes) >= 1, f"expected notes >= 1, got {len(notes)}"
PY

echo "PASS"
