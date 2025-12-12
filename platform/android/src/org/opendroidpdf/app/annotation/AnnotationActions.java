package org.opendroidpdf.app.annotation;

import android.graphics.PointF;
import android.util.Log;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.core.AnnotationCallback;
import org.opendroidpdf.core.AnnotationController;
import org.opendroidpdf.core.AnnotationController.AnnotationJob;

/**
 * Thin UI-side wrapper around core AnnotationController to keep MuPDFPageView lean.
 * Owns async jobs for add/delete and provides a simple callback on completion.
 */
public class AnnotationActions {
    private static final String TAG = "AnnotationActions";

    private final AnnotationController controller;
    private AnnotationJob addMarkupJob;
    private AnnotationJob addTextJob;
    private AnnotationJob deleteJob;

    public AnnotationActions(AnnotationController controller) {
        this.controller = controller;
    }

    public void addMarkupAnnotation(int pageIndex, PointF[] quadPoints, Annotation.Type type, Runnable onComplete) {
        cancel(addMarkupJob);
        addMarkupJob = controller.addMarkupAnnotationAsync(
                pageIndex,
                quadPoints,
                type,
                new AnnotationCallback() {
                    @Override public void onComplete() {
                        if (onComplete != null) onComplete.run();
                    }
                }
        );
    }

    public void addTextAnnotation(int pageIndex, PointF[] rectTwoPoints, String text, Runnable onComplete) {
        cancel(addTextJob);
        addTextJob = controller.addTextAnnotationAsync(
                pageIndex,
                rectTwoPoints,
                text,
                new AnnotationCallback() {
                    @Override public void onComplete() {
                        if (onComplete != null) onComplete.run();
                    }
                }
        );
    }

    public void deleteAnnotation(int pageIndex, int annotationIndex, Runnable onComplete) {
        cancel(deleteJob);
        deleteJob = controller.deleteAnnotationAsync(
                pageIndex,
                annotationIndex,
                new AnnotationCallback() {
                    @Override public void onComplete() {
                        if (onComplete != null) onComplete.run();
                    }
                }
        );
    }

    private static void cancel(AnnotationJob job) {
        if (job != null) job.cancel();
    }

    public void release() {
        cancel(addMarkupJob); addMarkupJob = null;
        cancel(addTextJob);  addTextJob  = null;
        cancel(deleteJob);   deleteJob   = null;
    }
}

