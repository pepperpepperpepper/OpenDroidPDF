package org.opendroidpdf.app.sidecar.model;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Immutable note/text annotation persisted in the sidecar store. */
public final class SidecarNote {
    @NonNull public final String id;
    public final int pageIndex;
    @Nullable public final String layoutProfileId;
    @NonNull public final RectF bounds;
    @Nullable public final String text;
    public final long createdAtEpochMs;

    public SidecarNote(@NonNull String id,
                       int pageIndex,
                       @Nullable String layoutProfileId,
                       @NonNull RectF bounds,
                       @Nullable String text,
                       long createdAtEpochMs) {
        this.id = id;
        this.pageIndex = pageIndex;
        this.layoutProfileId = layoutProfileId;
        this.bounds = bounds;
        this.text = text;
        this.createdAtEpochMs = createdAtEpochMs;
    }
}

