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

## User guidance
- If upgrading from Pen&PDF: uninstall the old package (different signature/packageId) and install OpenDroidPDF from `https://fdroid.uh-oh.wtf/repo`.
- If preferences appear reset, verify storage permission is granted; migration only runs when the app can read the legacy pref file.

## Developer notes
- Controllers/activities must route all MuPDF calls through `MuPdfRepository/MuPdfController`; new JNI entry points belong in the split files under `platform/android/jni/`.
- Keep legacy constants only for migration shims; new features should avoid `PenAndPDF*` identifiers to prevent regressions.
