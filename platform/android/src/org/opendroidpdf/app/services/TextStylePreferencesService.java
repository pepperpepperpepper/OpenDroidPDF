package org.opendroidpdf.app.services;

import org.opendroidpdf.app.preferences.TextStylePrefsSnapshot;

/** Contract for FreeText style prefs without leaking Android storage primitives. */
public interface TextStylePreferencesService {
    TextStylePrefsSnapshot get();
    void setFontFamily(int family);
    void setFontStyleFlags(int flags);
    void setFontSize(float value);
    void setLineHeight(float value);
    void setTextIndentPt(float value);
    void setColorIndex(int index);
    void setBackgroundColorIndex(int index);
    void setBackgroundOpacity(float value);
    void setBorderColorIndex(int index);
    void setBorderWidthPt(float value);
    void setBorderStyle(int style);
    void setBorderRadiusPt(float value);
}
