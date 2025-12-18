#!/usr/bin/env bash
set -euo pipefail

# Genymotion regression smoke:
# - Open a PDF with visible text (OCR gate)
# - Draw + commit stroke A
# - Draw stroke B (do NOT tap ✓/accept)
# - Switch to eraser (should auto-commit pending stroke B)
# - Ensure eraser can remove both A and B
#
# Usage:
#   DEVICE=localhost:42865 APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_eraser_autocommit_smoke.sh

DEVICE=${DEVICE:-localhost:42865}
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_with_text.pdf}
PDF_DEVICE=${PDF_DEVICE:-/sdcard/Download/pdf_with_text.pdf}
OUTDIR=${OUTDIR:-/tmp/opendroidpdf_eraser_autocommit_smoke}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

log(){ echo "[eraser-autocommit-smoke] $*"; }

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

mkdir -p "$OUTDIR"
BASELINE="$OUTDIR/baseline.png"
AFTER_B="$OUTDIR/after_b.png"
AFTER_ERASE_A="$OUTDIR/after_erase_a.png"
AFTER_ERASE_B="$OUTDIR/after_erase_b.png"
OCR_TXT="$OUTDIR/baseline_ocr.txt"
REPORT="$OUTDIR/report.txt"
INKDIALOG_XML="$OUTDIR/inkdialog.xml"

adb -s "$DEVICE" get-state >/dev/null

log "Installing APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

log "Pushing PDF"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_DEVICE" >/dev/null

log "Force-stopping app for clean intent handling"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true

log "Launching viewer"
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_DEVICE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2

uia_assert_in_document_view

log "Waiting for PDF text to render (OCR gate)"
ocr_ok=0
for attempt in 1 2 3 4 5 6 7 8; do
  adb -s "$DEVICE" exec-out screencap -p > "$BASELINE"
  if BASELINE="$BASELINE" OCR_TXT="$OCR_TXT" python - <<'PY'
import os, subprocess, sys
from PIL import Image

baseline=os.environ["BASELINE"]
out=os.environ["OCR_TXT"]
im=Image.open(baseline).convert("RGB")
w,h=im.size
im=im.crop((int(w*0.05), int(h*0.12), int(w*0.95), int(h*0.88)))
tmp=baseline + ".ocr.png"
im.save(tmp)
txt=subprocess.check_output(
    ["tesseract", tmp, "stdout", "--psm", "6", "-l", "eng"],
    stderr=subprocess.DEVNULL,
    timeout=30,
).decode("utf-8", errors="replace")
with open(out, "w", encoding="utf-8") as f:
    f.write(txt)
needles=["droidpdf","quick"]
low=txt.lower()
sys.exit(0 if any(n in low for n in needles) else 1)
PY
  then
    ocr_ok=1
    break
  fi
  sleep 1
done
if [[ "$ocr_ok" != "1" ]]; then
  log "FAIL: OCR gate did not detect expected text. See $OCR_TXT and $BASELINE"
  exit 1
fi

log "Entering draw mode"
uia_tap_any_res_id "org.opendroidpdf:id/draw_image_button" "org.opendroidpdf:id/menu_draw"
sleep 0.6

log "Drawing + committing stroke A"
adb -s "$DEVICE" shell input swipe 250 1200 900 1300 300
sleep 0.8
uia_tap_res_id "org.opendroidpdf:id/menu_accept"
sleep 1.2

log "Re-entering draw mode"
uia_tap_any_res_id "org.opendroidpdf:id/draw_image_button" "org.opendroidpdf:id/menu_draw"
sleep 0.6

log "Opening pen settings dialog (optional)"
uia_tap_any_res_id "org.opendroidpdf:id/menu_pen_size" "org.opendroidpdf:id/menu_ink_color"
sleep 0.8
adb -s "$DEVICE" shell uiautomator dump /sdcard/tmp_pen_smoke_inkdialog.xml >/dev/null || true
adb -s "$DEVICE" pull /sdcard/tmp_pen_smoke_inkdialog.xml "$INKDIALOG_XML" >/dev/null || true

log "Adjusting size"
DEVICE="$DEVICE" INKDIALOG_XML="$INKDIALOG_XML" python - <<'PY'
import os, re, sys, xml.etree.ElementTree as ET, subprocess

device=os.environ['DEVICE']
xml_path=os.environ['INKDIALOG_XML']
root=ET.parse(xml_path).getroot()

