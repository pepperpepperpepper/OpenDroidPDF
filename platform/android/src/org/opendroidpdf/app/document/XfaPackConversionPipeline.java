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
import org.opendroidpdf.xfapack.IXfaPackConverter;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * XFA conversion implementation backed by the optional XFA Pack companion APK.
 *
 * <p>This pipeline is synchronous and is expected to be called off the UI thread. It performs a
 * secure bind to the XFA Pack and routes conversions through it.</p>
 */
public final class XfaPackConversionPipeline {

    private static final String TAG = "XfaPackConversion";
    public static final String XFA_PACK_PACKAGE = "org.opendroidpdf.xfapack";
    private static final String XFA_PACK_SERVICE = "org.opendroidpdf.xfapack.XfaPackConverterService";
    private static final long BIND_TIMEOUT_MS = 2000;

    private static final String OUT_PREFIX_CONVERT = "xfa_convert_";
    private static final String OUT_PREFIX_FLATTEN = "xfa_flatten_";

    public static final class Result {
        @Nullable public final Uri outputUri;
        @Nullable public final String message;
        @Nullable public final Action action;

        private Result(@Nullable Uri outputUri, @Nullable String message, @Nullable Action action) {
            this.outputUri = outputUri;
            this.message = message;
            this.action = action;
        }

        public static Result success(@NonNull Uri outputUri) {
            return new Result(outputUri, null, null);
        }

        public static Result unavailable(@NonNull String message) {
            return new Result(null, message, null);
        }

        public static Result unavailable(@NonNull String message, @NonNull Action action) {
            return new Result(null, message, action);
        }
    }

    public enum Action {
        INSTALL_XFA_PACK
    }

    private XfaPackConversionPipeline() {
    }

    @NonNull
    public static Result convert(@NonNull Context context,
                                 @NonNull Uri pdfUri,
                                 int mode) {
        return convert(context, pdfUri, mode, null);
    }

    @NonNull
    public static Result convert(@NonNull Context context,
                                 @NonNull Uri pdfUri,
                                 int mode,
                                 @Nullable String password) {
        Context appContext = context.getApplicationContext();

        if (!isXfaPackInstalled(appContext)) {
            return Result.unavailable(
                    appContext.getString(R.string.xfa_pack_unavailable),
                    Action.INSTALL_XFA_PACK);
        }
        if (!signaturesMatch(appContext)) {
            return Result.unavailable(
                    appContext.getString(R.string.xfa_pack_signature_mismatch),
                    Action.INSTALL_XFA_PACK);
        }

        String docId = null;
        try {
            docId = DocumentIdentityResolver.resolve(appContext, pdfUri).docId();
        } catch (Throwable ignore) {
        }

        String token = stableHash(docId != null ? docId : pdfUri.toString());
        String prefix = mode == IXfaPackConverter.MODE_FLATTEN_TO_PDF ? OUT_PREFIX_FLATTEN : OUT_PREFIX_CONVERT;
        File outFile = new File(appContext.getCacheDir(), prefix + token + ".pdf");
        if (outFile.isFile() && outFile.length() > 0) {
            Log.i(TAG, "Using cached XFA pack output=" + outFile.getName());
            return Result.success(Uri.fromFile(outFile));
        }
        boolean success = false;

        try (ParcelFileDescriptor inPfd = openForRead(appContext, pdfUri);
             ParcelFileDescriptor outPfd = ParcelFileDescriptor.open(
                     outFile,
                     ParcelFileDescriptor.MODE_CREATE
                             | ParcelFileDescriptor.MODE_TRUNCATE
                             | ParcelFileDescriptor.MODE_WRITE_ONLY)) {

            if (inPfd == null) {
                return Result.unavailable(appContext.getString(R.string.xfa_pack_failed));
            }

            Intent intent = new Intent("org.opendroidpdf.xfapack.action.CONVERT");
            intent.setComponent(new ComponentName(XFA_PACK_PACKAGE, XFA_PACK_SERVICE));

            BlockingConn conn = new BlockingConn();
            boolean bound = appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE);
            if (!bound) {
                return Result.unavailable(appContext.getString(R.string.xfa_pack_failed));
            }

            try {
                if (!conn.awaitBound(BIND_TIMEOUT_MS)) {
                    Log.w(TAG, "XFA Pack bind timeout");
                    return Result.unavailable(appContext.getString(R.string.xfa_pack_failed));
                }
                IXfaPackConverter converter = conn.converterOrNull();
                if (converter == null) {
                    return Result.unavailable(appContext.getString(R.string.xfa_pack_failed));
                }

                int code;
                try {
                    if (password != null) {
                        try {
                            code = converter.convertXfaToPdfWithPassword(inPfd, outPfd, mode, password);
                        } catch (RemoteException re) {
                            // Backwards compatibility: older XFA Pack versions won't implement the
                            // password-enabled method. Fall back to the legacy call.
                            code = converter.convertXfaToPdf(inPfd, outPfd, mode);
                        }
                    } else {
                        code = converter.convertXfaToPdf(inPfd, outPfd, mode);
                    }
                } catch (RemoteException re) {
                    Log.e(TAG, "XFA Pack conversion failed", re);
                    return Result.unavailable(appContext.getString(R.string.xfa_pack_failed));
                }
                Log.i(TAG, "XFA Pack conversion finished code=" + code + " mode=" + mode);

                if (code == IXfaPackConverter.RESULT_OK) {
                    success = true;
                    return Result.success(Uri.fromFile(outFile));
                }
                if (code == IXfaPackConverter.RESULT_PASSWORD_REQUIRED) {
                    return Result.unavailable(appContext.getString(R.string.xfa_pack_password_required));
                }
                if (code == IXfaPackConverter.RESULT_UNSUPPORTED) {
                    return Result.unavailable(appContext.getString(R.string.xfa_pack_unsupported));
                }
                return Result.unavailable(appContext.getString(R.string.xfa_pack_failed));
            } finally {
                try {
                    appContext.unbindService(conn);
                } catch (Throwable ignore) {
                }
            }
        } catch (FileNotFoundException fnf) {
            Log.w(TAG, "PDF URI not found: " + pdfUri, fnf);
            return Result.unavailable(appContext.getString(R.string.xfa_pack_failed));
        } catch (Throwable t) {
            Log.e(TAG, "XFA conversion failed uri=" + pdfUri, t);
            return Result.unavailable(appContext.getString(R.string.xfa_pack_failed));
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

    private static boolean isXfaPackInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(XFA_PACK_PACKAGE, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean signaturesMatch(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.checkSignatures(context.getPackageName(), XFA_PACK_PACKAGE)
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
        private volatile IXfaPackConverter converter;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            converter = IXfaPackConverter.Stub.asInterface(service);
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
        IXfaPackConverter converterOrNull() {
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
