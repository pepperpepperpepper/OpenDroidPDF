#!/usr/bin/env bash
set -euo pipefail

# Quick Genymotion EPUB smoke: open a known EPUB, verify menu gating, and exercise the
# Reading settings dialog (cancel), plus a minimal annotation loop:
# enter draw mode -> draw -> accept -> undo.
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/hello.epub}
# Keep smokes independent of MANAGE_EXTERNAL_STORAGE by using app-private storage.
EPUB_REMOTE=${EPUB_REMOTE:-/data/data/org.opendroidpdf/files/hello.epub}
NOTE_TEXT=${NOTE_TEXT:-ODP_NOTE}
NOTE_TEXT_SUFFIX_EDIT=${NOTE_TEXT_SUFFIX_EDIT:-_EDIT}
NOTE_TEXT_EDIT=${NOTE_TEXT_EDIT:-${NOTE_TEXT}${NOTE_TEXT_SUFFIX_EDIT}}
ASSERT_NOTE_OCR=${ASSERT_NOTE_OCR:-1}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null
uia_disable_flaky_ime

if [[ "$ASSERT_NOTE_OCR" == "1" ]]; then
  if ! command -v tesseract >/dev/null 2>&1; then
    echo "FAIL: tesseract not found (install tesseract-ocr, or run ASSERT_NOTE_OCR=0)." >&2
    exit 2
  fi
fi

