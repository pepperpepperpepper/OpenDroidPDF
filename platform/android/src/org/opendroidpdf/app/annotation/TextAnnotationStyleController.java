package org.opendroidpdf.app.annotation;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.R;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.services.TextStylePreferencesService;

/** Controls the "Text style" dialog and applies color/size changes to the selected text box. */
public class TextAnnotationStyleController {

    public interface Host {
        @NonNull Context getContext();
        @NonNull LayoutInflater getLayoutInflater();
        @Nullable MuPDFPageView activePageViewOrNull();
        void showAnnotationInfo(@NonNull String message);
    }

    private final TextStylePreferencesService prefs;
    private final Host host;

    public TextAnnotationStyleController(@NonNull TextStylePreferencesService prefs,
                                         @NonNull Host host) {
        this.prefs = prefs;
        this.host = host;
    }

    public void show() {
        final Context context = host.getContext();
        final MuPDFPageView pageView = host.activePageViewOrNull();
        if (pageView == null) {
            host.showAnnotationInfo(context.getString(R.string.select_text_annot_to_style));
            return;
        }

        Annotation.Type selectedType = null;
        try { selectedType = pageView.selectedAnnotationType(); } catch (Throwable ignore) { selectedType = null; }
        boolean canStyle = (selectedType == Annotation.Type.FREETEXT);
        if (!canStyle) {
            try {
                SidecarSelectionController.Selection sel = pageView.selectedSidecarSelectionOrNull();
                canStyle = sel != null && sel.kind == SidecarSelectionController.Kind.NOTE;
            } catch (Throwable ignore) {
                canStyle = false;
            }
        }
        if (!canStyle) {
            host.showAnnotationInfo(context.getString(R.string.select_text_annot_to_style));
            return;
        }

        TextAnnotationStyleUi.showDialog(prefs, host, pageView);
    }
}

