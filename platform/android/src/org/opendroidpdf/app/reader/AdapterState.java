package org.opendroidpdf.app.reader;

/**
 * Tracks pending adapter index switches for ReaderView.
 * Keeps the legacy semantics: an index is only applied when
 * flagged as "countsAsNewCurrent".
 */
public final class AdapterState {
    private int lastRequestedIndex = -1;
    private int pendingIndex = -1;
    private boolean hasPending = false;

    public void requestSetDisplayedIndex(int index, boolean countsAsNewCurrent) {
        lastRequestedIndex = index;
        if (countsAsNewCurrent) {
            pendingIndex = index;
            hasPending = true;
        }
    }

    public boolean hasPending() { return hasPending; }
    public int getPendingIndex() { return pendingIndex; }
    public void clearPending() { hasPending = false; }
    public int getLastRequestedIndex() { return lastRequestedIndex; }
}

