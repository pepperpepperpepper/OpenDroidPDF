package org.opendroidpdf.app.document;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.R;
import org.opendroidpdf.app.helpers.RequestCodes;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.core.MuPdfRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Organize pages is the home for structural PDF edits (merge, extract, rotate, etc).
 *
 * <p>This is intentionally a small first pass: it prioritizes discoverable entry points and
 * reliable outputs, and will evolve into a thumbnail-based organizer UI in later phases.</p>
 */
public final class OrganizePagesController {

    public interface Host {
        @NonNull AppCompatActivity getActivity();
        @NonNull Context getContext();
        @NonNull android.content.ContentResolver getContentResolver();
        @Nullable MuPdfRepository getRepository();
        void commitPendingInkToCoreBlocking();
        void showInfo(@NonNull String message);
        @NonNull String currentDocumentName();
        void startActivityForResult(@NonNull Intent intent, int requestCode);
        void callInBackgroundAndShowDialog(@NonNull String message,
                                           @NonNull Callable<Exception> background,
                                           @Nullable Callable<Void> success,
                                           @Nullable Callable<Void> failure);
        @Nullable SidecarAnnotationProvider sidecarAnnotationProviderOrNull();
    }

    enum OperationType {
        EXTRACT_PAGES,
        REMOVE_PAGES,
        ROTATE_PAGES,
        MERGE_APPEND,
        REORDER_PAGES,
        INSERT_BLANK_PAGE,
        INSERT_FROM_PDF,
        SAVE_STAGED_COPY
    }

    static final class PendingOperation {
        final OperationType type;
        @Nullable final String pageSpec;
        @Nullable final String rotateExpr;
        @Nullable final Uri otherPdfUri;
        @Nullable final Integer insertBeforePage;

        PendingOperation(@NonNull OperationType type,
                         @Nullable String pageSpec,
                         @Nullable String rotateExpr,
                         @Nullable Uri otherPdfUri,
                         @Nullable Integer insertBeforePage) {
            this.type = type;
            this.pageSpec = pageSpec;
            this.rotateExpr = rotateExpr;
            this.otherPdfUri = otherPdfUri;
            this.insertBeforePage = insertBeforePage;
        }
    }

    private final Host host;
    private final OrganizePagesUi ui;
    private final OrganizePagesOps ops;

    private @Nullable PendingOperation pendingOutput;
    private @Nullable PendingOperation pendingInsertPick;

    public OrganizePagesController(@NonNull Host host) {
        this.host = host;
        this.ui = new OrganizePagesUi(host);
        this.ops = new OrganizePagesOps(host, ui::updateDoneEnabledState);
    }

    public void show() {
        AppCompatActivity activity = host.getActivity();
        if (activity == null) return;

        MuPdfRepository repo = host.getRepository();
        if (repo == null || !repo.isPdfDocument()) {
            host.showInfo(activity.getString(R.string.not_supported));
            return;
        }

        ui.showSheet(
                this::promptExtractPages,
                this::promptMergeAppend,
                this::promptInsertBlankPage,
                this::promptInsertFromPdf,
                this::promptRemovePages,
                this::promptReorderPages,
                this::promptRotatePages,
                this::onDoneTapped,
                () -> ops.hasStagedChanges(),
                this::clearAllState,
                this::clearAllState);
    }

    public void showInsertBlankPage() {
        show();
        try {
            promptInsertBlankPage();
        } catch (Throwable ignore) {
        }
    }

    public void onActivityResultPickMergeInput(int resultCode, @Nullable Intent intent) {
        if (resultCode != Activity.RESULT_OK || intent == null || intent.getData() == null) {
            return;
        }
        Uri picked = intent.getData();
        if (picked == null) {
            return;
        }
        OrganizePagesOps.takePersistableReadPermission(host.getContentResolver(), picked);

        final Uri pickedFinal = picked;
        ops.ensureStagedCopyReady(() -> ops.applyOperationToStagedCopy(
                new PendingOperation(OperationType.MERGE_APPEND, null, null, pickedFinal, null)));
    }

