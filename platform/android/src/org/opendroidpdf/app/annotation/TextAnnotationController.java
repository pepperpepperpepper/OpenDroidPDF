package org.opendroidpdf.app.annotation;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.R;

/**
 * Owns the text-annotation UI flow (prompt + commit) so activity/view wiring
 * stays minimal and the edit pipeline has a single owner.
 */
public final class TextAnnotationController {

    public interface Host {
        AppCompatActivity activity();
        AlertDialog.Builder alertBuilder();
        MuPDFPageView currentPageView();
    }

    private final Host host;

    public TextAnnotationController(Host host) {
        this.host = host;
    }

    public void requestTextAnnotationFromUserInput(final Annotation annotation) {
        if (annotation == null) return;

        final Host host = this.host;
        if (host == null) return;

        final AppCompatActivity activity = host.activity();
        final AlertDialog.Builder builder = host.alertBuilder();
        final MuPDFPageView pageView = host.currentPageView();
        if (activity == null || builder == null || pageView == null) return;

        final AlertDialog dialog = builder.create();
        final View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_text_input, null, false);
        final EditText input = dialogView.findViewById(R.id.dialog_text_input);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_NORMAL
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint(activity.getString(R.string.add_text_placeholder));
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setHorizontallyScrolling(false);
        input.setBackgroundDrawable(null);
        if (annotation.text != null) input.setText(annotation.text);

        dialog.setView(dialogView);

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, activity.getString(R.string.save),
                (d, which) -> {
                    try {
                        annotation.text = input.getText().toString();
                        // objectNumber is a packed (objnum<<32)|gen; treat 0/negative as "no stable id".
                        if (annotation.objectNumber > 0L) {
                            try { pageView.deselectAnnotation(); } catch (Throwable ignore) {}
                            pageView.updateTextAnnotationContentsByObjectNumber(annotation.objectNumber, annotation.text);
                        } else {
                            boolean shouldReplaceExisting = false;
                            if (annotation.type == Annotation.Type.TEXT) {
                                // Sidecar note edit: the sidecar selection controller owns note selection; deleteSelectedAnnotation()
                                // routes through PageSelectionCoordinator and removes the note from the sidecar store.
                                shouldReplaceExisting = true;
                            } else {
                                // Embedded FreeText without a stable object id: replace the currently selected text annotation.
                                // Avoid accidental deletes for brand new annotations by requiring an existing text selection.
                                try {
                                    Annotation.Type selectedType = pageView.selectedAnnotationType();
                                    shouldReplaceExisting = (selectedType == Annotation.Type.FREETEXT || selectedType == Annotation.Type.TEXT);
                                } catch (Throwable ignore) {
                                    shouldReplaceExisting = false;
                                }
                            }

                            if (shouldReplaceExisting) {
                                try { pageView.deleteSelectedAnnotation(); } catch (Throwable ignore) {}
                            } else {
                                try { pageView.deselectAnnotation(); } catch (Throwable ignore) {}
                            }
                            pageView.addTextAnnotationFromUi(annotation);
                        }
                    } catch (Throwable t) {
                        android.util.Log.e("TextAnnotationController", "Failed to save text annotation", t);
                    }
                    dialog.setOnCancelListener(null);
                });

        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getString(R.string.cancel),
                (d, which) -> {
                    try { pageView.deselectAnnotation(); } catch (Throwable ignore) {}
                    dialog.setOnCancelListener(null);
                });

        dialog.setOnCancelListener(di -> { try { pageView.deselectAnnotation(); } catch (Throwable ignore) {} });
        dialog.show();
    }
}
