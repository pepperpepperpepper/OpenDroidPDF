package org.opendroidpdf.app.assistant;

import androidx.annotation.Nullable;

public final class AssistantContextSnapshot {
    public enum Kind {
        SELECTION,
        PAGE,
        DOCUMENT
    }

    private final Kind kind;
    private final String documentTitle;
    private final int pageIndex;
    @Nullable private final String text;
    private final boolean truncated;

    public AssistantContextSnapshot(
            Kind kind,
            String documentTitle,
            int pageIndex,
            @Nullable String text,
            boolean truncated
    ) {
        this.kind = kind != null ? kind : Kind.PAGE;
        this.documentTitle = documentTitle != null ? documentTitle : "";
        this.pageIndex = pageIndex;
        this.text = text;
        this.truncated = truncated;
    }

    public Kind kind() { return kind; }

    public String documentTitle() { return documentTitle; }

    /** Zero-based page index (best effort). */
    public int pageIndex() { return pageIndex; }

    @Nullable
    public String text() { return text; }

    public boolean truncated() { return truncated; }
}

