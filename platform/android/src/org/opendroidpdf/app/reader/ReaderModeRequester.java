package org.opendroidpdf.app.reader;

import org.opendroidpdf.app.reader.gesture.ReaderMode;

@FunctionalInterface
public interface ReaderModeRequester {
    ReaderModeRequester NOOP = mode -> {};

    void requestMode(ReaderMode mode);
}

