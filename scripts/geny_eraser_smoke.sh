#!/usr/bin/env bash
set -euo pipefail

# Genymotion regression smoke:
# - Open a PDF with visible text (render check)
# - Draw a stroke and commit it
# - Switch to eraser and erase across the committed stroke
# - Verify the screen pixels change in the stroke region (best-effort)
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_eraser_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_with_text.pdf}
PDF_DEVICE=${PDF_DEVICE:-/sdcard/Download/pdf_with_text.pdf}
OUTDIR=${OUTDIR:-/tmp/opendroidpdf_eraser_smoke}
OCR_NEEDLES=${OCR_NEEDLES:-droidpdf,quick}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

log(){ echo "[eraser-smoke] $*"; }

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

mkdir -p "$OUTDIR"
BEFORE="$OUTDIR/before.png"
AFTER_PENDING_DRAW="$OUTDIR/after_pending_draw.png"
AFTER_PENDING_ERASE="$OUTDIR/after_pending_erase.png"
AFTER_COMMIT="$OUTDIR/after_commit.png"
AFTER_COMMIT_ERASE="$OUTDIR/after_commit_erase.png"
OCR_TXT="$OUTDIR/baseline_ocr.txt"
REPORT="$OUTDIR/report.txt"
LOGCAT_TXT="$OUTDIR/logcat.txt"

adb -s "$DEVICE" get-state >/dev/null

log "Installing APK"
if ! install_out="$(adb -s "$DEVICE" install -r "$APK" 2>&1)"; then
  if echo "$install_out" | rg -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
    log "APK signature mismatch; uninstalling $PKG and retrying"
    adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
    adb -s "$DEVICE" install -r "$APK" >/dev/null
  else
    echo "$install_out" >&2
    exit 1
  fi
fi
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

log "Pushing PDF"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_DEVICE" >/dev/null

log "Clearing logcat"
adb -s "$DEVICE" logcat -c || true

log "Force-stopping app for clean intent handling"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true

log "Launching viewer"
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_DEVICE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2

log "Verifying we're in document view"
uia_assert_in_document_view

PID0="$(adb -s "$DEVICE" shell pidof "$PKG" | tr -d '\r' | awk '{print $1}')"
if [[ -z "$PID0" ]]; then
  log "FAIL: could not determine app PID"
  adb -s "$DEVICE" logcat -d > "$LOGCAT_TXT" || true
  exit 1
fi

assert_app_alive() {
  local label="$1"
  local pid
  pid="$(adb -s "$DEVICE" shell pidof "$PKG" | tr -d '\r' | awk '{print $1}' || true)"

  if [[ -z "$pid" ]]; then
    log "FAIL: app process missing after $label"
    adb -s "$DEVICE" exec-out screencap -p > "$OUTDIR/fail_${label// /_}.png" || true
    adb -s "$DEVICE" logcat -d > "$LOGCAT_TXT" || true
    exit 1
  fi

  if [[ "$pid" != "$PID0" ]]; then
    log "FAIL: PID changed (crash/restart?) $PID0 -> $pid after $label"
    adb -s "$DEVICE" exec-out screencap -p > "$OUTDIR/fail_${label// /_}.png" || true
    adb -s "$DEVICE" logcat -d > "$LOGCAT_TXT" || true
    exit 1
  fi

  if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION|Fatal signal"; then
    log "FAIL: logcat contains crash markers after $label"
    adb -s "$DEVICE" exec-out screencap -p > "$OUTDIR/fail_${label// /_}.png" || true
    adb -s "$DEVICE" logcat -d > "$LOGCAT_TXT" || true
    rg -n "FATAL EXCEPTION|Fatal signal|Process: org\\.opendroidpdf" "$LOGCAT_TXT" || true
    exit 1
  fi

  uia_assert_in_document_view
}

