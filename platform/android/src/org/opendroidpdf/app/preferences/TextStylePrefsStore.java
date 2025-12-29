package org.opendroidpdf.app.preferences;

/** Storage abstraction for FreeText style prefs. */
public interface TextStylePrefsStore {
    TextStylePrefsSnapshot load();
    void save(TextStylePrefsSnapshot snapshot);
}

