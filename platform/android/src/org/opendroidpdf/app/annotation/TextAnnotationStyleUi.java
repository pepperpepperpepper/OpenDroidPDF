package org.opendroidpdf.app.annotation;

import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.app.services.TextStylePreferencesService;

final class TextAnnotationStyleUi {
    private TextAnnotationStyleUi() {
    }

    static void showDialog(TextStylePreferencesService prefs,
                           TextAnnotationStyleController.Host host,
                           MuPDFPageView pageView) {
        TextAnnotationStyleDialogBinder.showDialog(prefs, host, pageView);
    }
}
