package org.opendroidpdf.app.document;

/**
 * Pure-Java decision helper for what to do on startup/open intents.
 */
public final class DocumentOpenDecider {
    public enum Action { REQUEST_STORAGE_PERMISSION, OPEN_URI, OPEN_RECENT, SHOW_DASHBOARD }

    public static final class Inputs {
        public final boolean hasIntentUri;
        public final boolean hasStoragePermission;
        public final boolean hasRecentFiles;
        public final boolean coldStart;

        public Inputs(boolean hasIntentUri, boolean hasStoragePermission, boolean hasRecentFiles, boolean coldStart) {
            this.hasIntentUri = hasIntentUri;
            this.hasStoragePermission = hasStoragePermission;
            this.hasRecentFiles = hasRecentFiles;
            this.coldStart = coldStart;
        }
    }

    private DocumentOpenDecider() {}

    public static Action decide(Inputs in) {
        if (in.hasIntentUri) {
            return in.hasStoragePermission ? Action.OPEN_URI : Action.REQUEST_STORAGE_PERMISSION;
        }
        if (in.hasRecentFiles) {
            return Action.OPEN_RECENT;
        }
        return Action.SHOW_DASHBOARD;
    }
}

