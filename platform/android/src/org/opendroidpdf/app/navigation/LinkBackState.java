package org.opendroidpdf.app.navigation;

/**
 * Holds the last internal-link target so Activity/routers stay lean.
 */
public final class LinkBackState {
    private int page = -1;
    private float scale = 1f;
    private float normX = 0f;
    private float normY = 0f;

    public void remember(int page, float scale, float normX, float normY) {
        this.page = page;
        this.scale = scale;
        this.normX = normX;
        this.normY = normY;
    }

    public boolean isAvailable() {
        return page >= 0;
    }

    public int page() { return page; }
    public float scale() { return scale; }
    public float normX() { return normX; }
    public float normY() { return normY; }

    public void clear() {
        page = -1;
        scale = 1f;
        normX = 0f;
        normY = 0f;
    }
}
