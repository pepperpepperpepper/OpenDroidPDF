package org.opendroidpdf.app.annotation;

import org.opendroidpdf.MuPDFPageView;

/**
 * Tracks annotation selection/editable state off the activity and exposes simple setters/getters.
 */
public class AnnotationSelectionController {
    private boolean selectedAnnotationIsEditable = false;

    public boolean isSelectedAnnotationEditable() { return selectedAnnotationIsEditable; }
    public void setSelectedAnnotationEditable(boolean editable) { selectedAnnotationIsEditable = editable; }

    public void updateFromPageView(MuPDFPageView pageView) {
        if (pageView != null) {
            selectedAnnotationIsEditable = pageView.selectedAnnotationIsEditable();
        }
    }
}
