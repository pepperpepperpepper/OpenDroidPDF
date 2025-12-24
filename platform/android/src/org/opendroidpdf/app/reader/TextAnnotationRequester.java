package org.opendroidpdf.app.reader;

import org.opendroidpdf.Annotation;

/**
 * Document-scoped request surface for UI prompts needed by page-level logic.
 *
 * Keeps {@link org.opendroidpdf.MuPDFPageView} decoupled from parent view casts by
 * routing "edit/add text annotation" prompts through the per-document composition.
 */
@FunctionalInterface
public interface TextAnnotationRequester {

    void requestTextAnnotationFromUserInput(Annotation annotation);

    TextAnnotationRequester NOOP = annotation -> {};
}

