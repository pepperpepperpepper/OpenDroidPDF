package org.opendroidpdf.app.services.search;

/** Direction for search navigation. */
public enum SearchDirection {
    FORWARD(1),
    BACKWARD(-1);

    private final int step;

    SearchDirection(int step) {
        this.step = step;
    }

    public int step() {
        return step;
    }

    public static SearchDirection fromInt(int dir) {
        return dir >= 0 ? FORWARD : BACKWARD;
    }
}
