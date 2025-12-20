package org.opendroidpdf.app.sidecar;

import android.graphics.PointF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Compact binary codec for point lists used by sidecar storage.
 *
 * <p>Format (little-endian):
 * <ul>
 *   <li>int count</li>
 *   <li>repeat count times: float x, float y</li>
 * </ul></p>
 */
public final class SidecarPointCodec {
    private SidecarPointCodec() {}

    @NonNull
    public static byte[] encodePoints(@NonNull PointF[] points) {
        int n = points.length;
        ByteBuffer buf = ByteBuffer.allocate(4 + (n * 8)).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(n);
        for (PointF p : points) {
            float x = p != null ? p.x : Float.NaN;
            float y = p != null ? p.y : Float.NaN;
            buf.putFloat(x);
            buf.putFloat(y);
        }
        return buf.array();
    }

    @Nullable
    public static PointF[] decodePoints(@Nullable byte[] blob) {
        if (blob == null || blob.length < 4) return null;
        try {
            ByteBuffer buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
            int n = buf.getInt();
            if (n < 0) return null;
            PointF[] out = new PointF[n];
            for (int i = 0; i < n; i++) {
                if (buf.remaining() < 8) return null;
                float x = buf.getFloat();
                float y = buf.getFloat();
                if (Float.isNaN(x) || Float.isNaN(y) || Float.isInfinite(x) || Float.isInfinite(y)) {
                    out[i] = null;
                } else {
                    out[i] = new PointF(x, y);
                }
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }
}

