package org.opendroidpdf.app.reader.gesture;

import android.view.MotionEvent;
import android.view.View;

import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.selection.DocumentTextSelection;

/**
 * Handles text-selection marker drag gestures to keep MuPDFReaderView smaller.
 */
final class SelectionGestureHandler {

    interface Host {
        MuPDFPageView currentPageView();
        ReaderMode mode();
        MuPDFReaderView reader();
    }

    private final Host host;
    private boolean scrollStartedAtLeftMarker = false;
    private boolean scrollStartedAtRightMarker = false;
    private MuPDFPageView activeHandlePage = null;

    SelectionGestureHandler(Host host) {
        this.host = host;
    }

    /**
     * @return true if the gesture was consumed by selection handling.
     */
    boolean onScroll(MotionEvent e1, MotionEvent e2) {
        if (host.mode() != ReaderMode.SELECTING) return false;
        final MuPDFReaderView reader = host.reader();
        if (reader == null) return false;

        // Resolve which page the gesture began on for marker hit-testing.
        MuPDFPageView downPage = activeHandlePage;
        if (downPage == null) {
            downPage = pageViewUnderPoint(reader, e1.getX(), e1.getY());
            if (downPage == null) downPage = host.currentPageView();
        }
        if (downPage == null) return false;

        if ((downPage.hitsLeftMarker(e1.getX(), e1.getY()) || scrollStartedAtLeftMarker)
                && !scrollStartedAtRightMarker) {
            scrollStartedAtLeftMarker = true;
            activeHandlePage = downPage;
            return dragHandle(reader, /*dragLeft*/true, e2);
        }

        if ((downPage.hitsRightMarker(e1.getX(), e1.getY()) || scrollStartedAtRightMarker)
                && !scrollStartedAtLeftMarker) {
            scrollStartedAtRightMarker = true;
            activeHandlePage = downPage;
            return dragHandle(reader, /*dragLeft*/false, e2);
        }

        return false;
    }

    void reset() {
        scrollStartedAtLeftMarker = false;
        scrollStartedAtRightMarker = false;
        activeHandlePage = null;
    }

    private boolean dragHandle(MuPDFReaderView reader, boolean dragLeft, MotionEvent e2) {
        MuPDFPageView handlePage = activeHandlePage;
        if (handlePage == null) return false;

        DocumentTextSelection sel = ensureDocumentSelection(reader, handlePage);
        if (sel == null) return false;

        MuPDFPageView targetPage = pageViewUnderPoint(reader, e2.getX(), e2.getY());
        if (targetPage == null) {
            targetPage = inferAdjacentPageIfInGap(reader, handlePage, e2.getY(), dragLeft);
        }
        if (targetPage == null) targetPage = handlePage;

        // Same-page drag: keep legacy selection-controller behavior so PageState bounds stay in sync.
        if (targetPage == handlePage) {
            if (dragLeft) {
                handlePage.moveLeftMarker(e2.getX(), e2.getY());
            } else {
                handlePage.moveRightMarker(e2.getX(), e2.getY());
            }
            android.graphics.RectF box = handlePage.getSelectBox();
            if (box == null) return true;

            if (dragLeft) {
                int startPage = handlePage.getPageNumber();
                float startX = box.left;
                float startY = box.top;
                if (compare(startPage, startX, startY, sel.endPage, sel.endX, sel.endY) > 0) {
                    startPage = sel.endPage;
                    startX = sel.endX;
                    startY = sel.endY;
                }
                reader.setDocumentTextSelection(DocumentTextSelection.of(
                        startPage, startX, startY,
                        sel.endPage, sel.endX, sel.endY));
            } else {
                int endPage = handlePage.getPageNumber();
                float endX = box.right;
                float endY = box.bottom;
                if (compare(sel.startPage, sel.startX, sel.startY, endPage, endX, endY) > 0) {
                    endPage = sel.startPage;
                    endX = sel.startX;
                    endY = sel.startY;
                }
                reader.setDocumentTextSelection(DocumentTextSelection.of(
                        sel.startPage, sel.startX, sel.startY,
                        endPage, endX, endY));
            }
            return true;
        }

        float scale = targetPage.getScale();
        if (scale <= 0f) return true;
        float docX = (e2.getX() - targetPage.getLeft()) / scale;
        float docY = (e2.getY() - targetPage.getTop()) / scale;

        if (dragLeft) {
            int startPage = targetPage.getPageNumber();
            float startX = docX;
            float startY = docY;
            if (compare(startPage, startX, startY, sel.endPage, sel.endX, sel.endY) > 0) {
                startPage = sel.endPage;
                startX = sel.endX;
                startY = sel.endY;
            }
            DocumentTextSelection next = DocumentTextSelection.of(
                    startPage, startX, startY,
                    sel.endPage, sel.endX, sel.endY);
            reader.setDocumentTextSelection(next);
            if (next.startPage == targetPage.getPageNumber()) activeHandlePage = targetPage;
        } else {
            int endPage = targetPage.getPageNumber();
            float endX = docX;
            float endY = docY;
            if (compare(sel.startPage, sel.startX, sel.startY, endPage, endX, endY) > 0) {
                endPage = sel.startPage;
                endX = sel.startX;
                endY = sel.startY;
            }
            DocumentTextSelection next = DocumentTextSelection.of(
                    sel.startPage, sel.startX, sel.startY,
                    endPage, endX, endY);
            reader.setDocumentTextSelection(next);
            if (next.endPage == targetPage.getPageNumber()) activeHandlePage = targetPage;
        }
        return true;
    }