_wm_size() {
  # Prints: "<w> <h>"
  local line
  # Prefer "Override size" when present (matches input tap coordinate space).
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

_draw_swipe() {
  # Draw a visible diagonal stroke in the center-ish of the page.
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
  # Debug builds should allow run-as. Use exec-out to avoid filesystem permissions.
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db >"$out"
  # WAL mode: schema/rows may live in the -wal file until checkpointed.
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

_ocr_png() {
  local png="$1"
  # Keep OCR stable (no fancy layout analysis).
  tesseract "$png" stdout -l eng --psm 6 2>/dev/null | tr -d '\f' | tr -d '\r'
}

echo "[1/9] Install debug APK"
if ! adb -s "$DEVICE" install -r "$APK" >/dev/null; then
  echo "  install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
  adb -s "$DEVICE" install "$APK" >/dev/null
fi

echo "[2/9] Clear app data (fresh sidecar DB)"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/9] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[4/9] Push sample EPUB"
adb -s "$DEVICE" shell "run-as $PKG sh -lc 'mkdir -p files && cat > files/hello.epub'" <"$EPUB_LOCAL"

echo "[5/9] Launch viewer with sample EPUB"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[6/9] Capture baseline screenshot (artifact)"
OUT_PREFIX="${OUT_PREFIX:-tmp_geny_epub_smoke}"
OUT_BASE="${OUT_BASE:-${OUT_PREFIX}_base.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_BASE"
echo "  wrote $OUT_BASE"

echo "[7/9] Open overflow and verify menu gating"
menu_xml="$(mktemp -t geny_epub_overflow_XXXXXX.xml)"
menu_ok=0
for attempt in 1 2 3 4 5 6 7 8; do
  # Keep attempts scoped to the document view. Avoid unguarded BACK presses (can exit to launcher).
  uia_assert_in_document_view || true

  # Prefer KEYCODE_MENU (82) which is more reliable than the toolbar "More options" tap in some images.
  adb -s "$DEVICE" shell input keyevent 82 || true
  sleep 0.5
  _uia_dump_to "$menu_xml" || true

  if ! rg -q 'org.opendroidpdf:id/menu_reading_settings|text="Reading settings"' "$menu_xml" 2>/dev/null; then
    # Fallback: tap overflow icon directly.
    uia_tap_desc "More options" || true
    sleep 0.5
    _uia_dump_to "$menu_xml" || true
  fi

  if rg -q 'org.opendroidpdf:id/menu_reading_settings|text="Reading settings"' "$menu_xml" 2>/dev/null; then
    menu_ok=1
    break
  fi

  # Close overflow only if it appears to be open (avoid backing out of the document).
  if rg -q 'text="Contents"|text="Reading settings"|text="Go to page"' "$menu_xml" 2>/dev/null; then
    adb -s "$DEVICE" shell input keyevent 4 || true
    sleep 0.3
  fi
done
if [[ "$menu_ok" -ne 1 ]]; then
  fail_xml="${OUT_PREFIX}_overflow_fail.xml"
  cp -f "$menu_xml" "$fail_xml" 2>/dev/null || true
  echo "FAIL: unable to open overflow menu (reading settings not found). Wrote $fail_xml" >&2
  rm -f "$menu_xml"
  exit 1
fi
if rg -q 'resource-id="org.opendroidpdf:id/menu_save"|text="Save"' "$menu_xml" 2>/dev/null; then
  echo "FAIL: Save is visible for EPUB" >&2
  rm -f "$menu_xml"
  exit 1
fi
rg -q 'org.opendroidpdf:id/menu_reading_settings|text="Reading settings"' "$menu_xml" 2>/dev/null || { echo "FAIL: Reading settings missing" >&2; exit 1; }
rg -q 'org.opendroidpdf:id/menu_toc|text="Contents"' "$menu_xml" 2>/dev/null || { echo "FAIL: Contents missing" >&2; exit 1; }
rm -f "$menu_xml"

echo "[8/9] Open Reading settings and cancel"
if ! uia_tap_any_res_id "org.opendroidpdf:id/menu_reading_settings"; then
  # Fallback for some device builds where overflow rows don't expose resource-ids.
  uia_tap_text_contains "Reading settings"
fi
sleep 0.6
if ! uia_tap_text_contains "Cancel"; then
  adb -s "$DEVICE" shell input keyevent 4
fi
sleep 0.3
# Close overflow only if it’s still open (avoid backing out of the document view).
if uia_has_text_contains "Reading settings" || uia_has_text_contains "Contents"; then
  adb -s "$DEVICE" shell input keyevent 4
  sleep 0.3
fi

echo "[9/9] Draw -> accept -> undo (sidecar ink)"
# Ensure we’re back in document view after menus.
sleep 0.5
uia_assert_in_document_view

if [[ -z "${DB_LOCAL:-}" ]]; then
  DB_LOCAL="$(mktemp -t geny_epub_sidecar_XXXXXX.db)"
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

echo "  add text note"
uia_tap_any_res_id "org.opendroidpdf:id/menu_add_text_annot" || uia_tap_desc "Add text" || { echo "FAIL: add text not found" >&2; exit 1; }
sleep 0.4
read -r w h < <(_wm_size)
adb -s "$DEVICE" shell input tap "$((w * 5 / 10))" "$((h * 45 / 100))"
sleep 0.6
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
_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after note" >&2; exit 1; }
notes_count="$(_sqlite_count "$DB_LOCAL" notes || echo 0)"
if [[ "$notes_count" -lt 1 ]]; then
  echo "FAIL: expected notes >= 1, got $notes_count" >&2
  exit 1
fi
echo "  notes: $notes_count"

echo "  delete note (sidecar select + delete) and undo restore"
# Select the sidecar note marker by locating its amber pixels in a screenshot.
NOTE_SEL_SHOT="${NOTE_SEL_SHOT:-${OUT_PREFIX}_note_select.png}"
adb -s "$DEVICE" exec-out screencap -p >"$NOTE_SEL_SHOT"
echo "  wrote $NOTE_SEL_SHOT" >&2
if [[ "$ASSERT_NOTE_OCR" == "1" ]]; then
  ocr_note="$(_ocr_png "$NOTE_SEL_SHOT" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')"
  note_canon="$(printf '%s' "$NOTE_TEXT" | tr -cd '[:alnum:]' | tr '[:lower:]' '[:upper:]')"
  ocr_canon="$(printf '%s' "$ocr_note" | tr -cd '[:alnum:]' | tr '[:lower:]' '[:upper:]')"
  if ! printf '%s\n' "$ocr_canon" | rg -q "$note_canon"; then
    echo "FAIL: OCR did not find note token '$NOTE_TEXT' in screenshot" >&2
    echo "OCR output: $ocr_note" >&2
    exit 1
  fi
fi
note_xy="$(python - "$NOTE_SEL_SHOT" <<'PY'
import sys
from PIL import Image

path = sys.argv[1]
img = Image.open(path).convert("RGB")
w, h = img.size
px = img.load()

# Skip toolbar region (roughly the top ~140px on a 2160px-tall device).
ystart = min(h, 140)
step = 3

minx = miny = 10**9
maxx = maxy = -1
count = 0

for y in range(ystart, h, step):
    for x in range(0, w, step):
        r, g, b = px[x, y]
        # Note marker paint is ~#FFD54F (amber). Allow for anti-aliasing/filters.
        if r >= 235 and g >= 180 and b <= 140:
            count += 1
            if x < minx: minx = x
            if y < miny: miny = y
            if x > maxx: maxx = x
            if y > maxy: maxy = y

if count < 20 or maxx < 0:
    raise SystemExit("FAIL: could not locate note marker pixels in screenshot")

cx = (minx + maxx) // 2
cy = (miny + maxy) // 2
print(f"{cx} {cy}")
PY
)"
set -- $note_xy
adb -s "$DEVICE" shell input tap "$1" "$2"
sleep 0.8

