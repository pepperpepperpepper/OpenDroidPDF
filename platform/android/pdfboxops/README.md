# pdfboxops (optional)

Small Android library that wraps pdfbox-android for form flattening and simple metadata edits.

- Not bundled into the base APK by default; intended as a dynamic feature or optional AAR.
- Requires `PDFBoxResourceLoader.init(context)` (handled internally on first call).
- APIs:
  - `flattenForm(context, inputUri, outputUri)` – flattens AcroForm fields, returns page count + presence flag.
  - `applyMetadata(context, inputUri, outputUri, MetadataRequest)` – copies PDF while setting title/author/subject/keywords.

To measure size impact, add a temporary dependency in the app module:
```gradle
debugImplementation project(':pdfboxops')
```
then run `./gradlew :app:assembleDebug` and record the APK delta.
