package org.opendroidpdf.app.services.recent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Immutable viewport snapshot (page + normalized scale/scroll). */
public final class ViewportSnapshot {
    private final int page;
    private final float normalizedScale;
    private final float normalizedXScroll;
    private final float normalizedYScroll;
    @Nullable private final String layoutProfileId;

    public ViewportSnapshot(int page, float normalizedScale, float normalizedXScroll, float normalizedYScroll) {
        this(page, normalizedScale, normalizedXScroll, normalizedYScroll, null);
    }

    public ViewportSnapshot(int page,
                            float normalizedScale,
                            float normalizedXScroll,
                            float normalizedYScroll,
                            @Nullable String layoutProfileId) {
        this.page = page;
        this.normalizedScale = normalizedScale;
        this.normalizedXScroll = normalizedXScroll;
        this.normalizedYScroll = normalizedYScroll;
        this.layoutProfileId = layoutProfileId;
    }

    public int page() { return page; }
    public float normalizedScale() { return normalizedScale; }
    public float normalizedXScroll() { return normalizedXScroll; }
    public float normalizedYScroll() { return normalizedYScroll; }
    @Nullable public String layoutProfileId() { return layoutProfileId; }

    public ViewportSnapshot withLayoutProfileId(@Nullable String layoutProfileId) {
        return new ViewportSnapshot(page, normalizedScale, normalizedXScroll, normalizedYScroll, layoutProfileId);
    }

    @NonNull @Override public String toString() {
        return "ViewportSnapshot{page=" + page + ", scale=" + normalizedScale +
                ", x=" + normalizedXScroll + ", y=" + normalizedYScroll +
                (layoutProfileId != null ? ", layout=" + layoutProfileId : "") +
                "}";
    }
}
