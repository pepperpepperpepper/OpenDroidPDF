package org.opendroidpdf.app.hosts;

import android.content.Intent;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;
import org.opendroidpdf.app.document.DocumentAccessIntents;
import org.opendroidpdf.app.helpers.RequestCodes;
import org.opendroidpdf.app.ui.UiUtils;

/**
 * Centralizes activity-scoped SAF launch behavior (request codes + transitions).
 */
public final class DocumentAccessHostAdapter {
    private final OpenDroidPDFActivity activity;

    public DocumentAccessHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    /** Launches the SAF picker requesting durable read+write access for the active document. */
    public void showOpenDocumentForEditActivity() {
        Intent intent = DocumentAccessIntents.newOpenDocumentForEditIntent();
        try {
            activity.startActivityForResult(intent, RequestCodes.EDIT);
            activity.overridePendingTransition(R.animator.enter_from_left, R.animator.fade_out);
        } catch (Throwable t) {
            UiUtils.showInfo(activity, activity.getString(R.string.cannot_open_document_permission_hint));
        }
    }
}

