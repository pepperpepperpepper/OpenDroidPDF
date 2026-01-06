package org.opendroidpdf.app.reader.gesture;

import android.view.MotionEvent;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.Hit;
import org.opendroidpdf.LinkInfo;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;

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

    public TapGestureRouter(Host host) {
        this.host = host;
    }

    public void handleSingleTap(MotionEvent e) {
        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) return;

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
    }
}
