#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for EPUB viewport/recents restore:
# - Open an EPUB with a TOC (edge fixture)
# - Navigate to a later section via Contents
# - Background the app (triggers onPause -> saveViewport)
# - Force-stop to ensure persistence is exercised
# - Mutate the saved viewport snapshot to force a layout mismatch and clear docProgress/page,
#   so restore must use the stable MuPDF reflow location (chapter/page)
# - Relaunch the same EPUB and assert the toolbar page indicator restores
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_viewport_restore_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/edge.epub}
EPUB_REMOTE=${EPUB_REMOTE:-/sdcard/Download/edge.epub}
TOC_ENTRY_TEXT=${TOC_ENTRY_TEXT:-Long Paragraphs}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_page_indicator_text() {
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python - "$tmp" <<'PY'
import re, sys, xml.etree.ElementTree as ET

xml_path = sys.argv[1]
try:
    tree = ET.parse(xml_path)
except Exception:
    raise SystemExit(1)

rx = re.compile(r"^\d+/\d+$")

def top_of(bounds: str):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    return int(m.group(2))

candidates = []
for node in tree.iter("node"):
    for key in ("text", "content-desc"):
        raw = (node.attrib.get(key) or "").strip()
        if not raw:
            continue
        if not rx.match(raw):
            continue
        t = top_of(node.attrib.get("bounds") or "")
        if t is None:
            continue
        candidates.append((t, raw))

if not candidates:
    raise SystemExit(1)

candidates.sort(key=lambda x: x[0])
print(candidates[0][1])
raise SystemExit(0)
PY
  local rc=$?
  rm -f "$tmp"
  return $rc
}

_force_mismatch_restore_uses_location() {
  # After the viewport has been saved, force a reflow "layout mismatch" by mutating the stored
  # viewport snapshot. We clear docProgress/page so restore must use reflowLocation.
  local prefs tmp out
  prefs="$(mktemp -t geny_epub_viewport_prefs_XXXXXX.xml)"
  tmp="$(mktemp -t geny_epub_viewport_prefs_mut_XXXXXX.xml)"

  adb -s "$DEVICE" exec-out run-as "$PKG" cat "shared_prefs/OpenDroidPDF.xml" >"$prefs" || {
    echo "FAIL: could not export shared_prefs/OpenDroidPDF.xml" >&2
    rm -f "$prefs" "$tmp"
    return 1
  }

  out="$(python - "$prefs" "$tmp" <<'PY'
import sys, xml.etree.ElementTree as ET

src, dst = sys.argv[1], sys.argv[2]
tree = ET.parse(src)
root = tree.getroot()

def find_pref(tag, name):
    for el in root.findall(tag):
        if el.attrib.get("name") == name:
            return el
    return None

def find_any(name):
    for el in root.iter():
        if el.attrib.get("name") == name:
            return el
    return None

# Prefer the viewport's stored reflow location key, since recents may not be persisted yet.
doc_id = ""
for el in root.iter():
    name = el.attrib.get("name", "") or ""
    if name.startswith("reflowLocation"):
        doc_id = name[len("reflowLocation"):]
        break

if not doc_id:
    # Fallback: stable docId recorded by recents; otherwise the uri string.
    doc_id_el = find_pref("string", "recentfile_docId0")
    doc_id = (doc_id_el.attrib.get("value") if doc_id_el is not None else "") or ""
    if not doc_id:
        uri_el = find_pref("string", "recentfile0")
        doc_id = (uri_el.attrib.get("value") if uri_el is not None else "") or ""

if not doc_id:
    raise SystemExit("FAIL: could not locate a viewport reflowLocation key or recentfile doc id (viewport not saved?)")

def set_or_add(tag, name, value):
    el = find_pref(tag, name)
    if el is None:
        el = ET.SubElement(root, tag, {"name": name})
    el.attrib["value"] = value

# Force mismatch and force restore to use location.
set_or_add("string", f"layoutProfileId{doc_id}", "bogus_layout_for_smoke")
set_or_add("float", f"docprogress{doc_id}", "-1.0")
set_or_add("int", f"page{doc_id}", "0")

tree.write(dst, encoding="utf-8", xml_declaration=True)
print(doc_id)
PY
)" || true

if [[ -z "$out" || "$out" == FAIL* ]]; then
  echo "${out:-FAIL: pref mutation failed}" >&2
  rm -f "$prefs" "$tmp"
  return 1
