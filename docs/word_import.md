# Word Import Strategy (`.doc` / `.docx`) — Cross‑platform plan

OpenDroidPDF does **not** edit Word documents in-place. Instead, Word files are opened via **Import as PDF**:
we convert the Word document to a derived PDF, then use the existing PDF pipeline (render/search/annotate/export).

This design keeps `pp_core` focused on document behavior for PDFs while letting each platform choose the most
appropriate conversion engine.

## Invariants (must hold on every platform)

- The user-visible workflow is **Import as PDF**, not “edit Word”.
- The derived PDF is a **cache artifact** used for viewing/annotation.
- Sidecar state (notes/highlights/ink) and “recents” must key off a **stable Word identity** (`docId`),
  not the derived PDF’s cache filename.
- The reader session should carry:
  - source Word URI/path,
  - source Word `docId` (`sha256:*` style stable identifier),
  - derived PDF URI/path (cache artifact),
  - origin flag “Word” (so Save/Export UX is correct).

## Engine selection by platform

### Android

- Engine: **PDFBox for Android** (`com.tom-roush:pdfbox-android`) is used inside the optional companion APK
  “**Office Pack**”.
- Why: LibreOffice/LibreOfficeKit is too large/complex for the main APK and problematic for F‑Droid-first
  distribution; using a companion APK keeps the main app slim.
- Integration pattern:
  - Main app detects Office Pack and securely binds to it (signature match).
  - Conversion I/O uses `ParcelFileDescriptor` (no broad storage permissions).
  - Output is a deterministic cached PDF under the app’s cache directory.

Entry points:
- Main app pipeline interface: `platform/android/src/org/opendroidpdf/app/document/WordImportPipeline.java`
- Office Pack-backed implementation: `platform/android/src/org/opendroidpdf/app/document/OfficePackWordImportPipeline.java`
- Office Pack APK module: `platform/android/officepack/` (declares `pdfbox-android` dependency)

### Linux / Desktop (x11/gl)

- Engine: **LibreOffice headless** (`soffice`) converts `.doc/.docx` to PDF.
- Why: LibreOffice is commonly available on desktop distros and offers broad format coverage without embedding
  a second PDF stack into OpenDroidPDF.
- Behavior:
  - Derived PDF is written under `$XDG_CACHE_HOME/opendroidpdf/word/`.
  - The viewer opens the derived PDF; export uses the original document name semantics (`*-annotated.pdf`).

Entry points:
- Converter wrapper: `platform/gl/odp_word_import.c`
- Public API: `platform/gl/odp_word_import.h`
- Desktop open hook: `platform/gl/gl-main.c` (detects Word docs and converts before opening)

## Testing / Smokes

- Android (Genymotion): see `scripts/geny_docx_*.sh`
- Linux: `scripts/linux_docx_import_smoke.sh`

## Related: “Fill out PDFs”

Word import is separate from filling PDFs. For AcroForm widgets and PDF annotations we prefer MuPDF’s built-in
support across platforms and treat third-party libraries as optional add-ons (e.g., flattening utilities).