echo "  edit note (toolbar edit) -> append ${NOTE_TEXT_SUFFIX_EDIT}"
if ! uia_tap_any_res_id "org.opendroidpdf:id/menu_edit"; then
  # Some devices place Edit in the overflow menu.
  uia_tap_desc "More options" || { echo "FAIL: could not open overflow menu for Edit" >&2; exit 1; }
  sleep 0.4
  uia_tap_text_contains "Edit" || { echo "FAIL: Edit menu item not found" >&2; exit 1; }
fi
sleep 0.9
for _ in $(seq 1 10); do
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    break
  fi
  sleep 0.3
done
uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || { echo "FAIL: note edit dialog did not appear" >&2; exit 1; }
adb -s "$DEVICE" shell input text "$NOTE_TEXT_SUFFIX_EDIT"
sleep 0.3
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || { echo "FAIL: could not confirm edited note text dialog" >&2; exit 1; }
sleep 0.9
_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after note edit" >&2; exit 1; }
notes_count_after_edit="$(_sqlite_count "$DB_LOCAL" notes || echo 0)"
if [[ "$notes_count_after_edit" -ne 1 ]]; then
  echo "FAIL: expected notes == 1 after edit, got $notes_count_after_edit" >&2
  exit 1
fi
python - "$DB_LOCAL" "$NOTE_TEXT_EDIT" <<'PY' || { echo "FAIL: edited note text not found in DB ($NOTE_TEXT_EDIT)" >&2; exit 1; }
import sqlite3, sys
db, token = sys.argv[1], sys.argv[2]
conn = sqlite3.connect(db)
try:
    rows = conn.execute("select text from notes").fetchall()
    ok = any((r[0] or "").find(token) >= 0 for r in rows)
    if not ok:
        raise SystemExit(2)
finally:
    conn.close()
PY
echo "  note edited: $NOTE_TEXT_EDIT"

# Re-select note before deleting it (edit flow clears selection).
adb -s "$DEVICE" shell input tap "$1" "$2"
sleep 0.6

# Long-press the cancel button to delete selected annotation.
uia_long_press_any_res_id "org.opendroidpdf:id/cancel_image_button" || { echo "FAIL: cancel long-press (delete) not found" >&2; exit 1; }
sleep 0.8
_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after note delete" >&2; exit 1; }
notes_count_del="$(_sqlite_count "$DB_LOCAL" notes || echo 0)"
if [[ "$notes_count_del" -ge "$notes_count" ]]; then
  echo "FAIL: expected notes to decrease after delete ($notes_count -> $notes_count_del)" >&2
  exit 1
