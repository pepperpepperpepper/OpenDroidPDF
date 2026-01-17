#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for document identity:
# - Open an EPUB from a file:// URI
# - Draw/commit ink (sidecar)
# - Rename/move the underlying file (new URI string)
# - Reopen and draw/commit again
# - Assert ink rows still belong to ONE doc_id (content-based id, not URI string)
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_docid_rename_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/edge.epub}
EPUB_REMOTE_DIR=${EPUB_REMOTE_DIR:-/sdcard/Download}

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

_export_sidecar_db() {
  local out="$1"
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db >"$out"
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db-wal >"${out}-wal" 2>/dev/null || true
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db-shm >"${out}-shm" 2>/dev/null || true
}

_sqlite_scalar() {
  local db="$1"
  local sql="$2"
  python - "$db" "$sql" <<'PY'
import sqlite3, sys
db, sql = sys.argv[1], sys.argv[2]
conn = sqlite3.connect(db)
try:
    cur = conn.execute(sql)
    row = cur.fetchone()
    print("" if row is None or row[0] is None else row[0])
finally:
    conn.close()
PY
}

_draw_commit() {
  uia_enter_draw_mode || { echo "FAIL: draw entry point missing" >&2; exit 1; }
  sleep 0.5
  _draw_swipe 220
  sleep 0.2
  _draw_swipe 220
  sleep 0.4
  uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || uia_tap_desc "Accept" || { echo "FAIL: accept not found" >&2; exit 1; }
  sleep 0.8
}

REMOTE_A="$EPUB_REMOTE_DIR/docid_a.epub"
REMOTE_B="$EPUB_REMOTE_DIR/docid_b.epub"

echo "[1/9] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/9] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/9] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[4/9] Push EPUB as docid_a.epub"
adb -s "$DEVICE" shell rm -f "$REMOTE_A" "$REMOTE_B" >/dev/null 2>&1 || true
adb -s "$DEVICE" push "$EPUB_LOCAL" "$REMOTE_A" >/dev/null

echo "[5/9] Launch with file://docid_a.epub and commit ink"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$REMOTE_A" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view
_draw_commit

DB_LOCAL="$(mktemp -t geny_docid_rename_XXXXXX.db)"
trap 'rm -f -- "$DB_LOCAL" "${DB_LOCAL}-wal" "${DB_LOCAL}-shm" 2>/dev/null || true' EXIT

_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after first commit" >&2; exit 1; }
doc_id_1="$(_sqlite_scalar "$DB_LOCAL" "select doc_id from ink_strokes limit 1")"
distinct_1="$(_sqlite_scalar "$DB_LOCAL" "select count(distinct doc_id) from ink_strokes")"
echo "  doc_id after first commit: $doc_id_1 (distinct=$distinct_1)"
if [[ -z "$doc_id_1" || "$distinct_1" != "1" ]]; then
  echo "FAIL: expected exactly one ink doc_id after first commit" >&2
  exit 1
fi
if [[ "$doc_id_1" != sha256:* ]]; then
  echo "FAIL: expected content-based doc_id (sha256:*), got '$doc_id_1'" >&2
  exit 1
fi

echo "[6/9] Rename file on device (docid_a -> docid_b)"
adb -s "$DEVICE" shell mv "$REMOTE_A" "$REMOTE_B" >/dev/null

echo "[7/9] Relaunch with file://docid_b.epub and commit ink again"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$REMOTE_B" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view
_draw_commit

_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after second commit" >&2; exit 1; }
doc_id_2="$(_sqlite_scalar "$DB_LOCAL" "select doc_id from ink_strokes order by created_at_ms desc limit 1")"
distinct_2="$(_sqlite_scalar "$DB_LOCAL" "select count(distinct doc_id) from ink_strokes")"
echo "  doc_id after second commit: $doc_id_2 (distinct=$distinct_2)"
if [[ "$distinct_2" != "1" ]]; then
  echo "FAIL: expected ink to remain under a single doc_id after rename (distinct=$distinct_2)" >&2
  exit 1
fi
if [[ "$doc_id_2" != "$doc_id_1" ]]; then
  echo "FAIL: expected doc_id to remain stable across rename (before=$doc_id_1 after=$doc_id_2)" >&2
  exit 1
fi

echo "[8/9] Logcat fatal check"
if adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" >/dev/null; then
  echo "FAIL: detected fatal logcat entries" >&2
  adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" | tail -n 40 >&2
  exit 1
fi

echo "[9/9] Done"
echo "DocId rename smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
