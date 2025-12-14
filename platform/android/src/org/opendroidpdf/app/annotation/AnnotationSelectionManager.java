package org.opendroidpdf.app.annotation;

import android.graphics.RectF;

import org.opendroidpdf.Annotation;

/**
 * Manages selection state for annotations on a single page.
 * Keeps the selected index and exposes convenience helpers
 * so MuPDFPageView doesn't have to track selection bookkeeping.
 */
public class AnnotationSelectionManager {
    private int selectedIndex = -1;

    public interface Host {
        void setItemSelectBox(RectF rect);
    }

    public void select(int index, RectF bounds, Host host) {
        selectedIndex = index;
        if (host != null) host.setItemSelectBox(bounds);
    }

    public void deselect(Host host) {
        selectedIndex = -1;
        if (host != null) host.setItemSelectBox(null);
    }

    public boolean hasSelection() {
        return selectedIndex != -1;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public Annotation.Type selectedType(Annotation[] annotations) {
        if (!hasSelection() || annotations == null || selectedIndex >= annotations.length) return null;
        return annotations[selectedIndex].type;
    }

    public boolean isEditable(Annotation[] annotations) {
        if (!hasSelection() || annotations == null || selectedIndex >= annotations.length) return false;
        Annotation selected = annotations[selectedIndex];
        return selected != null && (selected.arcs != null || selected.text != null);
    }
}
