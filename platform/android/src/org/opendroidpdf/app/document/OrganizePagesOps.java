package org.opendroidpdf.app.document;

import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
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

final class OrganizePagesOps {
    interface BoolConsumer {
        void accept(boolean value);
    }

    private final OrganizePagesController.Host host;
    private final BoolConsumer onHasStagedChangesChanged;

    private boolean hasStagedChanges;
    private @Nullable File stagedPdfFile;
    private @Nullable OpenDroidPDFCore stagedCore;
    private @Nullable MuPdfRepository stagedRepo;

    private final List<OpenDroidPDFCore> staleCores = new ArrayList<>();
    private final List<File> staleFiles = new ArrayList<>();

    OrganizePagesOps(@NonNull OrganizePagesController.Host host, @NonNull BoolConsumer onHasStagedChangesChanged) {
        this.host = host;
        this.onHasStagedChangesChanged = onHasStagedChangesChanged;
    }

    boolean hasStagedChanges() { return hasStagedChanges; }

    @Nullable
    MuPdfRepository stagedRepoOrNull() { return stagedRepo; }

    @Nullable
    File stagedPdfFileOrNull() { return stagedPdfFile; }

    void clearStagedState() {
        hasStagedChanges = false;
        onHasStagedChangesChanged.accept(false);

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

    void ensureStagedCopyReady(@NonNull Runnable onReady) {
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

    void applyOperationToStagedCopy(@NonNull OrganizePagesController.PendingOperation op) {
        ensureStagedCopyReady(() -> applyOperationToStagedCopyInternal(op));
    }

    void saveOutputToUri(@NonNull Uri dest, @NonNull OrganizePagesController.PendingOperation op, @NonNull Runnable onSuccess) {
        final Context ctx = host.getContext();
        final Context appContext = ctx.getApplicationContext();
        final String documentName = host.currentDocumentName();
        final File staged = stagedPdfFile;
        if (staged == null || !staged.isFile()) {
            host.showInfo(ctx.getString(R.string.not_supported));
            return;
        }
        host.callInBackgroundAndShowDialog(
                ctx.getString(R.string.organize_pages_preparing),
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
                        onSuccess.run();
                        return null;
                    }
                },
                null);
    }

    private void applyOperationToStagedCopyInternal(@NonNull OrganizePagesController.PendingOperation op) {
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
                        onHasStagedChangesChanged.accept(true);

                        if (srcCore != null) staleCores.add(srcCore);
                        staleFiles.add(srcFile);

                        host.showInfo(ctx.getString(R.string.organize_pages_changes_staged));
                        return null;
                    }
                },
                null);
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

    static void takePersistableReadPermission(@NonNull android.content.ContentResolver resolver, @NonNull Uri uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignore) {
        }
    }

    static String suffixFor(OrganizePagesController.OperationType type) {
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

    static String[] buildInsertSelections(@NonNull File src,
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

    static void writeMinimalBlankPdf(@NonNull File dest, int widthPt, int heightPt) throws Exception {
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

    static File newTempPdfFile(Context appContext, String baseName, String suffix) {
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

    static File copyUriToTempFile(Context appContext, Uri src, String baseName) throws Exception {
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

    static void copyFileToUri(Context ctx, File src, Uri dest) throws Exception {
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

    static Set<Integer> parsePages(@NonNull String spec, int pageCount) throws IllegalArgumentException {
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

    static String compressPages(@NonNull List<Integer> pagesSortedAscending) {
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
}
