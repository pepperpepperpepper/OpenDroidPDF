package org.opendroidpdf.app.preferences;

/** Persistence boundary for activity-level preferences. */
public interface AppPrefsStore {
    AppPrefsSnapshot load();
}

