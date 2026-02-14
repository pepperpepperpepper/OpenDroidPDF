package org.opendroidpdf.app.overlay;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import org.opendroidpdf.TextWord;

public final class SelectionRenderer {
    private final Path leftMarker = new Path();
    private final Path rightMarker = new Path();

    public void draw(Canvas canvas,
                     Resources res,
                     float scale,
                     TextWord[][] text,
                     RectF selectBox,
                     Paint selectBoxPaint,
                     Paint selectMarkerPaint,
                     RectF leftMarkerRectOut,
                     RectF rightMarkerRectOut,
                     int viewWidth,
                     int viewHeight,
                     boolean showLeftMarker,
                     boolean showRightMarker) {
        if (text == null || selectBox == null) return;

        RectF firstLineRect = null;
        RectF lastLineRect = null;

        for (TextWord[] line : text) {
            if (line == null || line.length == 0) continue;
            if (!(line[0].bottom > selectBox.top && line[0].top < selectBox.bottom)) continue;

            boolean firstLine = line[0].top < selectBox.top;
            boolean lastLine = line[0].bottom > selectBox.bottom;
            float start;
            float end;
            if (firstLine && lastLine) {
                start = Math.min(selectBox.left, selectBox.right);
                end = Math.max(selectBox.left, selectBox.right);
            } else if (firstLine) {
                start = selectBox.left;
                end = Float.POSITIVE_INFINITY;
            } else if (lastLine) {
                start = Float.NEGATIVE_INFINITY;
                end = selectBox.right;
            } else {
                start = Float.NEGATIVE_INFINITY;
                end = Float.POSITIVE_INFINITY;
            }

            RectF rect = new RectF();
            for (TextWord word : line) {
                if (word.right > start && word.left < end) {
                    rect.union(word);
                }
            }

            if (!rect.isEmpty()) {
                if (firstLineRect == null || firstLineRect.top > rect.top) {
                    if (firstLineRect == null) firstLineRect = new RectF();
                    firstLineRect.set(rect);
                }
                if (lastLineRect == null || lastLineRect.bottom < rect.bottom) {
                    if (lastLineRect == null) lastLineRect = new RectF();
                    lastLineRect.set(rect);
                }

                canvas.drawRect(rect.left * scale, rect.top * scale,
                        rect.right * scale, rect.bottom * scale,
                        selectBoxPaint);
            }
        }

        if (firstLineRect != null && lastLineRect != null) {
            float xdpi = res.getDisplayMetrics().xdpi;
            float hBase = xdpi * 0.07f / scale;
            float firstH = firstLineRect.bottom - firstLineRect.top;
            float lastH = lastLineRect.bottom - lastLineRect.top;
            float height = Math.min(Math.max(Math.max(firstH, lastH), hBase), 4 * hBase);

            if (leftMarkerRectOut != null) {
                if (showLeftMarker) {
                    leftMarkerRectOut.set(firstLineRect.left - 0.9f * height,
                            firstLineRect.top,
                            firstLineRect.left,
                            firstLineRect.top + 1.9f * height);
                } else {
                    leftMarkerRectOut.setEmpty();
                }
            }
            if (rightMarkerRectOut != null) {
                if (showRightMarker) {
                    rightMarkerRectOut.set(lastLineRect.right,
                            lastLineRect.top,
                            lastLineRect.right + 0.9f * height,
                            lastLineRect.top + 1.9f * height);
                } else {
                    rightMarkerRectOut.setEmpty();
                }
            }

            // Build and draw the marker paths (offset by scaled positions)
            if (showLeftMarker) {
                leftMarker.rewind();
                leftMarker.moveTo(0f, 0f);
                leftMarker.rLineTo(0f, 1.9f * height * scale);
                leftMarker.rLineTo(-0.9f * height * scale, 0f);
                leftMarker.rLineTo(0f, -0.9f * height * scale);
                leftMarker.close();
                leftMarker.offset(firstLineRect.left * scale, firstLineRect.top * scale);
                canvas.drawPath(leftMarker, selectMarkerPaint);
                leftMarker.offset(-firstLineRect.left * scale, -firstLineRect.top * scale);
            }

            if (showRightMarker) {
                rightMarker.rewind();
                rightMarker.moveTo(0f, 0f);
                rightMarker.rLineTo(0f, 1.9f * height * scale);
                rightMarker.rLineTo(0.9f * height * scale, 0f);
                rightMarker.rLineTo(0f, -0.9f * height * scale);
                rightMarker.close();
                rightMarker.offset(lastLineRect.right * scale, lastLineRect.top * scale);
                canvas.drawPath(rightMarker, selectMarkerPaint);
                rightMarker.offset(-lastLineRect.right * scale, -lastLineRect.top * scale);
            }
        }
    }
}