fi
echo "  notes after delete: $notes_count_del"

uia_tap_any_res_id "org.opendroidpdf:id/menu_undo" || uia_tap_desc "Undo" || { echo "FAIL: undo not found (after note delete)" >&2; exit 1; }
sleep 0.8
_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after undo note delete" >&2; exit 1; }
notes_count_restore="$(_sqlite_count "$DB_LOCAL" notes || echo 0)"
if [[ "$notes_count_restore" -lt "$notes_count" ]]; then
  echo "FAIL: expected notes to restore after undo ($notes_count_del -> $notes_count_restore)" >&2
  exit 1
fi
echo "  notes after undo: $notes_count_restore"

echo "  highlight text selection (sidecar) + delete + undo"
HIGHLIGHT_SHOT_BEFORE="${HIGHLIGHT_SHOT_BEFORE:-${OUT_PREFIX}_highlight_before.png}"
adb -s "$DEVICE" exec-out screencap -p >"$HIGHLIGHT_SHOT_BEFORE"
echo "  wrote $HIGHLIGHT_SHOT_BEFORE" >&2

# Choose a likely text pixel coordinate from a screenshot so we long-press on actual glyphs.
hl_xy="$(python - "$HIGHLIGHT_SHOT_BEFORE" <<'PY'
import sys
from PIL import Image

path = sys.argv[1]
img = Image.open(path).convert("RGB")
w, h = img.size
px = img.load()

# Skip toolbar/status region.
ystart = min(h, 160)
yend = min(h, 650)
xend = min(w, int(w * 0.75))

best = None
best_score = 10**9
count = 0

step = 2
for y in range(ystart, yend, step):
    for x in range(0, xend, step):
        r, g, b = px[x, y]
        # Find a dark pixel on a light background (text).
        if r < 80 and g < 80 and b < 80:
            count += 1
            score = r + g + b
            if score < best_score:
                best_score = score
                best = (x, y)

if best is None or count < 200:
    raise SystemExit("FAIL: could not locate enough dark text pixels for long-press")

print(f"{best[0]} {best[1]}")
PY
)"
set -- $hl_xy
hl_x="$1"
hl_y="$2"
adb -s "$DEVICE" shell input swipe "$hl_x" "$hl_y" "$hl_x" "$hl_y" 1500
sleep 1.0

if ! uia_tap_any_res_id "org.opendroidpdf:id/menu_highlight"; then
  # Some devices expose action-mode items without stable ids; fall back to text.
  uia_tap_text_contains "Highlight" || {
    _uia_dump_to "${OUT_PREFIX}_highlight_ui.xml" || true
    adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_highlight_fail.png" || true
    echo "FAIL: could not find Highlight action after long-press selection" >&2
    exit 1
  }
fi
sleep 0.8

_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after highlight" >&2; exit 1; }
highlights_count="$(_sqlite_count "$DB_LOCAL" highlights || echo 0)"
if [[ "$highlights_count" -lt 1 ]]; then
  echo "FAIL: expected highlights >= 1, got $highlights_count" >&2
  exit 1
fi
echo "  highlights: $highlights_count"

# Exit selection mode if it's still active (avoid back-navigation).
if uia_has_res_id "org.opendroidpdf:id/menu_highlight"; then
  adb -s "$DEVICE" shell input keyevent 4 || true
  sleep 0.3
fi

echo "  delete highlight and undo restore"
adb -s "$DEVICE" shell input tap "$hl_x" "$hl_y"
sleep 0.8
uia_long_press_any_res_id "org.opendroidpdf:id/cancel_image_button" || {
  _uia_dump_to "${OUT_PREFIX}_highlight_delete_ui.xml" || true
  adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_highlight_delete_fail.png" || true
  echo "FAIL: cancel long-press (delete) not found for highlight selection" >&2
  exit 1
}
sleep 0.8
_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after highlight delete" >&2; exit 1; }
highlights_count_del="$(_sqlite_count "$DB_LOCAL" highlights || echo 0)"
if [[ "$highlights_count_del" -ge "$highlights_count" ]]; then
  echo "FAIL: expected highlights to decrease after delete ($highlights_count -> $highlights_count_del)" >&2
  exit 1
