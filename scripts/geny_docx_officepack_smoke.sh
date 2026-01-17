#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for Word (.docx/.doc) behavior when the Office Pack is installed.
#
# Ensures:
# - the main app detects the Office Pack
# - signature verification passes (same signing key)
# - the app binds to the Office Pack service and routes the conversion call
# - the Office Pack converts the docx fixture to a PDF and the main app opens it
# - the rendered output is non-blank (and optionally contains a known token via OCR)
# - sidecar annotations + recents/viewport key off the Word docId (not the cache PDF)
# - the derived PDF is cached and reused on reopen
#
# Usage:
#   DEVICE=localhost:<port> \
#   APK=/path/to/OpenDroidPDF-debug.apk \
#   APK_OFFICEPACK=/path/to/officepack-debug.apk \
#   ./scripts/geny_docx_officepack_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
APK_OFFICEPACK=${APK_OFFICEPACK:-/mnt/subtitled/opendroidpdf-android-build/officepack/outputs/apk/debug/officepack-debug.apk}
DOCX_LOCAL=${DOCX_LOCAL:-test_assets/word_with_text.docx}
# Keep smokes independent of MANAGE_EXTERNAL_STORAGE by using app-private storage.
DOCX_REMOTE=${DOCX_REMOTE:-/data/data/org.opendroidpdf/files/word_with_text.docx}
DOCX_NAME=${DOCX_NAME:-$(basename "$DOCX_REMOTE")}
# OCR of rendered output is inherently fuzzy; keep the assertions based on stable
# words present in the fixture rather than an exact token match.
EXPECTED_TOKEN=${EXPECTED_TOKEN:-opendroidpdf-fixture}
ASSERT_OCR=${ASSERT_OCR:-1}
ASSERT_MUPDF_TEXT=${ASSERT_MUPDF_TEXT:-1}
ASSERT_RED_PIXELS=${ASSERT_RED_PIXELS:-0}
MIN_RED_PIXELS=${MIN_RED_PIXELS:-250}
NOTE_TEXT=${NOTE_TEXT:-ODP_DOCX_NOTE}

PKG=org.opendroidpdf
PKG_OFFICEPACK=org.opendroidpdf.officepack
ACT=.OpenDroidPDFActivity
DOCX_MIME="application/vnd.openxmlformats-officedocument.wordprocessingml.document"

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null
uia_disable_flaky_ime

