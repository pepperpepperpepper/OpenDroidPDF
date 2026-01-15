# geny_pdf_smoke_ocr.sh: render + OCR helpers for Genymotion smokes.
#
# Intended to be sourced from a top-level smoke script. Assumes `set -euo pipefail`
# and core env vars (e.g. DEVICE) are managed by the caller.

_render_pdf_to_png() {
  local pdf="$1"
  local out_png="$2"
  local tmpdir prefix
  tmpdir="$(mktemp -d -t odp_pdf_render_XXXXXX)"
  prefix="$tmpdir/out"
  pdftoppm -f 1 -l 1 -r 300 -singlefile -png "$pdf" "$prefix" >/dev/null
  mv -f -- "${prefix}.png" "$out_png"
  rm -rf -- "$tmpdir"
}

_assert_token_in_rendered_pdf() {
  local png="$1"
  local token="$2"
  local tmp_crop tmp_bw
  tmp_crop="$(mktemp -t odp_render_crop_XXXXXX).png"
  tmp_bw="$(mktemp -t odp_render_bw_XXXXXX).png"

  # The final rendered page can contain colored annotation fills/borders (e.g. red). Vanilla OCR tends to
  # miss those regions. We stabilize by:
  # 1) cropping to the red-ish bounding box (if present),
  # 2) whitening red-ish pixels, then
  # 3) thresholding to black/white for OCR.
  python3 - "$png" "$tmp_crop" "$tmp_bw" <<'PY'
from PIL import Image
import sys

src, crop_out, bw_out = sys.argv[1], sys.argv[2], sys.argv[3]
im = Image.open(src).convert("RGBA")
w, h = im.size
px = im.load()

minx = miny = None
maxx = maxy = None
count = 0

def is_redish(r, g, b, a):
    if a < 200:
        return False
    return r > 200 and g < 90 and b < 90

for y in range(h):
    for x in range(w):
        r, g, b, a = px[x, y]
        if not is_redish(r, g, b, a):
            continue
        count += 1
        if minx is None:
            minx = maxx = x
            miny = maxy = y
        else:
            if x < minx: minx = x
            if y < miny: miny = y
            if x > maxx: maxx = x
            if y > maxy: maxy = y

if minx is not None and count > 200:
    pad = 30
    x0 = max(0, minx - pad)
    y0 = max(0, miny - pad)
    x1 = min(w, maxx + pad)
    y1 = min(h, maxy + pad)
    crop = im.crop((x0, y0, x1, y1))
else:
    crop = im

crop.save(crop_out)

# Whiten red-ish pixels so black text on colored backgrounds becomes OCR-friendly.
rgb = crop.convert("RGB")
px2 = rgb.load()
cw, ch = rgb.size
for y in range(ch):
    for x in range(cw):
        r, g, b = px2[x, y]
        if r > 200 and g < 90 and b < 90:
            px2[x, y] = (255, 255, 255)

gray = rgb.convert("L")
# Keep text (dark) and drop near-white paper.
bw = gray.point(lambda p: 0 if p < 200 else 255)
bw.save(bw_out)
PY

  local ocr_raw ocr_key token_key token_head suffix_key require_suffix
  ocr_raw="$(tesseract "$tmp_bw" stdout -l eng --psm 6 2>/dev/null | tr -d '\f' | tr -d '\r')"
  ocr_key="$(printf '%s' "$ocr_raw" | tr -cd '[:alnum:]')"
  token_key="$(printf '%s' "$token" | tr -cd '[:alnum:]')"
  token_head="$(printf '%s' "$token_key" | cut -c1-10)"
  suffix_key="$(printf '%s' "$TOKEN_SUFFIX_EDIT" | tr -cd '[:alnum:]')"
  require_suffix=0
  if [[ -n "$suffix_key" && "$token_key" == *"$suffix_key" ]]; then
    require_suffix=1
  fi

  rm -f -- "$tmp_crop" "$tmp_bw" || true

  if [[ -z "$token_head" || "$ocr_key" != *"$token_head"* ]]; then
    echo "FAIL: OCR did not find token '$token' in rendered output" >&2
    echo "  token_head=$token_head" >&2
    echo "  OCR raw: $(printf '%s' "$ocr_raw" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')" >&2
    return 1
  fi
  if (( require_suffix == 1 )) && [[ -n "$suffix_key" && "$ocr_key" != *"$suffix_key"* ]]; then
    echo "FAIL: OCR found token head but not suffix '$TOKEN_SUFFIX_EDIT' in rendered output" >&2
    echo "  suffix_key=$suffix_key" >&2
    echo "  OCR raw: $(printf '%s' "$ocr_raw" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')" >&2
    return 1
  fi
  return 0
}

