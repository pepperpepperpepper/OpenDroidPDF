package org.opendroidpdf.officepack;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.Xml;

import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

    static int convert(BufferedInputStream in, OutputStream out, File tempDir) throws IOException {
        in.mark(16);
        byte[] header = new byte[8];
        int read = readAtLeast(in, header);
        in.reset();

        if (read >= 2 && header[0] == 'P' && header[1] == 'K') {
            return convertDocxToPdf(in, out, tempDir);
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

    private static int convertDocxToPdf(InputStream in, OutputStream out, File tempDir) throws IOException {
        File tmpDocx = File.createTempFile("odp_docx_", ".docx", tempDir);
        try {
            try (FileOutputStream fout = new FileOutputStream(tmpDocx)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    fout.write(buf, 0, n);
                }
            }

            try (ZipFile zip = new ZipFile(tmpDocx);
                 PDDocument pdf = new PDDocument()) {

                ZipEntry documentXmlEntry = zip.getEntry("word/document.xml");
                if (documentXmlEntry == null) {
                    Log.i(TAG, "DOCX missing word/document.xml");
                    return IOfficePackConverter.RESULT_UNSUPPORTED;
                }

                Map<String, String> imageRels;
                try {
                    imageRels = parseDocxImageRelationships(zip);
                } catch (XmlPullParserException xpe) {
                    Log.e(TAG, "DOCX relationship parse failed", xpe);
                    return IOfficePackConverter.RESULT_ERROR;
                }

                PdfFlowWriter writer;
                try {
                    writer = new PdfFlowWriter(pdf);
                } catch (IOException ioe) {
                    Log.e(TAG, "PDF writer init failed", ioe);
                    return IOfficePackConverter.RESULT_ERROR;
                }

                try (InputStream xml = zip.getInputStream(documentXmlEntry)) {
                    parseDocxDocumentXml(xml, zip, imageRels, writer);
                } catch (XmlPullParserException xpe) {
                    Log.e(TAG, "DOCX XML parse failed", xpe);
                    return IOfficePackConverter.RESULT_ERROR;
                } finally {
                    writer.finish();
                }

                if (!writer.didWriteContent()) {
                    Log.i(TAG, "DOCX contained no extractable content");
                    return IOfficePackConverter.RESULT_UNSUPPORTED;
                }

                try {
                    pdf.save(out);
                    return IOfficePackConverter.RESULT_OK;
                } catch (IOException ioe) {
                    Log.e(TAG, "PDF write failed", ioe);
                    return IOfficePackConverter.RESULT_ERROR;
                }
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmpDocx.delete();
        }
    }

    private static Map<String, String> parseDocxImageRelationships(ZipFile zip)
            throws IOException, XmlPullParserException {
        Map<String, String> rels = new HashMap<>();
        ZipEntry entry = zip.getEntry("word/_rels/document.xml.rels");
        if (entry == null) return rels;

        try (InputStream xml = zip.getInputStream(entry)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new InputStreamReader(xml, StandardCharsets.UTF_8));
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event != XmlPullParser.START_TAG) continue;
                String name = parser.getName();
                if (!isTag(name, "Relationship")) continue;

                String id = getAttr(parser, "Id");
                String type = getAttr(parser, "Type");
                String target = getAttr(parser, "Target");
                if (id == null || type == null || target == null) continue;
                if (!type.endsWith("/image")) continue;

                String resolved;
                if (target.startsWith("/")) {
                    resolved = target.substring(1);
                } else if (target.startsWith("word/")) {
                    resolved = target;
                } else {
                    resolved = "word/" + target;
                }
                rels.put(id, resolved);
            }
        }
        return rels;
    }

    private static void parseDocxDocumentXml(InputStream xml, ZipFile zip, Map<String, String> imageRels, PdfFlowWriter writer)
            throws IOException, XmlPullParserException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new InputStreamReader(xml, StandardCharsets.UTF_8));

        StringBuilder paragraph = new StringBuilder();
        List<String> paragraphImages = new ArrayList<>();
        boolean inText = false;

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            switch (event) {
                case XmlPullParser.START_TAG: {
                    String name = parser.getName();
                    if (isTag(name, "tbl")) {
                        writer.appendTable(parseDocxTable(parser));
                        // parseDocxTable consumes through the </w:tbl>; advance to next event.
                        event = parser.next();
                        continue;
                    }

                    if (isTag(name, "p")) {
                        paragraph.setLength(0);
                        paragraphImages.clear();
                    } else if (isTag(name, "t")) {
                        inText = true;
                    } else if (isTag(name, "tab")) {
                        paragraph.append('\t');
                    } else if (isTag(name, "br") || isTag(name, "cr")) {
                        paragraph.append('\n');
                    } else if (isTag(name, "blip")) {
                        String rid = getEmbedAttr(parser);
                        if (rid != null && !rid.isEmpty()) {
                            paragraphImages.add(rid);
                        }
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
                        for (String rid : paragraphImages) {
                            writer.appendImageFromRelationship(zip, imageRels, rid);
                        }
                        paragraph.setLength(0);
                        paragraphImages.clear();
                    }
                    break;
                }
            }

            event = parser.next();
        }
    }

    private static List<List<String>> parseDocxTable(XmlPullParser parser) throws IOException, XmlPullParserException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = null;
        StringBuilder cellText = new StringBuilder();
        boolean inText = false;

        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if (isTag(name, "tr")) {
                    row = new ArrayList<>();
                } else if (isTag(name, "tc")) {
                    cellText.setLength(0);
                } else if (isTag(name, "t")) {
                    inText = true;
                } else if (isTag(name, "tab")) {
                    cellText.append('\t');
                } else if (isTag(name, "br") || isTag(name, "cr")) {
                    cellText.append('\n');
                }
            } else if (event == XmlPullParser.TEXT) {
                if (inText) cellText.append(parser.getText());
            } else if (event == XmlPullParser.END_TAG) {
                String name = parser.getName();
                if (isTag(name, "t")) {
                    inText = false;
                } else if (isTag(name, "tc")) {
                    if (row != null) row.add(cellText.toString());
                    cellText.setLength(0);
                } else if (isTag(name, "tr")) {
                    if (row != null) rows.add(row);
                    row = null;
                } else if (isTag(name, "tbl")) {
                    break;
                }
            }
        }

        return rows;
    }

    private static String getAttr(XmlPullParser parser, String localName) {
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String name = parser.getAttributeName(i);
            if (name == null) continue;
            if (name.equals(localName) || name.endsWith(":" + localName)) {
                return parser.getAttributeValue(i);
            }
        }
        return null;
    }

    private static String getEmbedAttr(XmlPullParser parser) {
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String name = parser.getAttributeName(i);
            if (name == null) continue;
            if (name.equals("embed") || name.endsWith(":embed")) {
                return parser.getAttributeValue(i);
            }
        }
        return null;
    }

    private static boolean isTag(String name, String localName) {
        if (name == null) return false;
        return name.equals(localName) || name.endsWith(":" + localName);
    }

    private static final class PdfFlowWriter {
        private static final PDFont FONT = PDType1Font.HELVETICA;

        private final PDDocument pdf;
        private final float lineHeight;
        private final float paragraphGap;
        private final float usableWidth;

        private PDPage currentPage;
        private PDPageContentStream contentStream;
        private float x;
        private float yTop;

        private boolean wroteContent;

        PdfFlowWriter(PDDocument pdf) throws IOException {
            this.pdf = pdf;

            lineHeight = TEXT_SIZE_PT * LINE_SPACING_MULT;
            paragraphGap = lineHeight * PARAGRAPH_SPACING_MULT;

            usableWidth = PDF_WIDTH_PT - (MARGIN_PT * 2f);

            startPage();
        }

        boolean didWriteContent() {
            return wroteContent;
        }

        void appendParagraph(String raw) throws IOException {
            if (raw == null) return;

            String paragraph = raw.replace('\t', ' ').trim();
            if (paragraph.isEmpty()) {
                yTop -= paragraphGap;
                return;
            }

            for (String part : paragraph.split("\n")) {
                drawWrappedLineBlock(part.trim());
                yTop -= lineHeight;
            }
            yTop -= paragraphGap;
        }

        private void drawWrappedLineBlock(String text) throws IOException {
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

                if (line.length() == 0 || measureText(candidate) <= usableWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }

                drawLine(line.toString());
                yTop -= lineHeight;

                line.setLength(0);
                line.append(word);
            }

            if (line.length() > 0) {
                drawLine(line.toString());
            }
        }

        private float measureText(String text) throws IOException {
            return (FONT.getStringWidth(text) / 1000f) * TEXT_SIZE_PT;
        }

        private void drawLine(String text) throws IOException {
            ensureSpace(lineHeight);
            contentStream.beginText();
            contentStream.setFont(FONT, TEXT_SIZE_PT);
            // yTop tracks the top-of-line box; PDF text uses baseline coordinates.
            contentStream.newLineAtOffset(x, yTop - TEXT_SIZE_PT);
            contentStream.showText(text);
            contentStream.endText();
            wroteContent = true;
        }

        void appendTable(List<List<String>> rows) throws IOException {
            if (rows == null || rows.isEmpty()) return;

            int cols = 0;
            for (List<String> row : rows) {
                if (row != null) cols = Math.max(cols, row.size());
            }
            if (cols <= 0) return;

            float tableWidth = usableWidth;
            float colWidth = tableWidth / cols;
            float pad = 2.5f;

            for (List<String> row : rows) {
                if (row == null) continue;

                List<List<String>> wrapped = new ArrayList<>(cols);
                int maxLines = 1;
                for (int c = 0; c < cols; c++) {
                    String cell = c < row.size() ? row.get(c) : "";
                    List<String> lines = wrapText(cell == null ? "" : cell.trim(), colWidth - (pad * 2f));
                    wrapped.add(lines);
                    maxLines = Math.max(maxLines, lines.size());
                }

                float rowHeight = (maxLines * lineHeight) + (pad * 2f);
                ensureSpace(rowHeight + paragraphGap);

                float yBottom = yTop - rowHeight;
                for (int c = 0; c < cols; c++) {
                    float xLeft = x + (c * colWidth);
                    contentStream.addRect(xLeft, yBottom, colWidth, rowHeight);
                }
                contentStream.stroke();

                for (int c = 0; c < cols; c++) {
                    float xLeft = x + (c * colWidth);
                    List<String> lines = wrapped.get(c);
                    float lineYTop = yTop - pad;
                    for (int i = 0; i < lines.size(); i++) {
                        String text = lines.get(i);
                        if (text.isEmpty()) continue;
                        contentStream.beginText();
                        contentStream.setFont(FONT, TEXT_SIZE_PT);
                        contentStream.newLineAtOffset(xLeft + pad, (lineYTop - (i * lineHeight)) - TEXT_SIZE_PT);
                        contentStream.showText(text);
                        contentStream.endText();
                        wroteContent = true;
                    }
                }

                yTop = yBottom;
            }

            yTop -= paragraphGap;
        }

        void appendImageFromRelationship(ZipFile zip, Map<String, String> imageRels, String rid) throws IOException {
            if (zip == null || imageRels == null || rid == null) return;
            String entryName = imageRels.get(rid);
            if (entryName == null || entryName.isEmpty()) {
                Log.i(TAG, "DOCX image relationship not found for " + rid);
                return;
            }

            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                Log.i(TAG, "DOCX missing image entry " + entryName + " (rid " + rid + ")");
                return;
            }

            Bitmap bitmap;
            try (InputStream img = zip.getInputStream(entry)) {
                bitmap = BitmapFactory.decodeStream(img);
            }
            if (bitmap == null) {
                Log.i(TAG, "DOCX image decode failed for " + entryName);
                return;
            }

            try {
                appendImage(bitmap);
            } finally {
                bitmap.recycle();
            }
        }

        private void appendImage(Bitmap bitmap) throws IOException {
            float maxWidth = usableWidth;
            float desiredWidth = Math.min(maxWidth, bitmap.getWidth());
            float scale = desiredWidth / Math.max(1f, bitmap.getWidth());
            float desiredHeight = bitmap.getHeight() * scale;

            ensureSpace(desiredHeight + paragraphGap);

            PDImageXObject image = LosslessFactory.createFromImage(pdf, bitmap);
            float yBottom = yTop - desiredHeight;
            contentStream.drawImage(image, x, yBottom, desiredWidth, desiredHeight);
            wroteContent = true;

            yTop = yBottom - paragraphGap;
        }

        private List<String> wrapText(String text, float maxWidth) throws IOException {
            String canon = text == null ? "" : text.replace('\t', ' ').trim();
            if (canon.isEmpty()) {
                List<String> lines = new ArrayList<>(1);
                lines.add("");
                return lines;
            }

            String[] words = canon.split("\\s+");
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                String candidate = line.length() == 0 ? word : (line + " " + word);
                if (line.length() == 0 || measureText(candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }

                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
            if (line.length() > 0) lines.add(line.toString());
            return lines;
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (yTop - requiredHeight >= MARGIN_PT) return;
            startPage();
        }

        private void startPage() throws IOException {
            finish();
            currentPage = new PDPage(new PDRectangle(PDF_WIDTH_PT, PDF_HEIGHT_PT));
            pdf.addPage(currentPage);
            contentStream = new PDPageContentStream(pdf, currentPage);
            contentStream.setLineWidth(0.75f);
            x = MARGIN_PT;
            // PDF coordinates are bottom-left. Track the top-of-page cursor.
            yTop = PDF_HEIGHT_PT - MARGIN_PT;
        }

        void finish() throws IOException {
            if (contentStream != null) {
                contentStream.close();
                contentStream = null;
            }
            currentPage = null;
        }
    }
}
