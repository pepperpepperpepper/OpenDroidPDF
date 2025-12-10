package org.opendroidpdf.app.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.R;

/** Simple helper to show the Go-To-Page dialog and navigate the doc view. */
public final class GoToPageDialog {
    private GoToPageDialog() {}

    public static void show(Context context, AlertDialog.Builder baseBuilder, final MuPDFReaderView docView) {
        if (context == null || baseBuilder == null || docView == null) return;
        final AlertDialog dialog = baseBuilder.create();
        final View editTextLayout = LayoutInflater.from(context).inflate(R.layout.dialog_text_input, null, false);
        final EditText input = editTextLayout.findViewById(R.id.dialog_text_input);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine();
        input.setBackgroundDrawable(null);
        input.setHint(context.getString(R.string.dialog_gotopage_hint));
        input.setFocusable(true);
        dialog.setTitle(R.string.dialog_gotopage_title);
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.dialog_gotopage_ok), new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                int pageNumber;
                try { pageNumber = Integer.parseInt(String.valueOf(input.getText())); }
                catch (NumberFormatException e) { pageNumber = 0; }
                docView.setDisplayedViewIndex(pageNumber == 0 ? 0 : pageNumber - 1);
                docView.setScale(1.0f);
                docView.setNormalizedScroll(0.0f, 0.0f);
            }
        });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(R.string.dialog_gotopage_cancel), (d, w) -> {});
        dialog.setView(editTextLayout);
        dialog.show();
        input.requestFocus();
    }
}

