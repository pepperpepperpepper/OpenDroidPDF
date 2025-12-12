package org.opendroidpdf.app.content;

import org.opendroidpdf.app.preferences.EditorPreferences;
import org.opendroidpdf.app.reader.PageState;

/**
 * Applies preference-driven values (colors, thickness) into PageState consumers.
 */
public final class PagePreferenceUpdater {
    private PagePreferenceUpdater() {}

    public interface Host {
        void setInkColor(int color);
        void setEraserColor(int color);
        void setInkThickness(float px);
        void setEraserThickness(float px);
        void invalidateOverlay();
    }

    public static void apply(EditorPreferences prefs, Host host, PageState state) {
        if (prefs == null || host == null || state == null) return;
        state.setInkColor(prefs.getInkColorHex());
        state.setEraserColor(EditorPreferences.DEFAULT_ERASER_COLOR);
        state.setInkThickness(prefs.getInkThickness());
        state.setEraserThickness(prefs.getEraserThickness());

        host.setInkColor(state.getInkColor());
        host.setEraserColor(state.getEraserColor());
        host.setInkThickness(state.getInkThickness());
        host.setEraserThickness(state.getEraserThickness());
        host.invalidateOverlay();
    }
}
