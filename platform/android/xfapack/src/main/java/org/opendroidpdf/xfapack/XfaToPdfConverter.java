package org.opendroidpdf.xfapack;

import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class XfaToPdfConverter {

    private static final String PRODUCER = "OpenDroidPDF XFA Pack";

    private XfaToPdfConverter() {
    }

    static int convert(InputStream in, OutputStream out, int mode, File tempDir) throws IOException {
        return convert(in, out, mode, tempDir, null);
    }

    static int convert(InputStream in,
                       OutputStream out,
                       int mode,
                       File tempDir,
                       String password) throws IOException {
        if (mode != IXfaPackConverter.MODE_CONVERT_TO_ACROFORM
                && mode != IXfaPackConverter.MODE_FLATTEN_TO_PDF) {
            return IXfaPackConverter.RESULT_ERROR;
        }

        // NOTE: This is not a full XFA runtime. We only support the "hybrid/static XFA" case
        // where the PDF contains both:
        //   - an AcroForm field tree (/Fields), and
        //   - an /XFA entry that causes many viewers (including MuPDF) to treat it as XFA-only.
        //
        // In that case, removing /XFA restores compatibility with the embedded AcroForm fields.
        try (PDDocument doc = loadPdf(in, tempDir, password)) {
            // Ensure we don't attempt to re-save the original encryption dictionary without a
            // protection policy; output PDFs should be normal, openable copies.
            try {
                doc.setAllSecurityToBeRemoved(true);
            } catch (Throwable ignore) {
            }

            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            if (form == null) return IXfaPackConverter.RESULT_UNSUPPORTED;

            COSDictionary acro = form.getCOSObject();
            if (acro == null) return IXfaPackConverter.RESULT_UNSUPPORTED;

            if (!acro.containsKey(COSName.XFA)) {
                // Not an XFA form (or already converted).
                return IXfaPackConverter.RESULT_UNSUPPORTED;
            }

            acro.removeItem(COSName.XFA);

            // If there are no AcroForm fields after stripping /XFA, this is likely a dynamic XFA
            // form with no embedded AcroForm fallback.
            if (form.getFields() == null || form.getFields().isEmpty()) {
                return IXfaPackConverter.RESULT_UNSUPPORTED;
            }

            if (mode == IXfaPackConverter.MODE_FLATTEN_TO_PDF) {
                try {
                    form.flatten();
                } catch (Throwable t) {
                    return IXfaPackConverter.RESULT_ERROR;
                }
            }

            PDDocumentInformation info = doc.getDocumentInformation();
            if (info != null) {
                info.setProducer(PRODUCER);
            }

            doc.save(out);
            return IXfaPackConverter.RESULT_OK;
        } catch (InvalidPasswordException ipe) {
            return IXfaPackConverter.RESULT_PASSWORD_REQUIRED;
        }
    }

    private static PDDocument loadPdf(InputStream in, File tempDir, String password) throws IOException {
        try {
            MemoryUsageSetting mem = MemoryUsageSetting.setupMixed(10 * 1024 * 1024);
            if (tempDir != null) {
                mem.setTempDir(tempDir);
            }
            if (password != null) {
                return PDDocument.load(in, password, mem);
            }
            return PDDocument.load(in, mem);
        } catch (NoSuchMethodError nsme) {
            // Defensive: older pdfbox-android shims may not expose all overloads.
            if (password != null) {
                return PDDocument.load(in, password);
            }
            return PDDocument.load(in);
        }
    }
}
