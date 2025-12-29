package org.opendroidpdf.app.services;

import org.opendroidpdf.app.preferences.TextStylePrefsSnapshot;

/** Contract for FreeText style prefs without leaking Android storage primitives. */
public interface TextStylePreferencesService {
    TextStylePrefsSnapshot get();
    void setFontSize(float value);
    void setColorIndex(int index);
}

