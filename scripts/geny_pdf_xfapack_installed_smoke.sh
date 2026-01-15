#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for XFA Pack installed behavior:
# - Install main app + XFA Pack (debug, same signing key)
# - Open a tiny PDF that includes an AcroForm /XFA entry
# - Assert the XFA banner appears and "Learn more" shows XFA Pack actions
#
# Usage:
#   DEVICE=localhost:<port> \
#   APK=/path/to/OpenDroidPDF-debug.apk \
#   APK_XFAPACK=/path/to/xfapack-debug.apk \
#   ./scripts/geny_pdf_xfapack_installed_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
APK_XFAPACK=${APK_XFAPACK:-/mnt/subtitled/opendroidpdf-android-build/xfapack/outputs/apk/debug/xfapack-debug.apk}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_xfapack_installed_smoke.pdf}

PKG=org.opendroidpdf
PKG_XFAPACK=org.opendroidpdf.xfapack
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_generate_xfa_pdf() {
  local out="$1"
  python3 - "$out" <<'PY'
import io
import sys

out = sys.argv[1]

objs = []

def add(body: bytes) -> None:
    objs.append(body)

def stream_obj(data: bytes) -> bytes:
    return b"<< /Length %d >>\nstream\n%s\nendstream\n" % (len(data), data)

# 1) Catalog w/ AcroForm /XFA
add(b"<< /Type /Catalog /Pages 2 0 R /AcroForm << /XFA 5 0 R >> >>\n")
# 2) Pages
add(b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n")
# 3) Page
add(b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 6 0 R >> >> >>\n")
# 4) Contents (simple text)
add(stream_obj(b"BT /F1 24 Tf 72 720 Td (XFA smoke) Tj ET"))
# 5) XFA stream (dummy payload; still a valid /XFA reference)
add(stream_obj(b"<xfa>dummy</xfa>"))
# 6) Font
add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\n")

pdf = io.BytesIO()
pdf.write(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")

offsets = [0]
for i, body in enumerate(objs, start=1):
    offsets.append(pdf.tell())
    pdf.write(f"{i} 0 obj\n".encode("ascii"))
    pdf.write(body)
    if not body.endswith(b"\n"):
        pdf.write(b"\n")
    pdf.write(b"endobj\n")

xref_pos = pdf.tell()
pdf.write(b"xref\n")
pdf.write(f"0 {len(objs)+1}\n".encode("ascii"))
pdf.write(b"0000000000 65535 f \n")
for off in offsets[1:]:
    pdf.write(f"{off:010d} 00000 n \n".encode("ascii"))

pdf.write(b"trailer\n")
pdf.write(f"<< /Size {len(objs)+1} /Root 1 0 R >>\n".encode("ascii"))
pdf.write(b"startxref\n")
pdf.write(f"{xref_pos}\n".encode("ascii"))
pdf.write(b"%%EOF\n")

with open(out, "wb") as f:
    f.write(pdf.getvalue())
PY
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

  uia_assert_in_document_view
}

echo "[1/4] Install debug APKs (main + XFA Pack)"
adb -s "$DEVICE" uninstall "$PKG_XFAPACK" >/dev/null 2>&1 || true
adb -s "$DEVICE" uninstall "$PKG" >/dev/null 2>&1 || true
adb -s "$DEVICE" install -r "$APK" >/dev/null
adb -s "$DEVICE" install -r "$APK_XFAPACK" >/dev/null

echo "[2/4] Push XFA-ish PDF to Downloads"
tmp_pdf="$(mktemp -t odp_xfa_smoke_XXXXXX).pdf"
_generate_xfa_pdf "$tmp_pdf"
adb -s "$DEVICE" push "$tmp_pdf" "$PDF_REMOTE_PATH" >/dev/null
rm -f -- "$tmp_pdf" || true
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
uia_has_text_contains "XML Forms Architecture" || {
  echo "FAIL: did not find XFA explainer dialog text" >&2
  exit 1
}
uia_has_text_contains "Convert to form" || {
  echo "FAIL: did not find 'Convert to form (XFA Pack)' action in XFA explainer dialog" >&2
  exit 1
}
uia_has_text_contains "Flatten to PDF" || {
  echo "FAIL: did not find 'Flatten to PDF (XFA Pack)' action in XFA explainer dialog" >&2
  exit 1
}

echo "[4/4] OK"
echo "OK: XFA Pack actions are visible when the pack is installed"

