package org.opendroidpdf.app.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.util.Log;

import androidx.annotation.Nullable;

import org.opendroidpdf.R;
import org.opendroidpdf.core.WidgetCompletionCallback;
import org.opendroidpdf.core.WidgetController;
import org.opendroidpdf.core.WidgetBooleanCallback;

/**
 * Bridges widget UI (text/choice dialogs) with WidgetController jobs.
 * Keeps MuPDFPageView slimmer by owning the dialogs and async flows.
 */
public final class WidgetUiBridge {
    private static final String TAG = "WidgetUiBridge";
    public interface ChangeReporter { void run(); }

    public interface InlineTextEditorHost {
        void showInlineTextEditor(EditText editor, Rect boundsPx);
        void hideInlineTextEditor(EditText editor);
    }

    public interface FieldNavigationRequester {
        boolean navigate(int pageNumber, float docRelX, float docRelY, int direction);
    }

    private final Context context;
    private final WidgetController widgetController;
    private final EditText editText;
    private final AlertDialog textEntry;
    @Nullable private EditText inlineEditText;
    @Nullable private InlineTextEditorHost inlineHost;
    @Nullable private Rect inlineBoundsPx;
    private boolean inlineSubmitting = false;
    private boolean suppressInlineFocusLoss = false;

    private WidgetController.WidgetJob setWidgetTextJob;
    private WidgetController.WidgetJob setWidgetChoiceJob;

    private int pageNumber;
    private ChangeReporter changeReporter;
    @Nullable private FieldNavigationRequester fieldNavigationRequester;

    private float lastWidgetDocRelX = Float.NaN;
    private float lastWidgetDocRelY = Float.NaN;

    private static final int MULTILINE_MIN_LINES = 3;

