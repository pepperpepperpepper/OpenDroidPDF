package org.opendroidpdf.app.lifecycle;

/** Manages save/ignore flags and recent-files count, keeping them out of the activity. */
public final class SaveFlagController {
    private boolean saveOnStop;
    private boolean saveOnDestroy;
    private boolean ignoreSaveOnStopOnce;
    private boolean ignoreSaveOnDestroyOnce;
    private int numberRecentFiles = 20;

    public boolean shouldSaveOnStop() { return saveOnStop; }
    public boolean shouldSaveOnDestroy() { return saveOnDestroy; }
    public boolean shouldIgnoreSaveOnStopOnce() { return ignoreSaveOnStopOnce; }
    public boolean shouldIgnoreSaveOnDestroyOnce() { return ignoreSaveOnDestroyOnce; }
    public void clearIgnoreSaveOnStopFlag() { ignoreSaveOnStopOnce = false; }
    public void clearIgnoreSaveOnDestroyFlag() { ignoreSaveOnDestroyOnce = false; }
    public void setIgnoreSaveFlagsForFinish() { ignoreSaveOnStopOnce = true; ignoreSaveOnDestroyOnce = true; }
    public void markIgnoreSaveOnStop() { ignoreSaveOnStopOnce = true; }

    public void setSaveFlags(boolean saveOnStop, boolean saveOnDestroy, int numberRecentFiles) {
        this.saveOnStop = saveOnStop;
        this.saveOnDestroy = saveOnDestroy;
        this.numberRecentFiles = numberRecentFiles;
    }

    public int maxRecentFiles() { return numberRecentFiles; }
}
