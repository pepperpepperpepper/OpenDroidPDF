package org.opendroidpdf.app.annotation;

import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.R;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.app.annotation.FreeTextBoundsFitter;
import org.opendroidpdf.app.comments.CommentsNavigationController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.core.MuPdfRepository;

import java.util.Objects;

/**
 * Owns the text-annotation UI flow (prompt + commit) so activity/view wiring
 * stays minimal and the edit pipeline has a single owner.
 */
public final class TextAnnotationController {
    private static final String TAG = "TextAnnotCtrl";

    public interface Host {
        AppCompatActivity activity();
        AlertDialog.Builder alertBuilder();
        MuPDFPageView currentPageView();
        @Nullable MuPDFReaderView docViewOrNull();
        @Nullable MuPdfRepository repoOrNull();
        @Nullable SidecarAnnotationProvider sidecarAnnotationProviderOrNull();
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
        final MuPDFReaderView docView = host.docViewOrNull();
        final MuPdfRepository repo = host.repoOrNull();
        final SidecarAnnotationProvider sidecarProvider = host.sidecarAnnotationProviderOrNull();
        if (activity == null || builder == null || pageView == null) return;
        if (activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;
        final int pageNumber = pageView.pageNumber();
        final boolean canNavigateComments = (docView != null && repo != null);

        // Prefer in-place editing (Acrobat-ish). Fall back to the dialog for unsupported cases
        // (e.g. rotated/rich FreeText) or when inline placement fails.
        try {
            if (pageView.showInlineTextAnnotationEditor(annotation)) {
                return;
            }
        } catch (Throwable ignore) {
        }

        final AlertDialog dialog = builder.create();
        final View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_text_annotation_input, null, false);
        final EditText input = dialogView.findViewById(R.id.dialog_text_input);
        final android.widget.TextView richWarning = dialogView.findViewById(R.id.dialog_text_rich_warning);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_NORMAL
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint(activity.getString(R.string.add_text_placeholder));
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setHorizontallyScrolling(false);
        input.setBackgroundDrawable(null);
        if (annotation.text != null) input.setText(annotation.text);

        if (richWarning != null) {
            boolean showWarning = false;
            try {
                if (annotation.type == Annotation.Type.FREETEXT && annotation.objectNumber > 0L && repo != null) {
                    showWarning = repo.hasFreeTextRichContentsByObjectNumber(pageNumber, annotation.objectNumber);
                }
            } catch (Throwable ignore) {
                showWarning = false;
            }
            richWarning.setVisibility(showWarning ? View.VISIBLE : View.GONE);
        }

        dialog.setView(dialogView);