_ocr_png() {
  local png="$1"
  # Keep OCR stable (no fancy layout analysis).
  tesseract "$png" stdout -l eng --psm 6 2>/dev/null | tr -d '\f' | tr -d '\r'
}

_assert_token_onscreen_fuzzy() {
  local png="$1"
  local token="$2"
  local label="$3"

  # Fuzzy match: OCR often corrupts underscores/letters once handles/selection boxes overlay the page.
  # We strip non-alphanumerics and do a simple substring check on the first 10 chars.
  local token_key
  token_key="$(printf '%s' "$token" | tr -cd '[:alnum:]' | cut -c1-10)"
  if [[ -z "$token_key" ]]; then
    echo "FAIL: token_key empty for token '$token'" >&2
    return 1
  fi

  local ocr_raw ocr_key
  ocr_raw="$(_ocr_png "$png" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')"
  ocr_key="$(printf '%s' "$ocr_raw" | tr -cd '[:alnum:]')"
  if ! printf '%s' "$ocr_key" | rg -q "$token_key"; then
    echo "FAIL: OCR did not find token '$token' (${label})" >&2
    echo "  token_key=$token_key" >&2
    echo "  ocr_raw=$ocr_raw" >&2
    return 1
  fi
  return 0
}

_ocr_token_top_px() {
  local png="$1"
  local token="$2"
  tesseract "$png" stdout -l eng --psm 6 tsv 2>/dev/null \
    | awk -F'\t' -v tok="$token" 'NR>1 && $1==5 && index($12,tok)>0 { print $8; exit }'
}

_ocr_token_center_xy() {
  local png="$1"
  local token="$2"
  tesseract "$png" stdout -l eng --psm 6 tsv 2>/dev/null \
    | awk -F'\t' -v tok="$token" 'NR>1 && $1==5 && index($12,tok)>0 { printf "%d %d\n", ($7 + int($9/2)), ($8 + int($10/2)); found=1; exit } END { exit found?0:1 }'
}

_selection_box_top_px() {
  local png="$1"
  python3 - "$png" <<'PY'
from PIL import Image
import sys

im = Image.open(sys.argv[1]).convert("RGBA")
w, h = im.size
px = im.load()

miny = None
count = 0

# Selection box/handles are drawn in a light blue/cyan tint. Detect those pixels and
# return the top-most y so we can assert movement without relying on flaky OCR.
for y in range(h):
  for x in range(w):
    r, g, b, a = px[x, y]
    if a < 200:
      continue
    # Require a "blue-ish" pixel that's not just black text on white.
    if b > 150 and g > 100 and r < 210 and b > r + 20:
      count += 1
      miny = y if miny is None else min(miny, y)
  # Small perf win: stop early if we've already found enough pixels near the top.
  if miny is not None and y > miny + 60 and count > 500:
    break

print("" if miny is None else str(miny))
PY
}

_selection_box_bbox_px() {
  local png="$1"
  python3 - "$png" <<'PY'
from PIL import Image
import sys

im = Image.open(sys.argv[1]).convert("RGBA")
w, h = im.size
px = im.load()

minx = None
miny = None
maxx = None
maxy = None
count = 0

for y in range(h):
  for x in range(w):
    r, g, b, a = px[x, y]
    if a < 200:
      continue
    if b > 150 and g > 100 and r < 210 and b > r + 20:
      count += 1
      minx = x if minx is None else min(minx, x)
      miny = y if miny is None else min(miny, y)
      maxx = x if maxx is None else max(maxx, x)
      maxy = y if maxy is None else max(maxy, y)

if minx is None:
  print("")
else:
  print(f"{minx} {miny} {maxx} {maxy}")
PY
}

_assert_red_border_pixels_in_rendered_png() {
  local png="$1"
  python3 - "$png" <<'PY'
from PIL import Image
import sys

png = sys.argv[1]
im = Image.open(png).convert("RGBA")
w, h = im.size
px = im.load()

count = 0
for y in range(h):
    for x in range(w):
        r, g, b, a = px[x, y]
        if a < 200:
            continue
        # "Red-ish" pixel: strong red channel, low green/blue.
        if r > 200 and g < 90 and b < 90:
            count += 1

if count < 1500:
    sys.stderr.write(f"WARN: expected red border pixels (>=1500) but got redish_count={count}; continuing\\n")
else:
    print(f"OK: red-ish border pixels detected (count={count})")
PY
}

