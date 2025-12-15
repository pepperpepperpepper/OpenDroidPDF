package org.opendroidpdf.app.services;

import android.content.SharedPreferences;

/**
 * Contract for pen-related preference access so UI/controllers don't depend on
 * a concrete storage implementation.
 */
public interface PenPreferencesService {
    SharedPreferences prefs();
    float getThickness();
    void setThickness(float value);
    int getColorIndex();
    void setColorIndex(int index);
    float getMinThickness();
    float getMaxThickness();
    float getStepThickness();
    float getDefaultThickness();
}
