package org.opendroidpdf.app.document;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.R;
import org.opendroidpdf.officepack.IOfficePackConverter;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Word import implementation backed by the optional Office Pack companion APK.
 *
 * <p>This pipeline is synchronous and is expected to be called off the UI thread. It performs a
 * secure bind to the Office Pack and routes conversions through it.</p>
 */
public final class OfficePackWordImportPipeline implements WordImportPipeline {

    private static final String TAG = "OfficePackWordImport";
    private static final String OFFICE_PACK_PACKAGE = "org.opendroidpdf.officepack";
    private static final String OFFICE_PACK_SERVICE = "org.opendroidpdf.officepack.OfficePackConverterService";
    private static final long BIND_TIMEOUT_MS = 2000;
    private static final String OUT_PREFIX = "word_import_";

    @NonNull
    @Override
    public Result importToPdf(@NonNull Context context, @NonNull Uri wordUri) {
        Context appContext = context.getApplicationContext();
        boolean isLegacyDoc = isLikelyLegacyDoc(appContext, wordUri);

        if (!isOfficePackInstalled(appContext)) {
            return Result.unavailable(appContext.getString(R.string.word_import_unavailable));
        }
        if (!signaturesMatch(appContext)) {
            return Result.unavailable(appContext.getString(R.string.word_import_office_pack_signature_mismatch));
        }

        String docId = null;
        try {
            docId = DocumentIdentityResolver.resolve(appContext, wordUri).docId();
        } catch (Throwable ignore) {
        }
        String token = stableHash(docId != null ? docId : wordUri.toString());
        File outFile = new File(appContext.getCacheDir(), OUT_PREFIX + token + ".pdf");
        if (outFile.isFile() && outFile.length() > 0) {
            Log.i(TAG, "Using cached Word import pdf=" + outFile.getName());
            return Result.success(Uri.fromFile(outFile));
        }
        boolean success = false;

        try (ParcelFileDescriptor inPfd = openForRead(appContext, wordUri);
             ParcelFileDescriptor outPfd = ParcelFileDescriptor.open(
                     outFile,
                     ParcelFileDescriptor.MODE_CREATE
                             | ParcelFileDescriptor.MODE_TRUNCATE
                             | ParcelFileDescriptor.MODE_WRITE_ONLY)) {

            if (inPfd == null) {
                return Result.unavailable(appContext.getString(R.string.word_import_unavailable));
            }

            Intent intent = new Intent("org.opendroidpdf.officepack.action.CONVERT");
            intent.setComponent(new ComponentName(OFFICE_PACK_PACKAGE, OFFICE_PACK_SERVICE));

            BlockingConn conn = new BlockingConn();
            boolean bound = appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE);
            if (!bound) {
                return Result.unavailable(appContext.getString(R.string.word_import_unavailable));
            }

            try {
                if (!conn.awaitBound(BIND_TIMEOUT_MS)) {
                    Log.w(TAG, "Office Pack bind timeout");
                    return Result.unavailable(appContext.getString(R.string.word_import_office_pack_failed));
                }
                IOfficePackConverter converter = conn.converterOrNull();
                if (converter == null) {
                    return Result.unavailable(appContext.getString(R.string.word_import_office_pack_failed));
                }

                int code;
                try {
                    code = converter.convertWordToPdf(inPfd, outPfd);
                } catch (RemoteException re) {
                    Log.e(TAG, "Office Pack conversion failed", re);
                    return Result.unavailable(appContext.getString(R.string.word_import_office_pack_failed));
                }

                if (code == IOfficePackConverter.RESULT_OK) {
                    success = true;
                    return Result.success(Uri.fromFile(outFile));
                }
                if (code == IOfficePackConverter.RESULT_UNSUPPORTED) {
                    if (isLegacyDoc) {
                        return Result.unavailable(appContext.getString(R.string.word_import_office_pack_doc_unsupported));
                    }
                    return Result.unavailable(appContext.getString(R.string.word_import_office_pack_unsupported));
                }
                return Result.unavailable(appContext.getString(R.string.word_import_office_pack_failed));
            } finally {
                try {
                    appContext.unbindService(conn);
                } catch (Throwable ignore) {
                }
            }
        } catch (FileNotFoundException fnf) {
            Log.w(TAG, "Word URI not found: " + wordUri, fnf);
            return Result.unavailable(appContext.getString(R.string.word_import_unavailable));
        } catch (Throwable t) {
            Log.e(TAG, "Word import failed uri=" + wordUri, t);
            return Result.unavailable(appContext.getString(R.string.word_import_office_pack_failed));
        } finally {
            if (!success) {
                try {
                // Best-effort cleanup of partial outputs.
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
            } catch (Throwable ignore) {
            }
        }
    }
    }

    private static boolean isOfficePackInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(OFFICE_PACK_PACKAGE, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean signaturesMatch(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.checkSignatures(context.getPackageName(), OFFICE_PACK_PACKAGE)
                    == PackageManager.SIGNATURE_MATCH;
        } catch (Throwable t) {
            return false;
        }
    }

    private static @Nullable ParcelFileDescriptor openForRead(Context context, Uri uri) throws FileNotFoundException {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path == null) throw new FileNotFoundException("missing file path for uri=" + uri);
            return ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
        }
        return context.getContentResolver().openFileDescriptor(uri, "r");
    }

    private static boolean isLikelyLegacyDoc(@NonNull Context context, @NonNull Uri uri) {
        try {
            String mime = context.getContentResolver().getType(uri);
            if (mime != null) {
                String m = mime.toLowerCase(java.util.Locale.US);
                if (m.contains("wordprocessingml")) return false;
                if (m.contains("msword")) return true;
            }
        } catch (Throwable ignore) {
        }

        try {
            String path = uri.getPath();
            if (path != null) {
                String p = path.toLowerCase(java.util.Locale.US);
                if (p.endsWith(".docx")) return false;
                if (p.endsWith(".doc")) return true;
            }
        } catch (Throwable ignore) {
        }

        try {
            android.database.Cursor c = context.getContentResolver().query(
                    uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        String name = c.getString(0);
                        if (name != null) {
                            String n = name.toLowerCase(java.util.Locale.US);
                            if (n.endsWith(".docx")) return false;
                            if (n.endsWith(".doc")) return true;
                        }
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Throwable ignore) {
        }

        return false;
    }

    private static final class BlockingConn implements ServiceConnection {
        private final CountDownLatch bound = new CountDownLatch(1);
        private volatile IOfficePackConverter converter;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            converter = IOfficePackConverter.Stub.asInterface(service);
            bound.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            converter = null;
        }

        boolean awaitBound(long timeoutMs) {
            try {
                return bound.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        @Nullable
        IOfficePackConverter converterOrNull() {
            return converter;
        }
    }

    private static String stableHash(@NonNull String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(24);
            // Shorten: we only need a stable filename token, not a full content hash.
            for (int i = 0; i < Math.min(hash.length, 12); i++) {
                byte b = hash[i];
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Throwable t) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
