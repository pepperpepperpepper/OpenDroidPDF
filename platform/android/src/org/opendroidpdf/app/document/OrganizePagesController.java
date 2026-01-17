package org.opendroidpdf.app.document;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.net.Uri;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.MuPDFCore;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.app.helpers.RequestCodes;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.core.PdfOps;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private enum OperationType {
        EXTRACT_PAGES,
        REMOVE_PAGES,
        ROTATE_PAGES,
        MERGE_APPEND,
        REORDER_PAGES,
        INSERT_BLANK_PAGE,
        INSERT_FROM_PDF,
        SAVE_STAGED_COPY
    }

    private static final class PendingOperation {
        final OperationType type;
        @Nullable final String pageSpec;
        @Nullable final String rotateExpr;
        @Nullable final Uri otherPdfUri;
        @Nullable final Integer insertBeforePage;

        private PendingOperation(@NonNull OperationType type,
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

    private interface StringConsumer {
        void accept(@NonNull String value);
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private final Host host;
    private @Nullable PendingOperation pendingOutput;
    private @Nullable PendingOperation pendingInsertPick;
    private boolean hasStagedChanges;

    private @Nullable File stagedPdfFile;
    private @Nullable OpenDroidPDFCore stagedCore;
    private @Nullable MuPdfRepository stagedRepo;

    private @Nullable BottomSheetDialog sheetDialog;
    private @Nullable View doneAction;

    private final List<OpenDroidPDFCore> staleCores = new ArrayList<>();
    private final List<File> staleFiles = new ArrayList<>();

    public OrganizePagesController(@NonNull Host host) {
        this.host = host;
    }

    public void show() {
        AppCompatActivity activity = host.getActivity();
        if (activity == null) return;

        MuPdfRepository repo = host.getRepository();
        if (repo == null || !repo.isPdfDocument()) {
            host.showInfo(activity.getString(R.string.not_supported));
            return;
        }

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_organize_pages_sheet, null);
        dialog.setContentView(root);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        sheetDialog = dialog;

        View extract = root.findViewById(R.id.organize_pages_action_extract);
        if (extract != null) {
            extract.setOnClickListener(v -> {
                promptExtractPages();
            });
        }

        View merge = root.findViewById(R.id.organize_pages_action_merge);
        if (merge != null) {
            merge.setOnClickListener(v -> {
                promptMergeAppend();
            });
        }

        View insertBlank = root.findViewById(R.id.organize_pages_action_insert_blank);
        if (insertBlank != null) {
            insertBlank.setOnClickListener(v -> {
                promptInsertBlankPage();
            });
        }

        View insertFromPdf = root.findViewById(R.id.organize_pages_action_insert_from_pdf);
        if (insertFromPdf != null) {
            insertFromPdf.setOnClickListener(v -> {
                promptInsertFromPdf();
            });
        }

        View remove = root.findViewById(R.id.organize_pages_action_remove);
        if (remove != null) {
            remove.setOnClickListener(v -> {
                promptRemovePages();
            });
        }

        View reorder = root.findViewById(R.id.organize_pages_action_reorder);
        if (reorder != null) {
            reorder.setOnClickListener(v -> {
                promptReorderPages();
            });
        }

        View rotate = root.findViewById(R.id.organize_pages_action_rotate);
        if (rotate != null) {
            rotate.setOnClickListener(v -> {
                promptRotatePages();
            });
        }

        View cancel = root.findViewById(R.id.organize_pages_action_cancel);
        if (cancel != null) {
            cancel.setOnClickListener(v -> confirmDiscardAndClose(dialog));
        }

        View done = root.findViewById(R.id.organize_pages_action_done);
        doneAction = done;
        updateDoneEnabledState();
        if (done != null) {
            done.setOnClickListener(v -> onDoneTapped());
        }

        dialog.setOnDismissListener(d -> {
            if (sheetDialog == dialog) {
                sheetDialog = null;
            }
            doneAction = null;
            clearStagedState();
        });

        dialog.show();
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
        try {
            host.getContentResolver().takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignore) {
        }

        final Uri pickedFinal = picked;
        ensureStagedCopyReady(() -> applyOperationToStagedCopy(
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
        try {
            host.getContentResolver().takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignore) {
        }

        pendingInsertPick = null;
        final Uri pickedFinal = picked;
        ensureStagedCopyReady(() -> applyOperationToStagedCopy(
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

        final Context appContext = host.getContext().getApplicationContext();
        final String documentName = host.currentDocumentName();
        final File staged = stagedPdfFile;
        if (staged == null || !staged.isFile()) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        host.callInBackgroundAndShowDialog(
                host.getContext().getString(R.string.organize_pages_preparing),
                new Callable<Exception>() {
                    @Override
                    public Exception call() {
                        try {
                            if (!BuildConfig.ENABLE_QPDF_OPS) {
                                return new Exception("qpdf ops disabled");
                            }
                            File out = newTempPdfFile(appContext, documentName, suffixFor(op.type));

                            boolean ok;
                            switch (op.type) {
                                case EXTRACT_PAGES:
                                    if (op.pageSpec == null || op.pageSpec.trim().isEmpty()) {
                                        return new Exception("Missing page selection");
                                    }
                                    ok = PdfOps.INSTANCE.extractPages(staged, op.pageSpec, out);
                                    break;
                                case SAVE_STAGED_COPY:
                                    copyFileToUri(appContext, staged, dest);
                                    return null;
                                default:
                                    return new Exception("Unknown operation");
                            }

                            if (!ok) return new Exception("PDF operation failed");
                            copyFileToUri(appContext, out, dest);
                            return null;
                        } catch (Exception e) {
                            return e;
                        }
                    }
                },
                new Callable<Void>() {
                    @Override
                    public Void call() {
                        host.showInfo(host.getContext().getString(R.string.save_complete));
                        if (op.type == OperationType.SAVE_STAGED_COPY) {
                            BottomSheetDialog dialog = sheetDialog;
                            if (dialog != null) dialog.dismiss();
                            else clearStagedState();
                        }
                        return null;
                    }
                },
                null);
    }

    private void promptExtractPages() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ensureStagedCopyReady(() -> promptForPageSpec(
                R.string.organize_pages_prompt_extract_title,
                R.string.saveas,
                new StringConsumer() {
                    @Override
                    public void accept(@NonNull String value) {
                        pendingOutput = new PendingOperation(OperationType.EXTRACT_PAGES, value, null, null, null);
                        launchSaveCreateIntent(pendingOutput);
                    }
                }));
    }

    private void promptRemovePages() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ensureStagedCopyReady(() -> {
            final MuPdfRepository repo = stagedRepo;
            if (repo == null) return;
            final int pageCount = repo.getPageCount();
            promptForPageSpec(
                    R.string.organize_pages_prompt_remove_title,
                    R.string.menu_accept,
                    new StringConsumer() {
                        @Override
                        public void accept(@NonNull String value) {
                            try {
                                Set<Integer> toRemove = parsePages(value, pageCount);
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
                                String keepSpec = compressPages(keep);
                                applyOperationToStagedCopy(new PendingOperation(OperationType.REMOVE_PAGES, keepSpec, null, null, null));
                            } catch (IllegalArgumentException e) {
                                host.showInfo(host.getContext().getString(R.string.organize_pages_error_invalid_pages));
                            }
                        }
                    });
        });
    }

    private void promptReorderPages() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ensureStagedCopyReady(() -> {
            final MuPdfRepository repo = stagedRepo;
            if (repo == null) return;
            int pageCount = 0;
            try { pageCount = repo.getPageCount(); } catch (Throwable ignore) { pageCount = 0; }
            if (pageCount <= 1) {
                host.showInfo(host.getContext().getString(R.string.organize_pages_error_reorder_requires_multiple_pages));
                return;
            }

            showReorderDialog(repo, pageCount);
        });
    }

    private void showReorderDialog(@NonNull MuPdfRepository repo, int pageCount) {
        final Context ctx = host.getContext();
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_reorder_pages, null, false);
        RecyclerView recycler = root.findViewById(R.id.reorder_pages_recycler);
        if (recycler == null) {
            host.showInfo(ctx.getString(R.string.not_supported));
            return;
        }

        final ReorderPagesAdapter adapter = new ReorderPagesAdapter(ctx, repo, pageCount);
        recycler.setLayoutManager(new LinearLayoutManager(ctx));
        recycler.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(adapter.touchCallback());
        helper.attachToRecyclerView(recycler);
        adapter.setItemTouchHelper(helper);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.organize_pages_prompt_reorder_title)
                .setView(root)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.menu_accept, (d, w) -> {
                    String spec = adapter.buildPageSpec();
                    if (spec == null || spec.trim().isEmpty()) {
                        host.showInfo(ctx.getString(R.string.organize_pages_error_pages_required));
                        return;
                    }
                    applyOperationToStagedCopy(new PendingOperation(OperationType.REORDER_PAGES, spec, null, null, null));
                })
                .create();
        dialog.setOnDismissListener(d -> adapter.release());
        dialog.show();
    }

    private void promptRotatePages() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ensureStagedCopyReady(() -> {
        final Context ctx = host.getContext();

        final LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (ctx.getResources().getDisplayMetrics().density * 16);
        container.setPadding(pad, pad, pad, pad);

        final EditText pagesField = new EditText(ctx);
        pagesField.setHint(R.string.organize_pages_prompt_pages_hint);
        pagesField.setInputType(InputType.TYPE_CLASS_TEXT);
        pagesField.setSingleLine();
        container.addView(pagesField);

        final RadioGroup rg = new RadioGroup(ctx);
        rg.setOrientation(RadioGroup.VERTICAL);

        RadioButton cw = new RadioButton(ctx);
        cw.setId(View.generateViewId());
        cw.setText(R.string.organize_pages_rotate_cw_90);
        rg.addView(cw);

        RadioButton d180 = new RadioButton(ctx);
        d180.setId(View.generateViewId());
        d180.setText(R.string.organize_pages_rotate_180);
        rg.addView(d180);

        RadioButton ccw = new RadioButton(ctx);
        ccw.setId(View.generateViewId());
        ccw.setText(R.string.organize_pages_rotate_ccw_90);
        rg.addView(ccw);

        rg.check(cw.getId());
        container.addView(rg);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.organize_pages_prompt_rotate_title)
                .setMessage(R.string.organize_pages_prompt_pages)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.menu_accept, (d, w) -> {
                    String pages = pagesField.getText() != null ? pagesField.getText().toString().trim() : "";
                    if (pages.isEmpty()) pages = "1-z";

                    int checked = rg.getCheckedRadioButtonId();
                    String rotatePrefix;
                    if (checked == ccw.getId()) {
                        rotatePrefix = "-90:";
                    } else if (checked == d180.getId()) {
                        rotatePrefix = "+180:";
                    } else {
                        rotatePrefix = "+90:";
                    }

                    String expr = rotatePrefix + pages;
                    applyOperationToStagedCopy(new PendingOperation(OperationType.ROTATE_PAGES, null, expr, null, null));
                })
                .show();
        });
    }

    private void promptMergeAppend() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        final Context ctx = host.getContext();
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.organize_pages_prompt_merge_title)
                .setMessage(R.string.organize_pages_prompt_merge_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.okay, (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(DocumentAccessIntents.MIME_PDF);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    host.startActivityForResult(intent, RequestCodes.ORGANIZE_PAGES_PICK_MERGE);
                })
                .show();
    }

    private void promptInsertBlankPage() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ensureStagedCopyReady(() -> {
            final MuPdfRepository repo = stagedRepo;
            if (repo == null) return;
            final int pageCount = repo.getPageCount();
            promptForInsertPosition(
                    R.string.organize_pages_prompt_insert_blank_title,
                    repo,
                    pageCount,
                    new IntConsumer() {
                        @Override public void accept(int insertBeforePage) {
                            applyOperationToStagedCopy(new PendingOperation(OperationType.INSERT_BLANK_PAGE, null, null, null, insertBeforePage));
                        }
                    });
        });
    }

    private void promptInsertFromPdf() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        ensureStagedCopyReady(() -> {
            final MuPdfRepository repo = stagedRepo;
            if (repo == null) return;
            final int pageCount = repo.getPageCount();
            promptForInsertPosition(
                    R.string.organize_pages_prompt_insert_from_pdf_title,
                    repo,
                    pageCount,
                    new IntConsumer() {
                        @Override public void accept(int insertBeforePage) {
                            pendingInsertPick = new PendingOperation(OperationType.INSERT_FROM_PDF, null, null, null, insertBeforePage);
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType(DocumentAccessIntents.MIME_PDF);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                            host.startActivityForResult(intent, RequestCodes.ORGANIZE_PAGES_PICK_INSERT);
                        }
                    });
        });
    }

    private void promptForPageSpec(int titleRes, int positiveRes, @NonNull final StringConsumer onValid) {
        final Context ctx = host.getContext();
        final EditText field = new EditText(ctx);
        field.setHint(R.string.organize_pages_prompt_pages_hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setSingleLine();

        final LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (ctx.getResources().getDisplayMetrics().density * 16);
        container.setPadding(pad, pad, pad, pad);
        container.addView(field);

        new AlertDialog.Builder(ctx)
                .setTitle(titleRes)
                .setMessage(R.string.organize_pages_prompt_pages)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(positiveRes, (d, w) -> {
                    String spec = field.getText() != null ? field.getText().toString().trim() : "";
                    if (spec.isEmpty()) {
                        host.showInfo(ctx.getString(R.string.organize_pages_error_pages_required));
                        return;
                    }
                    onValid.accept(spec);
                })
                .show();
    }

    private void promptForInsertPosition(int titleRes,
                                         @NonNull MuPdfRepository repo,
                                         int pageCount,
                                         @NonNull final IntConsumer onValid) {
        final Context ctx = host.getContext();
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_insert_position, null, false);
        RecyclerView recycler = root.findViewById(R.id.insert_position_recycler);
        TextView help = root.findViewById(R.id.insert_position_help);
        if (help != null) {
            help.setText(ctx.getString(R.string.organize_pages_insert_position_help, pageCount + 1));
        }
        if (recycler == null) {
            host.showInfo(ctx.getString(R.string.not_supported));
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(titleRes)
                .setView(root)
                .setNegativeButton(R.string.cancel, null)
                .create();

        final InsertPositionAdapter adapter = new InsertPositionAdapter(ctx, repo, pageCount, value -> {
            dialog.dismiss();
            onValid.accept(value);
        });
        recycler.setLayoutManager(new LinearLayoutManager(ctx));
        recycler.setAdapter(adapter);

        dialog.setOnDismissListener(d -> adapter.release());
        dialog.show();
    }

    private void launchSaveCreateIntent(@Nullable PendingOperation op) {
        if (op == null) return;
        final Context context = host.getContext();
        String docTitle = host.currentDocumentName();
        if (docTitle == null || docTitle.trim().isEmpty()) docTitle = "document.pdf";
        String suffix = suffixFor(op.type);
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

    private static String suffixFor(OperationType type) {
        switch (type) {
            case EXTRACT_PAGES:
                return "_pages.pdf";
            case REMOVE_PAGES:
                return "_pages_removed.pdf";
            case ROTATE_PAGES:
                return "_rotated.pdf";
            case MERGE_APPEND:
                return "_merged.pdf";
            case REORDER_PAGES:
                return "_reordered.pdf";
            case INSERT_BLANK_PAGE:
                return "_blank_inserted.pdf";
            case INSERT_FROM_PDF:
                return "_pages_inserted.pdf";
            case SAVE_STAGED_COPY:
                return "_organized.pdf";
            default:
                return "_edited.pdf";
        }
    }

    private static String[] buildInsertSelections(@NonNull File src,
                                                  @NonNull File insertFile,
                                                  int insertBeforePage,
                                                  int srcPageCount) {
        List<String> out = new ArrayList<>();
        if (insertBeforePage > 1) {
            out.add(src.getAbsolutePath());
            out.add("1-" + (insertBeforePage - 1));
        }
        out.add(insertFile.getAbsolutePath());
        out.add("1-z");
        if (insertBeforePage <= srcPageCount) {
            out.add(src.getAbsolutePath());
            out.add(insertBeforePage + "-z");
        }
        return out.toArray(new String[0]);
    }

    private static void writeMinimalBlankPdf(@NonNull File dest, int widthPt, int heightPt) throws Exception {
        int w = widthPt > 0 ? widthPt : 612;
        int h = heightPt > 0 ? heightPt : 792;

        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        List<Integer> offsets = new ArrayList<>(5);
        offsets.add(0);

        writeLatin1(baos, "%PDF-1.4\n");
        writeLatin1(baos, "%\u00e2\u00e3\u00cf\u00d3\n");

        offsets.add(baos.size());
        writeLatin1(baos, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        offsets.add(baos.size());
        writeLatin1(baos, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

        offsets.add(baos.size());
        writeLatin1(baos, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + w + " " + h + "] /Contents 4 0 R /Resources << >> >>\nendobj\n");

        offsets.add(baos.size());
        writeLatin1(baos, "4 0 obj\n<< /Length 0 >>\nstream\nendstream\nendobj\n");

        int xrefOffset = baos.size();
        writeLatin1(baos, "xref\n0 5\n");
        writeLatin1(baos, "0000000000 65535 f \n");
        for (int i = 1; i <= 4; i++) {
            String line = String.format(Locale.US, "%010d 00000 n \n", offsets.get(i));
            writeLatin1(baos, line);
        }
        writeLatin1(baos, "trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n");

        try (OutputStream out = new FileOutputStream(dest, false)) {
            out.write(baos.toByteArray());
            out.flush();
        }
    }

    private static void writeLatin1(@NonNull ByteArrayOutputStream out, @NonNull String s) {
        byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes, 0, bytes.length);
    }

    private Uri exportPdfForExternalUse(Context appContext, MuPdfRepository repo, String baseName) throws Exception {
        host.commitPendingInkToCoreBlocking();
        SidecarAnnotationProvider sidecar = host.sidecarAnnotationProviderOrNull();
        if (sidecar != null) {
            try {
                return SidecarPdfEmbedExporter.export(appContext, repo, sidecar, baseName);
            } catch (Throwable embedError) {
                if (org.opendroidpdf.BuildConfig.DEBUG) {
                    android.util.Log.w("OrganizePages", "embed export failed; falling back to flattened", embedError);
                }
            }
            return FlattenedPdfExporter.export(appContext, repo, sidecar, baseName);
        }
        return repo.exportDocument(appContext);
    }

    private void onDoneTapped() {
        if (!hasStagedChanges) {
            BottomSheetDialog dialog = sheetDialog;
            if (dialog != null) dialog.dismiss();
            return;
        }
        ensureStagedCopyReady(() -> {
            pendingOutput = new PendingOperation(OperationType.SAVE_STAGED_COPY, null, null, null, null);
            launchSaveCreateIntent(pendingOutput);
        });
    }

    private void confirmDiscardAndClose(@NonNull BottomSheetDialog dialog) {
        if (!hasStagedChanges) {
            dialog.dismiss();
            return;
        }
        final Context ctx = host.getContext();
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.organize_pages_discard_title)
                .setMessage(R.string.organize_pages_discard_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.menu_discard, (d, w) -> {
                    clearStagedState();
                    dialog.dismiss();
                })
                .show();
    }

    private void clearStagedState() {
        pendingOutput = null;
        pendingInsertPick = null;
        hasStagedChanges = false;
        updateDoneEnabledState();

        OpenDroidPDFCore core = stagedCore;
        stagedCore = null;
        stagedRepo = null;
        File file = stagedPdfFile;
        stagedPdfFile = null;

        if (core != null) staleCores.add(core);
        if (file != null) staleFiles.add(file);

        for (int i = 0; i < staleCores.size(); i++) {
            try { staleCores.get(i).onDestroy(); } catch (Throwable ignore) {}
        }
        for (int i = 0; i < staleFiles.size(); i++) {
            try { staleFiles.get(i).delete(); } catch (Throwable ignore) {}
        }
        staleCores.clear();
        staleFiles.clear();
    }

    private void updateDoneEnabledState() {
        View done = doneAction;
        if (done == null) return;
        boolean enabled = hasStagedChanges;
        done.setEnabled(enabled);
        done.setAlpha(enabled ? 1f : 0.38f);
    }

    private void ensureStagedCopyReady(@NonNull Runnable onReady) {
        if (stagedRepo != null && stagedPdfFile != null && stagedPdfFile.isFile()) {
            onReady.run();
            return;
        }
        final Context ctx = host.getContext();
        final Context appContext = ctx.getApplicationContext();
        final String documentName = host.currentDocumentName();

        final class WorkingCopy {
            File file;
            OpenDroidPDFCore core;
            MuPdfRepository repo;
        }

        final WorkingCopy[] holder = new WorkingCopy[1];
        host.callInBackgroundAndShowDialog(
                ctx.getString(R.string.organize_pages_preparing),
                new Callable<Exception>() {
                    @Override
                    public Exception call() {
                        OpenDroidPDFCore core = null;
                        File src = null;
                        try {
                            if (!BuildConfig.ENABLE_QPDF_OPS) {
                                return new Exception("qpdf ops disabled");
                            }
                            MuPdfRepository repo = host.getRepository();
                            if (repo == null || !repo.isPdfDocument()) {
                                return new Exception("No PDF loaded");
                            }

                            Uri exportedUri = exportPdfForExternalUse(appContext, repo, documentName);
                            src = copyUriToTempFile(appContext, exportedUri, documentName);
                            core = new OpenDroidPDFCore(appContext, Uri.fromFile(src));
                            MuPdfRepository staged = new MuPdfRepository(core);
                            staged.getPageCount(); // validate load

                            WorkingCopy wc = new WorkingCopy();
                            wc.file = src;
                            wc.core = core;
                            wc.repo = staged;
                            holder[0] = wc;
                            return null;
                        } catch (Exception e) {
                            if (core != null) {
                                try { core.onDestroy(); } catch (Throwable ignore) {}
                            }
                            if (src != null) {
                                try { src.delete(); } catch (Throwable ignore) {}
                            }
                            return e;
                        }
                    }
                },
                new Callable<Void>() {
                    @Override
                    public Void call() {
                        WorkingCopy wc = holder[0];
                        if (wc == null || wc.file == null || wc.core == null || wc.repo == null) {
                            host.showInfo(ctx.getString(R.string.not_supported));
                            return null;
                        }
                        stagedPdfFile = wc.file;
                        stagedCore = wc.core;
                        stagedRepo = wc.repo;
                        onReady.run();
                        return null;
                    }
                },
                null);
    }

    private void applyOperationToStagedCopy(@NonNull PendingOperation op) {
        ensureStagedCopyReady(() -> applyOperationToStagedCopyInternal(op));
    }

    private void applyOperationToStagedCopyInternal(@NonNull PendingOperation op) {
        final Context ctx = host.getContext();
        final Context appContext = ctx.getApplicationContext();
        final String documentName = host.currentDocumentName();
        final File srcFile = stagedPdfFile;
        final OpenDroidPDFCore srcCore = stagedCore;
        final MuPdfRepository repo = stagedRepo;
        if (srcFile == null || !srcFile.isFile() || repo == null) {
            host.showInfo(ctx.getString(R.string.not_supported));
            return;
        }

        final class Swap {
            File outFile;
            OpenDroidPDFCore outCore;
            MuPdfRepository outRepo;
        }
        final Swap[] holder = new Swap[1];

        host.callInBackgroundAndShowDialog(
                ctx.getString(R.string.organize_pages_applying),
                new Callable<Exception>() {
                    @Override
                    public Exception call() {
                        try {
                            if (!BuildConfig.ENABLE_QPDF_OPS) {
                                return new Exception("qpdf ops disabled");
                            }
                            File out = newTempPdfFile(appContext, documentName, suffixFor(op.type));
                            boolean ok;
                            switch (op.type) {
                                case REMOVE_PAGES:
                                case REORDER_PAGES:
                                    if (op.pageSpec == null || op.pageSpec.trim().isEmpty()) {
                                        return new Exception("Missing page selection");
                                    }
                                    ok = PdfOps.INSTANCE.extractPages(srcFile, op.pageSpec, out);
                                    break;
                                case ROTATE_PAGES:
                                    if (op.rotateExpr == null || op.rotateExpr.trim().isEmpty()) {
                                        return new Exception("Missing rotate expression");
                                    }
                                    ok = PdfOps.INSTANCE.rotatePages(srcFile, op.rotateExpr, out);
                                    break;
                                case MERGE_APPEND: {
                                    if (op.otherPdfUri == null) return new Exception("Missing merge input");
                                    File other = copyUriToTempFile(appContext, op.otherPdfUri, "merge_input");
                                    try {
                                        ok = PdfOps.INSTANCE.mergePdfs(srcFile, other, out);
                                    } finally {
                                        try { other.delete(); } catch (Throwable ignore) {}
                                    }
                                    break;
                                }
                                case INSERT_BLANK_PAGE: {
                                    Integer insertBefore = op.insertBeforePage;
                                    if (insertBefore == null) return new Exception("Missing insert position");
                                    int pageCount = repo.getPageCount();
                                    if (insertBefore < 1 || insertBefore > pageCount + 1) {
                                        return new Exception("Invalid insert position");
                                    }
                                    PointF size = null;
                                    try { size = repo.getPageSize(0); } catch (Throwable ignore) {}
                                    int widthPt = 612;
                                    int heightPt = 792;
                                    if (size != null && size.x > 0 && size.y > 0) {
                                        widthPt = Math.max(1, Math.round(size.x));
                                        heightPt = Math.max(1, Math.round(size.y));
                                    }
                                    File blank = newTempPdfFile(appContext, "blank_page", "_blank.pdf");
                                    try {
                                        writeMinimalBlankPdf(blank, widthPt, heightPt);
                                        String[] selections = buildInsertSelections(srcFile, blank, insertBefore, pageCount);
                                        ok = PdfOps.INSTANCE.assemblePages(selections, out);
                                    } finally {
                                        try { blank.delete(); } catch (Throwable ignore) {}
                                    }
                                    break;
                                }
                                case INSERT_FROM_PDF: {
                                    Integer insertBefore = op.insertBeforePage;
                                    if (insertBefore == null) return new Exception("Missing insert position");
                                    if (op.otherPdfUri == null) return new Exception("Missing insert input");
                                    int pageCount = repo.getPageCount();
                                    if (insertBefore < 1 || insertBefore > pageCount + 1) {
                                        return new Exception("Invalid insert position");
                                    }
                                    File other = copyUriToTempFile(appContext, op.otherPdfUri, "insert_input");
                                    try {
                                        String[] selections = buildInsertSelections(srcFile, other, insertBefore, pageCount);
                                        ok = PdfOps.INSTANCE.assemblePages(selections, out);
                                    } finally {
                                        try { other.delete(); } catch (Throwable ignore) {}
                                    }
                                    break;
                                }
                                default:
                                    return new Exception("Unsupported operation");
                            }

                            if (!ok) return new Exception("PDF operation failed");

                            OpenDroidPDFCore newCore = new OpenDroidPDFCore(appContext, Uri.fromFile(out));
                            MuPdfRepository newRepo = new MuPdfRepository(newCore);
                            newRepo.getPageCount(); // validate load

                            Swap swap = new Swap();
                            swap.outFile = out;
                            swap.outCore = newCore;
                            swap.outRepo = newRepo;
                            holder[0] = swap;
                            return null;
                        } catch (Exception e) {
                            return e;
                        }
                    }
                },
                new Callable<Void>() {
                    @Override
                    public Void call() {
                        Swap swap = holder[0];
                        if (swap == null || swap.outFile == null || swap.outCore == null || swap.outRepo == null) {
                            host.showInfo(ctx.getString(R.string.not_supported));
                            return null;
                        }

                        if (stagedPdfFile != srcFile) {
                            try { swap.outCore.onDestroy(); } catch (Throwable ignore) {}
                            try { swap.outFile.delete(); } catch (Throwable ignore) {}
                            return null;
                        }

                        stagedPdfFile = swap.outFile;
                        stagedCore = swap.outCore;
                        stagedRepo = swap.outRepo;
                        hasStagedChanges = true;
                        updateDoneEnabledState();

                        if (srcCore != null) staleCores.add(srcCore);
                        staleFiles.add(srcFile);

                        host.showInfo(ctx.getString(R.string.organize_pages_changes_staged));
                        return null;
                    }
                },
                null);
    }

    private static File newTempPdfFile(Context appContext, String baseName, String suffix) {
        File dir = new File(appContext.getCacheDir(), "tmpfiles");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        String safe = (baseName == null || baseName.trim().isEmpty()) ? "document" : baseName.trim();
        safe = safe.replace('/', '_').replace('\\', '_');
        safe = safe.replaceAll("[^A-Za-z0-9._ -]", "_");
        if (safe.toLowerCase().endsWith(".pdf")) {
            safe = safe.substring(0, safe.length() - 4);
        }
        if (safe.length() > 48) safe = safe.substring(0, 48);
        File f = new File(dir, safe + suffix);
        try { f.delete(); } catch (Throwable ignore) {}
        return f;
    }

    private static File copyUriToTempFile(Context appContext, Uri src, String baseName) throws Exception {
        if (src == null) throw new IllegalArgumentException("src Uri null");
        File dest = newTempPdfFile(appContext, baseName, "_input.pdf");
        try (InputStream in = appContext.getContentResolver().openInputStream(src);
             OutputStream out = new FileOutputStream(dest, false)) {
            if (in == null) throw new IllegalStateException("InputStream null for " + src);
            byte[] buf = new byte[16 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            out.flush();
        }
        return dest;
    }

    private static void copyFileToUri(Context ctx, File src, Uri dest) throws Exception {
        if (src == null || !src.isFile()) throw new IllegalArgumentException("src file missing");
        try (InputStream in = new FileInputStream(src);
             OutputStream out = ctx.getContentResolver().openOutputStream(dest, "rwt")) {
            if (out == null) throw new IllegalStateException("OutputStream null for " + dest);
            byte[] buf = new byte[16 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            out.flush();
        }
    }

    private static Set<Integer> parsePages(@NonNull String spec, int pageCount) throws IllegalArgumentException {
        if (pageCount <= 0) throw new IllegalArgumentException("pageCount");
        String raw = spec.trim();
        if (raw.isEmpty()) return Collections.emptySet();
        String[] parts = raw.split(",");
        Set<Integer> out = new HashSet<>();
        for (String part : parts) {
            String token = part != null ? part.trim() : "";
            if (token.isEmpty()) continue;
            int dash = token.indexOf('-');
            if (dash >= 0) {
                String a = token.substring(0, dash).trim();
                String b = token.substring(dash + 1).trim();
                int start = parsePageToken(a, pageCount);
                int end = parsePageToken(b, pageCount);
                if (start <= 0 || end <= 0 || start > end) throw new IllegalArgumentException("range");
                if (end > pageCount) throw new IllegalArgumentException("range");
                for (int i = start; i <= end; i++) out.add(i);
            } else {
                int page = parsePageToken(token, pageCount);
                if (page <= 0 || page > pageCount) throw new IllegalArgumentException("page");
                out.add(page);
            }
        }
        return out;
    }

    private static int parsePageToken(@NonNull String token, int pageCount) {
        String t = token.trim();
        if (t.isEmpty()) return -1;
        if ("z".equalsIgnoreCase(t)) return pageCount;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String compressPages(@NonNull List<Integer> pagesSortedAscending) {
        if (pagesSortedAscending.isEmpty()) return "";
        List<Integer> pages = new ArrayList<>(pagesSortedAscending);
        Collections.sort(pages);
        StringBuilder sb = new StringBuilder();
        int rangeStart = pages.get(0);
        int prev = rangeStart;
        for (int i = 1; i < pages.size(); i++) {
            int n = pages.get(i);
            if (n == prev + 1) {
                prev = n;
                continue;
            }
            appendRange(sb, rangeStart, prev);
            sb.append(",");
            rangeStart = prev = n;
        }
        appendRange(sb, rangeStart, prev);
        return sb.toString();
    }

    private static void appendRange(@NonNull StringBuilder sb, int start, int end) {
        if (start == end) {
            sb.append(start);
        } else {
            sb.append(start).append("-").append(end);
        }
    }

    private static final class ReorderPagesAdapter extends RecyclerView.Adapter<ReorderPagesAdapter.Holder> {
        private static final int THUMBNAIL_WIDTH_DP = 56;
        private static final int THUMBNAIL_CACHE_SIZE = 32;

        private final MuPdfRepository repo;
        private final List<Integer> pages;
        private @Nullable ItemTouchHelper helper;
        private final int thumbnailWidthPx;
        private final android.util.LruCache<Integer, Bitmap> thumbnailCache;
        private final ExecutorService thumbExecutor;
        private final Set<Integer> inFlight = Collections.synchronizedSet(new HashSet<>());
        private volatile boolean released;

        ReorderPagesAdapter(@NonNull Context ctx, @NonNull MuPdfRepository repo, int pageCount) {
            this.repo = repo;
            List<Integer> out = new ArrayList<>(Math.max(0, pageCount));
            for (int i = 1; i <= pageCount; i++) out.add(i);
            this.pages = out;

            float density = 1f;
            try { density = ctx.getResources().getDisplayMetrics().density; } catch (Throwable ignore) { density = 1f; }
            thumbnailWidthPx = Math.max(1, Math.round(density * THUMBNAIL_WIDTH_DP));
            thumbnailCache = new android.util.LruCache<Integer, Bitmap>(THUMBNAIL_CACHE_SIZE) {
                @Override protected int sizeOf(@NonNull Integer key, @NonNull Bitmap value) {
                    return 1;
                }
            };
            thumbExecutor = Executors.newSingleThreadExecutor();
        }

        void setItemTouchHelper(@NonNull ItemTouchHelper helper) {
            this.helper = helper;
        }

        @NonNull ItemTouchHelper.Callback touchCallback() {
            return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    int from = viewHolder.getBindingAdapterPosition();
                    int to = target.getBindingAdapterPosition();
                    if (from < 0 || to < 0 || from == to) return false;
                    Collections.swap(pages, from, to);
                    notifyItemMoved(from, to);
                    return true;
                }

                @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                }

                @Override public boolean isLongPressDragEnabled() { return false; }
                @Override public boolean isItemViewSwipeEnabled() { return false; }
            };
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reorder_page, parent, false);
            return new Holder(v);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            Integer page = pages.get(position);
            holder.boundPage = page != null ? page : -1;
            if (holder.label != null) {
                holder.label.setText(holder.itemView.getContext().getString(R.string.organize_pages_page_label, page));
            }
            bindThumbnail(holder, page);
            if (holder.handle != null) {
                holder.handle.setOnTouchListener((v, event) -> {
                    if (event != null && event.getAction() == MotionEvent.ACTION_DOWN) {
                        ItemTouchHelper h = helper;
                        if (h != null) {
                            h.startDrag(holder);
                            return true;
                        }
                    }
                    return false;
                });
            }
        }

        @Override public int getItemCount() {
            return pages.size();
        }

        void release() {
            released = true;
            try { thumbExecutor.shutdownNow(); } catch (Throwable ignore) {}
            try { thumbnailCache.evictAll(); } catch (Throwable ignore) {}
            try { inFlight.clear(); } catch (Throwable ignore) {}
        }

        private void bindThumbnail(@NonNull Holder holder, @Nullable Integer page1Based) {
            if (released) return;
            if (holder.thumbnail == null || page1Based == null) return;
            Bitmap cached = thumbnailCache.get(page1Based);
            if (cached != null) {
                holder.thumbnail.setImageBitmap(cached);
                return;
            }
            holder.thumbnail.setImageDrawable(null);
            if (!inFlight.add(page1Based)) return;
            thumbExecutor.execute(() -> {
                Bitmap bm = null;
                try {
                    bm = renderThumbnail(page1Based);
                } catch (Throwable ignore) {
                    bm = null;
                } finally {
                    try { inFlight.remove(page1Based); } catch (Throwable ignore) {}
                }
                if (released || bm == null) return;
                thumbnailCache.put(page1Based, bm);
                try {
                    holder.itemView.post(() -> {
                        if (released) return;
                        if (holder.boundPage != page1Based) return;
                        Bitmap latest = thumbnailCache.get(page1Based);
                        if (latest != null && holder.thumbnail != null) {
                            holder.thumbnail.setImageBitmap(latest);
                        }
                    });
                } catch (Throwable ignore) {
                }
            });
        }

        @Nullable
        private Bitmap renderThumbnail(int page1Based) {
            int pageIndex = page1Based - 1;
            if (pageIndex < 0) return null;
            PointF size = null;
            try { size = repo.getPageSize(pageIndex); } catch (Throwable ignore) { size = null; }
            float ratio = 1.294f;
            if (size != null && size.x > 0 && size.y > 0) {
                ratio = size.y / size.x;
            }
            int w = thumbnailWidthPx;
            int h = Math.max(1, Math.round(w * ratio));
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            MuPDFCore.Cookie cookie = repo.newRenderCookie();
            try {
                repo.drawPage(bm, pageIndex, w, h, 0, 0, w, h, cookie);
            } finally {
                try { cookie.destroy(); } catch (Throwable ignore) {}
            }
            return bm;
        }

        @NonNull String buildPageSpec() {
            if (pages.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pages.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(pages.get(i));
            }
            return sb.toString();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final @Nullable TextView label;
            final @Nullable ImageView thumbnail;
            final @Nullable View handle;
            int boundPage = -1;

            Holder(@NonNull View itemView) {
                super(itemView);
                label = itemView.findViewById(R.id.reorder_page_label);
                thumbnail = itemView.findViewById(R.id.reorder_page_thumbnail);
                handle = itemView.findViewById(R.id.reorder_page_handle);
            }
        }
    }

    private static final class InsertPositionAdapter extends RecyclerView.Adapter<InsertPositionAdapter.Holder> {
        private static final int THUMBNAIL_WIDTH_DP = 56;
        private static final int THUMBNAIL_CACHE_SIZE = 32;

        private final Context ctx;
        private final MuPdfRepository repo;
        private final int pageCount;
        private final int thumbnailWidthPx;
        private final android.util.LruCache<Integer, Bitmap> thumbnailCache;
        private final ExecutorService thumbExecutor;
        private final Set<Integer> inFlight = Collections.synchronizedSet(new HashSet<>());
        private final IntConsumer onSelected;
        private volatile boolean released;

        InsertPositionAdapter(@NonNull Context ctx,
                              @NonNull MuPdfRepository repo,
                              int pageCount,
                              @NonNull IntConsumer onSelected) {
            this.ctx = ctx;
            this.repo = repo;
            this.pageCount = Math.max(0, pageCount);
            this.onSelected = onSelected;

            float density = 1f;
            try { density = ctx.getResources().getDisplayMetrics().density; } catch (Throwable ignore) { density = 1f; }
            thumbnailWidthPx = Math.max(1, Math.round(density * THUMBNAIL_WIDTH_DP));
            thumbnailCache = new android.util.LruCache<Integer, Bitmap>(THUMBNAIL_CACHE_SIZE) {
                @Override protected int sizeOf(@NonNull Integer key, @NonNull Bitmap value) {
                    return 1;
                }
            };
            thumbExecutor = Executors.newSingleThreadExecutor();
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_insert_position, parent, false);
            return new Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            int insertBeforePage = position + 1;
            holder.insertBeforePage = insertBeforePage;

            int page1Based;
            String label;
            if (insertBeforePage <= pageCount) {
                page1Based = insertBeforePage;
                label = ctx.getString(R.string.organize_pages_insert_before_page_label, insertBeforePage);
            } else {
                page1Based = Math.max(1, pageCount);
                label = ctx.getString(R.string.organize_pages_insert_at_end_label, pageCount);
            }

            if (holder.label != null) holder.label.setText(label);
            holder.boundThumbnailPage = page1Based;
            bindThumbnail(holder, page1Based);

            holder.itemView.setOnClickListener(v -> {
                if (released) return;
                onSelected.accept(holder.insertBeforePage);
            });
        }

        @Override public int getItemCount() {
            return pageCount + 1;
        }

        void release() {
            released = true;
            try { thumbExecutor.shutdownNow(); } catch (Throwable ignore) {}
            try { thumbnailCache.evictAll(); } catch (Throwable ignore) {}
            try { inFlight.clear(); } catch (Throwable ignore) {}
        }

        private void bindThumbnail(@NonNull Holder holder, int page1Based) {
            if (released) return;
            if (holder.thumbnail == null) return;
            Bitmap cached = thumbnailCache.get(page1Based);
            if (cached != null) {
                holder.thumbnail.setImageBitmap(cached);
                return;
            }
            holder.thumbnail.setImageDrawable(null);
            if (!inFlight.add(page1Based)) return;
            thumbExecutor.execute(() -> {
                Bitmap bm = null;
                try {
                    bm = renderThumbnail(page1Based);
                } catch (Throwable ignore) {
                    bm = null;
                } finally {
                    try { inFlight.remove(page1Based); } catch (Throwable ignore) {}
                }
                if (released || bm == null) return;
                thumbnailCache.put(page1Based, bm);
                try {
                    holder.itemView.post(() -> {
                        if (released) return;
                        if (holder.boundThumbnailPage != page1Based) return;
                        Bitmap latest = thumbnailCache.get(page1Based);
                        if (latest != null && holder.thumbnail != null) {
                            holder.thumbnail.setImageBitmap(latest);
                        }
                    });
                } catch (Throwable ignore) {
                }
            });
        }

        @Nullable
        private Bitmap renderThumbnail(int page1Based) {
            int pageIndex = page1Based - 1;
            if (pageIndex < 0) return null;
            PointF size = null;
            try { size = repo.getPageSize(pageIndex); } catch (Throwable ignore) { size = null; }
            float ratio = 1.294f;
            if (size != null && size.x > 0 && size.y > 0) {
                ratio = size.y / size.x;
            }
            int w = thumbnailWidthPx;
            int h = Math.max(1, Math.round(w * ratio));
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            MuPDFCore.Cookie cookie = repo.newRenderCookie();
            try {
                repo.drawPage(bm, pageIndex, w, h, 0, 0, w, h, cookie);
            } finally {
                try { cookie.destroy(); } catch (Throwable ignore) {}
            }
            return bm;
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final @Nullable TextView label;
            final @Nullable ImageView thumbnail;
            int insertBeforePage;
            int boundThumbnailPage;

            Holder(@NonNull View itemView) {
                super(itemView);
                label = itemView.findViewById(R.id.insert_position_label);
                thumbnail = itemView.findViewById(R.id.insert_position_thumbnail);
            }
        }
    }
}