fi

  # Avoid shell redirection quoting issues: push to /data/local/tmp then copy into shared_prefs via run-as.
  local remote_tmp="/data/local/tmp/odp_OpenDroidPDF_mut.xml"
  adb -s "$DEVICE" push "$tmp" "$remote_tmp" >/dev/null || {
    echo "FAIL: could not push mutated prefs to $remote_tmp" >&2
    rm -f "$prefs" "$tmp"
    return 1
  }
  adb -s "$DEVICE" shell run-as "$PKG" cp "$remote_tmp" "shared_prefs/OpenDroidPDF.xml" >/dev/null || {
    echo "FAIL: could not copy mutated prefs into app shared_prefs" >&2
    adb -s "$DEVICE" shell rm -f "$remote_tmp" >/dev/null 2>&1 || true
    rm -f "$prefs" "$tmp"
    return 1
  }
  adb -s "$DEVICE" shell rm -f "$remote_tmp" >/dev/null 2>&1 || true

  rm -f "$prefs" "$tmp"
  echo "  mutated viewport snapshot for docId: $out" >&2
  return 0
}

echo "[1/8] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/8] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/8] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[4/8] Push EPUB"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null

echo "[5/8] Launch viewer with EPUB"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

page_before=""
for _ in {1..60}; do
  page_before="$(_page_indicator_text 2>/dev/null || true)"
  if [[ -n "$page_before" ]]; then
    break
  fi
  sleep 0.5
done
if [[ -z "$page_before" ]]; then
  _uia_dump_to tmp_geny_epub_viewport_restore_before_ui.xml || true
  echo "FAIL: could not detect toolbar page indicator" >&2
  exit 1
fi
echo "  page indicator before: $page_before"

echo "[6/8] Navigate via Contents -> \"$TOC_ENTRY_TEXT\""
uia_tap_desc "More options"
sleep 0.4
uia_tap_text_contains "Contents" || { echo "FAIL: Contents menu missing" >&2; exit 1; }
sleep 0.8
uia_tap_text_contains "$TOC_ENTRY_TEXT" || {
  _uia_dump_to tmp_geny_epub_viewport_restore_toc_ui.xml || true
  adb -s "$DEVICE" exec-out screencap -p > tmp_geny_epub_viewport_restore_toc_fail.png || true
  echo "FAIL: could not find TOC entry '$TOC_ENTRY_TEXT'" >&2
  exit 1
}
sleep 0.6

page_target=""
for _ in {1..40}; do
  cur="$(_page_indicator_text 2>/dev/null || true)"
  if [[ -n "$cur" && "$cur" != "$page_before" ]]; then
    page_target="$cur"
    break
  fi
  sleep 0.5
done
if [[ -z "$page_target" ]]; then
  _uia_dump_to tmp_geny_epub_viewport_restore_after_nav_ui.xml || true
  adb -s "$DEVICE" exec-out screencap -p > tmp_geny_epub_viewport_restore_after_nav.png || true
  echo "FAIL: page indicator did not change after TOC navigation (before=$page_before)" >&2
  exit 1
fi
echo "  page indicator after nav: $page_target"

echo "[7/8] Background (HOME) -> force-stop -> relaunch and assert restore"
adb -s "$DEVICE" shell input keyevent 3
sleep 1.2
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
sleep 0.6

_force_mismatch_restore_uses_location || {
  echo "FAIL: could not mutate saved viewport snapshot for location-only restore" >&2
  exit 1
}

adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

page_restored=""
for _ in {1..40}; do
  cur="$(_page_indicator_text 2>/dev/null || true)"
  if [[ -n "$cur" ]]; then
    page_restored="$cur"
    break
  fi
  sleep 0.5
done
if [[ -z "$page_restored" ]]; then
  _uia_dump_to tmp_geny_epub_viewport_restore_relaunch_ui.xml || true
  echo "FAIL: could not detect page indicator after relaunch" >&2
  exit 1
fi
echo "  page indicator after relaunch: $page_restored"

if [[ "$page_restored" != "$page_target" ]]; then
  adb -s "$DEVICE" exec-out screencap -p > tmp_geny_epub_viewport_restore_relaunch_fail.png || true
  _uia_dump_to tmp_geny_epub_viewport_restore_relaunch_fail_ui.xml || true
  echo "FAIL: viewport not restored (expected=$page_target got=$page_restored)" >&2
  exit 1
fi

echo "[8/8] Logcat fatal check"
if adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" >/dev/null; then
  echo "FAIL: detected fatal logcat entries" >&2
  adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" | tail -n 40 >&2
  exit 1
fi

echo "EPUB viewport restore smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
