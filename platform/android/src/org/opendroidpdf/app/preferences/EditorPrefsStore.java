package org.opendroidpdf.app.preferences;

/** Persistence boundary for editor/annotation preferences (excluding pen thickness/color). */
public interface EditorPrefsStore {
    EditorPrefsSnapshot load();
}

