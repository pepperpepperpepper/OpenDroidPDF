package org.opendroidpdf.app.preferences;

/** Persistence boundary for viewer/navigation preferences. */
public interface ViewerPrefsStore {
    ViewerPrefsSnapshot load();
}