fi
echo "  highlights after delete: $highlights_count_del"

uia_tap_any_res_id "org.opendroidpdf:id/menu_undo" || uia_tap_desc "Undo" || { echo "FAIL: undo not found (after highlight delete)" >&2; exit 1; }
sleep 0.8
_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after undo highlight delete" >&2; exit 1; }
highlights_count_restore="$(_sqlite_count "$DB_LOCAL" highlights || echo 0)"
if [[ "$highlights_count_restore" -lt "$highlights_count" ]]; then
  echo "FAIL: expected highlights to restore after undo ($highlights_count_del -> $highlights_count_restore)" >&2
  exit 1
fi
echo "  highlights after undo: $highlights_count_restore"

_draw_commit_and_assert_rows() {
  local label="$1"
  local expect_min="${2:-1}"

  # Enter draw mode.
  uia_tap_res_id "org.opendroidpdf:id/draw_image_button" || { echo "FAIL: draw button missing" >&2; exit 1; }
  sleep 0.5

  # Draw a couple of strokes (in case one gets interpreted as a scroll).
  _draw_swipe 220
  sleep 0.2
  _draw_swipe 220
  sleep 0.4

  # Accept/commit.
  uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || uia_tap_desc "Accept" || { echo "FAIL: accept not found" >&2; exit 1; }
  sleep 0.8

  # Screenshot after draw.
  local out_draw="${OUT_PREFIX}_${label}.png"
  adb -s "$DEVICE" exec-out screencap -p >"$out_draw"
  echo "  wrote $out_draw" >&2

  # Verify DB row count increased.
  _export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB (run-as)" >&2; exit 1; }
  local ink_count
  ink_count="$(_sqlite_count "$DB_LOCAL" ink_strokes || echo 0)"
  if [[ "$ink_count" -lt "$expect_min" ]]; then
    echo "FAIL: expected ink_strokes >= $expect_min, got $ink_count" >&2
    exit 1
  fi
  echo "$ink_count"
}

echo "  draw/commit #1"
ink_count_1="$(_draw_commit_and_assert_rows draw1 1)"
echo "  ink_strokes: $ink_count_1"

echo "  undo"
uia_tap_any_res_id "org.opendroidpdf:id/menu_undo" || uia_tap_desc "Undo" || { echo "FAIL: undo not found" >&2; exit 1; }
sleep 0.8
_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after undo" >&2; exit 1; }
ink_count_undo="$(_sqlite_count "$DB_LOCAL" ink_strokes || echo 0)"
if [[ "$ink_count_undo" -ge "$ink_count_1" ]]; then
  echo "FAIL: expected undo to reduce ink_strokes ($ink_count_1 -> $ink_count_undo)" >&2
  exit 1
fi
echo "  ink_strokes after undo: $ink_count_undo"

echo "  draw/commit #2"
ink_count_2="$(_draw_commit_and_assert_rows draw2 1)"
echo "  ink_strokes: $ink_count_2"

echo "  erase"
uia_tap_res_id "org.opendroidpdf:id/draw_image_button" || { echo "FAIL: draw button missing (for erase)" >&2; exit 1; }
sleep 0.5
uia_tap_any_res_id "org.opendroidpdf:id/menu_erase" || uia_tap_desc "Erase" || { echo "FAIL: erase not found" >&2; exit 1; }
sleep 0.4
_draw_swipe 240
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || uia_tap_desc "Accept" || { echo "FAIL: accept not found (after erase)" >&2; exit 1; }
sleep 0.8
_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after erase" >&2; exit 1; }
ink_count_erase="$(_sqlite_count "$DB_LOCAL" ink_strokes || echo 0)"
if [[ "$ink_count_erase" -ge "$ink_count_2" ]]; then
  echo "FAIL: expected erase to reduce ink_strokes ($ink_count_2 -> $ink_count_erase)" >&2
  exit 1