log "Waiting for PDF text to render (OCR gate)"
ocr_ok=0
for attempt in 1 2 3 4 5 6 7 8; do
  adb -s "$DEVICE" exec-out screencap -p > "$BEFORE"
  if BEFORE="$BEFORE" OCR_TXT="$OCR_TXT" OCR_NEEDLES="$OCR_NEEDLES" python - <<'PY'
import os, subprocess, sys
from PIL import Image

before=os.environ["BEFORE"]
out=os.environ["OCR_TXT"]
needles=[n.strip().lower() for n in os.environ.get("OCR_NEEDLES","").split(",") if n.strip()]
if not needles:
    needles=["droidpdf","quick"]

im=Image.open(before).convert("RGB")
w,h=im.size
# Crop away status/action/nav bars to focus on the rendered page.
im=im.crop((int(w*0.05), int(h*0.12), int(w*0.95), int(h*0.88)))
tmp=before + ".ocr.png"
im.save(tmp)
try:
    txt=subprocess.check_output(
        ["tesseract", tmp, "stdout", "--psm", "6", "-l", "eng"],
        stderr=subprocess.DEVNULL,
        timeout=30,
    ).decode("utf-8", errors="replace")
except Exception as e:
    txt=f"(tesseract_failed: {e})\n"
with open(out, "w", encoding="utf-8") as f:
    f.write(txt)
low=txt.lower()
ok=any(n in low for n in needles)
print("ocr_needles=" + ",".join(needles))
print(f"ocr_hit={ok}")
sys.exit(0 if ok else 1)
PY
  then
    ocr_ok=1
    break
  fi
  sleep 1
done
if [[ "$ocr_ok" != "1" ]]; then
  log "FAIL: OCR gate did not detect expected text. See $OCR_TXT and $BEFORE"
  exit 1
fi

log "Entering draw mode (UIA)"
uia_enter_draw_mode || { log "FAIL: draw entry point missing"; exit 1; }
sleep 0.6

log "Drawing stroke"
adb -s "$DEVICE" shell input swipe 250 1400 900 1500 300
sleep 0.8

log "Capturing after pending draw"
adb -s "$DEVICE" exec-out screencap -p > "$AFTER_PENDING_DRAW"

log "Switching to eraser (UIA)"
uia_tap_res_id "org.opendroidpdf:id/menu_erase"
sleep 0.6

log "Erasing across the pending stroke"
adb -s "$DEVICE" shell input swipe 900 1500 250 1400 350
sleep 1.2
adb -s "$DEVICE" exec-out screencap -p > "$AFTER_PENDING_ERASE"
assert_app_alive "pending_erase"

log "Back to draw mode (UIA)"
uia_enter_draw_mode || { log "FAIL: draw entry point missing"; exit 1; }
sleep 0.6

log "Drawing stroke again for committed-ink erase scenario"
adb -s "$DEVICE" shell input swipe 250 1200 900 1300 300
sleep 0.8

log "Committing stroke (Accept UIA)"
uia_tap_res_id "org.opendroidpdf:id/menu_accept"
sleep 1.5
adb -s "$DEVICE" exec-out screencap -p > "$AFTER_COMMIT"

log "Re-entering annotation mode (UIA) for committed-ink erase"
uia_enter_draw_mode || { log "FAIL: draw entry point missing"; exit 1; }
sleep 0.6

log "Switching to eraser (UIA)"
uia_tap_res_id "org.opendroidpdf:id/menu_erase"
sleep 0.6

log "Erasing across the committed stroke"
adb -s "$DEVICE" shell input swipe 900 1300 250 1200 350
sleep 1.6
adb -s "$DEVICE" exec-out screencap -p > "$AFTER_COMMIT_ERASE"
assert_app_alive "committed_erase"

log "Capturing logcat"
adb -s "$DEVICE" logcat -d > "$LOGCAT_TXT" || true
if rg -q "FATAL EXCEPTION|Fatal signal" "$LOGCAT_TXT"; then
  log "FAIL: crash markers found in logcat"
  rg -n "FATAL EXCEPTION|Fatal signal|Process: org\\.opendroidpdf" "$LOGCAT_TXT" || true
  exit 1
