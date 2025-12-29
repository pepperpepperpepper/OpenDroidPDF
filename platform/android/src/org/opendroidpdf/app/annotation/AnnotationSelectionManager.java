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
    private long selectedObjectNumber = -1L;

    public interface Host {
        void setItemSelectBox(RectF rect);
    }

    public void select(int index, RectF bounds, Host host) {
        selectedIndex = index;
        selectedObjectNumber = -1L;
        if (host != null) host.setItemSelectBox(bounds);
    }

    /**
     * Selects an annotation while also recording a stable object id (when available).
     *
     * <p>For embedded PDF annotations, {@code objectNumber} is the stable identity that survives
     * annotation list reloads. If {@code objectNumber} is {@code <= 0}, selection falls back to
     * index-only behavior.</p>
     */
    public void select(int index, long objectNumber, RectF bounds, Host host) {
        selectedIndex = index;
        selectedObjectNumber = objectNumber;
        if (host != null) host.setItemSelectBox(bounds);
    }

    /**
     * Updates the selection to point at a stable object id, without assuming the current index.
     *
     * <p>Used during optimistic UI previews (e.g., drag-move/resize) where the annotation list
     * may be reloaded asynchronously.</p>
     */
    public void selectByObjectNumber(long objectNumber, RectF bounds, Host host) {
        selectedObjectNumber = objectNumber;
        if (host != null) host.setItemSelectBox(bounds);
    }

    public void deselect(Host host) {
        selectedIndex = -1;
        selectedObjectNumber = -1L;
        if (host != null) host.setItemSelectBox(null);
    }

    public boolean hasSelection() {
        return selectedIndex != -1;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public long selectedObjectNumber() {
        return selectedObjectNumber;
    }

    public void setSelectedIndex(int index) {
        this.selectedIndex = index;
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
