package org.opendroidpdf.officepack;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class WordToPdfConverter {

    private static final String TAG = "WordToPdfConverter";

    private static final int PDF_WIDTH_PT = 612;
    private static final int PDF_HEIGHT_PT = 792;
    private static final int MARGIN_PT = 54;

    private static final float TEXT_SIZE_PT = 12f;
    private static final float LINE_SPACING_MULT = 1.25f;
    private static final float PARAGRAPH_SPACING_MULT = 0.6f;

    private static final byte[] OLE_HEADER = new byte[]{
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };

    private WordToPdfConverter() {
    }

    static int convert(BufferedInputStream in, OutputStream out) throws IOException {
        in.mark(16);
        byte[] header = new byte[8];
        int read = readAtLeast(in, header);
        in.reset();

        if (read >= 2 && header[0] == 'P' && header[1] == 'K') {
            return convertDocxToPdf(in, out);
        }

        if (read == 8 && isOleHeader(header)) {
            Log.i(TAG, "Unsupported legacy .doc (OLE2) input");
            return IOfficePackConverter.RESULT_UNSUPPORTED;
        }

        Log.i(TAG, "Unsupported Word input (unknown magic)");
        return IOfficePackConverter.RESULT_UNSUPPORTED;
    }

    private static int readAtLeast(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n <= 0) break;
            off += n;
        }
        return off;
    }

    private static boolean isOleHeader(byte[] header) {
        for (int i = 0; i < OLE_HEADER.length; i++) {
            if (header[i] != OLE_HEADER[i]) return false;
        }
        return true;
    }

    private static int convertDocxToPdf(InputStream in, OutputStream out) throws IOException {
        PdfDocument pdf = new PdfDocument();
        try {
            PdfTextWriter writer = new PdfTextWriter(pdf);
            boolean foundDocumentXml = false;

            try (ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if ("word/document.xml".equals(entry.getName())) {
                        foundDocumentXml = true;
                        parseDocxDocumentXml(zip, writer);
                        break;
                    }
                    zip.closeEntry();
                }
            } catch (XmlPullParserException xpe) {
                Log.e(TAG, "DOCX XML parse failed", xpe);
                return IOfficePackConverter.RESULT_ERROR;
            } finally {
                // Ensure any started page is closed.
                writer.finish();
            }

            if (!foundDocumentXml) {
                Log.i(TAG, "DOCX missing word/document.xml");
                return IOfficePackConverter.RESULT_UNSUPPORTED;
            }

            if (!writer.didWriteText()) {
                Log.i(TAG, "DOCX contained no extractable text (v1 converter is text-only)");
                return IOfficePackConverter.RESULT_UNSUPPORTED;
            }

            try {
                pdf.writeTo(out);
                return IOfficePackConverter.RESULT_OK;
            } catch (IOException ioe) {
                Log.e(TAG, "PDF write failed", ioe);
                return IOfficePackConverter.RESULT_ERROR;
            }
        } finally {
            pdf.close();
        }
    }

    private static void parseDocxDocumentXml(InputStream xml, PdfTextWriter writer)
            throws IOException, XmlPullParserException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new InputStreamReader(xml, StandardCharsets.UTF_8));

        StringBuilder paragraph = new StringBuilder();
        boolean inText = false;

        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            switch (event) {
                case XmlPullParser.START_TAG: {
                    String name = parser.getName();
                    if (isTag(name, "p")) {
                        paragraph.setLength(0);
                    } else if (isTag(name, "t")) {
                        inText = true;
                    } else if (isTag(name, "tab")) {
                        paragraph.append('\t');
                    } else if (isTag(name, "br") || isTag(name, "cr")) {
                        paragraph.append('\n');
                    }
                    break;
                }
                case XmlPullParser.TEXT: {
                    if (inText) {
                        paragraph.append(parser.getText());
                    }
                    break;
                }
                case XmlPullParser.END_TAG: {
                    String name = parser.getName();
                    if (isTag(name, "t")) {
                        inText = false;
                    } else if (isTag(name, "p")) {
                        writer.appendParagraph(paragraph.toString());
                        paragraph.setLength(0);
                    }
                    break;
                }
            }
        }
    }

    private static boolean isTag(String name, String localName) {
        if (name == null) return false;
        return name.equals(localName) || name.endsWith(":" + localName);
    }

    private static final class PdfTextWriter {
        private final PdfDocument pdf;
        private final Paint paint;
        private final float lineHeight;
        private final float paragraphGap;
        private final int usableWidth;

        private PdfDocument.Page currentPage;
        private Canvas canvas;
        private float x;
        private float y;

        private boolean wroteText;
        private int pageNumber;

        PdfTextWriter(PdfDocument pdf) {
            this.pdf = pdf;

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.BLACK);
            paint.setTextSize(TEXT_SIZE_PT);

            lineHeight = TEXT_SIZE_PT * LINE_SPACING_MULT;
            paragraphGap = lineHeight * PARAGRAPH_SPACING_MULT;

            usableWidth = PDF_WIDTH_PT - (MARGIN_PT * 2);

            startPage();
        }

        boolean didWriteText() {
            return wroteText;
        }

        void appendParagraph(String raw) {
            if (raw == null) return;

            String paragraph = raw.replace('\t', ' ').trim();
            if (paragraph.isEmpty()) {
                y += paragraphGap;
                return;
            }

            for (String part : paragraph.split("\n")) {
                drawWrappedLineBlock(part.trim());
                y += lineHeight;
            }
            y += paragraphGap;
        }

        private void drawWrappedLineBlock(String text) {
            if (text.isEmpty()) return;
            String[] words = text.split("\\s+");

            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;

                String candidate;
                if (line.length() == 0) {
                    candidate = word;
                } else {
                    candidate = line + " " + word;
                }

                if (line.length() == 0 || paint.measureText(candidate) <= usableWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }

                ensureSpace(lineHeight);
                canvas.drawText(line.toString(), x, y, paint);
                wroteText = true;
                y += lineHeight;

                line.setLength(0);
                line.append(word);
            }

            if (line.length() > 0) {
                ensureSpace(lineHeight);
                canvas.drawText(line.toString(), x, y, paint);
                wroteText = true;
            }
        }

        private void ensureSpace(float requiredHeight) {
            if (y + requiredHeight <= PDF_HEIGHT_PT - MARGIN_PT) return;
            startPage();
        }

        private void startPage() {
            finish();
            pageNumber++;
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PDF_WIDTH_PT, PDF_HEIGHT_PT, pageNumber).create();
            currentPage = pdf.startPage(info);
            canvas = currentPage.getCanvas();
            x = MARGIN_PT;
            y = MARGIN_PT + TEXT_SIZE_PT;
        }

        void finish() {
            if (currentPage != null) {
                pdf.finishPage(currentPage);
                currentPage = null;
                canvas = null;
            }
        }
    }
}
