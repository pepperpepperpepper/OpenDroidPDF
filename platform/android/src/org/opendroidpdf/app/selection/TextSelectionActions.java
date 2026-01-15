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
            @Override public void onStartLine() { rect = new RectF(); line = new StringBuilder(); }
            public void onWord(TextWord word) {
                rect.union(word);
                if (line != null) line.append(word.w);
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
                    quadPoints.add(new PointF(rect.left, rect.bottom));
                    quadPoints.add(new PointF(rect.right, rect.bottom));
                    quadPoints.add(new PointF(rect.right, rect.top));
                    quadPoints.add(new PointF(rect.left, rect.top));
                }
                line = null;
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
            quadPoints.add(new PointF(x0, y1));
            quadPoints.add(new PointF(x1, y1));
            quadPoints.add(new PointF(x1, y0));
            quadPoints.add(new PointF(x0, y0));
        }

        PointF[] quadArray = quadPoints.toArray(new PointF[quadPoints.size()]);
        addMarkup.add(quadArray, type, text.toString(), host::deselectText);
        return true;
    }
}
