package org.opendroidpdf.app.document;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.app.Activity;
import android.net.Uri;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.PdfPrintAdapter;
import org.opendroidpdf.R;
import org.opendroidpdf.app.document.DocumentAccessIntents;
import org.opendroidpdf.app.helpers.RequestCodes;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.SidecarBundleJson;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.core.PdfBoxFacade;
import org.opendroidpdf.core.PdfOps;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Callable;

/**
 * Encapsulates export/print flows so the activity can stay lean.
 */
public class ExportController {

    public interface Host {
        MuPdfRepository getRepository();
        void commitPendingInkToCoreBlocking();
        void showInfo(String message);
        String currentDocumentName();
        void markIgnoreSaveOnStop();
        Context getContext();
        android.content.ContentResolver getContentResolver();
        void startActivityForResult(Intent intent, int requestCode);
        void invalidateDocumentView();
        void callInBackgroundAndShowDialog(String message, Callable<Exception> background, Callable<Void> success, Callable<Void> failure);
        void promptSaveAs();
        @Nullable SidecarAnnotationProvider sidecarAnnotationProviderOrNull();
    }

    private final Host host;
    private @Nullable Uri lastExportedUri;
    private @Nullable String pendingUserPw;
    private @Nullable String pendingOwnerPw;
    private boolean pendingEncryptSave;
    @VisibleForTesting
    @Nullable Callable<Uri> exportUriOverrideForTest;

    public ExportController(Host host) {
        this.host = host;
    }

    public @Nullable Uri lastExportedUriOrNull() {
        return lastExportedUri;
    }

