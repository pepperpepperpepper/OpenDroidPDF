package org.opendroidpdf.app.services;

import org.opendroidpdf.app.preferences.PenPrefsSnapshot;

/** Contract for pen prefs without leaking Android storage primitives. */
public interface PenPreferencesService {
    PenPrefsSnapshot get();
    void setThickness(float value);
    void setColorIndex(int index);
}
