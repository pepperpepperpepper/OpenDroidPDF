package org.opendroidpdf.app.ui;

import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.lifecycle.ActivityComposition;

/**
 * Centralizes alert builder and title handling.
 */
public class AlertUiManager {
    private final OpenDroidPDFActivity activity;
    private final ActivityComposition.Composition comp;
    private AlertDialog.Builder alertBuilder;

    public AlertUiManager(OpenDroidPDFActivity activity, ActivityComposition.Composition comp) {
        this.activity = activity;
        this.comp = comp;
    }

    public AlertDialog.Builder getAlertBuilder() {
        if (comp != null && comp.uiStateDelegate != null) {
            alertBuilder = comp.uiStateDelegate.alertBuilder();
        }
        return alertBuilder;
    }

    public void setAlertBuilder(AlertDialog.Builder builder) {
        alertBuilder = builder;
        if (comp != null && comp.uiStateDelegate != null) comp.uiStateDelegate.setAlertBuilder(builder);
    }

    public void setTitle() {
        if (comp != null && comp.titleHostAdapter != null) comp.titleHostAdapter.setTitle();
    }

    public boolean isPreparingOptionsMenu() {
        return comp != null && comp.optionsMenuController != null && comp.optionsMenuController.isPreparingOptionsMenu();
    }
}
