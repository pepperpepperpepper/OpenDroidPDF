package org.opendroidpdf.app.sidecar.model;

import android.graphics.PointF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Immutable ink stroke persisted in the sidecar store. */
public final class SidecarInkStroke {
    @NonNull public final String id;
    public final int pageIndex;
    @Nullable public final String layoutProfileId;
    public final int color;
    public final float thickness;
    public final long createdAtEpochMs;
    @NonNull public final PointF[] points;

    public SidecarInkStroke(@NonNull String id,
                            int pageIndex,
                            @Nullable String layoutProfileId,
                            int color,
                            float thickness,
                            long createdAtEpochMs,
                            @NonNull PointF[] points) {
        this.id = id;
        this.pageIndex = pageIndex;
        this.layoutProfileId = layoutProfileId;
        this.color = color;
        this.thickness = thickness;
        this.createdAtEpochMs = createdAtEpochMs;
        this.points = points;
    }
}

