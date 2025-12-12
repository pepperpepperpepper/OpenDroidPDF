package org.opendroidpdf.app.alert;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.EditText;

import org.opendroidpdf.R;

/**
 * Central helpers for common widget/signature dialogs used by MuPDFPageView.
 * Extracted to trim monolithic UI code and keep MuPDFPageView focused on
 * document/page behavior.
 */
public final class WidgetDialogFactory {
    private WidgetDialogFactory() {}

    public static AlertDialog newTextEntryDialog(Context ctx,
                                                 View dialogView,
                                                 EditText editText,
                                                 DialogInterface.OnClickListener onOk) {
        AlertDialog.Builder b = new AlertDialog.Builder(ctx)
                .setTitle(ctx.getString(R.string.fill_out_text_field))
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.okay, onOk);
        return b.create();
    }

    public static AlertDialog.Builder newChoiceEntryBuilder(Context ctx) {
        return new AlertDialog.Builder(ctx)
                .setTitle(ctx.getString(R.string.choose_value));
    }

    public static AlertDialog.Builder newSigningDialogBuilder(Context ctx,
                                                              DialogInterface.OnClickListener onOk) {
        return new AlertDialog.Builder(ctx)
                .setTitle(R.string.signature_dialog_title)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.okay, onOk);
    }

    public static AlertDialog newPasswordEntryDialog(Context ctx,
                                                     EditText passwordText) {
        AlertDialog.Builder b = new AlertDialog.Builder(ctx)
                .setTitle(R.string.enter_password)
                .setView(passwordText)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss());
        return b.create();
    }

    public static AlertDialog.Builder newSignatureReportBuilder(Context ctx) {
        return new AlertDialog.Builder(ctx)
                .setTitle(R.string.signature_report_title)
                .setPositiveButton(R.string.okay, (d, w) -> d.dismiss());
    }
}

