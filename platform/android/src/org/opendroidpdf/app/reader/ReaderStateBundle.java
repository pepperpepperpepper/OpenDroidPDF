package org.opendroidpdf.app.reader;

import android.os.Bundle;

/**
 * Centralizes Bundle keys and get/put helpers for ReaderView state.
 */
public final class ReaderStateBundle {
    private ReaderStateBundle() {}

    public static final String SUPER = "superInstanceState";
    public static final String CURRENT = "mCurrent";
    public static final String X = "mXScroll";
    public static final String Y = "mYScroll";
    public static final String SCROLLER_LAST_X = "mScrollerLastX";
    public static final String SCROLLER_LAST_Y = "mScrollerLastY";
    public static final String PREV_FOCUS_X = "previousFocusX";
    public static final String PREV_FOCUS_Y = "previousFocusY";
    public static final String REFLOW = "mReflow";
    public static final String SCROLL_DISABLED = "mScrollDisabled";

    public static void save(Bundle b,
                            int current, int x, int y,
                            int scrollerLastX, int scrollerLastY,
                            int prevFocusX, int prevFocusY,
                            boolean reflow, boolean scrollDisabled) {
        b.putInt(CURRENT, current);
        b.putInt(X, x);
        b.putInt(Y, y);
        b.putInt(SCROLLER_LAST_X, scrollerLastX);
        b.putInt(SCROLLER_LAST_Y, scrollerLastY);
        b.putInt(PREV_FOCUS_X, prevFocusX);
        b.putInt(PREV_FOCUS_Y, prevFocusY);
        b.putBoolean(REFLOW, reflow);
        b.putBoolean(SCROLL_DISABLED, scrollDisabled);
    }

    public static Values restore(Bundle b,
                                 int defCurrent, int defX, int defY,
                                 int defScrollerLastX, int defScrollerLastY,
                                 int defPrevFocusX, int defPrevFocusY,
                                 boolean defReflow, boolean defScrollDisabled) {
        Values v = new Values();
        v.current = b.getInt(CURRENT, defCurrent);
        v.x = b.getInt(X, defX);
        v.y = b.getInt(Y, defY);
        v.scrollerLastX = b.getInt(SCROLLER_LAST_X, defScrollerLastX);
        v.scrollerLastY = b.getInt(SCROLLER_LAST_Y, defScrollerLastY);
        v.prevFocusX = b.getInt(PREV_FOCUS_X, defPrevFocusX);
        v.prevFocusY = b.getInt(PREV_FOCUS_Y, defPrevFocusY);
        v.reflow = b.getBoolean(REFLOW, defReflow);
        v.scrollDisabled = b.getBoolean(SCROLL_DISABLED, defScrollDisabled);
        return v;
    }

    public static final class Values {
        public int current;
        public int x;
        public int y;
        public int scrollerLastX;
        public int scrollerLastY;
        public int prevFocusX;
        public int prevFocusY;
        public boolean reflow;
        public boolean scrollDisabled;
    }
}

