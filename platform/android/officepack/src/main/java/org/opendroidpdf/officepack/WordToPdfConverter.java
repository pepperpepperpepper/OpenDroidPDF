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
import java.util.Locale;
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

        List<TextSpan> paragraphSpans = new ArrayList<>();
        List<String> paragraphImages = new ArrayList<>();
        StringBuilder runText = new StringBuilder();

        ParagraphFormat format = new ParagraphFormat();

        boolean inText = false;
        boolean inRun = false;
        boolean runBold = false;
        boolean runItalic = false;
        boolean runUnderline = false;

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
                        paragraphSpans.clear();
                        paragraphImages.clear();
                        format = new ParagraphFormat();
                    } else if (isTag(name, "pStyle")) {
                        format.headingLevel = parseHeadingLevel(getAttr(parser, "val"));
                    } else if (isTag(name, "numPr")) {
                        format.isList = true;
                    } else if (isTag(name, "ilvl")) {
                        format.listLevel = parseIntOrZero(getAttr(parser, "val"));
                    } else if (isTag(name, "r")) {
                        inRun = true;
                        runText.setLength(0);
                        runBold = false;
                        runItalic = false;
                        runUnderline = false;
                    } else if (isTag(name, "b")) {
                        if (inRun) runBold = parseOnOffAttr(parser, true);
                    } else if (isTag(name, "i")) {
                        if (inRun) runItalic = parseOnOffAttr(parser, true);
                    } else if (isTag(name, "u")) {
                        if (inRun) runUnderline = parseUnderlineAttr(parser);
                    } else if (isTag(name, "t")) {
                        inText = true;
                    } else if (isTag(name, "tab")) {
                        if (inRun) runText.append('\t');
                    } else if (isTag(name, "br") || isTag(name, "cr")) {
                        if (inRun) runText.append('\n');
                    } else if (isTag(name, "blip")) {
                        String rid = getEmbedAttr(parser);
                        if (rid != null && !rid.isEmpty()) {
                            paragraphImages.add(rid);
                        }
                    }
                    break;
                }
                case XmlPullParser.TEXT: {
                    if (inText && inRun) {
                        runText.append(parser.getText());
                    }
                    break;
                }
                case XmlPullParser.END_TAG: {
                    String name = parser.getName();
                    if (isTag(name, "t")) {
                        inText = false;
                    } else if (isTag(name, "r")) {
                        if (inRun && runText.length() > 0) {
                            paragraphSpans.add(new TextSpan(runText.toString(), runBold, runItalic, runUnderline));
                        }
                        runText.setLength(0);
                        inRun = false;
                    } else if (isTag(name, "p")) {
                        writer.appendParagraph(paragraphSpans, format);
                        for (String rid : paragraphImages) {
                            writer.appendImageFromRelationship(zip, imageRels, rid);
                        }
                        paragraphSpans.clear();
                        paragraphImages.clear();
                        runText.setLength(0);
                        inRun = false;
                        inText = false;
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

    private static int parseHeadingLevel(String style) {
        if (style == null) return 0;

        String canon = style.trim();
        if (canon.isEmpty()) return 0;

        String lower = canon.toLowerCase(Locale.US);
        if (lower.startsWith("heading")) {
            String suffix = lower.substring("heading".length());
            try {
                int level = Integer.parseInt(suffix);
                return Math.max(1, Math.min(6, level));
            } catch (NumberFormatException ignore) {
                return 1;
            }
        }
        if (lower.equals("title")) return 1;
        return 0;
    }

    private static boolean parseOnOffAttr(XmlPullParser parser, boolean defaultValue) {
        String val = getAttr(parser, "val");
        if (val == null) return defaultValue;
        String lower = val.trim().toLowerCase(Locale.US);
        return !(lower.equals("0") || lower.equals("false") || lower.equals("off"));
    }

    private static boolean parseUnderlineAttr(XmlPullParser parser) {
        String val = getAttr(parser, "val");
        if (val == null) return true;
        String lower = val.trim().toLowerCase(Locale.US);
        return !lower.equals("none") && !lower.equals("0") && !lower.equals("false") && !lower.equals("off");
    }

    private static int parseIntOrZero(String raw) {
        if (raw == null) return 0;
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignore) {
            return 0;
        }
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

    private static final class ParagraphFormat {
        int headingLevel;
        boolean isList;
        int listLevel;
    }

    private static final class TextSpan {
        final String text;
        final boolean bold;
        final boolean italic;
        final boolean underline;

        TextSpan(String text, boolean bold, boolean italic, boolean underline) {
            this.text = text;
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
        }
    }

    private static final class PdfFlowWriter {
        private static final PDFont FONT_NORMAL = PDType1Font.HELVETICA;
        private static final PDFont FONT_BOLD = PDType1Font.HELVETICA_BOLD;
        private static final PDFont FONT_ITALIC = PDType1Font.HELVETICA_OBLIQUE;
        private static final PDFont FONT_BOLD_ITALIC = PDType1Font.HELVETICA_BOLD_OBLIQUE;

        private final PDDocument pdf;
        private final float bodyLineHeight;
        private final float bodyParagraphGap;
        private final float usableWidth;

        private PDPage currentPage;
        private PDPageContentStream contentStream;
        private float x;
        private float yTop;

        private boolean wroteContent;

        PdfFlowWriter(PDDocument pdf) throws IOException {
            this.pdf = pdf;

            bodyLineHeight = TEXT_SIZE_PT * LINE_SPACING_MULT;
            bodyParagraphGap = bodyLineHeight * PARAGRAPH_SPACING_MULT;

            usableWidth = PDF_WIDTH_PT - (MARGIN_PT * 2f);

            startPage();
        }

        boolean didWriteContent() {
            return wroteContent;
        }

        void appendParagraph(List<TextSpan> spans, ParagraphFormat format) throws IOException {
            if (spans == null) return;

            float fontSize = fontSizeForHeading(format == null ? 0 : format.headingLevel);
            float lineHeight = fontSize * LINE_SPACING_MULT;
            float paragraphGap = lineHeight * PARAGRAPH_SPACING_MULT;

            boolean paragraphBold = format != null && format.headingLevel > 0;
            boolean isList = format != null && format.isList;
            int listLevel = format != null ? Math.max(0, format.listLevel) : 0;

            List<StyledWord> tokens = tokenizeSpans(spans);
            if (tokens.isEmpty()) {
                yTop -= paragraphGap;
                return;
            }

            float baseX = x;
            float textX = x;
            float maxWidth = usableWidth;
            float listPrefixWidth = 0f;
            String listPrefix = "- ";
            float listIndentPerLevel = 18f;

            if (isList) {
                baseX = x + (listIndentPerLevel * listLevel);
                listPrefixWidth = measureText(FONT_NORMAL, fontSize, listPrefix);
                textX = baseX + listPrefixWidth;
                maxWidth = usableWidth - (listIndentPerLevel * listLevel) - listPrefixWidth;
            }

            List<List<StyledWord>> lines = wrapStyledWords(tokens, maxWidth, fontSize, paragraphBold);
            if (lines.isEmpty()) {
                yTop -= paragraphGap;
                return;
            }

            for (int i = 0; i < lines.size(); i++) {
                ensureSpace(lineHeight);

                if (isList && i == 0) {
                    contentStream.beginText();
                    contentStream.setFont(FONT_NORMAL, fontSize);
                    contentStream.newLineAtOffset(baseX, yTop - fontSize);
                    contentStream.showText(listPrefix.trim());
                    contentStream.endText();
                    wroteContent = true;
                }

                drawStyledLine(lines.get(i), textX, fontSize, paragraphBold);
                yTop -= lineHeight;
            }

            yTop -= paragraphGap;
        }

        private static float fontSizeForHeading(int level) {
            switch (level) {
                case 1:
                    return 20f;
                case 2:
                    return 16f;
                case 3:
                    return 14f;
                default:
                    return TEXT_SIZE_PT;
            }
        }

        private static PDFont fontFor(boolean bold, boolean italic) {
            if (bold && italic) return FONT_BOLD_ITALIC;
            if (bold) return FONT_BOLD;
            if (italic) return FONT_ITALIC;
            return FONT_NORMAL;
        }

        private static final class StyledWord {
            final String text;
            final boolean bold;
            final boolean italic;
            final boolean underline;
            final boolean newline;

            private StyledWord(String text, boolean bold, boolean italic, boolean underline, boolean newline) {
                this.text = text;
                this.bold = bold;
                this.italic = italic;
                this.underline = underline;
                this.newline = newline;
            }

            static StyledWord word(String text, boolean bold, boolean italic, boolean underline) {
                return new StyledWord(text, bold, italic, underline, false);
            }

            static StyledWord newline() {
                return new StyledWord("", false, false, false, true);
            }
        }

        private static List<StyledWord> tokenizeSpans(List<TextSpan> spans) {
            List<StyledWord> out = new ArrayList<>();
            for (TextSpan span : spans) {
                if (span == null || span.text == null) continue;
                String s = span.text.replace('\t', ' ');
                int i = 0;
                while (i < s.length()) {
                    char ch = s.charAt(i);
                    if (ch == '\n') {
                        out.add(StyledWord.newline());
                        i++;
                        continue;
                    }
                    if (Character.isWhitespace(ch)) {
                        i++;
                        continue;
                    }

                    int j = i + 1;
                    while (j < s.length()) {
                        char cj = s.charAt(j);
                        if (cj == '\n' || Character.isWhitespace(cj)) break;
                        j++;
                    }
                    out.add(StyledWord.word(s.substring(i, j), span.bold, span.italic, span.underline));
                    i = j;
                }
            }
            return out;
        }

        private float measureText(PDFont font, float fontSize, String text) throws IOException {
            return (font.getStringWidth(text) / 1000f) * fontSize;
        }

        private List<List<StyledWord>> wrapStyledWords(List<StyledWord> tokens, float maxWidth, float fontSize, boolean paragraphBold)
                throws IOException {
            List<List<StyledWord>> lines = new ArrayList<>();
            List<StyledWord> logicalLine = new ArrayList<>();
            for (StyledWord token : tokens) {
                if (token.newline) {
                    lines.addAll(wrapLogicalLine(logicalLine, maxWidth, fontSize, paragraphBold));
                    logicalLine.clear();
                } else {
                    logicalLine.add(token);
                }
            }
            lines.addAll(wrapLogicalLine(logicalLine, maxWidth, fontSize, paragraphBold));
            return lines;
        }

        private List<List<StyledWord>> wrapLogicalLine(List<StyledWord> tokens, float maxWidth, float fontSize, boolean paragraphBold)
                throws IOException {
            List<List<StyledWord>> out = new ArrayList<>();
            if (tokens.isEmpty()) {
                out.add(new ArrayList<>());
                return out;
            }

            List<StyledWord> line = new ArrayList<>();
            float width = 0f;

            for (StyledWord token : tokens) {
                if (token == null || token.text == null || token.text.isEmpty()) continue;

                PDFont font = fontFor(token.bold || paragraphBold, token.italic);
                float tokenWidth = measureText(font, fontSize, token.text);
                float spaceWidth = line.isEmpty() ? 0f : measureText(font, fontSize, " ");

                if (!line.isEmpty() && width + spaceWidth + tokenWidth > maxWidth) {
                    out.add(line);
                    line = new ArrayList<>();
                    width = 0f;
                    spaceWidth = 0f;
                }

                if (!line.isEmpty()) width += spaceWidth;
                line.add(token);
                width += tokenWidth;
            }

            if (line.isEmpty()) {
                out.add(new ArrayList<>());
            } else {
                out.add(line);
            }

            return out;
        }

        private void drawStyledLine(List<StyledWord> line, float startX, float fontSize, boolean paragraphBold) throws IOException {
            if (line == null || line.isEmpty()) return;

            contentStream.beginText();
            contentStream.newLineAtOffset(startX, yTop - fontSize);

            boolean first = true;
            for (StyledWord word : line) {
                if (word == null || word.text == null || word.text.isEmpty()) continue;

                PDFont font = fontFor(word.bold || paragraphBold, word.italic);
                contentStream.setFont(font, fontSize);
                if (!first) {
                    contentStream.showText(" ");
                }
                contentStream.showText(word.text);
                first = false;
            }

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
                    List<String> lines = wrapText(FONT_NORMAL, TEXT_SIZE_PT, cell == null ? "" : cell.trim(), colWidth - (pad * 2f));
                    wrapped.add(lines);
                    maxLines = Math.max(maxLines, lines.size());
                }

                float rowHeight = (maxLines * bodyLineHeight) + (pad * 2f);
                ensureSpace(rowHeight + bodyParagraphGap);

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
                        contentStream.setFont(FONT_NORMAL, TEXT_SIZE_PT);
                        contentStream.newLineAtOffset(xLeft + pad, (lineYTop - (i * bodyLineHeight)) - TEXT_SIZE_PT);
                        contentStream.showText(text);
                        contentStream.endText();
                        wroteContent = true;
                    }
                }

                yTop = yBottom;
            }

            yTop -= bodyParagraphGap;
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

            ensureSpace(desiredHeight + bodyParagraphGap);

            PDImageXObject image = LosslessFactory.createFromImage(pdf, bitmap);
            float yBottom = yTop - desiredHeight;
            contentStream.drawImage(image, x, yBottom, desiredWidth, desiredHeight);
            wroteContent = true;

            yTop = yBottom - bodyParagraphGap;
        }

        private List<String> wrapText(PDFont font, float fontSize, String text, float maxWidth) throws IOException {
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
                if (line.length() == 0 || measureText(font, fontSize, candidate) <= maxWidth) {
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
