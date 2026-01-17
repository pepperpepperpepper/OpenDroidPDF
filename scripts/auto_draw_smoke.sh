#!/usr/bin/env bash
set -euo pipefail

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
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

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

log "Pushing test PDF"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_DEVICE" >/dev/null

log "Launching viewer"
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_DEVICE" -t application/pdf $PKG/$ACT >/dev/null
sleep 2

log "Capturing before screenshot"
adb -s "$DEVICE" exec-out screencap -p > "$BEFORE"

log "Entering draw mode"
uia_enter_draw_mode || { log "FAIL: draw entry point missing"; exit 1; }
sleep 0.5

log "Performing swipe to draw"
adb -s "$DEVICE" shell input swipe 300 900 900 1400 300
sleep 0.8

log "Capturing after screenshot"
adb -s "$DEVICE" exec-out screencap -p > "$AFTER"

log "Committing pending ink (Accept, best-effort)"
uia_tap_any_res_id "org.opendroidpdf:id/accept_image_button" "org.opendroidpdf:id/menu_accept" || true
sleep 0.8

log "Attempting Save changes (best-effort)"
if uia_save_changes; then
  sleep 0.8
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
  sleep 2
else
  log "Save changes entry point not found; skipping export pull"
fi

# Pull exported PDF (best effort)
adb -s "$DEVICE" pull "$PDF_DEVICE" "$EXPORTED_PDF" >/dev/null 2>&1 || true

log "Analyzing pixel delta"
BEFORE="$BEFORE" AFTER="$AFTER" REPORT="$REPORT" EXPORTED_PDF="$EXPORTED_PDF" python - <<'PY'
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
