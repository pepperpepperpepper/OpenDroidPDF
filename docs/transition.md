# Migration & Compatibility – OpenDroidPDF

## Package/namespace
- Current applicationId: `org.opendroidpdf` (upgraded from `com.cgogolin.penandpdf`). The self-hosted F-Droid repo serves only the new package; installs signed with the old key must be uninstalled before installing OpenDroidPDF builds.

## Preferences
- Legacy shared preferences (`PenAndPDF`) are copied into the new namespace on first launch; the helper then clears the old file. Keys: ink thickness/color, toolbar choices, recent files. Users keep their settings; no action required.

## Notes / storage
- Legacy note dirs (`PenAndPDFNotes`, internal `notes/`) are migrated and removed if empty. Existing note files are retained in the new `OpenDroidPDFNotes` path.

## Data compatibility
- Document edits/annotations remain compatible with upstream MuPDF; no file format change.
- Ink/text annotations are now marked dirty in native code and persisted via `pdf_save_document`, so exports include recent edits without reopening.
- Sidecar annotations (EPUB + read-only PDFs) are stored in an internal SQLite database (`sidecar_annotations.db`) keyed by a stable document id (see below).
- Word documents (`.doc/.docx`) are supported as **import-as-PDF**:
  - the original file is never edited in-place,
  - a derived PDF is cached, and the user’s sharing path is “Export annotated PDF”.

## Document identity (sidecar/recents/viewport)
- Older builds keyed some persisted state by the URI string (e.g., `content://…` or `file://…`). Newer builds resolve a canonical `docId` early when opening a document:
  - Preferred: content-derived `sha256:*` id (via `DocumentIdentityResolver`).
  - Fallback: legacy `uri.toString()` only when hashing fails.
- Migration behavior:
  - On first open with a canonical `sha256:*` id, sidecar rows are migrated forward from legacy ids when possible.
  - Viewport/recents restore will fall back to legacy ids once and then write-forward to the canonical `docId` so reopening after a rename keeps state.

## User guidance
- If upgrading from Pen&PDF: uninstall the old package (different signature/packageId) and install OpenDroidPDF from `https://fdroid.uh-oh.wtf/repo`.
- If preferences appear reset, verify storage permission is granted; migration only runs when the app can read the legacy pref file.
- For EPUB: annotations are saved in the app (sidecar). Use “Export annotated PDF” for a portable copy.

## Developer notes
- Controllers/activities must route all MuPDF calls through `MuPdfRepository/MuPdfController`; new JNI entry points belong in the split files under `platform/android/jni/`.
- Keep legacy constants only for migration shims; new features should avoid `PenAndPDF*` identifiers to prevent regressions.