fi
echo "  ink_strokes after erase: $ink_count_erase"

echo "  relaunch (persistence sanity)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view
OUT_RELAUNCH="${OUT_RELAUNCH:-${OUT_PREFIX}_relaunch.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_RELAUNCH"
echo "  wrote $OUT_RELAUNCH"

# DB should still be readable (and not be wiped on open).
_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after relaunch" >&2; exit 1; }
notes_count2="$(_sqlite_count "$DB_LOCAL" notes || echo 0)"
ink_count3="$(_sqlite_count "$DB_LOCAL" ink_strokes || echo 0)"
if [[ "$notes_count2" -lt 1 ]]; then
  echo "FAIL: expected notes >= 1 after relaunch, got $notes_count2" >&2
  exit 1
fi
if [[ "$ink_count3" -lt "$ink_count_erase" ]]; then
  echo "FAIL: expected ink_strokes >= $ink_count_erase after relaunch, got $ink_count3" >&2
  exit 1
fi
echo "  notes after relaunch: $notes_count2; ink_strokes: $ink_count3"

echo "  export (share) sanity"
uia_tap_desc "More options"
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/menu_share" || uia_tap_text_contains "Share" || { echo "FAIL: share missing" >&2; exit 1; }
sleep 4
# Dismiss chooser if it opened.
adb -s "$DEVICE" shell input keyevent 4 || true
sleep 0.5

export_pdf_path="$(adb -s "$DEVICE" exec-out run-as "$PKG" sh -c 'ls -1t cache/tmpfiles/*.pdf 2>/dev/null | head -n 1' | tr -d '\r' || true)"
if [[ -z "$export_pdf_path" ]]; then
  echo "FAIL: expected an exported PDF under cache/tmpfiles/" >&2
  exit 1
fi
OUT_EXPORT_PDF="${OUT_EXPORT_PDF:-${OUT_PREFIX}_export.pdf}"
adb -s "$DEVICE" exec-out run-as "$PKG" cat "$export_pdf_path" >"$OUT_EXPORT_PDF"
echo "  wrote $OUT_EXPORT_PDF ($export_pdf_path)"
if ! head -c 4 "$OUT_EXPORT_PDF" | rg -q '%PDF'; then
  echo "FAIL: exported file does not look like a PDF" >&2
  exit 1
fi
export_size="$(wc -c <"$OUT_EXPORT_PDF" | tr -d ' ')"
if [[ "$export_size" -lt 10000 ]]; then
  echo "FAIL: exported PDF too small ($export_size bytes)" >&2
  exit 1
fi
if command -v pdftoppm >/dev/null 2>&1; then
  export_render_prefix="${OUT_PREFIX}_export_render"
  pdftoppm -png -f 1 -singlefile "$OUT_EXPORT_PDF" "$export_render_prefix" >/dev/null 2>&1 || {
    echo "FAIL: pdftoppm failed to render exported PDF" >&2
    exit 1
  }
  export_render_png="${export_render_prefix}.png"
  if [[ ! -f "$export_render_png" ]]; then
    echo "FAIL: expected rendered PNG at $export_render_png" >&2
    exit 1
  fi
  python - "$export_render_png" <<'PY'
import sys
from PIL import Image

path = sys.argv[1]
img = Image.open(path).convert("RGB")
pixels = img.getdata()
nonwhite = 0
for r, g, b in pixels:
    if r < 250 or g < 250 or b < 250:
        nonwhite += 1
        if nonwhite > 2000:
            break
if nonwhite <= 2000:
    raise SystemExit(f"FAIL: rendered export looks blank (nonwhite={nonwhite})")
print(f"  export render nonwhite pixels: >2000 ({img.size[0]}x{img.size[1]})")
PY
fi

echo "EPUB smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
