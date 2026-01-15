# Third-Party Notices: QPDF & PDFBox

## QPDF
- License: Apache-2.0 (see `thirdparty/qpdf/COPYING` in the upstream source).
- Binary placement: `platform/android/libs/<abi>/libqpdf.so` and `libqpdfjni.so`.
- Source provenance: built from qpdf 11.x with libjpeg-turbo 3.0.0; build scripts live in `scripts/qpdf_android_build.sh` and `scripts/qpdf_jni_build.sh`.
- Obligations: include Apache-2.0 notice; no copyleft requirements. Keep VERSION string visible via `QpdfNative.version()`.

## PDFBox (optional module)
- License: Apache-2.0 (upstream `LICENSE` in pdfbox project).
- Packaging intent: dynamic/on-demand module or AAR (not in base APK) for form fill/flatten and rich metadata edits.
- Dependencies: pdfbox-android flavor brings font resources; shrink with R8/ProGuard and resource excludes when possible.
- Obligations: include Apache-2.0 notice if/when bundled; attribute fonts if retained.

## Excluded/Non-bundled options
- OpenPDF (LGPL/MPL) and MuPDF (AGPL/commercial) are **not** shipped in the base APK; require separate licensing if ever enabled.
- pdftk (GPL/commercial, GCJ deps) is excluded from mobile distribution.

## Distribution Checklist
- Ensure `NOTICE` (or Play store notices screen) cites QPDF and PDFBox when present.
- Verify ABI splits include native `libqpdf*.so`; strip symbols for release.
- For F-Droid/side-load builds without dynamic features, gate PDFBox usage behind a runtime check and fail gracefully if the module is absent.
