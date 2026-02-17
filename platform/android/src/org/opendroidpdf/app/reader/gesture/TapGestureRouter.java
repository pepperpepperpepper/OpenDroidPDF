package org.opendroidpdf.app.reader.gesture;

import android.view.MotionEvent;
import android.widget.Adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.Hit;
import org.opendroidpdf.LinkInfo;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.reader.ScrollMode;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.selection.DocumentTextSelection;

/**
 * Routes single-tap handling away from MuPDFReaderView so the view can stay lean.
 */
public final class TapGestureRouter {
    private static final float SINGLE_PAGE_TAP_ZONE_FRACTION = 0.25f;

    public interface Host {
        MuPDFPageView currentPageView();
        MuPDFReaderView reader();
        boolean isTapDisabled();
        int tapPageMargin();
        boolean linksEnabled();
        ReaderMode mode();
        void requestMode(ReaderMode mode);
        void onHit(Hit item);
        void onTapMainDocArea();
        void onTapTopLeftMargin();
        void onBottomRightMargin();
        void addTextAnnotation(Annotation annot);
    }

    private final Host host;

    @Nullable private Runnable pendingSelectRetry;

    public TapGestureRouter(Host host) {
        this.host = host;
    }

    public void handleSingleTap(MotionEvent e) {
        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) return;

        // When a text annotation is selected, margin taps should behave like "deselect/exit edit"
        // rather than triggering page navigation. Capture pre-click selection before passClickEvent
        // potentially clears it.
        boolean hadSelectedTextAnnotation = wasTextAnnotationSelected(pageView);

        Hit item = pageView.passClickEvent(e);
        host.onHit(item);

        ReaderMode mode = host.mode();

        if ((mode == ReaderMode.VIEWING || mode == ReaderMode.SEARCHING) && !host.isTapDisabled()) {
            LinkInfo link = null;
            if (host.linksEnabled() &&
                    (item == Hit.LinkInternal || item == Hit.LinkExternal || item == Hit.LinkRemote) &&
                    (link = pageView.hitLink(e.getX(), e.getY())) != null) {
                LinkTapHandler.handle(host.reader(), link);
                return;
            }

            if (item == Hit.Nothing) {
                if (hadSelectedTextAnnotation) {
                    host.onTapMainDocArea();
                    return;
                }
                if (handleSinglePageTapZones(pageView, e)) {
                    return;
                }
                int margin = host.tapPageMargin();
                float x = e.getX();
                float y = e.getY();
                float left = (float) pageView.getLeft();
                float top = (float) pageView.getTop();
                float right = (float) pageView.getRight();
                float bottom = (float) pageView.getBottom();

                if (x < left || x > right || y < top || y > bottom) {
                    host.onTapMainDocArea();
                    return;
                }

                float localX = x - left;
                float localY = y - top;
                float w = (float) pageView.getWidth();
                float h = (float) pageView.getHeight();
                if (localX < margin && localY < margin) {
                    host.onTapTopLeftMargin();
                } else if (localX > w - margin && localY > h - margin) {
                    host.onBottomRightMargin();
                } else {
                    host.onTapMainDocArea();
                }
            }
            return;
        }

        if (mode == ReaderMode.ADDING_TEXT_ANNOT && !host.isTapDisabled()) {
            float scale = pageView.getScale();
            if (scale <= 0f) return;
            final float docWidth = pageView.getWidth() / scale;
            final float docHeight = pageView.getHeight() / scale;
            final float docRelX = (e.getX() - pageView.getLeft()) / scale;
            final float docRelY = (e.getY() - pageView.getTop()) / scale;
            float defaultWidth = pageView.getResources().getDimension(org.opendroidpdf.R.dimen.text_annot_default_width) / scale;
            float defaultHeight = pageView.getResources().getDimension(org.opendroidpdf.R.dimen.text_annot_default_height) / scale;

            // Clamp default size to a sane fraction of the visible page.
            defaultWidth = Math.max(1f, Math.min(docWidth * 0.90f, defaultWidth));
            defaultHeight = Math.max(1f, Math.min(docHeight * 0.30f, defaultHeight));

            // Place a small FreeText box anchored to the tap point (Acrobat-ish), then clamp to page bounds.
            float left = docRelX;
            float top = docRelY;
            float right = left + defaultWidth;
            float bottom = top + defaultHeight;

            if (right > docWidth) {
                right = docWidth;
                left = Math.max(0f, right - defaultWidth);
            }
            if (bottom > docHeight) {
                bottom = docHeight;
                top = Math.max(0f, bottom - defaultHeight);
            }

            left = Math.max(0f, left);
            right = Math.min(docWidth, right);
            top = Math.max(0f, top);
            bottom = Math.min(docHeight, bottom);
            if (right <= left) right = Math.min(docWidth, left + Math.max(12f, defaultWidth * 0.5f));
            if (bottom <= top) bottom = Math.min(docHeight, top + Math.max(12f, defaultHeight * 0.5f));

            Annotation annot = new Annotation(left, top, right, bottom, Annotation.Type.FREETEXT, null, null);
            host.addTextAnnotation(annot);
            host.requestMode(ReaderMode.VIEWING);
            host.onTapMainDocArea();
        }

