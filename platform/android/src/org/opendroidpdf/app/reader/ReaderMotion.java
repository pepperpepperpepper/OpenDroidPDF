package org.opendroidpdf.app.reader;

import android.graphics.Rect;

/**
 * Small collection of motion-related helpers factored out of ReaderView to
 * simplify that class. Pure functions only; keeps logic unit-testable.
 */
public final class ReaderMotion {
    private ReaderMotion() {}

    public static final int  MOVING_DIAGONALLY = 0;
    public static final int  MOVING_LEFT       = 1;
    public static final int  MOVING_RIGHT      = 2;
    public static final int  MOVING_UP         = 3;
    public static final int  MOVING_DOWN       = 4;

    public static int directionOfTravel(float vx, float vy) {
        if (Math.abs(vx) > 3 * Math.abs(vy))
            return (vx > 0) ? MOVING_RIGHT : MOVING_LEFT;
        else if (Math.abs(vy) > 3 * Math.abs(vx))
            return (vy > 0) ? MOVING_DOWN : MOVING_UP;
        else
            return MOVING_DIAGONALLY;
    }

    public static boolean withinBoundsInDirectionOfTravel(Rect bounds, float vx, float vy) {
        switch (directionOfTravel(vx, vy)) {
            case MOVING_DIAGONALLY: return bounds.contains(0, 0);
            case MOVING_LEFT:       return bounds.left <= 0;
            case MOVING_RIGHT:      return bounds.right >= 0;
            case MOVING_UP:         return bounds.top <= 0;
            case MOVING_DOWN:       return bounds.bottom >= 0;
            default: return false;
        }
    }
}

