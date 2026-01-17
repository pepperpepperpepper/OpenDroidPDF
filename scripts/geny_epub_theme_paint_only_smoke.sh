#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for the invariant: "EPUB theme is paint-only and must NOT affect layoutProfileId".
#
# Steps:
# - Open a known EPUB in sidecar mode
# - Create a sidecar note (so we have annotations under the current layout id)
# - Change theme (Light -> Dark) via Reading settings
# - Force-stop + relaunch
# - Assert no "layout mismatch / annotations hidden" banner appears (i.e., theme did not change layoutProfileId)
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_theme_paint_only_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
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

_export_sidecar_db() {
  local out="$1"
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db >"$out"
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db-wal >"${out}-wal" 2>/dev/null || true
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db-shm >"${out}-shm" 2>/dev/null || true
}

_sqlite_count() {
  local db="$1"
  local table="$2"
  python - "$db" "$table" <<'PY'
import sqlite3, sys
db, table = sys.argv[1], sys.argv[2]
conn = sqlite3.connect(db)
try:
    cur = conn.execute(f"select count(*) from {table}")
    print(cur.fetchone()[0])
finally:
    conn.close()
PY
}

echo "[1/7] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/7] Clear app data (fresh sidecar DB)"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/7] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[4/7] Push sample EPUB and launch"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

if [[ -z "${DB_LOCAL:-}" ]]; then
  DB_LOCAL="$(mktemp -t geny_epub_theme_XXXXXX.db)"
  cleanup_db=1
else
  cleanup_db=0
fi

cleanup() {
  if [[ "${cleanup_db}" -eq 1 ]]; then
    rm -f -- "$DB_LOCAL" "${DB_LOCAL}-wal" "${DB_LOCAL}-shm" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "[5/7] Create a sidecar note (anchors layout id)"
uia_enter_add_text_mode || { echo "FAIL: add text entry point missing" >&2; exit 1; }
sleep 0.4
read -r w h < <(_wm_size)
adb -s "$DEVICE" shell input tap "$((w * 5 / 10))" "$((h * 45 / 100))"
sleep 0.8
_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after note" >&2; exit 1; }
notes_count="$(_sqlite_count "$DB_LOCAL" notes || echo 0)"
if [[ "$notes_count" -lt 1 ]]; then
  echo "FAIL: expected notes >= 1, got $notes_count" >&2
  exit 1
fi
echo "  notes: $notes_count"

echo "[6/7] Change theme (Light -> Dark) via Reading settings"
uia_open_navigate_view_sheet || { echo "FAIL: could not open Navigate & View sheet" >&2; exit 1; }
uia_tap_any_res_id "org.opendroidpdf:id/navigate_view_action_reading_settings" || uia_tap_text_contains "Reading settings" || {
  echo "FAIL: Reading settings missing" >&2
  exit 1
}
sleep 0.6
uia_tap_text_contains "Dark" || { echo "FAIL: Dark theme option not found" >&2; exit 1; }
sleep 0.3
uia_tap_text_contains "Apply" || uia_tap_any_res_id "android:id/button1" || { echo "FAIL: Apply not found" >&2; exit 1; }
sleep 1.5
uia_assert_in_document_view

echo "[7/7] Relaunch and assert no layout-mismatch banner (theme is paint-only)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
sleep 0.6
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

if uia_has_text_contains "annotated layout" || uia_has_text_contains "Annotations are hidden" || uia_has_text_contains "different layout"; then
  adb -s "$DEVICE" exec-out screencap -p >"${OUT:-tmp_geny_epub_theme_mismatch.png}" || true
  echo "FAIL: theme change triggered layout mismatch (theme must not affect layoutProfileId)" >&2
  echo "Logcat tail:" >&2
  adb -s "$DEVICE" logcat -d | tail -n 120 >&2
  exit 1
fi

echo "Smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 120
