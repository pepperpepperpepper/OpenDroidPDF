package org.opendroidpdf.app.document;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.core.MuPdfRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

final class ExportOps {
    private final ExportController.Host host;

    ExportOps(@NonNull ExportController.Host host) {
        this.host = host;
    }

    @NonNull
    Uri exportPdfForExternalUse(@NonNull Context appContext,
                               @NonNull MuPdfRepository repo,
                               @Nullable String baseName) throws Exception {
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

    @NonNull
    Uri exportSidecarBundleForExternalUse(@NonNull Context appContext,
                                         @NonNull SidecarAnnotationSession session,
                                         @Nullable String baseName) throws Exception {
        File outFile = newSidecarBundleFile(appContext, baseName);
        try (OutputStream os = new FileOutputStream(outFile, false)) {
            session.writeBundleJson(os);
        }
        return FileProvider.getUriForFile(appContext, "org.opendroidpdf.fileprovider", outFile);
    }

    @NonNull
    File copyUriToTempFile(@NonNull Context appContext, @NonNull Uri src, @Nullable String baseName) throws Exception {
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

    @NonNull
    File newTempPdfFile(@NonNull Context appContext, @Nullable String baseName, @NonNull String suffix) {
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

    void copyFileToUri(@NonNull Context ctx, @NonNull File src, @NonNull Uri dest) throws Exception {
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

    void copyUriToUri(@NonNull Context ctx, @NonNull Uri src, @NonNull Uri dest) throws Exception {
        try (InputStream in = ctx.getContentResolver().openInputStream(src);
             OutputStream out = ctx.getContentResolver().openOutputStream(dest, "rwt")) {
            if (in == null) throw new IllegalStateException("InputStream null for " + src);
            if (out == null) throw new IllegalStateException("OutputStream null for " + dest);
            byte[] buf = new byte[16 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            out.flush();
        }
    }

    void grantUriToShareTargets(@NonNull Uri uri, @NonNull Intent shareIntent) {
        PackageManager pm = host.getContext().getPackageManager();
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                try {
                    host.getContext().grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignore) {}
            }
        }
    }

    @NonNull
    static String suggestedPdfCopyTitle(@Nullable String documentName) {
        String name = (documentName == null || documentName.trim().isEmpty()) ? "document" : documentName.trim();
        String lower = name.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".pdf")) {
            name = name.substring(0, name.length() - 4);
        } else if (lower.endsWith(".epub")) {
            name = name.substring(0, name.length() - 5);
        } else if (lower.endsWith(".docx")) {
            name = name.substring(0, name.length() - 5);
        } else if (lower.endsWith(".doc")) {
            name = name.substring(0, name.length() - 4);
        }
        return name + "_copy.pdf";
    }

    @NonNull
    private static File newSidecarBundleFile(@NonNull Context appContext, @Nullable String baseName) {
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
