package org.opendroidpdf.app.fillsign;

import android.graphics.PointF;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, normalized signature/initials template (0..1 in both axes). */
public final class SignatureTemplate {
    public final float aspectRatio;
    @NonNull public final List<List<PointF>> strokes;

    public SignatureTemplate(float aspectRatio, @NonNull List<List<PointF>> strokes) {
        this.aspectRatio = aspectRatio > 0f && Float.isFinite(aspectRatio) ? aspectRatio : 1f;
        this.strokes = deepCopy(strokes);
    }

    @NonNull
    private static List<List<PointF>> deepCopy(@NonNull List<List<PointF>> in) {
        if (in == null || in.isEmpty()) return Collections.emptyList();
        ArrayList<List<PointF>> out = new ArrayList<>(in.size());
        for (List<PointF> stroke : in) {
            if (stroke == null || stroke.isEmpty()) continue;
            ArrayList<PointF> pts = new ArrayList<>(stroke.size());
            for (PointF p : stroke) {
                if (p == null) continue;
                if (!Float.isFinite(p.x) || !Float.isFinite(p.y)) continue;
                pts.add(new PointF(p.x, p.y));
            }
            if (pts.size() >= 2) out.add(Collections.unmodifiableList(pts));
        }
        return Collections.unmodifiableList(out);
    }
}

