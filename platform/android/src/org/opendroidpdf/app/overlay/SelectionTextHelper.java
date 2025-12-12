package org.opendroidpdf.app.overlay;

import android.graphics.RectF;

import org.opendroidpdf.TextProcessor;
import org.opendroidpdf.TextSelector;
import org.opendroidpdf.TextWord;
import org.opendroidpdf.app.reader.PageState;

/**
 * Shared selection/text utilities to keep PageView slim.
 */
public final class SelectionTextHelper {
    private SelectionTextHelper() {}

    public static boolean hasTextSelected(TextWord[][] text,
                                          RectF selectBox,
                                          PageState pageState,
                                          boolean smartSelectionEnabled) {
        if (selectBox == null) return false;
        final boolean[] found = new boolean[]{false};
        processSelectedText(text, selectBox, pageState, smartSelectionEnabled, new TextProcessor() {
            @Override public void onStartLine() {}
            @Override public void onWord(TextWord word) { found[0] = true; }
            @Override public void onEndLine() {}
            @Override public void onEndText() {}
        });
        return found[0];
    }

    public static void processSelectedText(TextWord[][] text,
                                           RectF selectBox,
                                           PageState pageState,
                                           boolean smartSelectionEnabled,
                                           TextProcessor tp) {
        if (text == null || selectBox == null) return;
        if (smartSelectionEnabled)
            (new TextSelector(text, selectBox, pageState.getDocRelXmin(), pageState.getDocRelXmax())).select(tp);
        else
            (new TextSelector(text, selectBox)).select(tp);
    }
}
