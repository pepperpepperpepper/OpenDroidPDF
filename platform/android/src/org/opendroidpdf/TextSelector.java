package org.opendroidpdf;

import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Selects words intersecting a rectangular selection box and streams them
 * to a TextProcessor, preserving basic line structure.
 */
public class TextSelector {
    private final TextWord[][] text;
    private final RectF selectBox;
    private float startLimit = Float.NEGATIVE_INFINITY;
    private float endLimit = Float.POSITIVE_INFINITY;

    public TextSelector(TextWord[][] text, RectF selectBox) {
        this.text = text;
        this.selectBox = selectBox;
    }

    public TextSelector(TextWord[][] text, RectF selectBox, float startLimit, float endLimit) {
        this(text, selectBox);
        this.startLimit = startLimit;
        this.endLimit = endLimit;
    }

    public void select(TextProcessor tp) {
        if (text == null || selectBox == null) return;

        ArrayList<TextWord[]> lines = new ArrayList<TextWord[]>();
        for (TextWord[] line : text) {
            if (line != null && line.length > 0 && line[0].bottom > selectBox.top && line[0].top < selectBox.bottom) {
                lines.add(line);
            }
        }

        Iterator<TextWord[]> it = lines.iterator();
        while (it.hasNext()) {
            TextWord[] line = it.next();
            boolean firstLine = line[0].top < selectBox.top;
            boolean lastLine = line[0].bottom > selectBox.bottom;

            float start = startLimit;
            float end = endLimit;

            if (firstLine && lastLine) {
                start = Math.min(selectBox.left, selectBox.right);
                end = Math.max(selectBox.left, selectBox.right);
            } else if (firstLine) {
                start = selectBox.left;
            } else if (lastLine) {
                end = selectBox.right;
            }

            tp.onStartLine();

            for (TextWord word : line) {
                if (word.right > start && word.left < end) tp.onWord(word);
            }

            tp.onEndLine();
        }
        tp.onEndText();
    }
}

