package org.opendroidpdf.app.ui;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.R;
import org.opendroidpdf.app.document.DocumentState;

/**
 * Small helper to hold misc UI state helpers (title, alerts, memory checks)
 * outside the activity body.
 */
public final class UiStateDelegate {
    public interface DocumentStateProvider {
        @NonNull DocumentState currentDocumentState();
    }

    public interface DocViewProvider {
        @Nullable MuPDFReaderView docViewOrNull();
    }

    private final AppCompatActivity activity;
    private final DocumentStateProvider documentStateProvider;
    private final DocViewProvider docViewProvider;
    private AlertDialog.Builder alertBuilder;
    @Nullable private Snackbar reflowLayoutMismatchSnackbar;
    @Nullable private Snackbar pdfReadOnlySnackbar;
    @Nullable private Snackbar importedWordSnackbar;

    public UiStateDelegate(@NonNull AppCompatActivity activity,
                           @NonNull DocumentStateProvider documentStateProvider,
                           @NonNull DocViewProvider docViewProvider) {
        this.activity = activity;
        this.documentStateProvider = documentStateProvider;
        this.docViewProvider = docViewProvider;
    }

    public AlertDialog.Builder alertBuilder() {
        if (alertBuilder == null) {
            alertBuilder = new AlertDialog.Builder(activity);
            alertBuilder.setTitle(R.string.app_name);
        }
        return alertBuilder;
    }

    public void setAlertBuilder(AlertDialog.Builder builder) {
        this.alertBuilder = builder;
    }

    public void setTitle() {
        DocumentState docState = documentStateProvider != null ? documentStateProvider.currentDocumentState() : null;
        MuPDFReaderView docView = docViewProvider != null ? docViewProvider.docViewOrNull() : null;
        org.opendroidpdf.app.ui.TitleHelper.setTitle(activity, docView, docState);
    }

    public boolean isMemoryLow() {
        return org.opendroidpdf.app.ui.UiUtils.isMemoryLow(activity);
    }

    public void showReflowLayoutMismatchBanner(@StringRes int messageResId,
                                               @NonNull Runnable onSwitchToAnnotatedLayout) {
        View anchor = activity.findViewById(R.id.main_layout);
        if (anchor == null) {
            anchor = activity.findViewById(android.R.id.content);
        }
        if (anchor == null) return;

        dismissReflowLayoutMismatchBanner();

        Snackbar sb = Snackbar.make(
                anchor,
                activity.getString(messageResId),
                Snackbar.LENGTH_INDEFINITE);
        sb.setAction(R.string.reflow_switch_to_annotated, v -> {
            try {
                onSwitchToAnnotatedLayout.run();
            } finally {
                // The mismatch might persist across multiple layout profiles; callers can re-show.
                dismissReflowLayoutMismatchBanner();
            }
        });
        reflowLayoutMismatchSnackbar = sb;
        sb.show();
    }

    public void dismissReflowLayoutMismatchBanner() {
        Snackbar sb = reflowLayoutMismatchSnackbar;
        reflowLayoutMismatchSnackbar = null;
        if (sb != null) {
            try { sb.dismiss(); } catch (Throwable ignore) {}
        }
    }

    public void showPdfReadOnlyBanner(@StringRes int messageResId,
                                     @StringRes int actionResId,
                                     @NonNull Runnable onEnableSaving) {
        View anchor = activity.findViewById(R.id.main_layout);
        if (anchor == null) {
            anchor = activity.findViewById(android.R.id.content);
        }
        if (anchor == null) return;

        dismissPdfReadOnlyBanner();

        Snackbar sb = Snackbar.make(
                anchor,
                activity.getString(messageResId),
                Snackbar.LENGTH_INDEFINITE);
        sb.setAction(actionResId, v -> {
            try {
                onEnableSaving.run();
            } finally {
                dismissPdfReadOnlyBanner();
            }
        });
        pdfReadOnlySnackbar = sb;
        sb.show();
    }

    public void dismissPdfReadOnlyBanner() {
        Snackbar sb = pdfReadOnlySnackbar;
        pdfReadOnlySnackbar = null;
        if (sb != null) {
            try { sb.dismiss(); } catch (Throwable ignore) {}
        }
    }

    public void showImportedWordBanner(@StringRes int messageResId,
                                       @StringRes int actionResId,
                                       @NonNull Runnable onLearnMore) {
        View anchor = activity.findViewById(R.id.main_layout);
        if (anchor == null) {
            anchor = activity.findViewById(android.R.id.content);
        }
        if (anchor == null) return;

        dismissImportedWordBanner();

        Snackbar sb = Snackbar.make(
                anchor,
                activity.getString(messageResId),
                Snackbar.LENGTH_INDEFINITE);
        sb.setAction(actionResId, v -> {
            try {
                onLearnMore.run();
            } finally {
                // Keep the banner dismissed once the user acknowledges it.
                dismissImportedWordBanner();
            }
        });
        importedWordSnackbar = sb;
        sb.show();
    }

    public void dismissImportedWordBanner() {
        Snackbar sb = importedWordSnackbar;
        importedWordSnackbar = null;
        if (sb != null) {
            try { sb.dismiss(); } catch (Throwable ignore) {}
        }
    }
}
