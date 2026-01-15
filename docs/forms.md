# PDF forms: AcroForm vs XFA

OpenDroidPDF supports filling **standard AcroForm** fields (what most PDF editors/viewers call “PDF forms”).

## AcroForm (supported)

AcroForm forms store fields as PDF objects (widgets) and are broadly supported across viewers.

OpenDroidPDF supports common AcroForm field types:
- Text fields
- Checkboxes
- Radio buttons
- Choice fields (combo/list, including editable + multi-select where present)

## XFA (not supported)

XFA (XML Forms Architecture) is an Adobe-specific form technology where the form UI/logic is defined in embedded XML.
Many XFA PDFs are “dynamic” (layout can change at runtime), which requires an XFA runtime/renderer.

OpenDroidPDF **does not** include an XFA runtime, so XFA forms can’t be filled inside the app.

### What to do if your PDF is XFA

When OpenDroidPDF detects XFA, it shows an in-app warning banner with actions to help you complete the workflow:
- **Install OpenDroidPDF XFA Pack** to convert/flatten the file (hybrid/static XFA only), or
- **Open in another app** that supports XFA, or
- **Share** the original PDF to a converter workflow (XFA → AcroForm), or
- Fill it elsewhere and **print/export to PDF** (flatten), then open the resulting PDF in OpenDroidPDF.

## Build variants (XFA)

- **F-Droid builds (main app)**: no built-in XFA runtime (MuPDF backend; detection + guidance only). Install **OpenDroidPDF XFA Pack** to convert *hybrid/static* XFA PDFs into AcroForm or flattened PDFs.
- Optional future path (not planned for F-Droid): an SDK-backed variant could add XFA support as a separate distribution/plugin, subject to licensing and distribution constraints.