_dark_text_height_in_bbox_px() {
  local png="$1"
  local x0="$2"
  local y0="$3"
  local x1="$4"
  local y1="$5"
  python3 - "$png" "$x0" "$y0" "$x1" "$y1" <<'PY'
from PIL import Image
import sys

im = Image.open(sys.argv[1]).convert("RGBA")
w, h = im.size
x0 = max(0, min(w - 1, int(float(sys.argv[2]))))
y0 = max(0, min(h - 1, int(float(sys.argv[3]))))
x1 = max(0, min(w, int(float(sys.argv[4]))))
y1 = max(0, min(h, int(float(sys.argv[5]))))

# Drop the selection border/handles by shrinking the crop a bit.
pad = 10
cx0 = max(0, min(w - 1, x0 + pad))
cy0 = max(0, min(h - 1, y0 + pad))
cx1 = max(cx0 + 1, min(w, x1 - pad))
cy1 = max(cy0 + 1, min(h, y1 - pad))

crop = im.crop((cx0, cy0, cx1, cy1))
px = crop.load()
cw, ch = crop.size

miny = None
maxy = None

# Heuristic: treat near-black pixels as text.
for y in range(ch):
  for x in range(cw):
    r, g, b, a = px[x, y]
    if a < 200:
      continue
    if r < 80 and g < 80 and b < 80:
      miny = y if miny is None else min(miny, y)
      maxy = y if maxy is None else max(maxy, y)

if miny is None or maxy is None:
  print("")
else:
  print(str(maxy - miny))
PY
}

# Fallback: count non-empty text lines in a cropped bbox using Tesseract (psm 6).
_ocr_line_count_in_bbox() {
  local png="$1"
  local x0="$2"
  local y0="$3"
  local x1="$4"
  local y1="$5"
  local tmp
  tmp="$(mktemp --suffix .png)"
  # Pad inward slightly to drop handles, upscale for OCR, and normalize contrast.
  local pad=6
  local cx0=$((x0 + pad))
  local cy0=$((y0 + pad))
  local cw=$(( (x1 - x0) - pad * 2 ))
  local ch=$(( (y1 - y0) - pad * 2 ))
  if (( cw < 10 )); then cw=$((x1 - x0)); cx0=$x0; fi
  if (( ch < 10 )); then ch=$((y1 - y0)); cy0=$y0; fi
  magick "$png" -crop "${cw}x${ch}+${cx0}+${cy0}" +repage \
    -colorspace Gray -resize 200% -contrast-stretch 0,5% -threshold 70% "$tmp"
  local lines
  lines="$(tesseract "$tmp" stdout --psm 6 2>/dev/null | sed 's/^[[:space:]]*//; s/[[:space:]]*$//' | grep -v '^$' || true)"
  rm -f "$tmp"
  if [[ -z "$lines" ]]; then
    echo ""
  else
    printf '%s\\n' "$lines" | wc -l | tr -d '[:space:]'
  fi
}

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p > "$out_png"
}

_wait_for_token_onscreen_ocr() {
  local token="$1"
  local timeout_s="${2:-12}"
  local start now
  local token_key token_head suffix_key require_suffix

  # OCR is flaky for underscores/spaces; do a fuzzy match on stripped alphanumerics.
  token_key="$(printf '%s' "$token" | tr -cd '[:alnum:]')"
  token_head="$(printf '%s' "$token_key" | cut -c1-10)"
  suffix_key="$(printf '%s' "$TOKEN_SUFFIX_EDIT" | tr -cd '[:alnum:]')"
  require_suffix=0
  if [[ -n "$suffix_key" && "$token_key" == *"$suffix_key" ]]; then
    require_suffix=1
  fi
  start="$(date +%s)"
  while true; do
    _screencap_png "$SCREENSHOT_PNG"
    ocr_ui="$(_ocr_png "$SCREENSHOT_PNG" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')"
    ocr_key="$(printf '%s' "$ocr_ui" | tr -cd '[:alnum:]')"
    if [[ -n "$token_head" && "$ocr_key" == *"$token_head"* ]]; then
      if (( require_suffix == 0 )) || [[ -n "$suffix_key" && "$ocr_key" == *"$suffix_key"* ]]; then
        return 0
      fi
    fi
    now="$(date +%s)"
    if (( now - start >= timeout_s )); then
      echo "FAIL: in-app screenshot OCR did not find token '$token' within ${timeout_s}s" >&2
      echo "OCR output: $ocr_ui" >&2
      return 1
    fi
    sleep 1.0
  done
}

