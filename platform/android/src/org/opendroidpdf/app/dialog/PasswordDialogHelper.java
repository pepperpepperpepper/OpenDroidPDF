package org.opendroidpdf.app.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.R;

public final class PasswordDialogHelper {
    private PasswordDialogHelper() {}

    public interface Callback {
        /** Return true if the password was accepted; false to prompt again. */
        boolean onPasswordEntered(String password);
        void onCancelled();
    }

    public static void show(Context context, AlertDialog.Builder baseBuilder, final Callback cb) {
        if (context == null || baseBuilder == null || cb == null) return;

        final View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_text_input, null, false);
        final EditText input = dialogView.findViewById(R.id.dialog_text_input);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTransformationMethod(new PasswordTransformationMethod());
        input.setHint(R.string.enter_password);
        input.setSingleLine(true);
        input.setMinLines(1);

        final AlertDialog alert = baseBuilder.create();
        alert.setTitle(R.string.enter_password);
        alert.setView(dialogView);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.okay),
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        boolean ok = cb.onPasswordEntered(String.valueOf(input.getText()));
                        if (!ok) {
                            // Re-prompt
                            show(context, baseBuilder, cb);
                        }
                    }
                });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        cb.onCancelled();
                    }
                });
        alert.show();
    }
}

