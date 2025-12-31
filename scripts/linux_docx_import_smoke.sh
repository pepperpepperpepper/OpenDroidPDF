#!/usr/bin/env bash
set -euo pipefail

# Linux smoke for Word (.docx/.doc) import-as-PDF via LibreOffice headless.
#
# Usage:
#   ./scripts/linux_docx_import_smoke.sh
#
# Optional env:
#   BUILD=debug|release   (default: debug)
#   JOBS=<n>              (default: nproc)
#   DOCX=<path>           (default: test_assets/word_with_text.docx)
#   EXPECTED=<token>      (default: opendroidpdf-fixture)

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

BUILD="${BUILD:-debug}"
JOBS="${JOBS:-$(nproc)}"
DOCX="${DOCX:-$ROOT/test_assets/word_with_text.docx}"
EXPECTED="${EXPECTED:-opendroidpdf-fixture}"

if ! command -v soffice >/dev/null 2>&1; then
  echo "SKIP: LibreOffice is not installed (missing 'soffice')."
  echo "Install: pacman -S libreoffice-fresh (Arch) or equivalent, then rerun."
  exit 0
fi

OUT="$ROOT/build/$BUILD"
MUTOOL="$OUT/mutool"
PP_DEMO="$OUT/pp_demo"

echo "[1/6] Build (make build=$BUILD -j$JOBS)"
make -C "$ROOT" build="$BUILD" -j"$JOBS" >/dev/null

echo "[2/6] Compute docId (sha256:*) for fixture"
DOCID_HEX="$(python3 - "$DOCX" <<'PY'
import hashlib
import sys

path = sys.argv[1]
h = hashlib.sha256()
h.update(b"odpdf-docid-v1")
with open(path, "rb") as f:
    for chunk in iter(lambda: f.read(1024 * 64), b""):
        h.update(chunk)
print(h.hexdigest())
PY
)"

CACHE="$OUT/linux_docx_import_smoke"
mkdir -p "$CACHE"

PROFILE_DIR="$(mktemp -d "$CACHE/lo_profile_XXXXXX")"
INPUT_DIR="$(mktemp -d "$CACHE/input_XXXXXX")"
INPUT_PATH="$INPUT_DIR/$DOCID_HEX.docx"
PDF_PATH="$CACHE/$DOCID_HEX.pdf"

cp "$DOCX" "$INPUT_PATH"

echo "[3/6] Convert docx -> PDF (soffice headless)"
LOG_PATH="$CACHE/$DOCID_HEX.log"
soffice --headless --nologo --nolockcheck --nodefault --norestore --invisible \
  "-env:UserInstallation=file://$PROFILE_DIR" \
  --convert-to pdf --outdir "$CACHE" "$INPUT_PATH" >"$LOG_PATH" 2>&1 || {
    echo "Conversion failed; see: $LOG_PATH"
    exit 1
  }

rm -rf "$PROFILE_DIR" "$INPUT_DIR"

if [[ ! -s "$PDF_PATH" ]]; then
  echo "Expected output PDF missing/empty: $PDF_PATH"
  echo "See: $LOG_PATH"
  exit 1
fi

echo "[4/6] Render derived PDF -> PPM (non-blank oracle)"
PPM_PATH="$CACHE/docx_render.ppm"
"$MUTOOL" draw -o "$PPM_PATH" -r 96 "$PDF_PATH" 1 >/dev/null

python3 - "$PPM_PATH" <<'PY'
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

print(f"{path}: {w}x{h} span={span}")
if span < 10:
    raise SystemExit(f"Image looks blank/low-contrast (span={span})")
PY

echo "[5/6] Text extraction sanity (pp_demo)"
TEXT_UNUSED="$CACHE/docx_text_unused.ppm"
"$PP_DEMO" "$PDF_PATH" 0 "$TEXT_UNUSED" --text-smoke "$EXPECTED" >/dev/null

echo "[6/6] OK"
echo "Artifacts:"
echo "  $PDF_PATH"
echo "  $PPM_PATH"
echo "  $LOG_PATH"

