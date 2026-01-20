package org.opendroidpdf.app.selection;

import android.content.ClipData;
import android.content.Context;
import android.os.Build;
import android.graphics.PointF;
import android.graphics.RectF;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.TextProcessor;
import org.opendroidpdf.TextWord;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Builds text and quad-points for the current selection and performs
 * copy/markup actions via a small host/callback surface.
 */
public class TextSelectionActions {
    public interface Host {
        void processSelectedText(TextProcessor processor);
        void deselectText();
        Context getContext();
    }

    public interface AddMarkup {
        void add(PointF[] quadPoints, Annotation.Type type, String selectedText, Runnable onComplete);
    }

    public boolean copySelection(Host host) {
        final StringBuilder text = new StringBuilder();

        host.processSelectedText(new TextProcessor() {
            StringBuilder line;
            public void onStartLine() { line = new StringBuilder(); }
            public void onWord(TextWord word) { line.append(word.w); }
            public void onEndLine() {
                if (text.length() > 0) text.append('\n');
                text.append(line);
            }
            public void onEndText() {}
        });

        if (text.length() == 0) return false;

        Context context = host.getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(context.getPackageName(), text));
        } else {
            @SuppressWarnings("deprecation")
            android.text.ClipboardManager cm = (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setText(text);
        }

        host.deselectText();
        return true;
    }

    public boolean markupSelection(Host host, Annotation.Type type, AddMarkup addMarkup) {
        final ArrayList<PointF> quadPoints = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        final RectF firstLineRect = new RectF();
        final boolean[] haveFirstRect = new boolean[] { false };
        host.processSelectedText(new TextProcessor() {
            RectF rect;
            StringBuilder line;
            final ArrayList<Float> lineTops = new ArrayList<>();
            final ArrayList<Float> lineBottoms = new ArrayList<>();
            @Override public void onStartLine() { rect = new RectF(); line = new StringBuilder(); }
            public void onWord(TextWord word) {
                rect.union(word);
                if (line != null) line.append(word.w);
                if (word != null && !word.isEmpty()) {
                    lineTops.add(word.top);
                    lineBottoms.add(word.bottom);
                }
            }
            public void onEndLine() {
                if (line != null) {
                    if (text.length() > 0) text.append('\n');
                    text.append(line);
                }
                if (!rect.isEmpty()) {
                    if (!haveFirstRect[0]) {
                        firstLineRect.set(rect);
                        haveFirstRect[0] = true;
                    }
                    RectF quadRect = rect;
                    if (shouldTightenMarkupQuadBounds(type) && !lineTops.isEmpty() && !lineBottoms.isEmpty()) {
                        Collections.sort(lineTops);
                        Collections.sort(lineBottoms);
                        float top = percentileSorted(lineTops, 0.30f);
                        float bottom = percentileSorted(lineBottoms, 0.70f);
                        if (bottom > top) {
                            quadRect = new RectF(rect.left, top, rect.right, bottom);
                        }
                    }
                    // MuPDF markup expects quad points ordered as: UL, UR, LL, LR.
                    // (upper-left, upper-right, lower-left, lower-right).
                    quadPoints.add(new PointF(quadRect.left, quadRect.top));      // UL
                    quadPoints.add(new PointF(quadRect.right, quadRect.top));     // UR
                    quadPoints.add(new PointF(quadRect.left, quadRect.bottom));   // LL
                    quadPoints.add(new PointF(quadRect.right, quadRect.bottom));  // LR
                }
                line = null;
                lineTops.clear();
                lineBottoms.clear();
            }
            public void onEndText() {}
        });

        if (quadPoints.isEmpty()) return false;

        if (type == Annotation.Type.CARET && haveFirstRect[0]) {
            final float lineHeight = Math.max(firstLineRect.height(), 10f);
            final float caretWidth = Math.max(lineHeight * 0.35f, 6f);
            final float x0 = firstLineRect.left;
            final float y0 = firstLineRect.top;
            final float x1 = x0 + caretWidth;
            final float y1 = y0 + lineHeight;
            quadPoints.clear();
            quadPoints.add(new PointF(x0, y0)); // UL
            quadPoints.add(new PointF(x1, y0)); // UR
            quadPoints.add(new PointF(x0, y1)); // LL
            quadPoints.add(new PointF(x1, y1)); // LR
        }

        PointF[] quadArray = quadPoints.toArray(new PointF[quadPoints.size()]);
        addMarkup.add(quadArray, type, text.toString(), host::deselectText);
        return true;
    }

    private static boolean shouldTightenMarkupQuadBounds(Annotation.Type type) {
        // Underline/strikeout/squiggly render at an edge/center of the quad. Some PDFs have
        // oversized text bounding boxes, which pushes underline/strikeout far away from the
        // selected text if we use the full union rect. Use a trimmed (percentile) vertical
        // range to reduce outliers while keeping the selection stable.
        return type == Annotation.Type.UNDERLINE
                || type == Annotation.Type.STRIKEOUT
                || type == Annotation.Type.SQUIGGLY;
    }

    private static float percentileSorted(ArrayList<Float> sortedValues, float percentile01) {
        if (sortedValues == null || sortedValues.isEmpty()) return Float.NaN;
        if (sortedValues.size() == 1) return sortedValues.get(0);
        if (percentile01 < 0f) percentile01 = 0f;
        if (percentile01 > 1f) percentile01 = 1f;
        int idx = (int) Math.floor(percentile01 * (sortedValues.size() - 1));
        if (idx < 0) idx = 0;
        if (idx >= sortedValues.size()) idx = sortedValues.size() - 1;
        return sortedValues.get(idx);
    }
}
