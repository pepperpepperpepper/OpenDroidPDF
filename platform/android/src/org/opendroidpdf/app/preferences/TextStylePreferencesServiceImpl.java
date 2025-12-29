package org.opendroidpdf.app.preferences;

import org.opendroidpdf.app.services.TextStylePreferencesService;

/**
 * Default FreeText style prefs service backed by a TextStylePrefsStore.
 * All Android storage concerns live in the store, not the service interface.
 */
public class TextStylePreferencesServiceImpl implements TextStylePreferencesService {
    private final TextStylePrefsStore store;

    public TextStylePreferencesServiceImpl(TextStylePrefsStore store) {
        this.store = store;
    }

    @Override
    public TextStylePrefsSnapshot get() {
        return store.load();
    }

    @Override
    public void setFontSize(float value) {
        TextStylePrefsSnapshot snap = store.load().withFontSize(value);
        store.save(snap);
    }

    @Override
    public void setColorIndex(int index) {
        TextStylePrefsSnapshot snap = store.load().withColorIndex(index);
        store.save(snap);
    }
}

