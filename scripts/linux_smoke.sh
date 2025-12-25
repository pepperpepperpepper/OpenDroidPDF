#!/usr/bin/env bash
set -euo pipefail

# Linux smoke: build MuPDF tools/viewers and assert we can render non-blank output.
# This is a baseline harness for the Desktop/Linux parity track (see plan.md).
#
# Usage:
#   ./scripts/linux_smoke.sh
#
# Optional env:
#   BUILD=debug|release   (default: debug)
#   JOBS=<n>             (default: nproc)
#   PDF=<path>           (default: test_assets/pdf_with_text.pdf)
#   EPUB=<path>          (default: test_assets/hello.epub)
#   INK_PDF=<path>       (default: test_blank.pdf)

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

BUILD="${BUILD:-debug}"
JOBS="${JOBS:-$(nproc)}"
PDF="${PDF:-$ROOT/test_assets/pdf_with_text.pdf}"
EPUB="${EPUB:-$ROOT/test_assets/hello.epub}"
INK_PDF="${INK_PDF:-$ROOT/test_blank.pdf}"

OUT="$ROOT/build/$BUILD"
MUTOOL="$OUT/mutool"
PP_DEMO="$OUT/pp_demo"

EPUB_W="${EPUB_W:-450}"
EPUB_H="${EPUB_H:-600}"
EPUB_S="${EPUB_S:-12}"

assert_nonblank_ppm() {
  local ppm="$1"
  python3 - "$ppm" <<'PY'
import sys

path = sys.argv[1]
with open(path, "rb") as f:
    magic = f.readline().strip()
    if magic != b"P6":
        raise SystemExit(f"Expected P6 PPM, got: {magic!r}")

    def next_token():
        tok = b""
        while True:
            c = f.read(1)
            if not c:
                break
            if c == b"#":
                f.readline()
                continue
            if c.isspace():
                if tok:
                    break
                continue
            tok += c
        return tok

    w = int(next_token())
    h = int(next_token())
    maxval = int(next_token())
    if maxval <= 0 or maxval > 255:
        raise SystemExit(f"Unsupported maxval: {maxval}")

    data = f.read()

if not data:
    raise SystemExit("No pixel data")

min_b = min(data)
max_b = max(data)
span = max_b - min_b

print(f"{path}: {w}x{h} max={maxval} span={span}")

# "Blank" images tend to have extremely low variance (often pure white => span=0).
# For text PDFs and our EPUB fixtures we should see strong contrast.
if span < 10:
    raise SystemExit(f"Image looks blank/low-contrast (span={span})")
PY
}

echo "[1/8] Build (make build=$BUILD -j$JOBS)"
make -C "$ROOT" build="$BUILD" -j"$JOBS" >/dev/null

echo "[2/8] Cancel smoke (pp_demo cookie abort)"
"$PP_DEMO" "$PDF" 0 "$OUT/linux_smoke_cancel_unused.ppm" --cancel-smoke >/dev/null

echo "[3/8] Ink annotate smoke (pp_demo ink -> save -> reopen -> render)"
INK_OUT_PDF="$OUT/linux_smoke_ink_out.pdf"
INK_OUT_PPM="$OUT/linux_smoke_ink_after.ppm"
"$PP_DEMO" "$INK_PDF" 0 "$INK_OUT_PPM" --ink-smoke "$INK_OUT_PDF" >/dev/null

echo "[4/8] Markup/text annotate smoke (pp_demo highlight + free text -> save -> reopen -> render)"
ANNOT_OUT_PDF="$OUT/linux_smoke_annots_out.pdf"
ANNOT_OUT_PPM="$OUT/linux_smoke_annots_after.ppm"
"$PP_DEMO" "$INK_PDF" 0 "$ANNOT_OUT_PPM" --annot-smoke "$ANNOT_OUT_PDF" >/dev/null

echo "[5/8] Sanity: mutool info (PDF)"
"$MUTOOL" info "$PDF" >/dev/null

echo "[6/8] Render PDF fixture -> PPM"
PDF_OUT="$OUT/linux_smoke_pdf.ppm"
"$MUTOOL" draw -o "$PDF_OUT" -r 96 "$PDF" 1 >/dev/null
assert_nonblank_ppm "$PDF_OUT"

echo "[7/8] Render EPUB fixture -> PPM (stable layout)"
EPUB_OUT="$OUT/linux_smoke_epub.ppm"
"$MUTOOL" draw -o "$EPUB_OUT" -r 96 -W "$EPUB_W" -H "$EPUB_H" -S "$EPUB_S" "$EPUB" 1 >/dev/null
assert_nonblank_ppm "$EPUB_OUT"

echo "[8/8] OK"
echo "Artifacts:"
echo "  $INK_OUT_PDF"
echo "  $INK_OUT_PPM"
echo "  $ANNOT_OUT_PDF"
echo "  $ANNOT_OUT_PPM"
echo "  $PDF_OUT"
echo "  $EPUB_OUT"