        final View prev = dialogView.findViewById(R.id.dialog_text_prev);
        final View next = dialogView.findViewById(R.id.dialog_text_next);
        if (prev != null) {
            prev.setEnabled(canNavigateComments);
            prev.setAlpha(canNavigateComments ? 1f : 0.35f);
            prev.setOnClickListener(v -> {
                if (!canNavigateComments || docView == null || repo == null) return;
                CommentsNavigationController.SelectionKey key = CommentsNavigationController.captureCurrentSelection(docView);
                int currentPage = safeSelectedPage(docView);
                commitTextAnnotation(pageView, pageNumber, annotation, input, dialog, true);
                try { dialog.dismiss(); } catch (Throwable ignore) {}
                new CommentsNavigationController().navigateFrom(activity, docView, repo, sidecarProvider, -1, key, currentPage);
                scheduleEditSelectedAnnotation(activity, docView, 8);
            });
        }
        if (next != null) {
            next.setEnabled(canNavigateComments);
            next.setAlpha(canNavigateComments ? 1f : 0.35f);
            next.setOnClickListener(v -> {
                if (!canNavigateComments || docView == null || repo == null) return;
                CommentsNavigationController.SelectionKey key = CommentsNavigationController.captureCurrentSelection(docView);
                int currentPage = safeSelectedPage(docView);
                commitTextAnnotation(pageView, pageNumber, annotation, input, dialog, true);
                try { dialog.dismiss(); } catch (Throwable ignore) {}
                new CommentsNavigationController().navigateFrom(activity, docView, repo, sidecarProvider, 1, key, currentPage);
                scheduleEditSelectedAnnotation(activity, docView, 8);
            });
        }

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, activity.getString(R.string.save),
                (d, which) -> {
                    dialog.setOnCancelListener(null);
                    commitTextAnnotation(pageView, pageNumber, annotation, input, dialog, true);
                });

        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getString(R.string.cancel),
                (d, which) -> {
                    try {
                        if (!isPageViewActive(pageView, pageNumber)) return;
                        pageView.deselectAnnotation();
                    } catch (Throwable ignore) {}
                    dialog.setOnCancelListener(null);
                });

        dialog.setOnCancelListener(di -> {
            try {
                if (!isPageViewActive(pageView, pageNumber)) return;
                pageView.deselectAnnotation();
            } catch (Throwable ignore) {}
        });
        try {
            dialog.show();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to show text annotation dialog", t);
        }
    }

    private static void commitTextAnnotation(@NonNull MuPDFPageView pageView,
                                             int expectedPageNumber,
                                             @NonNull Annotation annotation,
                                             @NonNull EditText input,
                                             @NonNull AlertDialog dialog,
                                             boolean deselectOnCommit) {
        try {
            if (!isPageViewActive(pageView, expectedPageNumber)) return;
            final String priorText = annotation.text;
            final String nextText = input.getText() != null ? input.getText().toString() : "";
            annotation.text = nextText;
            // objectNumber is a packed (objnum<<32)|gen; treat 0/negative as "no stable id".
            if (annotation.objectNumber > 0L) {
                if (deselectOnCommit) {
                    try { pageView.deselectAnnotation(); } catch (Throwable ignore) {}
                }
                // Avoid unintended destructive edits (e.g. converting Acrobat rich text to plain)
                // when the user is simply stepping through comments without changing text.
                if (Objects.equals(priorText, nextText)) return;
                pageView.updateTextAnnotationContentsByObjectNumber(annotation.objectNumber, annotation.text);
                return;
            }

            if (annotation.type == Annotation.Type.TEXT) {
                // Sidecar note edit: update in-place so the selection/id remains stable.
                try {
                    if (pageView.textAnnotationDelegate().updateSelectedSidecarNoteText(annotation.text)) {
                        dialog.setOnCancelListener(null);
                        return;
                    }
                } catch (Throwable ignore) {
                }
            }

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
            } else if (deselectOnCommit) {
                try { pageView.deselectAnnotation(); } catch (Throwable ignore) {}
            }
            // For brand-new FreeText, tighten the default box around the entered content
            // (Acrobat-ish) so we don't end up with a huge empty rectangle.
            try {
                if (annotation.type == Annotation.Type.FREETEXT
                        && annotation.text != null
                        && !annotation.text.trim().isEmpty()) {
                    float scale = pageView.getScale();
                    if (scale > 0f) {
                        float docW = pageView.getWidth() / scale;
                        float docH = pageView.getHeight() / scale;
                        android.graphics.RectF fitted = FreeTextBoundsFitter.compute(
                                pageView.getResources(),
                                scale,
                                docW,
                                docH,
                                new android.graphics.RectF(annotation),
                                annotation.text,
                                12.0f,
                                160,
                                false,
                                true);
                        if (fitted != null) {
                            annotation.set(fitted);
                        }
                    }
                }
            } catch (Throwable ignore) {
            }
            pageView.addTextAnnotationFromUi(annotation);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to save text annotation", t);
        }
    }

    private static int safeSelectedPage(@NonNull MuPDFReaderView docView) {
        try {
            return docView.getSelectedItemPosition();
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private static void scheduleEditSelectedAnnotation(@NonNull AppCompatActivity activity,
                                                       @NonNull MuPDFReaderView docView,
                                                       int attemptsRemaining) {
        if (attemptsRemaining <= 0) return;
        AppCoroutines.launchMainDelayed(AppCoroutines.mainScope(), 120L, () -> {
            if (activity.isFinishing()) return;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;
            } catch (Throwable ignore) {
            }
            try {
                android.view.View v = docView.getSelectedView();
                if (v instanceof MuPDFPageView) {
                    MuPDFPageView pv = (MuPDFPageView) v;
                    boolean hasSelected = false;
                    try { hasSelected = (pv.textAnnotationDelegate().selectedEmbeddedAnnotationOrNull() != null); } catch (Throwable ignore) {}
                    if (!hasSelected) {
                        try { hasSelected = (pv.selectedSidecarSelectionOrNull() != null); } catch (Throwable ignore) {}
                    }
                    if (hasSelected) {
                        pv.editSelectedAnnotation();
                        return;
                    }
                }
            } catch (Throwable ignore) {
            }
            scheduleEditSelectedAnnotation(activity, docView, attemptsRemaining - 1);
        });
    }

    private static boolean isPageViewActive(MuPDFPageView pageView, int expectedPageNumber) {
        if (pageView == null) return false;
        try {
            if (pageView.pageNumber() != expectedPageNumber) return false;
        } catch (Throwable ignore) {
            // Best-effort; keep going.
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                return pageView.isAttachedToWindow();
            }
            return pageView.getWindowToken() != null;
        } catch (Throwable ignore) {
            return false;
        }
    }
}