        if (mode == ReaderMode.SELECTING && !host.isTapDisabled()) {
            // Tap-to-select: in selection mode, a tap should select nearby text without requiring long-press.
            if (pageView.hitsLeftMarker(e.getX(), e.getY()) || pageView.hitsRightMarker(e.getX(), e.getY())) {
                return;
            }
            if (item == Hit.Nothing) {
                selectTextAtTap(pageView, e);
            }
        }
    }

    private static boolean wasTextAnnotationSelected(MuPDFPageView pageView) {
        if (pageView == null) return false;
        try {
            Annotation a = pageView.textAnnotationDelegate().selectedEmbeddedAnnotationOrNull();
            if (a != null) {
                return a.type == Annotation.Type.FREETEXT || a.type == Annotation.Type.TEXT;
            }
        } catch (Throwable ignore) {
        }
        try {
            SidecarSelectionController.Selection sel = pageView.selectedSidecarSelectionOrNull();
            return sel != null && sel.kind == SidecarSelectionController.Kind.NOTE;
        } catch (Throwable ignore) {
        }
        return false;
    }

    private boolean handleSinglePageTapZones(@NonNull MuPDFPageView pageView, @NonNull MotionEvent e) {
        MuPDFReaderView reader = host.reader();
        if (reader == null) return false;
        if (reader.getScrollMode() != ScrollMode.PAGED) return false;

        int w = pageView.getWidth();
        if (w <= 0) return false;

        float localX = e.getX() - (float) pageView.getLeft();
        float localY = e.getY() - (float) pageView.getTop();
        if (localX < 0f || localX > w || localY < 0f || localY > pageView.getHeight()) return false;

        float x = localX;
        float leftZoneEnd = w * SINGLE_PAGE_TAP_ZONE_FRACTION;
        float rightZoneStart = w * (1f - SINGLE_PAGE_TAP_ZONE_FRACTION);
        if (x < leftZoneEnd) {
            turnPage(reader, -1);
            return true;
        }
        if (x > rightZoneStart) {
            turnPage(reader, +1);
            return true;
        }
        return false;
    }

    private void turnPage(@NonNull MuPDFReaderView reader, int direction) {
        if (direction == 0) return;
        try {
            int current = reader.getSelectedItemPosition();
            Adapter adapter = reader.getAdapter();
            int count = adapter != null ? adapter.getCount() : 0;
            int target = current + direction;
            if (target < 0 || target >= count) return;
            reader.setDisplayedViewIndex(target);
            reader.setNormalizedScroll(0.0f, 0.0f);
        } catch (Throwable ignore) {
        }
    }

    private void selectTextAtTap(@NonNull MuPDFPageView pageView, @NonNull MotionEvent e) {
        cancelPendingSelectRetry();
        final float tapX = e.getX();
        final float tapRawY = e.getRawY();
        doSelectText(pageView, tapX, tapRawY);

        if (pageView.hasTextSelected()) {
            updateDocumentSelectionFromPageView(pageView);
            host.requestMode(ReaderMode.SELECTING); // keep selection chrome visible
            return;
        }

        final MuPDFReaderView reader = host.reader();
        if (reader == null) return;
        final MuPDFPageView target = pageView;

        Runnable retry = new Runnable() {
            int attempts = 0;

            @Override public void run() {
                pendingSelectRetry = null;
                if (host.mode() != ReaderMode.SELECTING) return;
                MuPDFPageView current = host.currentPageView();
                if (current != target) return;

                if (target.hasTextSelected()) {
                    updateDocumentSelectionFromPageView(target);
                    host.requestMode(ReaderMode.SELECTING);
                    return;
                }

                attempts++;
                if (attempts >= 8) {
                    return;
                }

                doSelectText(target, tapX, tapRawY);
                pendingSelectRetry = this;
                try { reader.postDelayed(this, 120L); } catch (Throwable ignore) {}
            }
        };

        pendingSelectRetry = retry;
        try { reader.postDelayed(retry, 120L); } catch (Throwable ignore) {}
    }

    private void updateDocumentSelectionFromPageView(@NonNull MuPDFPageView pageView) {
        try {
            MuPDFReaderView reader = host.reader();
            if (reader == null || pageView == null) return;
            android.graphics.RectF box = pageView.getSelectBox();
            if (box == null) return;
            reader.setDocumentTextSelection(DocumentTextSelection.of(
                    pageView.getPageNumber(), box.left, box.top,
                    pageView.getPageNumber(), box.right, box.bottom));
        } catch (Throwable ignore) {
        }
    }

    private void doSelectText(@NonNull MuPDFPageView pageView, float tapX, float tapRawY) {
        try {
            MuPDFReaderView reader = host.reader();
            int[] locationOnScreen = new int[] {0, 0};
            if (reader != null) reader.getLocationOnScreen(locationOnScreen);
            final float x0 = tapX;
            final float y0 = tapRawY - locationOnScreen[1];
            final float x1 = x0 + 12f;
            final float y1 = y0 + 12f;
            pageView.deselectAnnotation();
            try {
                MuPDFReaderView readerView = host.reader();
                if (readerView != null) readerView.clearDocumentTextSelection();
            } catch (Throwable ignore) {
            }
            pageView.deselectText();
            pageView.selectText(x0, y0, x1, y1);
            host.requestMode(ReaderMode.SELECTING);
        } catch (Throwable ignore) {
        }
    }

    private void cancelPendingSelectRetry() {
        Runnable r = pendingSelectRetry;
        if (r == null) return;
        pendingSelectRetry = null;
        try {
            MuPDFReaderView reader = host.reader();
            if (reader != null) reader.removeCallbacks(r);
        } catch (Throwable ignore) {
        }
    }
}