    private DocumentTextSelection ensureDocumentSelection(MuPDFReaderView reader, MuPDFPageView pageView) {
        DocumentTextSelection existing = reader.getDocumentTextSelectionOrNull();
        if (existing != null) return existing;
        if (pageView == null) return null;
        android.graphics.RectF box = pageView.getSelectBox();
        if (box == null) return null;
        DocumentTextSelection sel = DocumentTextSelection.of(
                pageView.getPageNumber(), box.left, box.top,
                pageView.getPageNumber(), box.right, box.bottom);
        reader.setDocumentTextSelection(sel);
        return sel;
    }

    private static int compare(int pageA, float xA, float yA, int pageB, float xB, float yB) {
        if (pageA != pageB) return pageA < pageB ? -1 : 1;
        if (yA != yB) return yA < yB ? -1 : 1;
        if (xA == xB) return 0;
        return xA < xB ? -1 : 1;
    }

    private MuPDFPageView pageViewUnderPoint(MuPDFReaderView reader, float x, float y) {
        if (reader == null) return null;
        int center = reader.getSelectedItemPosition();
        for (int delta = -2; delta <= 2; delta++) {
            int idx = center + delta;
            View v = reader.getView(idx);
            if (!(v instanceof MuPDFPageView)) continue;
            MuPDFPageView pv = (MuPDFPageView) v;
            if (x >= pv.getLeft() && x <= pv.getRight()
                    && y >= pv.getTop() && y <= pv.getBottom()) {
                return pv;
            }
        }
        return null;
    }

    private MuPDFPageView inferAdjacentPageIfInGap(MuPDFReaderView reader,
                                                   MuPDFPageView handlePage,
                                                   float y,
                                                   boolean dragLeft) {
        if (reader == null || handlePage == null) return null;
        int current = handlePage.getPageNumber();
        if (y < handlePage.getTop()) {
            View prev = reader.getView(current - 1);
            return prev instanceof MuPDFPageView ? (MuPDFPageView) prev : null;
        }
        if (y > handlePage.getBottom()) {
            View next = reader.getView(current + 1);
            return next instanceof MuPDFPageView ? (MuPDFPageView) next : null;
        }
        return null;
    }
}
