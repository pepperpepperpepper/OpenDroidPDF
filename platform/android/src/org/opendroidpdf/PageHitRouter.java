package org.opendroidpdf;

import android.graphics.RectF;
import android.view.MotionEvent;

import org.opendroidpdf.core.WidgetController;
import org.opendroidpdf.core.WidgetController.WidgetJob;
import org.opendroidpdf.core.WidgetPassClickCallback;
import org.opendroidpdf.PassClickResultVisitor;
import org.opendroidpdf.PassClickResultText;
import org.opendroidpdf.PassClickResultChoice;
import org.opendroidpdf.PassClickResultSignature;

import java.util.Objects;

/**
 * Centralizes hit-testing and widget pass-click routing for a PageView.
 * Lives in the core package so it can work with package-private Hit.
 */
class PageHitRouter {
    interface Host {
        float scale();
        int viewLeft();
        int viewTop();
        int pageNumber();

        LinkInfo[] links();
        Annotation[] annotations();
        RectF[] widgetAreas();

        AnnotationHitHelper annotationHitHelper();
        WidgetController widgetController();
        void setWidgetJob(WidgetJob job);

        void deselectAnnotation();
        void selectAnnotation(int index, RectF bounds);
        void onTextAnnotationTapped(Annotation annotation);

        void requestChangeReport();
        void invokeTextDialog(String text);
        void invokeChoiceDialog(String[] options);
        void warnNoSignatureSupport();
        void invokeSigningDialog();
        void invokeSignatureCheckingDialog();
    }

    private final Host host;

    PageHitRouter(Host host) {
        this.host = Objects.requireNonNull(host, "host required");
    }

    Hit passClick(MotionEvent e) {
        float docRelX = docRelX(e);
        float docRelY = docRelY(e);

        Hit linkHit = linkHit(docRelX, docRelY);
        if (linkHit != Hit.Nothing) return linkHit;

        Hit annotationHit = annotationHit(docRelX, docRelY, true);
        if (annotationHit != Hit.Nothing) return annotationHit;

        if (!host.widgetController().javascriptSupported()) return Hit.Nothing;

        if (widgetAreaHit(docRelX, docRelY)) {
            dispatchWidgetPassClick(docRelX, docRelY);
            return Hit.Widget;
        }

        return Hit.Nothing;
    }

    Hit wouldHit(MotionEvent e) {
        float docRelX = docRelX(e);
        float docRelY = docRelY(e);

        Hit linkHit = linkHit(docRelX, docRelY);
        if (linkHit != Hit.Nothing) return linkHit;

        Hit annotationHit = annotationHit(docRelX, docRelY, false);
        if (annotationHit != Hit.Nothing) return annotationHit;

        if (!host.widgetController().javascriptSupported()) return Hit.Nothing;
        if (widgetAreaHit(docRelX, docRelY)) return Hit.Widget;

        return Hit.Nothing;
    }

    private float docRelX(MotionEvent e) {
        return (e.getX() - host.viewLeft()) / host.scale();
    }

    private float docRelY(MotionEvent e) {
        return (e.getY() - host.viewTop()) / host.scale();
    }

    private boolean widgetAreaHit(float docRelX, float docRelY) {
        RectF[] areas = host.widgetAreas();
        if (areas == null) return false;
        for (RectF area : areas) {
            if (area != null && area.contains(docRelX, docRelY)) return true;
        }
        return false;
    }

    private Hit linkHit(float docRelX, float docRelY) {
        LinkInfo[] links = host.links();
        if (links == null) return Hit.Nothing;
        for (LinkInfo l : links) {
            if (l.rect.contains(docRelX, docRelY)) {
                switch (l.type()) {
                    case Internal: return Hit.LinkInternal;
                    case External: return Hit.LinkExternal;
                    case Remote: return Hit.LinkRemote;
                    default: return Hit.Link;
                }
            }
        }
        return Hit.Nothing;
    }

    private Hit annotationHit(float docRelX, float docRelY, boolean applySelection) {
        AnnotationHitHelper helper = host.annotationHitHelper();
        if (helper == null) return Hit.Nothing;
        Annotation[] annots = host.annotations();
        return helper.handle(
                docRelX,
                docRelY,
                annots,
                applySelection ? new AnnotationHitHelper.Host() {
                    @Override public void deselectAnnotation() { host.deselectAnnotation(); }
                    @Override public void selectAnnotation(int index, RectF bounds) { host.selectAnnotation(index, bounds); }
                    @Override public void onTextAnnotationTapped(Annotation annotation) { host.onTextAnnotationTapped(annotation); }
                } : null,
                applySelection ? 1 : 0,
                applySelection
        );
    }

    private void dispatchWidgetPassClick(float docRelX, float docRelY) {
        WidgetJob job = host.widgetController().passClickAsync(
                host.pageNumber(),
                docRelX,
                docRelY,
                result -> {
                    if (result.changed) host.requestChangeReport();
                    result.acceptVisitor(new PassClickResultVisitor() {
                        @Override public void visitText(PassClickResultText result) { host.invokeTextDialog(result.text); }
                        @Override public void visitChoice(PassClickResultChoice result) { host.invokeChoiceDialog(result.options); }
                        @Override public void visitSignature(PassClickResultSignature result) {
                            switch (result.state) {
                                case NoSupport: host.warnNoSignatureSupport(); break;
                                case Unsigned: host.invokeSigningDialog(); break;
                                case Signed: host.invokeSignatureCheckingDialog(); break;
                            }
                        }
                    });
                });
        host.setWidgetJob(job);
    }
}