_wm_size() {
  # Prints: "<w> <h>"
  local line
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

_docx_rel_path() {
  local remote="$1"
  local prefix="/data/data/$PKG/"
  if [[ "$remote" == "$prefix"* ]]; then
    echo "${remote#$prefix}"
    return 0
  fi
  echo "files/$(basename "$remote")"
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

_assert_ppm_has_red_pixels() {
  local ppm="$1"
  local min_red="$2"
  python - "$ppm" "$min_red" <<'PY'
import sys
from PIL import Image

ppm = sys.argv[1]
min_red = int(sys.argv[2])

im = Image.open(ppm).convert("RGBA")
pixels = im.getdata()
count = 0
for r, g, b, a in pixels:
    if a > 0 and r > 200 and g < 120 and b < 120:
        count += 1

print(f"{ppm}: {im.size[0]}x{im.size[1]} red_pixels={count}")
if count < min_red:
    raise SystemExit(f"FAIL: expected red_pixels >= {min_red}, got {count} (image may not have rendered)")
PY
}

_export_latest_word_import_pdf() {
  local out="$1"

  local rel
  rel="$(adb -s "$DEVICE" exec-out run-as "$PKG" sh -lc 'ls -t cache/word_import_*.pdf 2>/dev/null | head -n 1' | tr -d '\r' || true)"
  if [[ -z "$rel" ]]; then
    echo "FAIL: no derived Word import PDF found in app cache (cache/word_import_*.pdf)" >&2
    return 1
  fi

  adb -s "$DEVICE" exec-out run-as "$PKG" cat "$rel" >"$out"
  if [[ ! -s "$out" ]]; then
    echo "FAIL: exported derived PDF is missing/empty: $out" >&2
    return 1
  fi
}

_ensure_pp_demo() {
  local build="${BUILD:-debug}"
  local jobs="${JOBS:-$(nproc)}"
  local out="$ROOT/build/$build"
  local pp_demo="$out/pp_demo"

  if [[ ! -x "$pp_demo" ]]; then
    echo "  building pp_demo (make build=$build -j$jobs)" >&2
    make -C "$ROOT" build="$build" -j"$jobs" >/dev/null
  fi

  printf '%s' "$pp_demo"
}

_assert_pdf_has_text_mupdf() {
  local pdf="$1"
  local expected="$2"
  local out_prefix="$3"

  local pp_demo
  pp_demo="$(_ensure_pp_demo)"

  local unused_ppm="${out_prefix}_mupdf_text_unused.ppm"
  "$pp_demo" "$pdf" 0 "$unused_ppm" --text-smoke "$expected" >/dev/null
}

if [[ "$ASSERT_OCR" == "1" ]]; then
  if ! command -v tesseract >/dev/null 2>&1; then
    echo "FAIL: tesseract not found (install tesseract-ocr, or run ASSERT_OCR=0)." >&2
    exit 2
  fi
fi

echo "[1/11] Install debug APKs (main + Office Pack)"
if ! adb -s "$DEVICE" install -r "$APK" >/dev/null; then
  echo "  main install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
  adb -s "$DEVICE" install "$APK" >/dev/null
fi

if ! adb -s "$DEVICE" install -r "$APK_OFFICEPACK" >/dev/null; then
  echo "  Office Pack install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG_OFFICEPACK" >/dev/null || true
  adb -s "$DEVICE" install "$APK_OFFICEPACK" >/dev/null
fi

echo "[2/11] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/11] Push docx fixture"
DOCX_REL="$(_docx_rel_path "$DOCX_REMOTE")"
DOCX_DIR="$(dirname "$DOCX_REL")"
adb -s "$DEVICE" shell "run-as $PKG sh -lc 'mkdir -p \"${DOCX_DIR}\" && cat > \"${DOCX_REL}\"'" <"$DOCX_LOCAL"

echo "[4/11] Launch viewer with docx (expect conversion + open derived PDF)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$DOCX_REMOTE" -t "$DOCX_MIME" "$PKG/$ACT" >/dev/null

# Word import runs off-thread; allow a longer window for conversion.
UIA_DOC_VIEW_TIMEOUT_S="${UIA_DOC_VIEW_TIMEOUT_S:-25}"
if ! uia_assert_in_document_view; then
  OUT_PREFIX="${OUT_PREFIX:-tmp_geny_docx_officepack}"
  _uia_dump_to "${OUT_PREFIX}_ui.xml" || true
  adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_fail.png" || true
  echo "FAIL: expected to reach document view after Word import (Office Pack installed)" >&2
  exit 1
fi

echo "[5/11] Assert conversion call routed through Office Pack (logcat)"
if ! adb -s "$DEVICE" logcat -d | rg -q "OfficePackConverter.*convertWordToPdf called"; then
  OUT_PREFIX="${OUT_PREFIX:-tmp_geny_docx_officepack}"
  adb -s "$DEVICE" logcat -d | tail -n 200 >"${OUT_PREFIX}_logcat_tail.txt" || true
  echo "FAIL: expected Office Pack service log entry (conversion not routed?)" >&2
  exit 1
fi

echo "[6/11] Assert rendered output non-blank (screenshot span)"
OUT_PREFIX="${OUT_PREFIX:-tmp_geny_docx_officepack}"
SHOT="${SHOT:-${OUT_PREFIX}_view.png}"
adb -s "$DEVICE" exec-out screencap -p >"$SHOT" || true
python - "$SHOT" <<'PY'
import sys
from PIL import Image

path = sys.argv[1]
im = Image.open(path).convert("L")
data = im.tobytes()
span = max(data) - min(data)
print(f"{path}: {im.size[0]}x{im.size[1]} span={span}")
if span < 10:
    raise SystemExit(f"FAIL: screenshot looks blank/low-contrast (span={span})")
PY

if [[ "$ASSERT_OCR" == "1" ]]; then
  echo "[7/11] OCR content check (fixture words)"
  ocr="$(tesseract "$SHOT" stdout -l eng --psm 6 2>/dev/null | tr -d '\f' | tr -d '\r' | tr '\n' ' ')"
  canon_ocr="$(printf '%s' "$ocr" | tr -cd '[:alnum:]' | tr '[:lower:]' '[:upper:]')"

  # The fixture includes:
  #   "Hello OpenDroidPDF"
  #   "Keyword: opendroidpdf-fixture"
  # OCR often mangles "OpenDroidPDF" itself, but reliably sees HELLO/KEYWORD/FIXTURE.
  for word in HELLO KEYWORD FIXTURE; do
    if ! printf '%s\n' "$canon_ocr" | rg -q "$word"; then
      echo "FAIL: OCR did not find '$word' in screenshot (conversion/render regression?)" >&2
      echo "OCR output: $ocr" >&2
      exit 1
    fi
  done
fi

echo "[8/11] Add a sidecar note and assert docId is sha256:* (not file:// cache)"
uia_enter_add_text_mode || { echo "FAIL: add text entry point missing" >&2; exit 1; }
sleep 0.4
read -r w h < <(_wm_size)
adb -s "$DEVICE" shell input tap "$((w * 5 / 10))" "$((h * 55 / 100))"
sleep 0.6
for _ in $(seq 1 12); do
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    break
  fi
  sleep 0.2
done
if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
  echo "FAIL: note text dialog did not appear" >&2
  exit 1
fi
uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || true
adb -s "$DEVICE" shell input text "$NOTE_TEXT"
sleep 0.2
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || { echo "FAIL: could not confirm note dialog" >&2; exit 1; }
sleep 0.8

DB_LOCAL="$(mktemp -t geny_docx_sidecar_XXXXXX.db)"
PREFS_LOCAL=""
cleanup() {
  rm -f -- "$DB_LOCAL" "${DB_LOCAL}-wal" "${DB_LOCAL}-shm" 2>/dev/null || true
  if [[ -n "${PREFS_LOCAL:-}" ]]; then
    rm -f -- "$PREFS_LOCAL" 2>/dev/null || true
  fi
}
trap cleanup EXIT

_export_sidecar_db "$DB_LOCAL"
notes_count="$(_sqlite_scalar "$DB_LOCAL" 'select count(*) from notes' || echo 0)"
if [[ "${notes_count:-0}" -lt 1 ]]; then
  echo "FAIL: expected notes >= 1, got ${notes_count:-0}" >&2
  exit 1
fi
doc_id="$(_sqlite_scalar "$DB_LOCAL" 'select doc_id from notes limit 1')"
if [[ -z "$doc_id" || "$doc_id" != sha256:* ]]; then
  echo "FAIL: expected sidecar notes keyed by sha256 docId, got '$doc_id'" >&2
  exit 1
fi

echo "  verify recents store uses Word URI (not derived pdf) + same docId"
PREFS_LOCAL="$(mktemp -t geny_docx_prefs_XXXXXX.xml)"
adb -s "$DEVICE" exec-out run-as "$PKG" cat shared_prefs/OpenDroidPDF.xml >"$PREFS_LOCAL" 2>/dev/null || true
if ! rg -Fq "$DOCX_NAME" "$PREFS_LOCAL"; then
  echo "FAIL: expected recents prefs to reference the .docx filename ($DOCX_NAME)" >&2
  exit 1
fi
if ! rg -q "name=\\\"recentfile_docId0\\\">sha256:" "$PREFS_LOCAL"; then
  echo "FAIL: expected recents docId0 to be sha256:*" >&2
  exit 1
fi
rm -f "$PREFS_LOCAL"
PREFS_LOCAL=""

if [[ "$ASSERT_MUPDF_TEXT" == "1" ]]; then
  echo "[9/11] Export derived PDF and assert it contains text via MuPDF extraction"
  DERIVED_PDF_LOCAL="${OUT_PREFIX:-tmp_geny_docx_officepack}_derived.pdf"
  _export_latest_word_import_pdf "$DERIVED_PDF_LOCAL"
  _assert_pdf_has_text_mupdf "$DERIVED_PDF_LOCAL" "$EXPECTED_TOKEN" "${OUT_PREFIX:-tmp_geny_docx_officepack}"

  if [[ "$ASSERT_RED_PIXELS" == "1" ]]; then
    echo "  assert rendered PDF contains red pixels (image heuristic)"
    pp_demo="$(_ensure_pp_demo)"
    render_ppm="${OUT_PREFIX:-tmp_geny_docx_officepack}_mupdf_render.ppm"
    "$pp_demo" "$DERIVED_PDF_LOCAL" 0 "$render_ppm" >/dev/null
    _assert_ppm_has_red_pixels "$render_ppm" "$MIN_RED_PIXELS"
  fi
fi

echo "[10/11] Force-stop + reopen (expect cached derived PDF; no Office Pack conversion)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$DOCX_REMOTE" -t "$DOCX_MIME" "$PKG/$ACT" >/dev/null
UIA_DOC_VIEW_TIMEOUT_S="${UIA_DOC_VIEW_TIMEOUT_S:-25}"
uia_assert_in_document_view
if ! adb -s "$DEVICE" logcat -d | rg -q "Using cached Word import"; then
  echo "FAIL: expected cached Word import log line on reopen" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2 || true
  exit 1
fi
if adb -s "$DEVICE" logcat -d | rg -q "OfficePackConverter.*convertWordToPdf called"; then
  echo "FAIL: Office Pack conversion ran on reopen (cache not reused)" >&2
  exit 1
fi

echo "  sidecar note should still be present after reopen"
_export_sidecar_db "$DB_LOCAL"
notes_count2="$(_sqlite_scalar "$DB_LOCAL" 'select count(*) from notes' || echo 0)"
if [[ "${notes_count2:-0}" -lt 1 ]]; then
  echo "FAIL: expected notes >= 1 after reopen, got ${notes_count2:-0}" >&2
  exit 1
fi

echo "[11/11] Assert no crash"
if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION" && adb -s "$DEVICE" logcat -d | rg -q "Process: ${PKG}"; then
  echo "FAIL: detected crash in logcat" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2
  exit 1
fi

echo "DOCX Office Pack smoke complete."
