package org.opendroidpdf.app.assistant;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.notes.NotesDelegate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class AssistantNoteDocumentCreator {
    private static final int A4_W_PT = 595;
    private static final int A4_H_PT = 842;

    private static final int MARGIN_PT = 48;
    private static final int FOOTER_RESERVED_PT = 28;
    private static final int HEADER_GAP_PT = 12;

    private AssistantNoteDocumentCreator() {}

    @NonNull
    static File createSummaryNotePdf(@NonNull Context context,
                                     @NonNull String sourceTitleOrFallback,
                                     @NonNull String summaryText,
                                     @Nullable String summaryStyleLabelOrNull) throws Exception {
        Context appContext = context.getApplicationContext();
        File notesDir = NotesDelegate.getNotesDir(appContext);

        String base = safeBaseName(sourceTitleOrFallback);
        if (base.toLowerCase(Locale.US).endsWith(".pdf")) {
            base = base.substring(0, base.length() - 4);
        }
        if (base.isEmpty()) base = "document";

        String fileName = base + "_assistant_summary_" + System.currentTimeMillis() + ".pdf";
        File out = new File(notesDir, fileName);
        writeAssistantPdf(out, "Assistant summary", sourceTitleOrFallback, summaryText, summaryStyleLabelOrNull);
        return out;
    }

    @NonNull
    static File createSummaryExportPdf(@NonNull Context context,
                                       @NonNull String sourceTitleOrFallback,
                                       @NonNull String summaryText,
                                       @Nullable String summaryStyleLabelOrNull) throws Exception {
        Context appContext = context.getApplicationContext();
        File dir = new File(appContext.getCacheDir(), "tmpfiles");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();

        String base = safeBaseName(sourceTitleOrFallback);
        if (base.toLowerCase(Locale.US).endsWith(".pdf")) {
            base = base.substring(0, base.length() - 4);
        }
        if (base.isEmpty()) base = "document";

        String fileName = base + "_assistant_summary_export_" + System.currentTimeMillis() + ".pdf";
        File out = new File(dir, fileName);
        writeAssistantPdf(out, "Assistant summary", sourceTitleOrFallback, summaryText, summaryStyleLabelOrNull);
        return out;
    }

    @NonNull
    static File createAnswerNotePdf(@NonNull Context context,
                                    @NonNull String sourceTitleOrFallback,
                                    @NonNull String answerText) throws Exception {
        Context appContext = context.getApplicationContext();
        File notesDir = NotesDelegate.getNotesDir(appContext);

        String base = safeBaseName(sourceTitleOrFallback);
        if (base.toLowerCase(Locale.US).endsWith(".pdf")) {
            base = base.substring(0, base.length() - 4);
        }
        if (base.isEmpty()) base = "document";

        String fileName = base + "_assistant_answer_" + System.currentTimeMillis() + ".pdf";
        File out = new File(notesDir, fileName);
        writeAssistantPdf(out, "Assistant answer", sourceTitleOrFallback, answerText, /*metaSuffix*/ null);
        return out;
    }

    private static void writeAssistantPdf(@NonNull File out,
                                          @NonNull String titlePrefix,
                                          @NonNull String sourceTitleOrFallback,
                                          @NonNull String bodyText,
                                          @Nullable String metaSuffixOrNull) throws Exception {
        String body = bodyText != null ? bodyText.trim() : "";
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Text is empty.");
        }

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(0xFF111111);
        titlePaint.setTextSize(18f);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        TextPaint metaPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        metaPaint.setColor(0xFF555555);
        metaPaint.setTextSize(10f);

        TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(0xFF111111);
        bodyPaint.setTextSize(12f);

        TextPaint footerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(0xFF777777);
        footerPaint.setTextSize(9f);

        String titleLine = (titlePrefix != null ? titlePrefix : "Assistant note")
                + " — "
                + (sourceTitleOrFallback != null ? sourceTitleOrFallback : "Document");
        String metaLine = formattedNow();
        if (metaSuffixOrNull != null && !metaSuffixOrNull.trim().isEmpty()) {
            metaLine = metaLine + " • " + metaSuffixOrNull.trim();
        }

        float headerTop = MARGIN_PT;
        float y = headerTop;
        y += lineHeight(titlePaint);
        y += lineHeight(metaPaint);
        y += HEADER_GAP_PT;
        int bodyTop = (int) Math.ceil(y);

        int contentWidth = Math.max(1, A4_W_PT - (MARGIN_PT * 2));
        int bodyHeight = Math.max(1, A4_H_PT - MARGIN_PT - bodyTop - FOOTER_RESERVED_PT);

        StaticLayout full = new StaticLayout(body, bodyPaint, contentWidth, Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false);
        List<PageSlice> slices = paginate(full, bodyHeight);

        PdfDocument pdf = new PdfDocument();
        try (OutputStream os = new FileOutputStream(out, false)) {
            int pageCount = slices.size();
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(A4_W_PT, A4_H_PT, pageIndex + 1).create();
                PdfDocument.Page page = pdf.startPage(info);
                try {
                    android.graphics.Canvas canvas = page.getCanvas();
                    canvas.drawColor(0xFFFFFFFF);

                    // Header.
                    float titleBaseline = headerTop - titlePaint.getFontMetrics().top;
                    canvas.drawText(titleLine, MARGIN_PT, titleBaseline, titlePaint);

                    float metaTop = headerTop + lineHeight(titlePaint);
                    float metaBaseline = metaTop - metaPaint.getFontMetrics().top;
                    canvas.drawText(metaLine, MARGIN_PT, metaBaseline, metaPaint);

                    // Body.
                    PageSlice slice = slices.get(pageIndex);
                    String pageText = body.substring(slice.startChar, slice.endChar);
                    StaticLayout pageLayout = new StaticLayout(pageText, bodyPaint, contentWidth, Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false);
                    canvas.save();
                    canvas.translate(MARGIN_PT, bodyTop);
                    pageLayout.draw(canvas);
                    canvas.restore();

                    // Footer.
                    String footer = (pageIndex + 1) + " / " + pageCount;
                    float footerWidth = footerPaint.measureText(footer);
                    float footerBaseline = (A4_H_PT - MARGIN_PT) - footerPaint.getFontMetrics().bottom;
                    canvas.drawText(footer, A4_W_PT - MARGIN_PT - footerWidth, footerBaseline, footerPaint);
                } finally {
                    pdf.finishPage(page);
                }
            }
            pdf.writeTo(os);
        } finally {
            pdf.close();
        }
    }

    private static List<PageSlice> paginate(@NonNull StaticLayout fullLayout, int bodyHeight) {
        int lineCount = fullLayout.getLineCount();
        List<PageSlice> slices = new ArrayList<>();
        int startLine = 0;
        while (startLine < lineCount) {
            int startY = fullLayout.getLineTop(startLine);
            int maxBottom = startY + bodyHeight;
            int endLineExclusive = startLine;
            while (endLineExclusive < lineCount && fullLayout.getLineBottom(endLineExclusive) <= maxBottom) {
                endLineExclusive++;
            }
            if (endLineExclusive == startLine) {
                endLineExclusive = Math.min(lineCount, startLine + 1);
            }
            int startChar = fullLayout.getLineStart(startLine);
            int endChar = fullLayout.getLineEnd(endLineExclusive - 1);
            slices.add(new PageSlice(startChar, endChar));
            startLine = endLineExclusive;
        }
        if (slices.isEmpty()) {
            slices.add(new PageSlice(0, 0));
        }
        return slices;
    }

    private static float lineHeight(@NonNull TextPaint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        return Math.max(1f, fm.bottom - fm.top);
    }

    @NonNull
    private static String safeBaseName(@Nullable String raw) {
        String s = raw != null ? raw.trim() : "";
        if (s.isEmpty()) return "";
        s = s.replace('/', '_').replace('\\', '_');
        s = s.replaceAll("[^A-Za-z0-9._ -]", "_");
        if (s.length() > 64) s = s.substring(0, 64);
        return s.trim();
    }

    @NonNull
    private static String formattedNow() {
        try {
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());
            return df.format(new Date());
        } catch (Throwable ignore) {
            return new Date().toString();
        }
    }

    private static final class PageSlice {
        final int startChar;
        final int endChar;

        PageSlice(int startChar, int endChar) {
            this.startChar = Math.max(0, startChar);
            this.endChar = Math.max(this.startChar, endChar);
        }
    }
}
