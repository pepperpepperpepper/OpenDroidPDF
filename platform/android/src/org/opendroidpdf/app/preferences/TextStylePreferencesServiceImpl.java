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
    public void setFontFamily(int family) {
        TextStylePrefsSnapshot snap = store.load().withFontFamily(family);
        store.save(snap);
    }

    @Override
    public void setFontStyleFlags(int flags) {
        TextStylePrefsSnapshot snap = store.load().withFontStyleFlags(flags);
        store.save(snap);
    }

    @Override
    public void setFontSize(float value) {
        TextStylePrefsSnapshot snap = store.load().withFontSize(value);
        store.save(snap);
    }

    @Override
    public void setLineHeight(float value) {
        TextStylePrefsSnapshot snap = store.load().withLineHeight(value);
        store.save(snap);
    }

    @Override
    public void setTextIndentPt(float value) {
        TextStylePrefsSnapshot snap = store.load().withTextIndentPt(value);
        store.save(snap);
    }

    @Override
    public void setColorIndex(int index) {
        TextStylePrefsSnapshot snap = store.load().withColorIndex(index);
        store.save(snap);
    }

    @Override
    public void setBackgroundColorIndex(int index) {
        TextStylePrefsSnapshot snap = store.load().withBackgroundColorIndex(index);
        store.save(snap);
    }

    @Override
    public void setBackgroundOpacity(float value) {
        TextStylePrefsSnapshot snap = store.load().withBackgroundOpacity(value);
        store.save(snap);
    }

    @Override
    public void setBorderColorIndex(int index) {
        TextStylePrefsSnapshot snap = store.load().withBorderColorIndex(index);
        store.save(snap);
    }

    @Override
    public void setBorderWidthPt(float value) {
        TextStylePrefsSnapshot snap = store.load().withBorderWidthPt(value);
        store.save(snap);
    }

    @Override
    public void setBorderStyle(int style) {
        TextStylePrefsSnapshot snap = store.load().withBorderStyle(style);
        store.save(snap);
    }

    @Override
    public void setBorderRadiusPt(float value) {
        TextStylePrefsSnapshot snap = store.load().withBorderRadiusPt(value);
        store.save(snap);
    }
}
