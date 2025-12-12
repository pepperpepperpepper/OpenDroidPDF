package org.opendroidpdf;

import android.view.View;

/**
 * Centralizes common annotation mode operations so the activity only delegates.
 * Placed in base package to access package-private APIs on MuPDFReaderView/PageView.
 */
public final class AnnotationModeController {
    private AnnotationModeController() {}

    public static void switchToDrawingMode(MuPDFReaderView docView) {
        if (docView != null) {
            docView.setMode(MuPDFReaderView.Mode.Drawing);
        }
    }

    public static void switchToErasingMode(MuPDFReaderView docView) {
        if (docView != null) {
            docView.setMode(MuPDFReaderView.Mode.Erasing);
        }
    }

    public static void switchToViewingMode(MuPDFReaderView docView) {
        if (docView != null) {
            docView.setMode(MuPDFReaderView.Mode.Viewing);
        }
    }

    public static void switchToAddingTextMode(MuPDFReaderView docView) {
        if (docView != null) {
            docView.setMode(MuPDFReaderView.Mode.AddingTextAnnot);
        }
    }

    /**
     * Cancel out of drawing/editing states; mirrors legacy behavior.
     */
    public static void cancelAnnotationMode(MuPDFReaderView docView,
                                            org.opendroidpdf.app.ui.ActionBarMode currentMode) {
        if (docView == null || currentMode == null) return;
        switch (currentMode) {
            case Annot:
            case Edit:
            case AddingTextAnnot:
                docView.setMode(MuPDFReaderView.Mode.Viewing);
                break;
            default:
                // no-op
                break;
        }
    }

    /**
     * Persist edits if needed when confirming annotation changes.
     */
    public static void confirmAnnotationChanges(MuPDFReaderView docView,
                                                org.opendroidpdf.app.ui.ActionBarMode currentMode) {
        if (docView == null || currentMode == null) return;
        PageView page = getActivePageView(docView);
        switch (currentMode) {
            case Annot:
                if (page != null) {
                    page.saveDraw();
                    docView.onNumberOfStrokesChanged(page.getDrawingSize());
                }
                break;
            case Edit:
                if (page != null) {
                    page.deselectAnnotation();
                }
                break;
            default:
                break;
        }
        if (currentMode == org.opendroidpdf.app.ui.ActionBarMode.Annot ||
            currentMode == org.opendroidpdf.app.ui.ActionBarMode.Edit) {
            docView.setMode(MuPDFReaderView.Mode.Viewing);
        }
    }

    /**
     * If a pen settings change is about to happen, ensure any in‑progress strokes are committed
     * so historical marks keep their original thickness/color.
     */
    public static void finalizePendingInkBeforePenSettingChange(MuPDFReaderView docView) {
        if (docView == null) return;
        try {
            MuPDFView view = (MuPDFView) docView.getSelectedView();
            if (view instanceof MuPDFPageView) {
                MuPDFPageView pageView = (MuPDFPageView) view;
                android.graphics.PointF[][] pending = pageView.getDraw();
                if (pending != null && pending.length > 0) {
                    pageView.saveDraw();
                    docView.onNumberOfStrokesChanged(pageView.getDrawingSize());
                }
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * Notify the view about changed stroke count after save/undo.
     */
    public static void notifyStrokeCountChanged(MuPDFReaderView docView, int strokeCount) {
        if (docView != null) {
            docView.onNumberOfStrokesChanged(strokeCount);
        }
    }

    public static PageView getActivePageView(MuPDFReaderView docView) {
        if (docView == null) return null;
        View selected = docView.getSelectedView();
        if (selected instanceof PageView) {
            return (PageView) selected;
        }
        return null;
    }
}
