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

    @NonNull
    @Override
    public Result importToPdf(@NonNull Context context, @NonNull Uri wordUri) {
        Context appContext = context.getApplicationContext();

        if (!isOfficePackInstalled(appContext)) {
            return Result.unavailable(appContext.getString(R.string.word_import_unavailable));
        }
        if (!signaturesMatch(appContext)) {
            return Result.unavailable(appContext.getString(R.string.word_import_office_pack_signature_mismatch));
        }

        File outFile = new File(appContext.getCacheDir(),
                "word_import_" + System.currentTimeMillis() + ".pdf");
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
}

