package org.opendroidpdf.app.sidecar;

import androidx.annotation.NonNull;

import org.opendroidpdf.app.sidecar.model.SidecarHighlight;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

import java.util.List;

/**
 * Read-only surface used by rendering/interaction layers to obtain sidecar-backed
 * annotations for the current document.
 *
 * <p>Implementations are expected to scope results to the current document and (for
 * reflowable docs) the active layout profile.</p>
 */
public interface SidecarAnnotationProvider {
    @NonNull List<SidecarInkStroke> inkStrokesForPage(int pageIndex);
    @NonNull List<SidecarHighlight> highlightsForPage(int pageIndex);
    @NonNull List<SidecarNote> notesForPage(int pageIndex);
}

