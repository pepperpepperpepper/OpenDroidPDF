package org.opendroidpdf.app.services.recent;

import androidx.annotation.NonNull;

/** Immutable viewport snapshot (page + normalized scale/scroll). */
public final class ViewportSnapshot {
    private final int page;
    private final float normalizedScale;
    private final float normalizedXScroll;
    private final float normalizedYScroll;

    public ViewportSnapshot(int page, float normalizedScale, float normalizedXScroll, float normalizedYScroll) {
        this.page = page;
        this.normalizedScale = normalizedScale;
        this.normalizedXScroll = normalizedXScroll;
        this.normalizedYScroll = normalizedYScroll;
    }

    public int page() { return page; }
    public float normalizedScale() { return normalizedScale; }
    public float normalizedXScroll() { return normalizedXScroll; }
    public float normalizedYScroll() { return normalizedYScroll; }

    @NonNull @Override public String toString() {
        return "ViewportSnapshot{page=" + page + ", scale=" + normalizedScale +
                ", x=" + normalizedXScroll + ", y=" + normalizedYScroll + "}";
    }
}
