package org.opendroidpdf.app.comments;

import android.graphics.RectF;

import androidx.annotation.NonNull;

import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.app.selection.SidecarSelectionController;

/**
 * Navigation helpers for jumping to / selecting comment-style annotations.
 *
 * <p>Best-effort: page views and annotations load lazily; selection is retried briefly.</p>
 */
public final class CommentsNavigator {
    private CommentsNavigator() {}

    public static void jumpTo(@NonNull MuPDFReaderView docView, @NonNull CommentsIndex.Entry entry) {
        if (entry.pageIndex < 0) return;

        docView.setDisplayedViewIndex(entry.pageIndex, true);
        RectF bounds = entry.boundsDoc;
        if (bounds != null) {
            docView.doNextScrollWithCenter();
            docView.setDocRelXScroll(bounds.centerX());
            docView.setDocRelYScroll(bounds.centerY());
            docView.resetupChildren();
        }

        scheduleSelectWithRetries(docView, entry, 6);
    }

    private static void scheduleSelectWithRetries(@NonNull MuPDFReaderView docView,
                                                  @NonNull CommentsIndex.Entry entry,
                                                  int attemptsRemaining) {
        if (attemptsRemaining <= 0) return;
        AppCoroutines.launchMainDelayed(AppCoroutines.mainScope(), 80L, () -> {
            try {
                android.view.View v = docView.getSelectedView();
                if (!(v instanceof MuPDFPageView)) {
                    scheduleSelectWithRetries(docView, entry, attemptsRemaining - 1);
                    return;
                }
                MuPDFPageView pv = (MuPDFPageView) v;
                if (pv.pageNumber() != entry.pageIndex) {
                    scheduleSelectWithRetries(docView, entry, attemptsRemaining - 1);
                    return;
                }

                switch (entry.backend) {
                    case EMBEDDED:
                        if (entry.embeddedObjectNumber > 0L) {
                            pv.textAnnotationDelegate().selectEmbeddedAnnotationByObjectNumber(entry.embeddedObjectNumber);
                        }
                        break;
                    case SIDECAR:
                        if (entry.sidecarKind == SidecarSelectionController.Kind.NOTE && entry.sidecarId != null) {
                            pv.textAnnotationDelegate().selectSidecarNoteById(entry.sidecarId);
                        } else if (entry.sidecarKind == SidecarSelectionController.Kind.HIGHLIGHT && entry.sidecarId != null) {
                            pv.textAnnotationDelegate().selectSidecarHighlightById(entry.sidecarId);
                        }
                        break;
                }
            } catch (Throwable ignore) {
                scheduleSelectWithRetries(docView, entry, attemptsRemaining - 1);
            }
        });
    }
}
