package org.opendroidpdf.app.preferences;

/** Immutable snapshot of activity-level preferences. */
public final class AppPrefsSnapshot {
    public final boolean keepScreenOn;
    public final boolean saveOnStop;
    public final boolean saveOnDestroy;
    public final int numberRecentFiles;

    public AppPrefsSnapshot(boolean keepScreenOn,
                            boolean saveOnStop,
                            boolean saveOnDestroy,
                            int numberRecentFiles) {
        this.keepScreenOn = keepScreenOn;
        this.saveOnStop = saveOnStop;
        this.saveOnDestroy = saveOnDestroy;
        this.numberRecentFiles = numberRecentFiles;
    }
}

