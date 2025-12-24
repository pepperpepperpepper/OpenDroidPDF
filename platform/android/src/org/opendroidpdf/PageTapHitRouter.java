package org.opendroidpdf;

import android.view.MotionEvent;

import org.opendroidpdf.app.selection.SidecarSelectionController;

import java.util.Objects;

/**
 * Combines embedded (PDF/widgets/links) hit-testing with sidecar overlay hit-testing so
 * {@link MuPDFPageView} can delegate click routing without owning coordination logic.
 *
 * <p>Lives in the core package so it can work with package-private {@link Hit}.</p>
 */
final class PageTapHitRouter {
    private final PageHitRouter embeddedRouter;
    private final SidecarSelectionController sidecarSelection;

    PageTapHitRouter(PageHitRouter embeddedRouter, SidecarSelectionController sidecarSelection) {
        this.embeddedRouter = Objects.requireNonNull(embeddedRouter, "embeddedRouter required");
        this.sidecarSelection = Objects.requireNonNull(sidecarSelection, "sidecarSelection required");
    }

    Hit passClick(MotionEvent e) {
        Hit hit = embeddedRouter.passClick(e);
        if (hit != Hit.Nothing) {
            // Prefer embedded hits; drop any sidecar selection state without touching
            // the item select box that PageHitRouter just populated.
            sidecarSelection.clearSelectionStateOnly();
            return hit;
        }
        return sidecarSelection.handleTap(e) != null ? Hit.Annotation : Hit.Nothing;
    }

    Hit wouldHit(MotionEvent e) {
        Hit hit = embeddedRouter.wouldHit(e);
        if (hit != Hit.Nothing) return hit;
        return sidecarSelection.wouldHit(e) ? Hit.Annotation : Hit.Nothing;
    }
}

