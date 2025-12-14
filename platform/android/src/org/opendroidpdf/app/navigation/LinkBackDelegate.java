package org.opendroidpdf.app.navigation;

/**
 * Holds link-back state and tiny helpers outside the activity.
 */
public final class LinkBackDelegate {
    private final LinkBackState state = new LinkBackState();

    public LinkBackState state() { return state; }
    public boolean isAvailable() { return state.isAvailable(); }
    public void remember(int page, float scale, float x, float y) { state.remember(page, scale, x, y); }
    public void clear() { state.clear(); }
}
