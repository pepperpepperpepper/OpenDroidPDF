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

    public SidecarHighlight(@NonNull String id,
                            int pageIndex,
                            @Nullable String layoutProfileId,
                            @NonNull Annotation.Type type,
                            int color,
                            float opacity,
                            long createdAtEpochMs,
                            @NonNull PointF[] quadPoints) {
        this.id = id;
        this.pageIndex = pageIndex;
        this.layoutProfileId = layoutProfileId;
        this.type = type;
        this.color = color;
        this.opacity = opacity;
        this.createdAtEpochMs = createdAtEpochMs;
        this.quadPoints = quadPoints;
    }
}

