package org.opendroidpdf.app.reader;

import android.view.View;

/** Child measurement helpers extracted from ReaderView. */
public final class ReaderMeasure {
    private ReaderMeasure() {}

    public static void measureChild(View v,
                                    int containerWidth, int containerHeight,
                                    int padLeft, int padRight, int padTop, int padBottom,
                                    boolean reflow,
                                    float viewScale) {
        // First let the child report its intrinsic size
        v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

        if (!reflow) {
            // Fit-to-screen base scale, then apply current view scale
            float fill = ReaderGeometry.fillScreenScale(containerWidth, containerHeight,
                    padLeft, padRight, padTop, padBottom,
                    v.getMeasuredWidth(), v.getMeasuredHeight());
            v.measure(View.MeasureSpec.EXACTLY | (int)(v.getMeasuredWidth()*fill*viewScale),
                      View.MeasureSpec.EXACTLY | (int)(v.getMeasuredHeight()*fill*viewScale));
        } else {
            v.measure(View.MeasureSpec.EXACTLY | (int)(v.getMeasuredWidth()),
                      View.MeasureSpec.EXACTLY | (int)(v.getMeasuredHeight()));
        }
    }
}

