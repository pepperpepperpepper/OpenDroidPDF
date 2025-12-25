package org.opendroidpdf.app.reader.gesture;

import android.view.MotionEvent;

import org.opendroidpdf.Hit;
import org.opendroidpdf.app.selection.SidecarSelectionController;

import java.util.Objects;

/**
 * Combines embedded (PDF/widgets/links) hit-testing with sidecar overlay hit-testing so
 * page views can delegate click routing without owning coordination logic.
 */
public final class PageTapHitRouter {
    private final PageHitRouter embeddedRouter;
    private final SidecarSelectionController sidecarSelection;

    public PageTapHitRouter(PageHitRouter embeddedRouter, SidecarSelectionController sidecarSelection) {
        this.embeddedRouter = Objects.requireNonNull(embeddedRouter, "embeddedRouter required");
        this.sidecarSelection = Objects.requireNonNull(sidecarSelection, "sidecarSelection required");
    }

    public Hit passClick(MotionEvent e) {
        Hit hit = embeddedRouter.passClick(e);
        if (hit != Hit.Nothing) {
            // Prefer embedded hits; drop any sidecar selection state without touching
            // the item select box that PageHitRouter just populated.
            sidecarSelection.clearSelectionStateOnly();
            return hit;
        }
        return sidecarSelection.handleTap(e) != null ? Hit.Annotation : Hit.Nothing;
    }

    public Hit wouldHit(MotionEvent e) {
        Hit hit = embeddedRouter.wouldHit(e);
        if (hit != Hit.Nothing) return hit;
        return sidecarSelection.wouldHit(e) ? Hit.Annotation : Hit.Nothing;
    }
}
