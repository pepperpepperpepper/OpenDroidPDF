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
    /** Optional prefix context immediately preceding {@link #quote} (whitespace-normalized). */
    @Nullable public final String quotePrefix;
    /** Optional suffix context immediately following {@link #quote} (whitespace-normalized). */
    @Nullable public final String quoteSuffix;
    /** Optional doc progression at creation time, 0..1 (or -1 when unknown). */
    public final float docProgress01;
    /**
     * Optional encoded MuPDF reflow {@code fz_location} (see {@code MuPDFCore.locationFromPageNumber}).
     * Used to target highlight re-anchoring after relayout. {@code -1} when unknown/unsupported.
     */
    public final long reflowLocation;
    /**
     * Optional word-range anchor within the page's extracted word stream.
     * Inclusive start word index, or {@code -1} when unknown.
     */
    public final int anchorStartWord;
    /**
     * Optional word-range anchor within the page's extracted word stream.
     * Exclusive end word index, or {@code -1} when unknown.
     */
    public final int anchorEndWordExclusive;

    public SidecarHighlight(@NonNull String id,
                            int pageIndex,
                            @Nullable String layoutProfileId,
                            @NonNull Annotation.Type type,
                            int color,
                            float opacity,
                            long createdAtEpochMs,
                            @NonNull PointF[] quadPoints,
                            @Nullable String quote,
                            @Nullable String quotePrefix,
                            @Nullable String quoteSuffix,
                            float docProgress01,
                            long reflowLocation,
                            int anchorStartWord,
                            int anchorEndWordExclusive) {
        this.id = id;
        this.pageIndex = pageIndex;
        this.layoutProfileId = layoutProfileId;
        this.type = type;
        this.color = color;
        this.opacity = opacity;
        this.createdAtEpochMs = createdAtEpochMs;
        this.quadPoints = quadPoints;
        this.quote = quote;
        this.quotePrefix = quotePrefix;
        this.quoteSuffix = quoteSuffix;
        this.docProgress01 = docProgress01;
        this.reflowLocation = reflowLocation;
        this.anchorStartWord = anchorStartWord;
        this.anchorEndWordExclusive = anchorEndWordExclusive;
    }
}
