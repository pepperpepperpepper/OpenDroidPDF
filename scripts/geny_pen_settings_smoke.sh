#!/usr/bin/env bash
set -euo pipefail

# Genymotion regression smoke:
# - Open a blank PDF
# - Draw a stroke
# - Change pen size + color (which finalizes pending ink)
# - Ensure the first stroke remains visible (does not disappear)
# - Draw a second stroke and ensure pixels increased
#
# Usage:
#   DEVICE=localhost:42865 APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pen_settings_smoke.sh

DEVICE=${DEVICE:-localhost:42865}
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_blank.pdf}
PDF_DEVICE=${PDF_DEVICE:-/sdcard/Download/test_blank.pdf}
OUTDIR=${OUTDIR:-/tmp/opendroidpdf_pen_settings_smoke}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

log(){ echo "[pen-smoke] $*"; }

mkdir -p "$OUTDIR"
BEFORE_DRAW="$OUTDIR/before_draw.png"
AFTER_DRAW="$OUTDIR/after_draw.png"
AFTER_SETTINGS="$OUTDIR/after_settings.png"
AFTER_SECOND="$OUTDIR/after_second.png"
INKDIALOG_XML="$OUTDIR/inkdialog.xml"
LOGCAT_TXT="$OUTDIR/logcat.txt"
REPORT="$OUTDIR/report.txt"

adb -s "$DEVICE" get-state >/dev/null

log "Installing APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

log "Pushing PDF"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_DEVICE" >/dev/null

log "Clearing logcat"
adb -s "$DEVICE" logcat -c || true

log "Launching viewer"
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_DEVICE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2

log "Entering draw mode"
# Known-good coords for the Pixel 6 A13 Genymotion profile used in this workspace.
adb -s "$DEVICE" shell input tap 970 80
sleep 0.8

log "Capturing baseline"
adb -s "$DEVICE" exec-out screencap -p > "$BEFORE_DRAW"

log "Drawing first stroke"
adb -s "$DEVICE" shell input swipe 300 900 900 1200 300
sleep 0.8
adb -s "$DEVICE" exec-out screencap -p > "$AFTER_DRAW"

log "Opening pen settings dialog"
adb -s "$DEVICE" shell input tap 875 80
sleep 0.8

adb -s "$DEVICE" shell uiautomator dump /sdcard/tmp_pen_smoke_inkdialog.xml >/dev/null || true
adb -s "$DEVICE" pull /sdcard/tmp_pen_smoke_inkdialog.xml "$INKDIALOG_XML" >/dev/null || true

log "Adjusting size + color"
DEVICE="$DEVICE" INKDIALOG_XML="$INKDIALOG_XML" python - <<'PY'
import os, re, sys, xml.etree.ElementTree as ET, subprocess

device=os.environ['DEVICE']
xml_path=os.environ['INKDIALOG_XML']
tree=ET.parse(xml_path)
root=tree.getroot()

def center(bounds: str):
    m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m: return None
    l,t,r,b=map(int,m.groups())
    return ((l+r)//2, (t+b)//2)

def seek_swipe(bounds: str):
    m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m: return None
    l,t,r,b=map(int,m.groups())
    y=(t+b)//2
    x1=int(l+(r-l)*0.85)
    x2=int(l+(r-l)*0.15)
    return (x1,y,x2,y)

seek_bounds=None
blue_bounds=None
for el in root.iter('node'):
    rid=el.attrib.get('resource-id','')
    if rid=='org.opendroidpdf:id/pen_size_seekbar':
        seek_bounds=el.attrib.get('bounds')
        break

for el in root.iter('node'):
    if el.attrib.get('clickable')=='true' and el.attrib.get('content-desc')=='Set ink color to Blue':
        blue_bounds=el.attrib.get('bounds')
        break

if not seek_bounds or not blue_bounds:
    print("Missing seekbar or swatch bounds; falling back to hardcoded taps", file=sys.stderr)
    # Best-effort fallback.
    subprocess.run(['adb','-s',device,'shell','input','swipe','800','744','300','744','300'], check=False)
    subprocess.run(['adb','-s',device,'shell','input','tap','460','1210'], check=False)
    raise SystemExit(0)

sx1,sy1,sx2,sy2=seek_swipe(seek_bounds)
bx,by=center(blue_bounds)

subprocess.run(['adb','-s',device,'shell','input','swipe',str(sx1),str(sy1),str(sx2),str(sy2),'300'], check=False)
subprocess.run(['adb','-s',device,'shell','input','tap',str(bx),str(by)], check=False)
PY

log "Dismissing dialog"
adb -s "$DEVICE" shell input keyevent 4
sleep 1
adb -s "$DEVICE" exec-out screencap -p > "$AFTER_SETTINGS"

log "Drawing second stroke"
adb -s "$DEVICE" shell input swipe 300 1200 900 900 300
sleep 0.8
adb -s "$DEVICE" exec-out screencap -p > "$AFTER_SECOND"

log "Capturing logcat"
adb -s "$DEVICE" logcat -d > "$LOGCAT_TXT" || true

log "Analyzing screenshots"
BEFORE_DRAW="$BEFORE_DRAW" AFTER_DRAW="$AFTER_DRAW" AFTER_SETTINGS="$AFTER_SETTINGS" AFTER_SECOND="$AFTER_SECOND" REPORT="$REPORT" python - <<'PY'
from PIL import Image
import numpy as np, os, math

before=os.environ['BEFORE_DRAW']
after_draw=os.environ['AFTER_DRAW']
after_settings=os.environ['AFTER_SETTINGS']
after_second=os.environ['AFTER_SECOND']
report=os.environ['REPORT']

def ink_pixels(path):
    im=Image.open(path).convert('RGB')
    w,h=im.size
    # Crop away the status/action/nav bars to focus on page content.
    top=int(h*0.12)
    bottom=int(h*0.88)
    left=int(w*0.06)
    right=int(w*0.94)
    im=im.crop((left, top, right, bottom))
    arr=np.asarray(im)
    # Consider "ink" as pixels sufficiently non-white.
    rgb=arr.astype(np.int16)
    dark=((rgb[:,:,0] < 235) | (rgb[:,:,1] < 235) | (rgb[:,:,2] < 235))
    return int(dark.sum())

base=ink_pixels(before)
draw1=ink_pixels(after_draw)
after=ink_pixels(after_settings)
draw2=ink_pixels(after_second)

lines=[
    f"baseline_ink_pixels={base}",
    f"after_draw_ink_pixels={draw1}",
    f"after_settings_ink_pixels={after}",
    f"after_second_ink_pixels={draw2}",
]

# Simple assertions:
ok=True
if draw1 <= base + 200:
    ok=False
    lines.append("FAIL: first stroke did not add enough ink pixels")
if after < int(draw1 * 0.85):
    ok=False
    lines.append("FAIL: ink pixels dropped after changing pen settings (stroke likely disappeared)")
if draw2 <= after + 200:
    ok=False
    lines.append("FAIL: second stroke did not add enough ink pixels")

lines.append("PASS" if ok else "FAIL")
with open(report,'w') as f:
    f.write("\n".join(lines) + "\n")
print("\n".join(lines))
raise SystemExit(0 if ok else 1)
PY

log "Done. Report: $REPORT"
log "Artifacts: $OUTDIR"
