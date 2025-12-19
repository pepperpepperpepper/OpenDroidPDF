package org.opendroidpdf;

import android.view.MotionEvent;

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
        MuPDFReaderView.Mode mode();
        void requestMode(MuPDFReaderView.Mode mode);
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

        MuPDFReaderView.Mode mode = host.mode();

        if ((mode == MuPDFReaderView.Mode.Viewing || mode == MuPDFReaderView.Mode.Searching) && !host.isTapDisabled()) {
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

        if (mode == MuPDFReaderView.Mode.AddingTextAnnot && !host.isTapDisabled()) {
            float scale = pageView.getScale();
            final float docWidth = pageView.getWidth() / scale;
            final float docHeight = pageView.getHeight() / scale;
            final float docRelX = (e.getX() - pageView.getLeft()) / scale;
            final float docRelY = (e.getY() - pageView.getTop()) / scale;
            final float defaultWidth = 0.35f * docWidth;
            final float defaultHeight = 0.07f * docHeight;
            float left = docRelX - defaultWidth * 0.5f;
            float right = docRelX + defaultWidth * 0.5f;
            float top = docRelY;
            float bottom = docRelY - defaultHeight;

            left = Math.max(0f, left);
            right = Math.min(docWidth, right);
            top = Math.min(docHeight, top);
            if (bottom < 0f) bottom = 0f;
            if (right <= left) right = Math.min(docWidth, left + defaultWidth);
            if (bottom >= top) bottom = Math.max(0f, top - Math.max(12f, defaultHeight * 0.5f));

            Annotation annot = new Annotation(left, top, right, bottom, Annotation.Type.FREETEXT, null, null);
            host.addTextAnnotation(annot);
            host.requestMode(MuPDFReaderView.Mode.Viewing);
            host.onTapMainDocArea();
        }
    }
}
