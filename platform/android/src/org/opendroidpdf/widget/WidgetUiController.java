package org.opendroidpdf.widget;

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

    public void showChoiceDialog(String[] options) {
        if (ui != null) ui.showChoiceDialog(options);
    }

    public void release() {
        if (ui != null) ui.release();
    }
}
