package org.opendroidpdf.app.sidecar.model;

import android.graphics.PointF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;

/** Immutable text markup persisted in the sidecar store. */
public final class SidecarHighlight {
    @NonNull public final String id;
    public final int pageIndex;
    @Nullable public final String layoutProfileId;
    @NonNull public final Annotation.Type type;
    public final int color;
    public final float opacity;
    public final long createdAtEpochMs;
    @NonNull public final PointF[] quadPoints;
    /** Optional selected-text anchor for reflow relayout re-anchoring. */
    @Nullable public final String quote;
    /** Optional doc progression at creation time, 0..1 (or -1 when unknown). */
    public final float docProgress01;

    public SidecarHighlight(@NonNull String id,
                            int pageIndex,
                            @Nullable String layoutProfileId,
                            @NonNull Annotation.Type type,
                            int color,
                            float opacity,
                            long createdAtEpochMs,
                            @NonNull PointF[] quadPoints,
                            @Nullable String quote,
                            float docProgress01) {
        this.id = id;
        this.pageIndex = pageIndex;
        this.layoutProfileId = layoutProfileId;
        this.type = type;
        this.color = color;
        this.opacity = opacity;
        this.createdAtEpochMs = createdAtEpochMs;
        this.quadPoints = quadPoints;
        this.quote = quote;
        this.docProgress01 = docProgress01;
    }
}
