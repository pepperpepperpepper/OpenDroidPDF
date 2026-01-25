#!/usr/bin/env bash
set -euo pipefail

# Linux regression: exporting an annotated copy should include the latest annotations.
#
# Runs the shared pp_core CLI (pp_demo) to add a highlight + FreeText annotation and
# immediately save-as, then reopens and renders to confirm a visible delta.
#
# Usage:
#   ./scripts/linux_export_latest_text_annot_smoke.sh
#
# Optional env:
#   BUILD=debug|release   (default: debug)
#   JOBS=<n>              (default: nproc)
#   PDF=<path>            (default: test_blank.pdf)

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

BUILD="${BUILD:-debug}"
JOBS="${JOBS:-$(nproc)}"
PDF="${PDF:-$ROOT/test_blank.pdf}"

if [[ ! -f "$PDF" ]]; then
  echo "Missing PDF: $PDF" >&2
  exit 2
fi

OUT="$ROOT/build/$BUILD"
PP_DEMO="$OUT/pp_demo"

echo "[1/3] Build (make build=$BUILD -j$JOBS)"
make -C "$ROOT" build="$BUILD" -j"$JOBS" >/dev/null

CACHE="$OUT/linux_export_latest_text_annot_smoke"
mkdir -p "$CACHE"

OUT_PDF="$CACHE/export_latest_text_annot.pdf"
OUT_PPM="$CACHE/export_latest_text_annot.ppm"

echo "[2/3] Annotate -> export -> reopen -> render (pp_demo --annot-smoke)"
"$PP_DEMO" "$PDF" 0 "$OUT_PPM" --annot-smoke "$OUT_PDF" >/dev/null

echo "[3/3] OK"
echo "Artifacts:"
echo "  $OUT_PDF"
echo "  $OUT_PPM"

