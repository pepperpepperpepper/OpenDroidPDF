package org.opendroidpdf.app.selection;

import android.graphics.RectF;

import androidx.annotation.NonNull;

/**
 * Document-level text selection range that can span multiple pages.
 *
 * <p>This is intentionally a lightweight value object; the reader/controller is responsible
 * for applying it to visible PageViews and for extracting text across pages.</p>
 */
public final class DocumentTextSelection {
    private static final float HUGE = 1.0e9f;

    public final int startPage;
    public final float startX;
    public final float startY;

    public final int endPage;
    public final float endX;
    public final float endY;

    private DocumentTextSelection(int startPage, float startX, float startY,
                                  int endPage, float endX, float endY) {
        this.startPage = startPage;
        this.startX = startX;
        this.startY = startY;
        this.endPage = endPage;
        this.endX = endX;
        this.endY = endY;
    }

    @NonNull
    public static DocumentTextSelection of(int startPage, float startX, float startY,
                                           int endPage, float endX, float endY) {
        if (compare(startPage, startX, startY, endPage, endX, endY) <= 0) {
            return new DocumentTextSelection(startPage, startX, startY, endPage, endX, endY);
        }
        return new DocumentTextSelection(endPage, endX, endY, startPage, startX, startY);
    }

    public boolean containsPage(int pageIndex) {
        return pageIndex >= startPage && pageIndex <= endPage;
    }

    /**
     * Returns a page-local selection box in document-relative coordinates suitable for the
     * existing page-level selection renderers/selectors.
     */
    @NonNull
    public RectF selectionBoxForPage(int pageIndex) {
        if (startPage == endPage) {
            return new RectF(startX, startY, endX, endY);
        }
        if (pageIndex == startPage) {
            // Start on this page; extend to bottom.
            return new RectF(startX, startY, startX, HUGE);
        }
        if (pageIndex == endPage) {
            // Selection continues from the top; end on this page.
            return new RectF(endX, -HUGE, endX, endY);
        }
        // Full-page selection (within the global X band).
        return new RectF(0f, -HUGE, 0f, HUGE);
    }

    public boolean showLeftHandleOnPage(int pageIndex) {
        if (startPage == endPage) return pageIndex == startPage;
        return pageIndex == startPage;
    }

    public boolean showRightHandleOnPage(int pageIndex) {
        if (startPage == endPage) return pageIndex == endPage;
        return pageIndex == endPage;
    }

    public float globalXMin() {
        return Math.min(startX, endX);
    }

    public float globalXMax() {
        return Math.max(startX, endX);
    }

    private static int compare(int pageA, float xA, float yA, int pageB, float xB, float yB) {
        if (pageA != pageB) return pageA < pageB ? -1 : 1;
        if (yA != yB) return yA < yB ? -1 : 1;
        if (xA == xB) return 0;
        return xA < xB ? -1 : 1;
    }
}

