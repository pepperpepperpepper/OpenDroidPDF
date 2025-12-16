package org.opendroidpdf.app.preferences;

/** Storage abstraction for pen preferences. */
public interface PenPrefsStore {
    PenPrefsSnapshot load();
    void save(PenPrefsSnapshot snapshot);
}
