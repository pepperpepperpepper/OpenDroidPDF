#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

OUT_ZIP="${1:-${OUT_ZIP:-${ROOT_DIR}/build/devicefarm/ui_gallery_test_package.zip}}"

if ! command -v zip >/dev/null 2>&1; then
  echo "FAIL: zip not found in PATH" >&2
  exit 2
fi

mkdir -p "$(dirname "$OUT_ZIP")"

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/odp_df_ui_gallery_pkg_XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

mkdir -p "$tmp_dir/scripts" "$tmp_dir/test_assets"

cp "$ROOT_DIR/devicefarm/ui_gallery/run_ui_gallery.sh" "$tmp_dir/run_ui_gallery.sh"
chmod +x "$tmp_dir/run_ui_gallery.sh"

cp "$ROOT_DIR/scripts/geny_ui_gallery_smoke.sh" "$tmp_dir/scripts/geny_ui_gallery_smoke.sh"
cp "$ROOT_DIR/scripts/geny_uia.sh" "$tmp_dir/scripts/geny_uia.sh"
chmod +x "$tmp_dir/scripts/geny_ui_gallery_smoke.sh" "$tmp_dir/scripts/geny_uia.sh"

cp "$ROOT_DIR/test_pdf.pdf" "$tmp_dir/test_pdf.pdf"
cp "$ROOT_DIR/test_assets/pdf_with_text.pdf" "$tmp_dir/test_assets/pdf_with_text.pdf"
cp "$ROOT_DIR/test_assets/pdf_form_nav.pdf" "$tmp_dir/test_assets/pdf_form_nav.pdf"
cp "$ROOT_DIR/test_assets/hello.epub" "$tmp_dir/test_assets/hello.epub"
cp "$ROOT_DIR/test_assets/word_with_text.docx" "$tmp_dir/test_assets/word_with_text.docx"

gitsha="$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || echo nogit)"
date_utc="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
cat >"$tmp_dir/BUILD_INFO.txt" <<EOF
OpenDroidPDF Device Farm UI Gallery package

Repo: $(basename "$ROOT_DIR")
Git:  $gitsha
UTC:  $date_utc
EOF

(
  cd "$tmp_dir"
  rm -f "$OUT_ZIP"
  zip -r "$OUT_ZIP" . >/dev/null
)

echo "OK: wrote $OUT_ZIP" >&2

