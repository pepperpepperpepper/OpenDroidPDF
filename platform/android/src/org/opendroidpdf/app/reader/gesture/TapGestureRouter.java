package org.opendroidpdf.app.reader.gesture;

import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.Hit;
import org.opendroidpdf.LinkInfo;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.selection.SidecarSelectionController;

/**
 * Routes single-tap handling away from MuPDFReaderView so the view can stay lean.
 */
public final class TapGestureRouter {

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
                int margin = host.tapPageMargin();
                if (e.getX() > pageView.getWidth() - margin)
                    host.onBottomRightMargin();
                else if (e.getX() < margin)
                    host.onTapTopLeftMargin();
                else if (e.getY() > pageView.getHeight() - margin)
                    host.onBottomRightMargin();
                else if (e.getY() < margin)
                    host.onTapTopLeftMargin();
                else
                    host.onTapMainDocArea();
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

    private void selectTextAtTap(@NonNull MuPDFPageView pageView, @NonNull MotionEvent e) {
        cancelPendingSelectRetry();
        final float tapX = e.getX();
        final float tapRawY = e.getRawY();
        doSelectText(pageView, tapX, tapRawY);

        if (pageView.hasTextSelected()) {
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