    public void printDoc() {
        final MuPdfRepository repo = host.getRepository();
        if (repo == null) {
            host.showInfo(host.getContext().getString(R.string.error_saveing));
            return;
        }

        final PrintManager printManager = (PrintManager) host.getContext().getSystemService(Context.PRINT_SERVICE);
        final String documentName = host.currentDocumentName();
        final Context appContext = host.getContext().getApplicationContext();

        host.callInBackgroundAndShowDialog(
            host.getContext().getString(R.string.preparing_to_print),
            new Callable<Exception>() {
                Uri exported;
                @Override
                public Exception call() {
                    try {
                        host.commitPendingInkToCoreBlocking();
                        exported = exportPdfForExternalUse(appContext, repo, documentName);
                    } catch (Exception e) {
                        return e;
                    }
                    lastExportedUri = exported;
                    return null;
                }
            },
            new Callable<Void>() {
                @Override
                public Void call() {
                    Uri exported = lastExportedUri;
                    if (exported == null) {
                        host.showInfo(host.getContext().getString(R.string.error_saveing));
                        return null;
                    }
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        android.util.Log.i("OpenDroidPDF", "DEBUG_PRINT_LAUNCHED uri=" + exported);
                    }
                    host.markIgnoreSaveOnStop();
                    PrintAttributes attrs = new PrintAttributes.Builder().build();
                    printManager.print(documentName, new PdfPrintAdapter(host.getContext(), exported), attrs);
                    return null;
                }
            },
            null);
    }

    public void shareDoc() {
        final MuPdfRepository repo = host.getRepository();
        if (repo == null) {
            host.showInfo(host.getContext().getString(R.string.error_exporting));
            return;
        }
        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        final Context appContext = host.getContext().getApplicationContext();
        final String documentName = host.currentDocumentName();

        host.callInBackgroundAndShowDialog(
            host.getContext().getString(R.string.preparing_to_share),
            new Callable<Exception>() {
                @Override
                public Exception call() {
                    Uri exportedUri = null;
                    try
                    {
                        host.commitPendingInkToCoreBlocking();
                        exportedUri = exportPdfForExternalUse(appContext, repo, documentName);
                    }
                    catch(Exception e)
                    {
                        return e;
                    }
                    lastExportedUri = exportedUri;
                    shareIntent.setType("application/pdf");
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.setClipData(ClipData.newUri(host.getContentResolver(), documentName, exportedUri));
                    shareIntent.putExtra(Intent.EXTRA_STREAM, exportedUri);

                    PackageManager pm = host.getContext().getPackageManager();
                    for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)) {
                        if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                            try {
                                host.getContext().grantUriPermission(ri.activityInfo.packageName, exportedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (Exception ignore) {}
                        }
                    }
                    return null;
                }
            },
            new Callable<Void>() {
                @Override
                public Void call() {
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        android.util.Log.i("OpenDroidPDF", "DEBUG_SHARE_LAUNCHED uri=" + lastExportedUri);
                    }
                    host.markIgnoreSaveOnStop();
                    Intent chooser = Intent.createChooser(shareIntent, host.getContext().getString(R.string.share_with));
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    host.getContext().startActivity(chooser);
                    return null;
                }
            },
            null);
    }

    /**
     * Shares a linearized copy of the current PDF (for faster streaming).
     * Falls back to normal share if qpdf ops are disabled or the doc is not PDF.
     */
    public void shareDocLinearized() {
        final MuPdfRepository repo = host.getRepository();
        if (repo == null) {
            host.showInfo(host.getContext().getString(R.string.error_exporting));
            return;
        }
        if (!BuildConfig.ENABLE_QPDF_OPS || !repo.isPdfDocument()) {
            shareDoc();
            return;
        }
        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        final Context appContext = host.getContext().getApplicationContext();
        final String documentName = host.currentDocumentName();

        host.callInBackgroundAndShowDialog(
            host.getContext().getString(R.string.preparing_to_share_linearized),
            new Callable<Exception>() {
                @Override
                public Exception call() {
                    try {
                        host.commitPendingInkToCoreBlocking();
                        Uri exportedUri = exportPdfForExternalUse(appContext, repo, documentName);
                        File src = copyUriToTempFile(appContext, exportedUri, documentName);
                        File linearized = newTempPdfFile(appContext, documentName, "_linearized.pdf");
                        boolean ok = PdfOps.INSTANCE.linearizePdf(src, linearized);
                        if (!ok) return new Exception("qpdf linearize failed");
                        Uri outUri = FileProvider.getUriForFile(appContext, "org.opendroidpdf.fileprovider", linearized);
                        lastExportedUri = outUri;
                        shareIntent.setType("application/pdf");
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        shareIntent.setClipData(ClipData.newUri(host.getContentResolver(), documentName, outUri));
                        shareIntent.putExtra(Intent.EXTRA_STREAM, outUri);
                        grantUriToShareTargets(outUri, shareIntent);
                        return null;
                    } catch (Exception e) {
                        return e;
                    }
                }
            },
            new Callable<Void>() {
                @Override
                public Void call() {
                    host.markIgnoreSaveOnStop();
                    Intent chooser = Intent.createChooser(shareIntent, host.getContext().getString(R.string.share_with));
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    host.getContext().startActivity(chooser);
                    return null;
                }
            },
            null);
    }

    /**
     * Shares a flattened PDF copy (rasterized pages) for maximum viewer compatibility.
     *
     * <p>This is useful when recipients/viewers do not render form appearances or annotations
     * reliably. The output sacrifices selectable text.</p>
     */
    public void shareDocFlattened() {
        final MuPdfRepository repo = host.getRepository();
        if (repo == null) {
            host.showInfo(host.getContext().getString(R.string.error_exporting));
            return;
        }
        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        final Context appContext = host.getContext().getApplicationContext();
        final String documentName = host.currentDocumentName();

        // Prefer pdfbox form-flatten when available to preserve text; otherwise fallback to
        // raster flatten for maximum viewer compatibility.
        if (BuildConfig.ENABLE_PDFBOX_OPS && PdfBoxFacade.isAvailable() && repo.isPdfDocument()) {
            host.callInBackgroundAndShowDialog(
                    host.getContext().getString(R.string.preparing_to_share_flattened_pdfbox),
                    new Callable<Exception>() {
                        @Override
                        public Exception call() {
                            try {
                                host.commitPendingInkToCoreBlocking();
                                Uri exportedUri = exportPdfForExternalUse(appContext, repo, documentName);
                                File flattened = newTempPdfFile(appContext, documentName, "_flattened.pdf");
                                Uri outUri = androidx.core.content.FileProvider.getUriForFile(appContext, "org.opendroidpdf.fileprovider", flattened);
                                // pdfboxops writes directly to the output Uri
                                PdfBoxFacade.FlattenResult res = PdfBoxFacade.flattenForm(appContext, exportedUri, outUri);
                                lastExportedUri = outUri;
                                shareIntent.setType("application/pdf");
                                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                shareIntent.setClipData(ClipData.newUri(host.getContentResolver(), documentName, outUri));
                                shareIntent.putExtra(Intent.EXTRA_STREAM, outUri);
                                grantUriToShareTargets(outUri, shareIntent);
                                android.util.Log.i("ExportController", "pdfbox flatten share pages=" + res.pageCount + " hadForm=" + res.hadAcroForm);
                                return null;
                            } catch (Exception e) {
                                return e;
                            }
                        }
                    },
                    new Callable<Void>() {
                        @Override
                        public Void call() {
                            host.markIgnoreSaveOnStop();
                            Intent chooser = Intent.createChooser(shareIntent, host.getContext().getString(R.string.share_with));
                            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            host.getContext().startActivity(chooser);
                            return null;
                        }
                    },
                null);
            return;
        }
        // Legacy raster-flatten flow remains for compatibility.
        host.callInBackgroundAndShowDialog(
                host.getContext().getString(R.string.preparing_to_share),
                new Callable<Exception>() {
                    @Override
                    public Exception call() {
                        Uri exportedUri;
                        try {
                            host.commitPendingInkToCoreBlocking();
                            exportedUri = FlattenedPdfExporter.export(
                                    appContext,
                                    repo,
                                    host.sidecarAnnotationProviderOrNull(),
                                    documentName);
                        } catch (Exception e) {
                            return e;
                        }
                        lastExportedUri = exportedUri;

                        shareIntent.setType("application/pdf");
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        shareIntent.setClipData(ClipData.newUri(host.getContentResolver(), documentName, exportedUri));
                        shareIntent.putExtra(Intent.EXTRA_STREAM, exportedUri);

                        PackageManager pm = host.getContext().getPackageManager();
                        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)) {
                            if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                                try {
                                    host.getContext().grantUriPermission(ri.activityInfo.packageName, exportedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                } catch (Exception ignore) {}
                            }
                        }
                        return null;
                    }
                },
                new Callable<Void>() {
                    @Override
                    public Void call() {
                        host.markIgnoreSaveOnStop();
                        Intent chooser = Intent.createChooser(shareIntent, host.getContext().getString(R.string.share_with));
                        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        host.getContext().startActivity(chooser);
                        return null;
                    }
                },
            null);
    }

    /** Prompt for passwords and share an encrypted copy via qpdf. */
    public void shareDocEncryptedPrompt() {
        final MuPdfRepository repo = host.getRepository();
        if (repo == null || !repo.isPdfDocument()) {
            host.showInfo(host.getContext().getString(R.string.error_exporting));
            return;
        }
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.error_exporting));
            return;
        }
        final Context ctx = host.getContext();
        promptForPasswords(ctx, R.string.menu_share_encrypted, (userPw, ownerPw) -> shareDocEncrypted(userPw, ownerPw));
    }

    private void shareDocEncrypted(final String userPw, final String ownerPw) {
        final MuPdfRepository repo = host.getRepository();
        if (repo == null) {
            host.showInfo(host.getContext().getString(R.string.error_exporting));
            return;
        }
        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        final Context appContext = host.getContext().getApplicationContext();
        final String documentName = host.currentDocumentName();

        host.callInBackgroundAndShowDialog(
            host.getContext().getString(R.string.preparing_to_share_encrypted),
            new Callable<Exception>() {
                @Override
                public Exception call() {
                    try {
                        host.commitPendingInkToCoreBlocking();
                        Uri exportedUri = exportPdfForExternalUse(appContext, repo, documentName);
                        File src = copyUriToTempFile(appContext, exportedUri, documentName);
                        File encrypted = newTempPdfFile(appContext, documentName, "_encrypted.pdf");
                        boolean ok = PdfOps.INSTANCE.encryptPdf(src, userPw, ownerPw, encrypted, "256");
                        if (!ok) return new Exception("qpdf encrypt failed");
                        Uri outUri = FileProvider.getUriForFile(appContext, "org.opendroidpdf.fileprovider", encrypted);
                        lastExportedUri = outUri;
                        shareIntent.setType("application/pdf");
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        shareIntent.setClipData(ClipData.newUri(host.getContentResolver(), documentName, outUri));
                        shareIntent.putExtra(Intent.EXTRA_STREAM, outUri);
                        grantUriToShareTargets(outUri, shareIntent);
                        return null;
                    } catch (Exception e) {
                        return e;
                    }
                }
            },
            new Callable<Void>() {
                @Override
                public Void call() {
                    host.markIgnoreSaveOnStop();
                    Intent chooser = Intent.createChooser(shareIntent, host.getContext().getString(R.string.share_with));
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    host.getContext().startActivity(chooser);
                    return null;
                }
            },
            null);
    }

    private void grantUriToShareTargets(@NonNull Uri uri, @NonNull Intent shareIntent) {
        PackageManager pm = host.getContext().getPackageManager();
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                try {
                    host.getContext().grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignore) {}
            }
        }
    }

    private boolean handleShareFlattened() {
        final MuPdfRepository repo = host.getRepository();
        if (repo == null || !repo.isPdfDocument()) {
            host.showInfo(host.getContext().getString(R.string.error_exporting));
            return true;
        }
        final Context appContext = host.getContext().getApplicationContext();
        if (!BuildConfig.ENABLE_PDFBOX_OPS || !PdfBoxFacade.isAvailable()) {
            Toast.makeText(appContext, org.opendroidpdf.R.string.export_not_available, Toast.LENGTH_SHORT).show();
            return true;
        }
        try {
            Uri exported = repo.exportDocument(appContext);
            if (exported == null) {
                Toast.makeText(appContext, org.opendroidpdf.R.string.export_failed, Toast.LENGTH_SHORT).show();
                return true;
            }
            File outFile = new File(appContext.getCacheDir(), "export_flattened.pdf");
            if (outFile.exists()) outFile.delete();
            Uri outUri = androidx.core.content.FileProvider.getUriForFile(appContext, "org.opendroidpdf.fileprovider", outFile);
            PdfBoxFacade.FlattenResult res = PdfBoxFacade.flattenForm(appContext, exported, outUri);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, outUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            appContext.startActivity(Intent.createChooser(intent,
                    appContext.getString(org.opendroidpdf.R.string.menu_share_flattened)));
            Toast.makeText(appContext,
                    appContext.getString(org.opendroidpdf.R.string.export_flatten_summary, res.pageCount, res.hadAcroForm),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("ExportController", "flatten/share failed", e);
            Toast.makeText(appContext, appContext.getString(org.opendroidpdf.R.string.export_failed_with_reason, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private File copyUriToTempFile(Context appContext, Uri src, String baseName) throws Exception {
        File dest = newTempPdfFile(appContext, baseName, ".pdf");
        try (InputStream in = appContext.getContentResolver().openInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IllegalStateException("InputStream null for " + src);
            byte[] buf = new byte[16 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
        }
        return dest;
    }

    private File newTempPdfFile(Context appContext, String baseName, String suffix) {
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

    private void launchSaveCreateIntent(int requestCode, String suffix) {
        Context context = host.getContext();
        String docTitle = host.currentDocumentName();
        if (docTitle == null || docTitle.trim().isEmpty()) docTitle = "document";
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
        host.startActivityForResult(intent, requestCode);
    }

    public void onActivityResultSaveLinearized(int resultCode, Intent intent) {
        if (resultCode != Activity.RESULT_OK || intent == null || intent.getData() == null) return;
        final Uri dest = intent.getData();
        final Context appContext = host.getContext().getApplicationContext();
        final String documentName = host.currentDocumentName();
        host.callInBackgroundAndShowDialog(
                host.getContext().getString(R.string.preparing_to_save_linearized),
                new Callable<Exception>() {
                    @Override
                    public Exception call() {
                        try {
                            Uri exportedUri = resolveExportUri(appContext, documentName);
                            File src = copyUriToTempFile(appContext, exportedUri, documentName);
                            File linearized = newTempPdfFile(appContext, documentName, "_linearized.pdf");
                            boolean ok = PdfOps.INSTANCE.linearizePdf(src, linearized);
                            if (!ok) return new Exception("qpdf linearize failed");
                            copyFileToUri(appContext, linearized, dest);
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
                        return null;
                    }
                },
                null);
    }

    public void onActivityResultSaveEncrypted(int resultCode, Intent intent) {
        if (resultCode != Activity.RESULT_OK || intent == null || intent.getData() == null) {
            pendingEncryptSave = false;
            pendingUserPw = null;
            pendingOwnerPw = null;
            return;
        }
        final Uri dest = intent.getData();
        final String userPw = pendingUserPw;
        final String ownerPw = pendingOwnerPw;
        pendingEncryptSave = false;
        pendingUserPw = null;
        pendingOwnerPw = null;
        if (userPw == null || ownerPw == null || userPw.isEmpty() || ownerPw.isEmpty()) return;
        final Context appContext = host.getContext().getApplicationContext();
        final String documentName = host.currentDocumentName();
        host.callInBackgroundAndShowDialog(
                host.getContext().getString(R.string.preparing_to_save_encrypted),
                new Callable<Exception>() {
                    @Override
                    public Exception call() {
                        try {
                            Uri exportedUri = resolveExportUri(appContext, documentName);
                            File src = copyUriToTempFile(appContext, exportedUri, documentName);
                            File encrypted = newTempPdfFile(appContext, documentName, "_encrypted.pdf");
                            boolean ok = PdfOps.INSTANCE.encryptPdf(src, userPw, ownerPw, encrypted, "256");
                            if (!ok) return new Exception("qpdf encrypt failed");
                            copyFileToUri(appContext, encrypted, dest);
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
                        return null;
                    }
                },
                null);
    }

    private void copyFileToUri(Context ctx, File src, Uri dest) throws Exception {
        try (InputStream in = new java.io.FileInputStream(src);
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

    @VisibleForTesting
    public void setExportUriOverrideForTest(@Nullable Callable<Uri> provider) {
        this.exportUriOverrideForTest = provider;
    }

    @VisibleForTesting
    public void setPendingEncryptionForTest(@NonNull String userPw, @NonNull String ownerPw) {
        this.pendingUserPw = userPw;
        this.pendingOwnerPw = ownerPw;
        this.pendingEncryptSave = true;
    }

    private Uri resolveExportUri(Context appContext, String documentName) throws Exception {
        if (exportUriOverrideForTest != null) {
            return exportUriOverrideForTest.call();
        }
        MuPdfRepository repo = host.getRepository();
        if (repo == null) throw new IllegalStateException("No repository available");
        host.commitPendingInkToCoreBlocking();
        return exportPdfForExternalUse(appContext, repo, documentName);
    }

    /**
     * Saves the current document (export to a new Uri) using the existing save-as prompt.
     */
    public void saveDoc() {
        host.promptSaveAs();
    }

    /** Save a linearized PDF copy to user-selected location. */
    public void saveDocLinearized() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        launchSaveCreateIntent(RequestCodes.SAVE_LINEARIZED, "_linearized.pdf");
    }

    /** Prompt then save encrypted PDF copy to user-selected location. */
    public void saveDocEncryptedPrompt() {
        if (!BuildConfig.ENABLE_QPDF_OPS) {
            host.showInfo(host.getContext().getString(R.string.not_supported));
            return;
        }
        final Context ctx = host.getContext();
        promptForPasswords(ctx, R.string.menu_save_encrypted, (userPw, ownerPw) -> {
            pendingUserPw = userPw;
            pendingOwnerPw = ownerPw;
            pendingEncryptSave = true;
            launchSaveCreateIntent(RequestCodes.SAVE_ENCRYPTED, "_encrypted.pdf");
        });
    }

    private interface PasswordConsumer {
        void onPasswords(String user, String owner);
    }

    private void promptForPasswords(Context ctx, int confirmLabelRes, PasswordConsumer consumer) {
        final EditText userField = new EditText(ctx);
        userField.setHint(R.string.encrypt_user_password);
        final EditText ownerField = new EditText(ctx);
        ownerField.setHint(R.string.encrypt_owner_password);
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (ctx.getResources().getDisplayMetrics().density * 16);
        ll.setPadding(pad, pad, pad, pad);
        ll.addView(userField);
        ll.addView(ownerField);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.encrypt_copy_title)
                .setView(ll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(confirmLabelRes, (d, w) -> {
                    String userPw = userField.getText() != null ? userField.getText().toString() : "";
                    String ownerPw = ownerField.getText() != null ? ownerField.getText().toString() : "";
                    if (userPw.isEmpty() || ownerPw.isEmpty()) {
                        host.showInfo(ctx.getString(R.string.encrypt_password_error));
                        return;
                    }
                    consumer.onPasswords(userPw, ownerPw);
                })
                .show();
    }

    public void shareSidecarAnnotationsBundle() {
        SidecarAnnotationProvider provider = host.sidecarAnnotationProviderOrNull();
        if (!(provider instanceof SidecarAnnotationSession)) {
            host.showInfo(host.getContext().getString(R.string.no_sidecar_annotations));
            return;
        }
        final SidecarAnnotationSession session = (SidecarAnnotationSession) provider;

        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        final Context appContext = host.getContext().getApplicationContext();
        final String documentName = host.currentDocumentName();

        host.callInBackgroundAndShowDialog(
                host.getContext().getString(R.string.preparing_to_share),
                new Callable<Exception>() {
                    Uri exportedUri;

                    @Override
                    public Exception call() {
                        try {
                            host.commitPendingInkToCoreBlocking();
                            exportedUri = exportSidecarBundleForExternalUse(appContext, session, documentName);
                        } catch (Exception e) {
                            return e;
                        }

                        shareIntent.setType("application/json");
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        shareIntent.setClipData(ClipData.newUri(host.getContentResolver(), documentName, exportedUri));
                        shareIntent.putExtra(Intent.EXTRA_STREAM, exportedUri);

                        PackageManager pm = host.getContext().getPackageManager();
                        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)) {
                            if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                                try {
                                    host.getContext().grantUriPermission(ri.activityInfo.packageName, exportedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                } catch (Exception ignore) {}
                            }
                        }
                        return null;
                    }
                },
                new Callable<Void>() {
                    @Override
                    public Void call() {
                        host.markIgnoreSaveOnStop();
                        Intent chooser = Intent.createChooser(shareIntent, host.getContext().getString(R.string.share_with));
                        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        host.getContext().startActivity(chooser);
                        return null;
                    }
                },
                null);
    }

    public void requestImportSidecarAnnotationsBundle() {
        Intent intent = DocumentAccessIntents.newOpenSidecarBundleIntent();
        host.startActivityForResult(intent, RequestCodes.IMPORT_ANNOTATIONS);
    }

    public void onActivityResultImportAnnotations(int resultCode, @Nullable Intent intent) {
        if (resultCode != Activity.RESULT_OK) return;
        Uri uri = intent != null ? intent.getData() : null;
        if (uri == null) return;
        importSidecarAnnotationsBundleFromUri(uri, /*forceImport*/ false);
    }

    /** Debug/testing hook: import a sidecar bundle without going through DocumentsUI. */
    public void importSidecarAnnotationsBundleFromUri(Uri uri, boolean forceImport) {
        if (uri == null) return;

        SidecarAnnotationProvider provider = host.sidecarAnnotationProviderOrNull();
        if (!(provider instanceof SidecarAnnotationSession)) {
            host.showInfo(host.getContext().getString(R.string.no_sidecar_import_target));
            return;
        }
        final SidecarAnnotationSession session = (SidecarAnnotationSession) provider;

        final AtomicReference<SidecarBundleJson.SidecarBundle> parsedRef = new AtomicReference<>();
        host.callInBackgroundAndShowDialog(
                host.getContext().getString(R.string.importing_annotations),
                new Callable<Exception>() {
                    @Override
                    public Exception call() {
                        try (InputStream in = host.getContentResolver().openInputStream(uri)) {
                            if (in == null) return new Exception("unable to open bundle: " + uri);
                            parsedRef.set(SidecarBundleJson.readBundleJson(in));
                            return null;
                        } catch (Exception e) {
                            return e;
                        }
                    }
                },
                new Callable<Void>() {
                    @Override
                    public Void call() {
                        SidecarBundleJson.SidecarBundle parsed = parsedRef.get();
                        if (parsed == null) return null;

                        boolean docMatch = session.docId().equals(parsed.docId);
                        if (!docMatch && !forceImport) {
                            showDocMismatchConfirm(session, parsed);
                            return null;
                        }
                        if (!docMatch && forceImport && org.opendroidpdf.BuildConfig.DEBUG) {
                            android.util.Log.w("ExportController", "DEBUG import: docId mismatch bundle="
                                    + shortId(parsed.docId) + " current=" + shortId(session.docId()));
                        }
                        importBundleIntoCurrentDoc(session, parsed);
                        return null;
                    }
                },
                null);
    }

    private void showDocMismatchConfirm(SidecarAnnotationSession session,
                                        SidecarBundleJson.SidecarBundle bundle) {
        String message = host.getContext().getString(
                R.string.import_docid_mismatch_message,
                shortId(bundle.docId),
                shortId(session.docId()));
        new androidx.appcompat.app.AlertDialog.Builder(host.getContext())
                .setTitle(R.string.import_annotations_title)
                .setMessage(message)
                .setPositiveButton(R.string.import_annotations_anyway, (d, w) -> importBundleIntoCurrentDoc(session, bundle))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void importBundleIntoCurrentDoc(SidecarAnnotationSession session,
                                           SidecarBundleJson.SidecarBundle bundle) {
        final AtomicReference<SidecarBundleJson.ImportStats> statsRef = new AtomicReference<>();
        host.callInBackgroundAndShowDialog(
                host.getContext().getString(R.string.importing_annotations),
                new Callable<Exception>() {
                    @Override
                    public Exception call() {
                        try {
                            statsRef.set(session.importBundleIntoThisDoc(bundle));
                            return null;
                        } catch (Exception e) {
                            return e;
                        }
                    }
                },
                new Callable<Void>() {
                    @Override
                    public Void call() {
                        host.invalidateDocumentView();
                        SidecarBundleJson.ImportStats stats = statsRef.get();
                        if (stats != null && stats.total() == 0) {
                            host.showInfo(host.getContext().getString(R.string.import_annotations_empty));
                            return null;
                        }
                        int ink = stats != null ? stats.inkCount : bundle.ink.size();
                        int hl = stats != null ? stats.highlightCount : bundle.highlights.size();
                        int notes = stats != null ? stats.noteCount : bundle.notes.size();
                        host.showInfo(host.getContext().getString(R.string.import_annotations_done, ink, hl, notes));
                        return null;
                    }
                },
                null);
    }

    private static String shortId(String id) {
        if (id == null) return "";
        String s = id.trim();
        if (s.length() <= 12) return s;
        return s.substring(0, 6) + "…" + s.substring(s.length() - 4);
    }

    private Uri exportPdfForExternalUse(Context appContext, MuPdfRepository repo, String baseName) throws Exception {
        SidecarAnnotationProvider sidecar = host.sidecarAnnotationProviderOrNull();
        if (sidecar != null) {
            if (repo.isPdfDocument()) {
                try {
                    return SidecarPdfEmbedExporter.export(appContext, repo, sidecar, baseName);
                } catch (Throwable embedError) {
                    // Fallback: always produce a usable PDF even if embedding fails.
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        android.util.Log.w("ExportController", "embed export failed; falling back to flattened", embedError);
                    }
                }
            }
            return FlattenedPdfExporter.export(appContext, repo, sidecar, baseName);
        }
        return repo.exportDocument(appContext);
    }

    private static Uri exportSidecarBundleForExternalUse(Context appContext,
                                                        SidecarAnnotationSession session,
                                                        String baseName) throws Exception {
        File outFile = newSidecarBundleFile(appContext, baseName);
        try (OutputStream os = new FileOutputStream(outFile, false)) {
            session.writeBundleJson(os);
        }
        return FileProvider.getUriForFile(appContext, "org.opendroidpdf.fileprovider", outFile);
    }

    private static File newSidecarBundleFile(Context appContext, String baseName) {
        File dir = new File(appContext.getCacheDir(), "tmpfiles");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();

        String safe = (baseName == null || baseName.trim().isEmpty()) ? "document" : baseName.trim();
        safe = safe.replace('/', '_').replace('\\', '_');
        safe = safe.replaceAll("[^A-Za-z0-9._ -]", "_");
        if (safe.length() > 64) safe = safe.substring(0, 64);

        if (safe.toLowerCase().endsWith(".epub")) {
            safe = safe.substring(0, safe.length() - 5);
        } else if (safe.toLowerCase().endsWith(".pdf")) {
            safe = safe.substring(0, safe.length() - 4);
        }

        String fileName = safe + "_annotations_" + System.currentTimeMillis() + ".json";
        return new File(dir, fileName);
    }
}
