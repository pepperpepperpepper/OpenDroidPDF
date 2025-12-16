#!/usr/bin/env bash
set -euo pipefail

DEVICE=${DEVICE:-localhost:42865}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_with_text.pdf}
PDF_DEVICE=${PDF_DEVICE:-/sdcard/Download/pdf_with_text.pdf}
PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity
TMPDIR=${TMPDIR:-/tmp}
BEFORE=${BEFORE:-$TMPDIR/tmp_auto_draw_before.png}
AFTER=${AFTER:-$TMPDIR/tmp_auto_draw_after.png}
DUMP_LOCAL=${DUMP_LOCAL:-$TMPDIR/tmp_auto_draw_uia.xml}
REPORT=${REPORT:-$TMPDIR/tmp_auto_draw_report.txt}
MENU_XML=${MENU_XML:-$TMPDIR/tmp_auto_draw_menu.xml}
EXPORTED_PDF=${EXPORTED_PDF:-$TMPDIR/tmp_auto_draw_saved.pdf}

log(){ echo "[auto-draw] $*"; }

log "Pushing test PDF"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_DEVICE" >/dev/null

log "Launching viewer"
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_DEVICE" -t application/pdf $PKG/$ACT >/dev/null
sleep 2

log "Capturing before screenshot"
adb -s "$DEVICE" exec-out screencap -p > "$BEFORE"

log "Locating draw menu item"
adb -s "$DEVICE" shell uiautomator dump /sdcard/tmp_auto_draw_uia.xml >/dev/null || true
adb -s "$DEVICE" pull /sdcard/tmp_auto_draw_uia.xml "$DUMP_LOCAL" >/dev/null || true
coords=""
if [[ -f "$DUMP_LOCAL" ]]; then
  coords=$(DUMP_LOCAL="$DUMP_LOCAL" python - <<'PY'
import os, sys, xml.etree.ElementTree as ET
path=os.environ['DUMP_LOCAL']
try:
    tree=ET.parse(path)
except Exception:
    sys.exit(0)
root=tree.getroot()
targets={'org.opendroidpdf:id/draw_image_button','org.opendroidpdf:id/menu_draw'}
bounds=None
for el in root.iter():
    rid=el.attrib.get('resource-id','')
    text=el.attrib.get('text','').lower()
    if rid in targets or text=='draw':
        b=el.attrib.get('bounds')
        if b:
            bounds=b
            break
if not bounds:
    sys.exit(0)
lt, rb = bounds.split('][')
lt = lt.strip('[')
rb = rb.strip(']')
l, t = map(int, lt.split(','))
r, b = map(int, rb.split(','))
print(f"{(l+r)//2} {(t+b)//2}")
PY
  )
  [[ -n "$coords" ]] && log "Found draw coords: $coords"
fi
[[ -z "$coords" ]] && coords="980 150" && log "Draw button fallback tap $coords"

set -- $coords
adb -s "$DEVICE" shell input tap "$1" "$2"
sleep 0.5

log "Performing swipe to draw"
adb -s "$DEVICE" shell input swipe 300 900 900 1400 300
sleep 0.8

log "Capturing after screenshot"
adb -s "$DEVICE" exec-out screencap -p > "$AFTER"

# Try overflow -> Save (best effort)
log "Attempting overflow -> Save"
adb -s "$DEVICE" shell input tap 1035 90 || true
sleep 0.8
adb -s "$DEVICE" shell uiautomator dump /sdcard/tmp_auto_draw_menu.xml >/dev/null || true
adb -s "$DEVICE" pull /sdcard/tmp_auto_draw_menu.xml "$MENU_XML" >/dev/null || true
save_coords=""
if [[ -f "$MENU_XML" ]]; then
  save_coords=$(MENU_XML="$MENU_XML" python - <<'PY'
import os, sys, xml.etree.ElementTree as ET
path=os.environ['MENU_XML']
try:
    tree=ET.parse(path)
except Exception:
    sys.exit(0)
root=tree.getroot()
for el in root.iter():
    txt=el.attrib.get('text','').lower()
    if txt.startswith('save'):
        b=el.attrib.get('bounds')
        if b:
            lt, rb = b.split('][')
            lt=lt.strip('['); rb=rb.strip(']')
            l,t = map(int, lt.split(',')); r,b2 = map(int, rb.split(','))
            print(f"{(l+r)//2} {(t+b2)//2}")
            break
PY
  )
  [[ -n "$save_coords" ]] && log "Found Save coords: $save_coords"
fi
if [[ -n "$save_coords" ]]; then
  set -- $save_coords
  adb -s "$DEVICE" shell input tap "$1" "$2" || true
  sleep 2
else
  log "Save item not found; skipping export pull"
fi

# Pull exported PDF (best effort)
adb -s "$DEVICE" pull "$PDF_DEVICE" "$EXPORTED_PDF" >/dev/null 2>&1 || true

log "Analyzing pixel delta"
python - <<'PY'
from PIL import Image, ImageChops
import numpy as np, os, subprocess, pathlib, sys
before=os.environ['BEFORE']; after=os.environ['AFTER']; report=os.environ['REPORT']; exported=os.environ['EXPORTED_PDF']
im1=Image.open(before).convert('RGB')
im2=Image.open(after).convert('RGB')
diff=ImageChops.difference(im1, im2)
arr=np.array(diff)
changed=((arr!=0).any(axis=2)).sum()
red_pixels=((arr[:,:,0] > arr[:,:,1]+arr[:,:,2]) & (arr[:,:,0]>10)).sum()
rows=[f"changed_pixels={changed}", f"red_like_pixels={red_pixels}"]
export_png=None
if exported and pathlib.Path(exported).exists():
    stem=str(pathlib.Path(exported).with_suffix(''))
    export_png=stem+'-1.png'
    try:
        subprocess.check_output(['pdftoppm','-png',exported,stem], stderr=subprocess.STDOUT)
        if pathlib.Path(export_png).exists():
            im=Image.open(export_png).convert('RGB')
            arr2=np.array(im)
            red2=((arr2[:,:,0] > arr2[:,:,1]+arr2[:,:,2]) & (arr2[:,:,0]>10)).sum()
            rows.append(f"export_red_like_pixels={red2}")
        else:
            rows.append("export_red_like_pixels=missing_png")
    except Exception as e:
        rows.append("export_convert_failed")
        rows.append(str(e))
else:
    rows.append("export_red_like_pixels=not_pulled")
with open(report,'w') as f:
    f.write("\n".join(rows)+"\n")
print("\n".join(rows))
PY

log "Results written to $REPORT"
log "Before screenshot: $BEFORE"
log "After screenshot:  $AFTER"
