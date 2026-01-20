package org.opendroidpdf.app.document;

import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.R;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.SidecarBundleJson;

final class ExportUi {
    interface PasswordConsumer {
        void onPasswords(@NonNull String user, @NonNull String owner);
    }

    private final ExportController.Host host;

    ExportUi(@NonNull ExportController.Host host) {
        this.host = host;
    }

    void promptForPasswords(@NonNull Context ctx, int confirmLabelRes, @NonNull PasswordConsumer consumer) {
        final EditText userField = new EditText(ctx);
        userField.setHint(R.string.encrypt_user_password);
        final EditText ownerField = new EditText(ctx);
        ownerField.setHint(R.string.encrypt_owner_password);
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (ctx.getResources().getDisplayMetrics().density * 16);
        ll.setPadding(pad, pad, pad, pad);
        ll.addView(userField);
        ll.addView(ownerField);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.encrypt_copy_title)
                .setView(ll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(confirmLabelRes, (d, w) -> {
                    String userPw = userField.getText() != null ? userField.getText().toString() : "";
                    String ownerPw = ownerField.getText() != null ? ownerField.getText().toString() : "";
                    if (userPw.isEmpty() || ownerPw.isEmpty()) {
                        host.showInfo(ctx.getString(R.string.encrypt_password_error));
                        return;
                    }
                    consumer.onPasswords(userPw, ownerPw);
                })
                .show();
    }

    void showDocMismatchConfirm(@NonNull SidecarAnnotationSession session,
                               @NonNull SidecarBundleJson.SidecarBundle bundle,
                               @NonNull Runnable onConfirmImport) {
        String message = host.getContext().getString(
                R.string.import_docid_mismatch_message,
                shortId(bundle.docId),
                shortId(session.docId()));
        new AlertDialog.Builder(host.getContext())
                .setTitle(R.string.import_annotations_title)
                .setMessage(message)
                .setPositiveButton(R.string.import_annotations_anyway, (d, w) -> onConfirmImport.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @NonNull
    static String shortId(@Nullable String id) {
        if (id == null) return "";
        String s = id.trim();
        if (s.length() <= 12) return s;
        return s.substring(0, 6) + "…" + s.substring(s.length() - 4);
    }
}
