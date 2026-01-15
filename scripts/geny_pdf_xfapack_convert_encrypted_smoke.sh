#!/usr/bin/env bash
set -euo pipefail

# Wrapper smoke that runs the XFA Pack convert flow against an encrypted hybrid-XFA fixture.
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_pdf_xfapack_convert_encrypted_smoke.sh
#
# Override inputs as needed:
#   PDF_LOCAL=... PASSWORD=... APK=... APK_XFAPACK=... DEVICE=...

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

export PDF_LOCAL="${PDF_LOCAL:-test_assets/odp_xfa_hybrid_encrypted.pdf}"
export PASSWORD="${PASSWORD:-test}"
export PDF_REMOTE_PATH="${PDF_REMOTE_PATH:-/sdcard/Download/odp_xfapack_convert_encrypted_smoke.pdf}"

exec "${SCRIPT_DIR}/geny_pdf_xfapack_convert_smoke.sh"

