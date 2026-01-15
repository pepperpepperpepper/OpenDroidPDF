#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for XFA Pack "Flatten to PDF" flow:
# - Install main app + XFA Pack (debug, same signing key)
# - Generate a tiny "hybrid" PDF (or provide `PDF_LOCAL`): AcroForm field tree + an /XFA entry
# - Open it via DocumentsUI (content:// URI)
# - Trigger "Flatten to PDF (XFA Pack)"
# - Assert a flattened copy opens (title contains "xfa_flatten_") and the XFA banner is gone
#
# Usage:
#   DEVICE=localhost:<port> \
#   APK=/path/to/OpenDroidPDF-debug.apk \
#   APK_XFAPACK=/path/to/xfapack-debug.apk \
#   PDF_LOCAL=test_assets/thirdparty/pdfium_xfa/static_password_field_rotate.pdf \
#   ./scripts/geny_pdf_xfapack_flatten_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
APK_XFAPACK=${APK_XFAPACK:-/mnt/subtitled/opendroidpdf-android-build/xfapack/outputs/apk/debug/xfapack-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_xfapack_flatten_smoke.pdf}
PASSWORD=${PASSWORD:-}

PKG=org.opendroidpdf
PKG_XFAPACK=org.opendroidpdf.xfapack
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_generate_hybrid_xfa_pdf() {
  local out="$1"
  python3 - "$out" <<'PY'
import io
import sys

out = sys.argv[1]

objs = []

def add(body: bytes) -> int:
    objs.append(body)
    return len(objs)

def stream_obj(data: bytes) -> bytes:
    return b"<< /Length %d >>\nstream\n%s\nendstream\n" % (len(data), data)

font_obj = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\n")
contents_obj = add(stream_obj(b"BT /F1 24 Tf 72 720 Td (Name:) Tj ET\n"))
xfa_obj = add(stream_obj(b"<xfa>dummy</xfa>"))
field_obj = add(b"<< /FT /Tx /T (name) /Kids [8 0 R] >>\n")
pages_obj = add(b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n")
page_obj = add(b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
               b"/Resources << /Font << /F1 7 0 R >> >> /Annots [8 0 R] >>\n")
widget_obj = add(b"<< /Type /Annot /Subtype /Widget /Rect [72 650 540 685] /P 3 0 R "
                 b"/Parent 5 0 R /F 4 /DA (/F1 12 Tf 0 g) >>\n")
catalog_obj = add(b"<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [5 0 R] /XFA 6 0 R "
                  b"/DA (/F1 12 Tf 0 g) /DR << /Font << /F1 7 0 R >> >> >> >>\n")

fixed = [None] * 8
fixed[0] = catalog_obj
fixed[1] = pages_obj
fixed[2] = page_obj
fixed[3] = contents_obj
fixed[4] = field_obj
fixed[5] = xfa_obj
fixed[6] = font_obj
fixed[7] = widget_obj

bodies = list(objs)

pdf = io.BytesIO()
pdf.write(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")

offsets = [0] * (len(fixed) + 1)
for obj_num, idx in enumerate(fixed, start=1):
    body = bodies[idx - 1]
    offsets[obj_num] = pdf.tell()
    pdf.write(f"{obj_num} 0 obj\n".encode("ascii"))
    pdf.write(body)
    if not body.endswith(b"\n"):
        pdf.write(b"\n")
    pdf.write(b"endobj\n")

xref_pos = pdf.tell()
pdf.write(b"xref\n")
pdf.write(f"0 {len(fixed)+1}\n".encode("ascii"))
pdf.write(b"0000000000 65535 f \n")
for obj_num in range(1, len(fixed) + 1):
    pdf.write(f"{offsets[obj_num]:010d} 00000 n \n".encode("ascii"))

pdf.write(b"trailer\n")
pdf.write(f"<< /Size {len(fixed)+1} /Root 1 0 R >>\n".encode("ascii"))
pdf.write(b"startxref\n")
pdf.write(f"{xref_pos}\n".encode("ascii"))
pdf.write(b"%%EOF\n")

with open(out, "wb") as f:
    f.write(pdf.getvalue())
PY
}

_maybe_enter_pdf_password() {
  if ! uia_has_res_id "android:id/alertTitle" && ! uia_has_res_id "org.opendroidpdf:id/alertTitle"; then
    return 0
  fi
  if [[ -z "$PASSWORD" ]]; then
    echo "FAIL: document is password-protected but PASSWORD env var is unset" >&2
    return 1
  fi

  uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || true
  adb -s "$DEVICE" shell input text "$PASSWORD"
  sleep 0.3
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
    echo "FAIL: could not press OK on password dialog" >&2
    return 1
  }

  for _ in $(seq 1 32); do
    if ! uia_has_res_id "android:id/alertTitle" && ! uia_has_res_id "org.opendroidpdf:id/alertTitle"; then
      break
    fi
    sleep 0.25
  done
  if uia_has_res_id "android:id/alertTitle" || uia_has_res_id "org.opendroidpdf:id/alertTitle"; then
    echo "FAIL: password dialog did not dismiss (wrong password?)" >&2
    return 1
  fi
  return 0
}

_open_pdf_via_documentsui() {
  local fname="$1"
  adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
  adb -s "$DEVICE" logcat -c >/dev/null || true
  adb -s "$DEVICE" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT" >/dev/null
  sleep 1.2

  uia_tap_any_res_id "org.opendroidpdf:id/entry_screen_open_document_card_view" || {
    echo "FAIL: could not tap entry-screen open-document card" >&2
    exit 1
  }
  sleep 1.5

  uia_tap_docsui_roots_drawer || {
    echo "FAIL: could not open DocumentsUI roots drawer" >&2
    exit 1
  }
  sleep 0.7
  uia_tap_text_contains "Downloads" || {
    echo "FAIL: could not switch DocumentsUI to Downloads root" >&2
    exit 1
  }
  sleep 0.9

  uia_tap_any_res_id "com.android.documentsui:id/option_menu_search" || uia_tap_desc "Search" || {
    echo "FAIL: could not open DocumentsUI search" >&2
    exit 1
  }
  sleep 0.6
  adb -s "$DEVICE" shell input text "$fname"
  sleep 1.2
  uia_tap_text_contains "$fname" || {
    echo "FAIL: could not select $fname in DocumentsUI search results" >&2
    adb -s "$DEVICE" logcat -d | tail -n 120 >&2
    exit 1
  }

  for _ in $(seq 1 24); do
    if uia_has_res_id "org.opendroidpdf:id/document_host_container"; then
      break
    fi
    if uia_has_res_id "android:id/alertTitle" || uia_has_res_id "org.opendroidpdf:id/alertTitle"; then
      _maybe_enter_pdf_password || exit 1
      break
    fi
    sleep 0.25
  done

  uia_assert_in_document_view
}

echo "[1/4] Install debug APKs (main + XFA Pack)"
adb -s "$DEVICE" uninstall "$PKG_XFAPACK" >/dev/null 2>&1 || true
adb -s "$DEVICE" uninstall "$PKG" >/dev/null 2>&1 || true
adb -s "$DEVICE" install -r "$APK" >/dev/null
adb -s "$DEVICE" install -r "$APK_XFAPACK" >/dev/null

echo "[2/4] Push XFA PDF to Downloads"
if [[ -n "$PDF_LOCAL" ]]; then
  adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null
else
  tmp_pdf="$(mktemp -t odp_xfa_hybrid_smoke_XXXXXX).pdf"
  _generate_hybrid_xfa_pdf "$tmp_pdf"
  adb -s "$DEVICE" push "$tmp_pdf" "$PDF_REMOTE_PATH" >/dev/null
  rm -f -- "$tmp_pdf" || true
fi
fname="$(basename "$PDF_REMOTE_PATH")"

echo "[3/4] Open PDF via DocumentsUI (expect XFA banner)"
_open_pdf_via_documentsui "$fname"

want_banner="This PDF uses XFA forms"
for _ in $(seq 1 24); do
  if uia_has_text_contains "$want_banner"; then
    break
  fi
  sleep 0.35
done
uia_has_text_contains "$want_banner" || {
  echo "FAIL: did not find XFA unsupported banner text after open" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2 || true
  exit 1
}

uia_tap_text_contains "Learn more" || {
  echo "FAIL: did not find 'Learn more' action for XFA banner" >&2
  exit 1
}
sleep 0.8
uia_has_text_contains "Flatten to PDF" || {
  echo "FAIL: did not find 'Flatten to PDF (XFA Pack)' action in XFA explainer dialog" >&2
  exit 1
}

uia_tap_text_contains "Flatten to PDF" || {
  echo "FAIL: could not tap 'Flatten to PDF' action" >&2
  exit 1
}

echo "[4/4] Wait for flattened copy to open"
for _ in $(seq 1 40); do
  if uia_has_text_contains "xfa_flatten_"; then
    break
  fi
  sleep 0.4
done
uia_has_text_contains "xfa_flatten_" || {
  echo "FAIL: did not observe flattened file title (xfa_flatten_*) after conversion" >&2
  adb -s "$DEVICE" logcat -d | tail -n 220 >&2 || true
  exit 1
}

if uia_has_text_contains "$want_banner"; then
  echo "FAIL: XFA banner still present after flatten" >&2
  exit 1
fi

echo "OK: XFA Pack flatten opened a flattened (non-XFA) PDF"
