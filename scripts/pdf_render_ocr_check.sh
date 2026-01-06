#!/usr/bin/env bash
set -euo pipefail

# Render the first page of a PDF at high DPI, OCR it, and summarize text presence.
# Requires: pdftoppm (poppler), tesseract (eng)

usage() {
  cat <<EOF2
Usage: ${0##*/} [-p PAGE] [-r DPI] PDF_FILE

Options:
  -p PAGE   Page number to render (default: 1)
  -r DPI    Render resolution (default: 300)

Outputs:
  - PNG render beside the PDF (suffix -p<page>.png)
  - OCR text file beside the PDF (suffix -p<page>.txt)
  - Summary to stdout
EOF2
}

PAGE=1
DPI=300
while getopts "p:r:h" opt; do
  case $opt in
    p) PAGE=$OPTARG ;;
    r) DPI=$OPTARG ;;
    h) usage; exit 0 ;;
    *) usage; exit 1 ;;
  esac
done
shift $((OPTIND-1))

if [[ $# -ne 1 ]]; then
  usage; exit 1
fi

PDF=$1
if [[ ! -f "$PDF" ]]; then
  echo "PDF not found: $PDF" >&2; exit 1
fi

stem=${PDF%.pdf}
# pdftoppm emits -1.png when a base name is given; match that.
out_png="${stem}-p${PAGE}-1.png"
out_txt="${stem}-p${PAGE}.txt"

echo "[render] pdftoppm -f $PAGE -l $PAGE -r $DPI -png $PDF ${stem}-p${PAGE}" >&2
pdftoppm -f "$PAGE" -l "$PAGE" -r "$DPI" -png "$PDF" "${stem}-p${PAGE}" >/dev/null

echo "[ocr] tesseract $out_png ${out_txt%.txt} --psm 6 -l eng" >&2
tesseract "$out_png" "${out_txt%.txt}" --psm 6 -l eng >/dev/null 2>&1 || true

echo "--- OCR (first 400 chars) ---"
if [[ -f "$out_txt" ]]; then
  head -c 400 "$out_txt"; echo
else
  echo "(no OCR output)"
fi

echo "--- Stats ---"
python - "$out_png" <<'PY'
from PIL import Image
import sys
png=sys.argv[1]
im=Image.open(png).convert('RGB')
w,h=im.size
hist=im.getcolors(maxcolors=w*h)
if hist is None:
    nonwhite="too_many_colors"
else:
    nonwhite=sum(cnt for cnt,color in hist if color!=(255,255,255))
print(f"png={png}")
print(f"size={w}x{h}")
print(f"nonwhite_pixels={nonwhite}")
PY

echo "Files: $out_png , $out_txt"
