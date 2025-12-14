package org.opendroidpdf;

/** Utility helpers that must live in the base package to access package-private MuPDFReaderView modes. */
public final class DocViewControls {
    private DocViewControls() {}

    public static void setViewingMode(MuPDFReaderView doc) {
        if (doc != null) doc.setMode(MuPDFReaderView.Mode.Viewing);
    }
}
