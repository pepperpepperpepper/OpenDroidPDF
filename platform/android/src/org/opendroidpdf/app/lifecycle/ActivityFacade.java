package org.opendroidpdf.app.lifecycle;

import android.net.Uri;

import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.document.DocumentLifecycleManager;
import org.opendroidpdf.app.ui.AlertUiManager;
import org.opendroidpdf.app.ui.UiStateManager;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Simple facade bundling frequently accessed controllers/hosts for adapters.
 */
public class ActivityFacade {
    private final DocumentLifecycleManager doc;
    private final UiStateManager ui;
    private final AlertUiManager alerts;
    private final OpenDroidPDFActivity activity;

    public ActivityFacade(OpenDroidPDFActivity activity,
                          DocumentLifecycleManager doc,
                          UiStateManager ui,
                          AlertUiManager alerts) {
        this.activity = activity;
        this.doc = doc;
        this.ui = ui;
        this.alerts = alerts;
    }

    public boolean hasCore() { return doc != null && doc.hasCore(); }
    public boolean hasUnsavedChanges() { return doc != null && doc.hasUnsavedChanges(); }
    public boolean canSaveToCurrentUri() { return doc != null && doc.canSaveToCurrentUri(); }
    public boolean hasRepository() { return doc != null && doc.hasRepository(); }
    @Nullable public MuPdfRepository repository() { return doc != null ? doc.getRepository() : null; }
    @Nullable public MuPdfController muPdfController() { return doc != null ? doc.getMuPdfController() : null; }
    @Nullable public Uri currentDocumentUri() { return doc != null ? doc.currentDocumentUri() : null; }
    public String currentDocumentName() { return doc != null ? doc.currentDocumentName() : activity.getString(org.opendroidpdf.R.string.app_name); }

    public boolean shouldSaveOnStop() { return ui != null && ui.shouldSaveOnStop(); }
    public boolean shouldSaveOnDestroy() { return ui != null && ui.shouldSaveOnDestroy(); }
    public boolean shouldIgnoreSaveOnStopOnce() { return ui != null && ui.shouldIgnoreSaveOnStopOnce(); }
    public boolean shouldIgnoreSaveOnDestroyOnce() { return ui != null && ui.shouldIgnoreSaveOnDestroyOnce(); }
    public void clearIgnoreSaveOnStopFlag() { if (ui != null) ui.clearIgnoreSaveOnStopFlag(); }
    public void clearIgnoreSaveOnDestroyFlag() { if (ui != null) ui.clearIgnoreSaveOnDestroyFlag(); }
    public void setSaveFlags(boolean saveOnStop, boolean saveOnDestroy, int numRecent) { if (ui != null) ui.setSaveFlags(saveOnStop, saveOnDestroy, numRecent); }
    public int maxRecentFiles() { return ui != null ? ui.maxRecentFiles() : 20; }
    public void markIgnoreSaveOnStop() { if (ui != null) ui.markIgnoreSaveOnStop(); }
    public boolean isPreparingOptionsMenu() { return ui != null && ui.isPreparingOptionsMenu(); }

    public androidx.appcompat.app.AlertDialog.Builder alertBuilder() {
        return alerts != null ? alerts.getAlertBuilder() : null;
    }
    public void setAlertBuilder(androidx.appcompat.app.AlertDialog.Builder b) { if (alerts != null) alerts.setAlertBuilder(b); }
    public void setTitle() { if (alerts != null) alerts.setTitle(); }
}
