package org.opendroidpdf.widget;

import android.graphics.Rect;

import org.opendroidpdf.app.widget.WidgetUiBridge;

/**
 * Holds widget UI interactions so MuPDFPageView only delegates.
 */
public class WidgetUiController {
    private final WidgetUiBridge ui;

    public WidgetUiController(WidgetUiBridge ui) {
        this.ui = ui;
    }

    public void setChangeReporter(Runnable reporter) {
        if (ui != null) ui.setChangeReporter(() -> reporter.run());
    }

    public void setPageNumber(int page) {
        if (ui != null) ui.setPageNumber(page);
    }

    public void showTextDialog(String text) {
        if (ui != null) ui.showTextDialog(text);
    }

    public void showChoiceDialog(String[] options, String[] selected) {
        if (ui != null) ui.showChoiceDialog(options, selected);
    }

    public void showTextDialog(String text, float docRelX, float docRelY) {
        if (ui != null) ui.showTextDialog(text, docRelX, docRelY);
    }

    public void showInlineTextEditor(WidgetUiBridge.InlineTextEditorHost host,
                                     String text,
                                     Rect boundsPx,
                                     float docRelX,
                                     float docRelY) {
        if (ui != null) ui.showInlineTextEditor(host, text, boundsPx, docRelX, docRelY);
    }

    public void dismissInlineTextEditor() {
        if (ui != null) ui.dismissInlineTextEditor();
    }

    public void showChoiceDialog(String[] options, String[] selected, float docRelX, float docRelY) {
        if (ui != null) ui.showChoiceDialog(options, selected, false, false, docRelX, docRelY);
    }

    public void setFieldNavigationRequester(WidgetUiBridge.FieldNavigationRequester requester) {
        if (ui != null) ui.setFieldNavigationRequester(requester);
    }

    public void showChoiceDialog(String[] options,
                                 String[] selected,
                                 boolean multiSelect,
                                 boolean editable,
                                 float docRelX,
                                 float docRelY) {
        if (ui != null) ui.showChoiceDialog(options, selected, multiSelect, editable, docRelX, docRelY);
    }

    public void release() {
        if (ui != null) ui.release();
    }
}