    public WidgetUiBridge(Context ctx, WidgetController controller) {
        this.context = ctx;
        this.widgetController = controller;

        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        android.view.View dialogView = inflater.inflate(R.layout.dialog_text_input, null);
        this.editText = dialogView.findViewById(R.id.dialog_text_input);
        this.textEntry = org.opendroidpdf.app.alert.WidgetDialogFactory.newTextEntryDialog(
                ctx, dialogView, editText, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        submitText(false);
                    }
                });

        editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                submitText(true);
                try { textEntry.dismiss(); } catch (Throwable ignore) {}
                return true;
            }
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitText(false);
                try { textEntry.dismiss(); } catch (Throwable ignore) {}
                return true;
            }
            return false;
        });
    }

    public void setPageNumber(int page) { this.pageNumber = page; }
    public void setChangeReporter(ChangeReporter reporter) { this.changeReporter = reporter; }
    public void setFieldNavigationRequester(@Nullable FieldNavigationRequester requester) { this.fieldNavigationRequester = requester; }

    public void showTextDialog(String text) {
        dismissInlineTextEditor();
        applyTextFieldUiHints(editText, lastWidgetDocRelX, lastWidgetDocRelY);
        editText.setText(text);
        try {
            if (text != null && !text.isEmpty()) {
                editText.selectAll();
            } else {
                editText.setSelection(editText.getText() != null ? editText.getText().length() : 0);
            }
        } catch (Throwable ignore) {
        }
        textEntry.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        textEntry.show();
    }

    public void showTextDialog(String text, float docRelX, float docRelY) {
        lastWidgetDocRelX = docRelX;
        lastWidgetDocRelY = docRelY;
        showTextDialog(text);
    }

    public void showInlineTextEditor(InlineTextEditorHost host, String text, Rect boundsPx, float docRelX, float docRelY) {
        if (host == null || boundsPx == null) {
            showTextDialog(text, docRelX, docRelY);
            return;
        }

        lastWidgetDocRelX = docRelX;
        lastWidgetDocRelY = docRelY;
        dismissTextDialogIfShown();

        ensureInlineEditor();
        if (inlineEditText == null) {
            showTextDialog(text, docRelX, docRelY);
            return;
        }

        inlineHost = host;
        inlineBoundsPx = new Rect(boundsPx);
        suppressInlineFocusLoss = false;

        applyTextFieldUiHints(inlineEditText, docRelX, docRelY);
        inlineEditText.setText(text);
        try {
            if (text != null && !text.isEmpty()) inlineEditText.selectAll();
            else inlineEditText.setSelection(inlineEditText.getText() != null ? inlineEditText.getText().length() : 0);
        } catch (Throwable ignore) {
        }

        try { host.showInlineTextEditor(inlineEditText, inlineBoundsPx); } catch (Throwable ignore) {}

        try {
            inlineEditText.requestFocus();
            inlineEditText.post(() -> {
                try {
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(inlineEditText, InputMethodManager.SHOW_IMPLICIT);
                } catch (Throwable ignore) {
                }
            });
        } catch (Throwable ignore) {
        }
    }

    public void showChoiceDialog(final String[] options, final String[] selected) {
        dismissInlineTextEditor();
        showChoiceDialog(options, selected, false, false);
    }

    public void showChoiceDialog(final String[] options, final String[] selected, boolean multiSelect, boolean editable) {
        dismissInlineTextEditor();
        if (options == null || options.length == 0) return;
        if (multiSelect) {
            showMultiChoiceDialog(options, selected);
            return;
        }
        if (editable) {
            showEditableChoiceDialog(selected);
            return;
        }
        int checkedItem = -1;
        if (selected != null && selected.length > 0) {
            String selectedValue = selected[0];
            if (selectedValue != null) {
                for (int i = 0; i < options.length; i++) {
                    if (selectedValue.equals(options[i])) {
                        checkedItem = i;
                        break;
                    }
                }
            }
        }

        AlertDialog.Builder builder = org.opendroidpdf.app.alert.WidgetDialogFactory.newChoiceEntryBuilder(context)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss());

        builder.setSingleChoiceItems(options, checkedItem, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (setWidgetChoiceJob != null) setWidgetChoiceJob.cancel();
                final String selection = options[which];
                setWidgetChoiceJob = widgetController.setWidgetChoiceAsync(new String[]{selection}, new WidgetCompletionCallback() {
                    @Override public void onComplete() {
                        if (changeReporter != null) changeReporter.run();
                    }
                });
                dialog.dismiss();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showChoiceDialog(final String[] options,
                                 final String[] selected,
                                 boolean multiSelect,
                                 boolean editable,
                                 float docRelX,
                                 float docRelY) {
        lastWidgetDocRelX = docRelX;
        lastWidgetDocRelY = docRelY;
        showChoiceDialog(options, selected, multiSelect, editable);
    }

    private void submitText(boolean navigateNext) {
        if (setWidgetTextJob != null) setWidgetTextJob.cancel();
        final String contents = editText.getText().toString();
        final float fromX = lastWidgetDocRelX;
        final float fromY = lastWidgetDocRelY;
        setWidgetTextJob = widgetController.setWidgetTextAsync(pageNumber, contents, new WidgetBooleanCallback() {
            @Override public void onResult(boolean result) {
                if (changeReporter != null) changeReporter.run();
                if (!result) {
                    showTextDialog(contents, fromX, fromY);
                    return;
                }
                if (navigateNext) {
                    FieldNavigationRequester requester = fieldNavigationRequester;
                    if (requester != null) {
                        float safeX = Float.isNaN(fromX) ? Float.NEGATIVE_INFINITY : fromX;
                        float safeY = Float.isNaN(fromY) ? Float.NEGATIVE_INFINITY : fromY;
                        try { requester.navigate(pageNumber, safeX, safeY, 1); } catch (Throwable ignore) {}
                    }
                }
            }
        });
    }

    private void submitInlineText(boolean navigateNext) {
        final EditText editor = inlineEditText;
        if (editor == null) return;
        if (inlineSubmitting) return;
        inlineSubmitting = true;

        if (setWidgetTextJob != null) setWidgetTextJob.cancel();
        final String contents = editor.getText() != null ? editor.getText().toString() : "";
        final float fromX = lastWidgetDocRelX;
        final float fromY = lastWidgetDocRelY;
        if (org.opendroidpdf.BuildConfig.DEBUG) {
            Log.d(TAG, "submitInlineText page=" + pageNumber + " len=" + contents.length() + " navigateNext=" + navigateNext);
        }

        try {
            if (!navigateNext) {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
            }
        } catch (Throwable ignore) {
        }

        try { editor.setEnabled(false); } catch (Throwable ignore) {}

        setWidgetTextJob = widgetController.setWidgetTextAsync(pageNumber, contents, new WidgetBooleanCallback() {
            @Override public void onResult(boolean result) {
                inlineSubmitting = false;
                try { editor.setEnabled(true); } catch (Throwable ignore) {}
                if (org.opendroidpdf.BuildConfig.DEBUG) {
                    Log.d(TAG, "submitInlineText result=" + result);
                }
                if (changeReporter != null) changeReporter.run();
                if (!result) {
                    try { editor.requestFocus(); } catch (Throwable ignore) {}
                    try {
                        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
                    } catch (Throwable ignore) {
                    }
                    return;
                }

                dismissInlineTextEditorInternal(false);

                if (navigateNext) {
                    FieldNavigationRequester requester = fieldNavigationRequester;
                    if (requester != null) {
                        float safeX = Float.isNaN(fromX) ? Float.NEGATIVE_INFINITY : fromX;
                        float safeY = Float.isNaN(fromY) ? Float.NEGATIVE_INFINITY : fromY;
                        try { requester.navigate(pageNumber, safeX, safeY, 1); } catch (Throwable ignore) {}
                    }
                }
            }
        });
    }

    private void showMultiChoiceDialog(final String[] options, final String[] selected) {
        final boolean[] checked = new boolean[options.length];
        if (selected != null) {
            for (String sel : selected) {
                if (sel == null) continue;
                for (int i = 0; i < options.length; i++) {
                    if (sel.equals(options[i])) {
                        checked[i] = true;
                        break;
                    }
                }
            }
        }

        AlertDialog.Builder builder = org.opendroidpdf.app.alert.WidgetDialogFactory.newChoiceEntryBuilder(context)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.okay, (d, w) -> {
                    if (setWidgetChoiceJob != null) setWidgetChoiceJob.cancel();
                    java.util.ArrayList<String> picks = new java.util.ArrayList<>();
                    for (int i = 0; i < options.length; i++) {
                        if (checked[i]) picks.add(options[i]);
                    }
                    setWidgetChoiceJob = widgetController.setWidgetChoiceAsync(
                            picks.toArray(new String[0]),
                            new WidgetCompletionCallback() {
                                @Override public void onComplete() {
                                    if (changeReporter != null) changeReporter.run();
                                }
                            });
                });

        builder.setMultiChoiceItems(options, checked, (dialog, which, isChecked) -> checked[which] = isChecked);
        builder.create().show();
    }

    private void showEditableChoiceDialog(final String[] selected) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        android.view.View dialogView = inflater.inflate(R.layout.dialog_text_input, null);
        final EditText input = dialogView.findViewById(R.id.dialog_text_input);
        input.setSingleLine(true);
        input.setMinLines(1);
        input.setMaxLines(1);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        try {
            String value = (selected != null && selected.length > 0) ? selected[0] : "";
            input.setText(value != null ? value : "");
            if (input.getText() != null) {
                if (input.getText().length() > 0) input.selectAll();
                else input.setSelection(0);
            }
        } catch (Throwable ignore) {
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.choose_value))
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.okay, (d, w) -> {
                    if (setWidgetChoiceJob != null) setWidgetChoiceJob.cancel();
                    final String value = input.getText() != null ? input.getText().toString() : "";
                    setWidgetChoiceJob = widgetController.setWidgetChoiceAsync(new String[]{value}, new WidgetCompletionCallback() {
                        @Override public void onComplete() {
                            if (changeReporter != null) changeReporter.run();
                        }
                    });
                })
                .create();
        try {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        } catch (Throwable ignore) {
        }
        dialog.show();
    }

    private void applyTextFieldUiHints(EditText target, float docRelX, float docRelY) {
        if (target == null) return;
        RectF[] areas = null;
        try {
            areas = widgetController != null ? widgetController.widgetAreas(pageNumber) : null;
        } catch (Throwable ignore) {
            areas = null;
        }
        RectF hit = null;
        if (areas != null && !Float.isNaN(docRelX) && !Float.isNaN(docRelY)) {
            for (RectF r : areas) {
                if (r != null && r.contains(docRelX, docRelY)) {
                    hit = r;
                    break;
                }
            }
        }

        boolean singleLine = false;
        if (hit != null) {
            float h = hit.height();
            float w = hit.width();
            if (h > 0f) {
                float ratio = w / h;
                singleLine = ratio >= 3.0f;
            }
        }

        try {
            if (singleLine) {
                target.setSingleLine(true);
                target.setMinLines(1);
                target.setMaxLines(1);
                target.setInputType(InputType.TYPE_CLASS_TEXT);
                target.setImeOptions(EditorInfo.IME_ACTION_NEXT);
                target.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            } else {
                target.setSingleLine(false);
                target.setMinLines(MULTILINE_MIN_LINES);
                target.setMaxLines(Integer.MAX_VALUE);
                target.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                target.setImeOptions(EditorInfo.IME_ACTION_DONE);
                target.setGravity(Gravity.START | Gravity.TOP);
            }
        } catch (Throwable ignore) {
        }
    }

    public void dismissInlineTextEditor() {
        dismissInlineTextEditorInternal(true);
    }

    private void dismissInlineTextEditorInternal(boolean hideKeyboard) {
        final EditText editor = inlineEditText;
        final InlineTextEditorHost host = inlineHost;
        if (editor != null && host != null) {
            try {
                suppressInlineFocusLoss = true;
                editor.clearFocus();
            } catch (Throwable ignore) {
            }
            if (hideKeyboard) {
                try {
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
                } catch (Throwable ignore) {
                }
            }
            try { host.hideInlineTextEditor(editor); } catch (Throwable ignore) {}
        }
        inlineHost = null;
        inlineBoundsPx = null;
        inlineSubmitting = false;
        suppressInlineFocusLoss = false;
    }

    private void dismissTextDialogIfShown() {
        try {
            if (textEntry != null && textEntry.isShowing()) textEntry.dismiss();
        } catch (Throwable ignore) {
        }
    }

    private void ensureInlineEditor() {
        if (inlineEditText != null) return;
        final EditText editor = new EditText(context);
        try { editor.setId(R.id.dialog_text_input); } catch (Throwable ignore) {}
        try { editor.setSingleLine(true); } catch (Throwable ignore) {}
        try { editor.setMinLines(1); } catch (Throwable ignore) {}
        try { editor.setMaxLines(1); } catch (Throwable ignore) {}
        try { editor.setInputType(InputType.TYPE_CLASS_TEXT); } catch (Throwable ignore) {}
        try { editor.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); } catch (Throwable ignore) {}
        try { editor.setImeOptions(EditorInfo.IME_ACTION_NEXT); } catch (Throwable ignore) {}

        editor.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                submitInlineText(true);
                return true;
            }
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitInlineText(false);
                return true;
            }
            return false;
        });

        editor.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) return;
            if (suppressInlineFocusLoss) return;
            if (org.opendroidpdf.BuildConfig.DEBUG) Log.d(TAG, "inline editor focus lost; committing");
            submitInlineText(false);
        });

        inlineEditText = editor;
    }

    public void release() {
        if (setWidgetTextJob != null) { setWidgetTextJob.cancel(); setWidgetTextJob = null; }
        if (setWidgetChoiceJob != null) { setWidgetChoiceJob.cancel(); setWidgetChoiceJob = null; }
        dismissInlineTextEditorInternal(true);
        fieldNavigationRequester = null;
    }
}
