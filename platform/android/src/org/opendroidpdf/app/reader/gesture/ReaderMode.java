package org.opendroidpdf.app.reader.gesture;

/**
 * Public, app-scoped reader interaction mode used by gesture routers outside
 * {@code org.opendroidpdf} package boundaries.
 *
 * <p>MuPDFReaderView's internal Mode enum remains package-private; the view maps
 * between the two so we don't leak internal view state across packages.
 */
public enum ReaderMode {
    VIEWING,
    SELECTING,
    DRAWING,
    ERASING,
    ADDING_TEXT_ANNOT,
    SEARCHING
}