def seek_swipe(bounds: str):
    m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m: return None
    l,t,r,b=map(int,m.groups())
    y=(t+b)//2
    x1=int(l+(r-l)*0.85)
    x2=int(l+(r-l)*0.15)
    return (x1,y,x2,y)

seek_bounds=None
for el in root.iter('node'):
    if el.attrib.get('resource-id','')=='org.opendroidpdf:id/pen_size_seekbar':
        seek_bounds=el.attrib.get('bounds')
        break

if not seek_bounds:
    raise SystemExit(0)  # dialog might not include the seekbar (fallback: still run test)

sx1,sy1,sx2,sy2=seek_swipe(seek_bounds)
subprocess.run(['adb','-s',device,'shell','input','swipe',str(sx1),str(sy1),str(sx2),str(sy2),'250'], check=False)
PY

log "Dismissing dialog"
adb -s "$DEVICE" shell input keyevent 4
sleep 0.8

log "Drawing stroke B (NO accept)"
adb -s "$DEVICE" shell input swipe 250 1500 900 1600 300
sleep 0.8
adb -s "$DEVICE" exec-out screencap -p > "$AFTER_B"

log "Switching to eraser (should auto-commit pending ink)"
uia_tap_any_res_id "org.opendroidpdf:id/draw_image_button" "org.opendroidpdf:id/menu_draw"
sleep 0.6
uia_tap_res_id "org.opendroidpdf:id/menu_erase"
sleep 0.6

log "Erasing across older stroke A"
adb -s "$DEVICE" shell input swipe 900 1300 250 1200 350
sleep 1.2
adb -s "$DEVICE" exec-out screencap -p > "$AFTER_ERASE_A"

log "Erasing across newer stroke B"
adb -s "$DEVICE" shell input swipe 900 1600 250 1500 350
sleep 1.2
adb -s "$DEVICE" exec-out screencap -p > "$AFTER_ERASE_B"

log "Analyzing screenshots"
AFTER_B="$AFTER_B" AFTER_ERASE_A="$AFTER_ERASE_A" AFTER_ERASE_B="$AFTER_ERASE_B" REPORT="$REPORT" python - <<'PY'
from PIL import Image, ImageChops
import numpy as np, os, sys

after_b=os.environ['AFTER_B']
after_erase_a=os.environ['AFTER_ERASE_A']
after_erase_b=os.environ['AFTER_ERASE_B']
report=os.environ['REPORT']

def crop_page(im):
    w,h=im.size
    return im.crop((int(w*0.05), int(h*0.12), int(w*0.95), int(h*0.88)))

def diff_stats(a, b):
    diff=ImageChops.difference(a, b)
    arr=np.asarray(diff)
    changed=int(((arr!=0).any(axis=2)).sum())
    c1=np.asarray(a).astype(np.int16)
    c2=np.asarray(b).astype(np.int16)
    whitened=int(((c2.sum(axis=2) - c1.sum(axis=2)) > 40).sum())
    return changed, whitened

im_b=crop_page(Image.open(after_b))
im_ea=crop_page(Image.open(after_erase_a))
im_eb=crop_page(Image.open(after_erase_b))
w,h=im_b.size

roi_a=(int(w*0.10), int(h*0.55), int(w*0.90), int(h*0.72))
roi_b=(int(w*0.10), int(h*0.72), int(w*0.90), int(h*0.88))

a_changed, a_whitened = diff_stats(im_b.crop(roi_a).convert('RGB'), im_ea.crop(roi_a).convert('RGB'))
b_changed, b_whitened = diff_stats(im_ea.crop(roi_b).convert('RGB'), im_eb.crop(roi_b).convert('RGB'))

ok=True
lines=[
    f"eraseA_roi_changed_pixels={a_changed}",
    f"eraseA_roi_whitened_pixels={a_whitened}",
    f"eraseB_roi_changed_pixels={b_changed}",
    f"eraseB_roi_whitened_pixels={b_whitened}",
]

if a_changed < 800 or a_whitened < 120:
    ok=False
    lines.append("FAIL: could not erase older stroke A (not enough pixel change)")
if b_changed < 800 or b_whitened < 120:
    ok=False
    lines.append("FAIL: could not erase newer stroke B (not enough pixel change)")

lines.append("PASS" if ok else "FAIL")
with open(report,'w') as f:
    f.write("\n".join(lines) + "\n")
print("\n".join(lines))
sys.exit(0 if ok else 1)
PY

log "Done. Report: $REPORT"
log "Artifacts: $OUTDIR"

