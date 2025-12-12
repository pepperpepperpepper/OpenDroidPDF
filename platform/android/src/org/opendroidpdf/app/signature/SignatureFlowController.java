package org.opendroidpdf.app.signature;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import org.opendroidpdf.R;
import org.opendroidpdf.core.SignatureController;
import org.opendroidpdf.core.SignatureController.SignatureJob;
import org.opendroidpdf.core.SignatureBooleanCallback;
import org.opendroidpdf.core.SignatureStringCallback;
// File picking is delegated back to the host package because FilePicker
// has package-private visibility. We define a tiny launcher here.

/**
 * Encapsulates signature interactions (sign, check, reports) previously
 * embedded in MuPDFPageView. This keeps the page view slimmer and focuses
 * this class on dialog orchestration and SignatureController jobs.
 */
public final class SignatureFlowController {
    public interface ChangeReporter { void run(); }

    private final Context context;
    private final SignatureController signatureController;
    public interface FilePickerLauncher {
        interface Callback { void onPick(android.net.Uri uri); }
        void pick(Callback callback);
    }
    private final FilePickerLauncher picker;
    private final ChangeReporter changeReporter;

    private final AlertDialog.Builder signingDialogBuilder;
    private final AlertDialog.Builder signatureReportBuilder;

    private final EditText passwordText;
    private final AlertDialog passwordEntry;

    private SignatureJob checkJob;
    private SignatureJob signJob;

    public SignatureFlowController(Context ctx,
                                   SignatureController controller,
                                   FilePickerLauncher picker,
                                   ChangeReporter reporter) {
        this.context = ctx;
        this.signatureController = controller;
        this.picker = picker;
        this.changeReporter = reporter;

        // Password input dialog
        passwordText = new EditText(ctx);
        passwordText.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        passwordEntry = org.opendroidpdf.app.alert.WidgetDialogFactory.newPasswordEntryDialog(ctx, passwordText);

        // Report dialogs
        signatureReportBuilder = org.opendroidpdf.app.alert.WidgetDialogFactory.newSignatureReportBuilder(ctx);

        // Signing dialog: triggers a file picker, then password prompt
        signingDialogBuilder = org.opendroidpdf.app.alert.WidgetDialogFactory.newSigningDialogBuilder(
                ctx,
                (d, w) -> picker.pick(uri -> signWithKeyFile(uri))
        );
    }

    public void showSigningDialog() {
        AlertDialog dialog = signingDialogBuilder.create();
        dialog.show();
    }

    public void showNoSignatureSupport() {
        AlertDialog dialog = signatureReportBuilder.create();
        dialog.setTitle(R.string.signature_not_supported_title);
        dialog.show();
    }

    public void checkFocusedSignature() {
        if (checkJob != null) checkJob.cancel();
        checkJob = signatureController.checkFocusedSignatureAsync(new SignatureStringCallback() {
            @Override public void onResult(String result) {
                AlertDialog report = signatureReportBuilder.create();
                report.setMessage(result);
                report.show();
            }
        });
    }

    private void signWithKeyFile(final Uri uri) {
        passwordEntry.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        passwordEntry.setButton(AlertDialog.BUTTON_POSITIVE,
                context.getString(R.string.signature_sign_action),
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        signWithKeyFileAndPassword(uri, passwordText.getText().toString());
                    }
                });
        passwordEntry.show();
    }

    private void signWithKeyFileAndPassword(final Uri uri, final String password) {
        if (signJob != null) signJob.cancel();
        signJob = signatureController.signFocusedSignatureAsync(Uri.decode(uri.getEncodedPath()), password, new SignatureBooleanCallback() {
            @Override public void onResult(boolean result) {
                if (result) {
                    if (changeReporter != null) changeReporter.run();
                } else {
                    passwordText.setText("");
                    signWithKeyFile(uri);
                }
            }
        });
    }

    public void release() {
        if (checkJob != null) { checkJob.cancel(); checkJob = null; }
        if (signJob != null) { signJob.cancel(); signJob = null; }
    }
}
