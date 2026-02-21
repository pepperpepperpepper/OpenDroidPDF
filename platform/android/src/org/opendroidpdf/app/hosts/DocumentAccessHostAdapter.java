package org.opendroidpdf.app.hosts;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

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
            activity.overridePendingTransition(R.anim.enter_from_left, R.anim.fade_out);
        } catch (Throwable t) {
            UiUtils.showInfo(activity, activity.getString(R.string.cannot_open_document_permission_hint));
        }
    }

    /**
     * Shows a short explainer for why saving is disabled, with quick actions to either:
     * <ul>
     *     <li>Re-open the file via SAF to grant write access (Enable saving).</li>
     *     <li>Export a copy (recommended for email attachments).</li>
     * </ul>
     */
    public void showEnableSavingDialog(@Nullable Runnable onExport) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(R.string.pdf_enable_saving);
        b.setMessage(R.string.pdf_enable_saving_explainer);
        b.setPositiveButton(R.string.pdf_enable_saving_choose_file, (d, w) -> showOpenDocumentForEditActivity());
        if (onExport != null) {
            b.setNeutralButton(R.string.menu_share, (d, w) -> {
                try { onExport.run(); } catch (Throwable ignore) {}
            });
        }
        b.setNegativeButton(R.string.cancel, (d, w) -> {});
        try {
            b.show();
        } catch (Throwable t) {
            // Fall back to launching the picker directly if the dialog can't be shown.
            showOpenDocumentForEditActivity();
        }
    }
}