    public void onActivityResultPickInsertInput(int resultCode, @Nullable Intent intent) {
        final PendingOperation op = pendingInsertPick;
        if (resultCode != Activity.RESULT_OK || intent == null || intent.getData() == null || op == null) {
            pendingInsertPick = null;
            return;
        }
        if (op.type != OperationType.INSERT_FROM_PDF || op.insertBeforePage == null) {
            pendingInsertPick = null;
            return;
        }

        Uri picked = intent.getData();
        if (picked == null) {
            pendingInsertPick = null;
            return;
        }
        OrganizePagesOps.takePersistableReadPermission(host.getContentResolver(), picked);

        pendingInsertPick = null;
        final Uri pickedFinal = picked;
        ops.ensureStagedCopyReady(() -> ops.applyOperationToStagedCopy(
                new PendingOperation(OperationType.INSERT_FROM_PDF, null, null, pickedFinal, op.insertBeforePage)));
    }

    public void onActivityResultSaveOutput(int resultCode, @Nullable Intent intent) {
        if (resultCode != Activity.RESULT_OK || intent == null || intent.getData() == null) {
            pendingOutput = null;
            return;
        }
        final Uri dest = intent.getData();
        final PendingOperation op = pendingOutput;
        pendingOutput = null;
        if (dest == null || op == null) return;

        ops.saveOutputToUri(dest, op, () -> {
            host.showInfo(host.getContext().getString(R.string.save_complete));
            if (op.type == OperationType.SAVE_STAGED_COPY) {
                if (!ui.dismissSheetIfShown()) {
                    clearAllState();
                }
            }
        });
    }