fi

log "Analyzing screenshots"
BEFORE="$BEFORE" AFTER_PENDING_DRAW="$AFTER_PENDING_DRAW" AFTER_PENDING_ERASE="$AFTER_PENDING_ERASE" AFTER_COMMIT="$AFTER_COMMIT" AFTER_COMMIT_ERASE="$AFTER_COMMIT_ERASE" REPORT="$REPORT" python - <<'PY'
from PIL import Image, ImageChops
import numpy as np, os, sys

before=os.environ['BEFORE']
after_pending_draw=os.environ['AFTER_PENDING_DRAW']
after_pending_erase=os.environ['AFTER_PENDING_ERASE']
after_commit=os.environ['AFTER_COMMIT']
after_commit_erase=os.environ['AFTER_COMMIT_ERASE']
report=os.environ['REPORT']

def crop_page(im):
    w,h=im.size
    # Remove status/action/nav bars.
    return im.crop((int(w*0.05), int(h*0.12), int(w*0.95), int(h*0.88)))

def nonwhite_pixels(im):
    arr=np.asarray(im.convert('RGB'))
    return int(((arr[:,:,0]!=255)|(arr[:,:,1]!=255)|(arr[:,:,2]!=255)).sum())

im_before=crop_page(Image.open(before))
im_pending_draw=crop_page(Image.open(after_pending_draw))
im_pending_erase=crop_page(Image.open(after_pending_erase))
im_commit=crop_page(Image.open(after_commit))
im_commit_erase=crop_page(Image.open(after_commit_erase))

baseline_nonwhite=nonwhite_pixels(im_before)

# Focus on lower-middle region where we draw/erase.
w,h=im_pending_draw.size
roi=(int(w*0.10), int(h*0.55), int(w*0.90), int(h*0.85))
pending_draw_roi=im_pending_draw.crop(roi).convert('RGB')
pending_erase_roi=im_pending_erase.crop(roi).convert('RGB')
commit_roi=im_commit.crop(roi).convert('RGB')
commit_erase_roi=im_commit_erase.crop(roi).convert('RGB')

def diff_stats(a, b):
    diff=ImageChops.difference(a, b)
    arr=np.asarray(diff)
    changed=int(((arr!=0).any(axis=2)).sum())

    # Heuristic: erase should increase brightness on at least some pixels in ROI.
    c1=np.asarray(a).astype(np.int16)
    c2=np.asarray(b).astype(np.int16)
    whitened=int(((c2.sum(axis=2) - c1.sum(axis=2)) > 40).sum())
    return changed, whitened

pending_changed, pending_whitened = diff_stats(pending_draw_roi, pending_erase_roi)
commit_changed, commit_whitened = diff_stats(commit_roi, commit_erase_roi)

ok=True
lines=[
    f"baseline_nonwhite_pixels={baseline_nonwhite}",
    f"pending_roi_changed_pixels={pending_changed}",
    f"pending_roi_whitened_pixels={pending_whitened}",
    f"commit_roi_changed_pixels={commit_changed}",
    f"commit_roi_whitened_pixels={commit_whitened}",
]

if baseline_nonwhite < 5000:
    ok=False
    lines.append("FAIL: baseline looks too blank (PDF may not have rendered)")

if pending_changed < 1200 or pending_whitened < 150:
    ok=False
    lines.append("FAIL: pending-ink erase did not visibly change pixels in ROI")

if commit_changed < 1200 or commit_whitened < 150:
    ok=False
    lines.append("FAIL: committed-ink erase did not visibly change pixels in ROI")

lines.append("PASS" if ok else "FAIL")
with open(report,'w') as f:
    f.write("\n".join(lines) + "\n")
print("\n".join(lines))
sys.exit(0 if ok else 1)
PY

log "Done. Report: $REPORT"
log "Artifacts: $OUTDIR"
