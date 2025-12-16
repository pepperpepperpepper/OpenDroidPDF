package org.opendroidpdf.app.preferences;

import org.opendroidpdf.app.services.PenPreferencesService;

/**
 * Default pen prefs service backed by a PenPrefsStore. All Android storage
 * concerns live in the store, not the service interface.
 */
public class PenPreferencesServiceImpl implements PenPreferencesService {
    private final PenPrefsStore store;

    public PenPreferencesServiceImpl(PenPrefsStore store) {
        this.store = store;
    }

    @Override
    public PenPrefsSnapshot get() {
        return store.load();
    }

    @Override
    public void setThickness(float value) {
        PenPrefsSnapshot snap = store.load().withThickness(value);
        store.save(snap);
    }

    @Override
    public void setColorIndex(int index) {
        PenPrefsSnapshot snap = store.load().withColorIndex(index);
        store.save(snap);
    }
}
