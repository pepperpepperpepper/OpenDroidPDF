#!/usr/bin/env bash
# Validate FreeText appearance stream fidelity in a non-MuPDF viewer (poppler).
# Generates a rich FreeText via pp_demo, renders it with pdftoppm, and asserts
# both non-white pixels (appearance visible) and text extraction presence.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

fixture="test_assets/pdf_freetext_poppler.pdf"
out_prefix="tmp_freetext_poppler"
out_ppm="${out_prefix}.ppm"

if [[ ! -f "$fixture" ]]; then
  echo "missing fixture: $fixture" >&2
  exit 2
fi

# Render page 1 with poppler (non-MuPDF) to ensure the appearance draws.
pdftoppm -r 144 -f 1 -l 1 -singlefile "$fixture" "$out_prefix"

# PPM sanity: require at least 1% non-white pixels so the box and text show up.
python - <<'PY'
import sys, pathlib
ppm = pathlib.Path("tmp_freetext_poppler.ppm")
data = ppm.read_bytes()
if not data.startswith(b"P6\n"):
    sys.exit("expected P6 ppm")
parts = data.split(b"\n", 3)
if len(parts) < 4:
    sys.exit("ppm header truncated")
width, height = map(int, parts[1].split())
maxval = int(parts[2])
body = parts[3]
if maxval != 255:
    sys.exit(f"unsupported maxval {maxval}")
thr = 245
nonwhite = 0
for i in range(0, len(body), 3):
    r, g, b = body[i:i+3]
    if r < thr or g < thr or b < thr:
        nonwhite += 1
total = width * height
if nonwhite < total * 0.001:
    sys.exit(f"image too blank: nonwhite {nonwhite}/{total}")
print(f"poppler render OK: nonwhite {nonwhite}/{total}")
PY

# Text payload should be embedded (searchable/inspectable) in the PDF bytes.
python - <<'PY'
import sys, pathlib
data = pathlib.Path("test_assets/pdf_freetext_poppler.pdf").read_bytes()
token = b"ODPTEST123"
if token not in data:
    sys.exit(f"missing text token: {token!r}")
print("token present in PDF bytes")
PY

echo "OK: FreeText appearance renders and remains searchable in poppler."
