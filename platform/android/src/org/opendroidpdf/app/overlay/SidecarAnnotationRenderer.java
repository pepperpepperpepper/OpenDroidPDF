package org.opendroidpdf.app.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.app.sidecar.model.SidecarHighlight;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Renders sidecar-backed annotations (ink/highlight/note) onto the page overlay.
 *
 * <p>These annotations are not part of MuPDF's embedded PDF annotation layer; they are
 * stored and rendered by the app (e.g., for EPUB or non-writable PDFs).</p>
 */
public final class SidecarAnnotationRenderer {
    private final Paint inkPaint = new Paint();
    private final Path inkPath = new Path();

    private final Paint highlightPaint = new Paint();
    private final Paint underlinePaint = new Paint();

    private final Paint notePaint = new Paint();
    private final TextPaint noteTextPaint = new TextPaint();

    private static final int NOTE_LAYOUT_CACHE_MAX = 64;
    private final Map<NoteLayoutKey, StaticLayout> noteLayoutCache = new LinkedHashMap<NoteLayoutKey, StaticLayout>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<NoteLayoutKey, StaticLayout> eldest) {
            return size() > NOTE_LAYOUT_CACHE_MAX;
        }
    };

    public SidecarAnnotationRenderer() {
        inkPaint.setAntiAlias(true);
        inkPaint.setDither(true);
        inkPaint.setStrokeJoin(Paint.Join.ROUND);
        inkPaint.setStrokeCap(Paint.Cap.ROUND);
        inkPaint.setStyle(Paint.Style.STROKE);

        highlightPaint.setAntiAlias(true);
        highlightPaint.setStyle(Paint.Style.FILL);

        underlinePaint.setAntiAlias(true);
        underlinePaint.setStyle(Paint.Style.STROKE);
        underlinePaint.setStrokeCap(Paint.Cap.ROUND);

        notePaint.setAntiAlias(true);
        notePaint.setStyle(Paint.Style.FILL);
        notePaint.setColor(0xFFFFD54F); // amber-ish

        noteTextPaint.setAntiAlias(true);
        noteTextPaint.setStyle(Paint.Style.FILL);
        noteTextPaint.setColor(0xFF111111);
    }

    public void draw(@NonNull Canvas canvas,
                     float scale,
                     int pageIndex,
                     @NonNull SidecarAnnotationProvider provider) {
        drawHighlights(canvas, scale, provider.highlightsForPage(pageIndex));
        drawInk(canvas, scale, provider.inkStrokesForPage(pageIndex));
        drawNotes(canvas, scale, provider.notesForPage(pageIndex));
    }

    private void drawInk(Canvas canvas, float scale, List<SidecarInkStroke> strokes) {
        if (strokes == null || strokes.isEmpty()) return;
        for (SidecarInkStroke stroke : strokes) {
            if (stroke == null || stroke.points == null || stroke.points.length < 2) continue;
            inkPaint.setColor(stroke.color);
            inkPaint.setStrokeWidth(Math.max(1f, stroke.thickness * scale));

            boolean started = false;
            for (PointF p : stroke.points) {
                if (p == null) continue;
                float x = p.x * scale;
                float y = p.y * scale;
                if (!started) {
                    inkPath.moveTo(x, y);
                    started = true;
                } else {
                    inkPath.lineTo(x, y);
                }
            }
            if (started) {
                canvas.drawPath(inkPath, inkPaint);
            }
            inkPath.reset();
        }
    }

    private void drawHighlights(Canvas canvas, float scale, List<SidecarHighlight> highlights) {
        if (highlights == null || highlights.isEmpty()) return;
        for (SidecarHighlight h : highlights) {
            if (h == null || h.quadPoints == null || h.quadPoints.length < 4) continue;
            if (h.type != Annotation.Type.HIGHLIGHT
                    && h.type != Annotation.Type.UNDERLINE
                    && h.type != Annotation.Type.STRIKEOUT) {
                continue;
            }

            int alpha = (int) Math.max(0, Math.min(255, Math.round(h.opacity * 255f)));
            if (h.type == Annotation.Type.HIGHLIGHT) {
                highlightPaint.setColor(h.color);
                highlightPaint.setAlpha(alpha);
                drawQuadRects(canvas, scale, h.quadPoints, highlightPaint, QuadMode.FILL);
            } else {
                underlinePaint.setColor(h.color);
                underlinePaint.setAlpha(alpha);
                underlinePaint.setStrokeWidth(Math.max(1f, 2.0f * scale));
                QuadMode mode = (h.type == Annotation.Type.UNDERLINE) ? QuadMode.UNDERLINE : QuadMode.STRIKEOUT;
                drawQuadRects(canvas, scale, h.quadPoints, underlinePaint, mode);
            }
        }
    }

    private enum QuadMode { FILL, UNDERLINE, STRIKEOUT }

    private static void drawQuadRects(Canvas canvas, float scale, PointF[] quadPoints, Paint paint, QuadMode mode) {
        int n = quadPoints.length - (quadPoints.length % 4);
        RectF r = new RectF();
        for (int i = 0; i < n; i += 4) {
            r.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
            for (int j = 0; j < 4; j++) {
                PointF p = quadPoints[i + j];
                if (p == null) continue;
                float x = p.x * scale;
                float y = p.y * scale;
                if (x < r.left) r.left = x;
                if (y < r.top) r.top = y;
                if (x > r.right) r.right = x;
                if (y > r.bottom) r.bottom = y;
            }
            if (!r.isEmpty()) {
                switch (mode) {
                    case FILL:
                        canvas.drawRect(r, paint);
                        break;
                    case UNDERLINE: {
                        float y = r.bottom - Math.max(1f, 1.0f * scale);
                        canvas.drawLine(r.left, y, r.right, y, paint);
                        break;
                    }
                    case STRIKEOUT: {
                        float y = (r.top + r.bottom) * 0.5f;
                        canvas.drawLine(r.left, y, r.right, y, paint);
                        break;
                    }
                }
            }
        }
    }

    private void drawNotes(Canvas canvas, float scale, List<SidecarNote> notes) {
        if (notes == null || notes.isEmpty()) return;
        for (SidecarNote n : notes) {
            if (n == null || n.bounds == null) continue;
            // Render a small marker near the note bounds.
            float left = n.bounds.left * scale;
            float top = n.bounds.top * scale;
            float size = Math.max(10f * scale, 18f);
            canvas.drawRoundRect(left, top - size, left + size, top, 4f * scale, 4f * scale, notePaint);

            String text = n.text;
            if (text == null || text.trim().isEmpty()) continue;

            float leftDoc = n.bounds.left;
            float topDoc = n.bounds.top;
            float rightDoc = n.bounds.right;
            float bottomDoc = n.bounds.bottom;

            float widthDoc = rightDoc - leftDoc;
            float heightDoc = bottomDoc - topDoc;
            if (widthDoc <= 2f || heightDoc <= 2f) continue;

            float fontSizeDoc = n.fontSize > 0f ? n.fontSize : SidecarNote.DEFAULT_FONT_SIZE;
            int color = n.color != 0 ? n.color : SidecarNote.DEFAULT_COLOR;
            int layoutWidthDocPx = Math.max(1, (int) Math.floor(widthDoc));
            StaticLayout layout = noteLayout(text, n.id, layoutWidthDocPx, fontSizeDoc, color);

            canvas.save();
            // Draw the layout in doc-space and let the canvas scaling match the page zoom.
            canvas.scale(scale, scale);
            canvas.translate(leftDoc, topDoc);
            canvas.clipRect(0f, 0f, widthDoc, heightDoc);
            layout.draw(canvas);
            canvas.restore();
        }
    }

    private StaticLayout noteLayout(@NonNull String text,
                                   @NonNull String noteId,
                                   int widthDocPx,
                                   float fontSizeDoc,
                                   int color) {
        NoteLayoutKey key = new NoteLayoutKey(noteId, text, widthDocPx, fontSizeDoc, color);
        StaticLayout cached = noteLayoutCache.get(key);
        if (cached != null) return cached;

        // StaticLayout keeps a reference to the paint; don't reuse a mutable shared TextPaint.
        TextPaint paint = new TextPaint(noteTextPaint);
        paint.setTextSize(fontSizeDoc);
        paint.setColor(color);

        StaticLayout layout = new StaticLayout(
                text,
                paint,
                widthDocPx,
                Layout.Alignment.ALIGN_NORMAL,
                1.0f,
                0.0f,
                false
        );
        noteLayoutCache.put(key, layout);
        return layout;
    }

    private static final class NoteLayoutKey {
        private final String noteId;
        private final String text;
        private final int widthDocPx;
        private final int fontSizeBits;
        private final int color;

        NoteLayoutKey(@NonNull String noteId, @NonNull String text, int widthDocPx, float fontSizeDoc, int color) {
            this.noteId = noteId;
            this.text = text;
            this.widthDocPx = widthDocPx;
            this.fontSizeBits = Float.floatToIntBits(fontSizeDoc);
            this.color = color;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NoteLayoutKey)) return false;
            NoteLayoutKey that = (NoteLayoutKey) o;
            return widthDocPx == that.widthDocPx
                    && fontSizeBits == that.fontSizeBits
                    && color == that.color
                    && noteId.equals(that.noteId)
                    && text.equals(that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(noteId, text, widthDocPx, fontSizeBits, color);
        }
    }
}