    private void promptExtractPages() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ops.ensureStagedCopyReady(() -> ui.promptForPageSpec(
                R.string.organize_pages_prompt_extract_title,
                R.string.saveas,
                value -> {
                    pendingOutput = new PendingOperation(OperationType.EXTRACT_PAGES, value, null, null, null);
                    launchSaveCreateIntent(pendingOutput);
                }));
    }

    private void promptRemovePages() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ops.ensureStagedCopyReady(() -> {
            final MuPdfRepository repo = ops.stagedRepoOrNull();
            if (repo == null) return;
            final int pageCount = repo.getPageCount();
            ui.promptForPageSpec(
                    R.string.organize_pages_prompt_remove_title,
                    R.string.menu_accept,
                    value -> {
                        try {
                            Set<Integer> toRemove = OrganizePagesOps.parsePages(value, pageCount);
                            if (toRemove.isEmpty()) {
                                host.showInfo(host.getContext().getString(R.string.organize_pages_error_pages_required));
                                return;
                            }
                            List<Integer> keep = new ArrayList<>();
                            for (int i = 1; i <= pageCount; i++) {
                                if (!toRemove.contains(i)) keep.add(i);
                            }
                            if (keep.isEmpty()) {
                                host.showInfo(host.getContext().getString(R.string.organize_pages_error_delete_all_pages));
                                return;
                            }
                            String keepSpec = OrganizePagesOps.compressPages(keep);
                            ops.applyOperationToStagedCopy(new PendingOperation(OperationType.REMOVE_PAGES, keepSpec, null, null, null));
                        } catch (IllegalArgumentException e) {
                            host.showInfo(host.getContext().getString(R.string.organize_pages_error_invalid_pages));
                        }
                    });
        });
    }

    private void promptReorderPages() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ops.ensureStagedCopyReady(() -> {
            final MuPdfRepository repo = ops.stagedRepoOrNull();
            if (repo == null) return;
            int pageCount = 0;
            try { pageCount = repo.getPageCount(); } catch (Throwable ignore) { pageCount = 0; }
            if (pageCount <= 1) {
                host.showInfo(host.getContext().getString(R.string.organize_pages_error_reorder_requires_multiple_pages));
                return;
            }

            ui.showReorderDialog(repo, pageCount, spec -> {
                if (spec == null || spec.trim().isEmpty()) {
                    host.showInfo(host.getContext().getString(R.string.organize_pages_error_pages_required));
                    return;
                }
                ops.applyOperationToStagedCopy(new PendingOperation(OperationType.REORDER_PAGES, spec, null, null, null));
            });
        });
    }

    private void promptRotatePages() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ops.ensureStagedCopyReady(() -> ui.promptRotatePages(expr ->
                ops.applyOperationToStagedCopy(new PendingOperation(OperationType.ROTATE_PAGES, null, expr, null, null))));
    }

    private void promptMergeAppend() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ui.promptMergeAppend();
    }

    private void promptInsertBlankPage() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ops.ensureStagedCopyReady(() -> {
            final MuPdfRepository repo = ops.stagedRepoOrNull();
            if (repo == null) return;
            final int pageCount = repo.getPageCount();
            ui.promptForInsertPosition(
                    R.string.organize_pages_prompt_insert_blank_title,
                    repo,
                    pageCount,
                    insertBeforePage -> ops.applyOperationToStagedCopy(
                            new PendingOperation(OperationType.INSERT_BLANK_PAGE, null, null, null, insertBeforePage)));
        });
    }

    private void promptInsertFromPdf() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ops.ensureStagedCopyReady(() -> {
            final MuPdfRepository repo = ops.stagedRepoOrNull();
            if (repo == null) return;
            final int pageCount = repo.getPageCount();
            ui.promptForInsertPosition(
                    R.string.organize_pages_prompt_insert_from_pdf_title,
                    repo,
                    pageCount,
                    insertBeforePage -> {
                        pendingInsertPick = new PendingOperation(OperationType.INSERT_FROM_PDF, null, null, null, insertBeforePage);
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType(DocumentAccessIntents.MIME_PDF);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        host.startActivityForResult(intent, RequestCodes.ORGANIZE_PAGES_PICK_INSERT);
                    });
        });
    }

    private void onDoneTapped() {
        if (!ops.hasStagedChanges()) {
            ui.dismissSheetIfShown();
            return;
        }
        ops.ensureStagedCopyReady(() -> {
            pendingOutput = new PendingOperation(OperationType.SAVE_STAGED_COPY, null, null, null, null);
            launchSaveCreateIntent(pendingOutput);
        });
    }

    private void clearAllState() {
        pendingOutput = null;
        pendingInsertPick = null;
        ops.clearStagedState();
    }

    private void launchSaveCreateIntent(@Nullable PendingOperation op) {
        if (op == null) return;
        final Context context = host.getContext();
        String docTitle = host.currentDocumentName();
        if (docTitle == null || docTitle.trim().isEmpty()) docTitle = "document.pdf";
        String suffix = OrganizePagesOps.suffixFor(op.type);
        if (!docTitle.toLowerCase().endsWith(".pdf")) {
            docTitle = docTitle + suffix;
        } else if (!docTitle.toLowerCase().endsWith(suffix.toLowerCase())) {
            docTitle = docTitle.substring(0, docTitle.length() - 4) + suffix;
        }
        Intent intent;
        if (android.os.Build.VERSION.SDK_INT < 19) {
            intent = new Intent(context.getApplicationContext(), org.opendroidpdf.OpenDroidPDFFileChooser.class);
            intent.putExtra(Intent.EXTRA_TITLE, docTitle);
            intent.setAction(Intent.ACTION_PICK);
        } else {
            intent = DocumentAccessIntents.newCreatePdfDocumentIntent(docTitle);
        }
        host.startActivityForResult(intent, RequestCodes.ORGANIZE_PAGES_SAVE_OUTPUT);
    }
}
