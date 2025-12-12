package org.opendroidpdf.app.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.EditText;

import org.opendroidpdf.R;
import org.opendroidpdf.core.WidgetCompletionCallback;
import org.opendroidpdf.core.WidgetController;
import org.opendroidpdf.core.WidgetBooleanCallback;

/**
 * Bridges widget UI (text/choice dialogs) with WidgetController jobs.
 * Keeps MuPDFPageView slimmer by owning the dialogs and async flows.
 */
public final class WidgetUiBridge {
    public interface ChangeReporter { void run(); }

    private final Context context;
    private final WidgetController widgetController;
    private final AlertDialog.Builder choiceBuilder;
    private final EditText editText;
    private final AlertDialog textEntry;

    private WidgetController.WidgetJob setWidgetTextJob;
    private WidgetController.WidgetJob setWidgetChoiceJob;

    private int pageNumber;
    private ChangeReporter changeReporter;

    public WidgetUiBridge(Context ctx, WidgetController controller) {
        this.context = ctx;
        this.widgetController = controller;
        this.choiceBuilder = org.opendroidpdf.app.alert.WidgetDialogFactory.newChoiceEntryBuilder(ctx);

        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        android.view.View dialogView = inflater.inflate(R.layout.dialog_text_input, null);
        this.editText = dialogView.findViewById(R.id.dialog_text_input);
        this.textEntry = org.opendroidpdf.app.alert.WidgetDialogFactory.newTextEntryDialog(
                ctx, dialogView, editText, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (setWidgetTextJob != null) setWidgetTextJob.cancel();
                        final String contents = editText.getText().toString();
                        setWidgetTextJob = widgetController.setWidgetTextAsync(pageNumber, contents, new WidgetBooleanCallback() {
                            @Override public void onResult(boolean result) {
                                if (changeReporter != null) changeReporter.run();
                                if (!result) showTextDialog(contents);
                            }
                        });
                    }
                });
    }

    public void setPageNumber(int page) { this.pageNumber = page; }
    public void setChangeReporter(ChangeReporter reporter) { this.changeReporter = reporter; }

    public void showTextDialog(String text) {
        editText.setText(text);
        textEntry.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        textEntry.show();
    }

    public void showChoiceDialog(final String[] options) {
        choiceBuilder.setItems(options, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (setWidgetChoiceJob != null) setWidgetChoiceJob.cancel();
                final String selection = options[which];
                setWidgetChoiceJob = widgetController.setWidgetChoiceAsync(new String[]{selection}, new WidgetCompletionCallback() {
                    @Override public void onComplete() {
                        if (changeReporter != null) changeReporter.run();
                    }
                });
            }
        });
        AlertDialog dialog = choiceBuilder.create();
        dialog.show();
    }

    public void release() {
        if (setWidgetTextJob != null) { setWidgetTextJob.cancel(); setWidgetTextJob = null; }
        if (setWidgetChoiceJob != null) { setWidgetChoiceJob.cancel(); setWidgetChoiceJob = null; }
    }
}

