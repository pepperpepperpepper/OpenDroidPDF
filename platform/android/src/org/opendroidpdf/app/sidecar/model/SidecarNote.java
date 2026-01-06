package org.opendroidpdf.app.sidecar.model;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Immutable note/text annotation persisted in the sidecar store. */
public final class SidecarNote {
    /** Default text color used when migrating older rows without a stored color. */
    public static final int DEFAULT_COLOR = 0xFF111111;
    /** Default font size (doc units) used when migrating older rows without a stored size. */
    public static final float DEFAULT_FONT_SIZE = 12.0f;

    @NonNull public final String id;
    public final int pageIndex;
    @Nullable public final String layoutProfileId;
    @NonNull public final RectF bounds;
    @Nullable public final String text;
    public final long createdAtEpochMs;
    public final int color;
    public final float fontSize;
    public final boolean userResized;

    public SidecarNote(@NonNull String id,
                       int pageIndex,
                       @Nullable String layoutProfileId,
                       @NonNull RectF bounds,
                       @Nullable String text,
                       long createdAtEpochMs,
                       int color,
                       float fontSize) {
        this(id, pageIndex, layoutProfileId, bounds, text, createdAtEpochMs, color, fontSize, false);
    }

    public SidecarNote(@NonNull String id,
                       int pageIndex,
                       @Nullable String layoutProfileId,
                       @NonNull RectF bounds,
                       @Nullable String text,
                       long createdAtEpochMs,
                       int color,
                       float fontSize,
                       boolean userResized) {
        this.id = id;
        this.pageIndex = pageIndex;
        this.layoutProfileId = layoutProfileId;
        this.bounds = bounds;
        this.text = text;
        this.createdAtEpochMs = createdAtEpochMs;
        this.color = color;
        this.fontSize = fontSize;
        this.userResized = userResized;
    }
}
