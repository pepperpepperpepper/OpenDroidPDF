package org.opendroidpdf.app.services.recent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Immutable viewport snapshot (page + normalized scale/scroll). */
public final class ViewportSnapshot {
    private final int page;
    private final float normalizedScale;
    private final float normalizedXScroll;
    private final float normalizedYScroll;
    private final float docProgress01;
    @Nullable private final String layoutProfileId;
    /**
     * Encoded MuPDF {@code fz_location} as {@code (chapter<<32) | (page & 0xffffffff)}.
     * <p>
     * For reflowable documents (EPUB), this is stable across relayout and can be used to restore
     * position even when page numbers change.
     */
    private final long reflowLocation;

    public ViewportSnapshot(int page, float normalizedScale, float normalizedXScroll, float normalizedYScroll) {
        this(page, normalizedScale, normalizedXScroll, normalizedYScroll, -1f, null, -1L);
    }

    public ViewportSnapshot(int page,
                            float normalizedScale,
                            float normalizedXScroll,
                            float normalizedYScroll,
                            float docProgress01,
                            @Nullable String layoutProfileId) {
        this(page, normalizedScale, normalizedXScroll, normalizedYScroll, docProgress01, layoutProfileId, -1L);
    }

    public ViewportSnapshot(int page,
                            float normalizedScale,
                            float normalizedXScroll,
                            float normalizedYScroll,
                            float docProgress01,
                            @Nullable String layoutProfileId,
                            long reflowLocation) {
        this.page = page;
        this.normalizedScale = normalizedScale;
        this.normalizedXScroll = normalizedXScroll;
        this.normalizedYScroll = normalizedYScroll;
        this.docProgress01 = docProgress01;
        this.layoutProfileId = layoutProfileId;
        this.reflowLocation = reflowLocation;
    }

    public int page() { return page; }
    public float normalizedScale() { return normalizedScale; }
    public float normalizedXScroll() { return normalizedXScroll; }
    public float normalizedYScroll() { return normalizedYScroll; }
    /** 0..1 when available, -1 otherwise. Useful for restoring position across reflow relayout. */
    public float docProgress01() { return docProgress01; }
    @Nullable public String layoutProfileId() { return layoutProfileId; }
    /** Encoded MuPDF reflow location, or {@code -1}. */
    public long reflowLocation() { return reflowLocation; }

    public ViewportSnapshot withLayoutProfileId(@Nullable String layoutProfileId) {
        return new ViewportSnapshot(page, normalizedScale, normalizedXScroll, normalizedYScroll, docProgress01, layoutProfileId, reflowLocation);
    }

    public ViewportSnapshot withDocProgress01(float docProgress01) {
        return new ViewportSnapshot(page, normalizedScale, normalizedXScroll, normalizedYScroll, docProgress01, layoutProfileId, reflowLocation);
    }

    public ViewportSnapshot withReflowLocation(long reflowLocation) {
        return new ViewportSnapshot(page, normalizedScale, normalizedXScroll, normalizedYScroll, docProgress01, layoutProfileId, reflowLocation);
    }

    @NonNull @Override public String toString() {
        return "ViewportSnapshot{page=" + page + ", scale=" + normalizedScale +
                ", x=" + normalizedXScroll + ", y=" + normalizedYScroll +
                (docProgress01 >= 0f ? ", p=" + docProgress01 : "") +
                (layoutProfileId != null ? ", layout=" + layoutProfileId : "") +
                (reflowLocation != -1L ? ", loc=" + reflowLocation : "") +
                "}";
    }
}
