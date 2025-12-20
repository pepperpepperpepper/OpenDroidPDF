package org.opendroidpdf.app.sidecar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.sidecar.model.SidecarHighlight;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

import java.util.List;

/**
 * Persistence backend for sidecar annotations.
 *
 * <p>This store is keyed by a document identity string. For reflowable documents, annotations
 * carry a {@code layoutProfileId} so consumers can choose whether to show/hide them under
 * a different layout.</p>
 */
public interface SidecarAnnotationStore {
    @NonNull List<SidecarInkStroke> listInk(@NonNull String docId, int pageIndex, @Nullable String layoutProfileId);
    void insertInk(@NonNull String docId, @NonNull List<SidecarInkStroke> strokes);
    void deleteInk(@NonNull String docId, @NonNull String strokeId);
    boolean hasAnyInk(@NonNull String docId);

    @NonNull List<SidecarHighlight> listHighlights(@NonNull String docId, int pageIndex, @Nullable String layoutProfileId);
    void insertHighlight(@NonNull String docId, @NonNull SidecarHighlight highlight);
    void deleteHighlight(@NonNull String docId, @NonNull String highlightId);

    @NonNull List<SidecarNote> listNotes(@NonNull String docId, int pageIndex, @Nullable String layoutProfileId);
    void insertNote(@NonNull String docId, @NonNull SidecarNote note);
    void deleteNote(@NonNull String docId, @NonNull String noteId);

    /**
     * Returns true if this document has any annotations created under a different layout profile ID.
     *
     * <p>Used to surface a UX hint when reflow settings change (e.g., EPUB font size), since
     * geometry-anchored annotations may no longer align under a new layout.</p>
     */
    boolean hasAnyAnnotationsOutsideLayout(@NonNull String docId, @NonNull String layoutProfileId);
}
