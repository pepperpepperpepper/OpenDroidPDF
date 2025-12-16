#!/usr/bin/env bash
# Vision check for emulator screenshots using Codex vision (gpt-5.2).
# Usage:
#   ./scripts/screenshot_check.sh "tmp_geny_*.png" vision_notes.md
# Defaults: glob=tmp_*.png, output=vision_notes.md

set -euo pipefail

GLOB="${1:-tmp_*.png}"
OUT="${2:-vision_notes.md}"

echo "# Vision pass $(date -Iseconds)" >> "$OUT"
for f in $GLOB; do
  [ -f "$f" ] || continue
  {
    echo "### $f"
    codex -m gpt-5.2 -i "$f" \
      "Describe visible pen strokes: color and estimated stroke width in pixels; compare stroke width to nearby text height/margins. \
Note if the page is blank, shows a loading/overlay spinner, or contains rendered PDF text/images. \
If text is visible, summarize it in one line." \
      || echo "(codex failed on $f)"
    echo
  } >> "$OUT"
done
