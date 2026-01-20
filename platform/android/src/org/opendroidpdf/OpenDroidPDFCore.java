package org.opendroidpdf;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import java.io.File;

public class OpenDroidPDFCore extends MuPDFCore
{
    Uri uri = null;
    File tmpFile = null;
    private String documentPassword = null;

        /* File IO is terribly inconsistent and badly documented on Android
         * to make matters worse the native part of the Core stops beeing
         * useful once the method saveInternal() is call by MuPDFCore.
         * Here we try to abstract away the complexity this brings with it
         * by implementing three methods export() save() and saveAs() that
         * try to do what one would expect such methods to do.
         * Unoftunately this leads to terribly messy code that is really
         * hard to maintain...
         */

    public OpenDroidPDFCore(Context context, Uri uri) throws Exception
        {
            init(context, uri);
        }


    public synchronized void init(Context context, Uri uri) throws Exception
        {
            this.uri = uri;
            this.documentPassword = null;

            final boolean isFileUri = "file".equalsIgnoreCase(uri.getScheme());
            final boolean isContentUri = "content".equalsIgnoreCase(uri.getScheme());
            final String decodedPath = uri.getEncodedPath() != null ? Uri.decode(uri.getEncodedPath()) : null;
            final File targetFile = decodedPath != null ? new File(decodedPath) : null;

                /*Sometimes we can open a uri both as a file and via a content provider. On old versions of Android the former works better, whereas on new versions the latter works generally better. Hence we switch the order in which we try depending on the Android version.*/

            if(isFileUri && targetFile != null && targetFile.isFile())
            {
                    // Allow direct access only when the file lives in app-private storage or we run on pre-Marshmallow devices.
                if((Build.VERSION.SDK_INT < 23) || OpenDroidPDFOpenOps.isPathInsideAppStorage(context, targetFile))
                {
                    super.init(context, targetFile.getAbsolutePath());
                    return;
                }
            }

            if (isContentUri || (isFileUri && targetFile != null && targetFile.isFile()))
            {
                File previousTemp = tmpFile;
                File materialized = OpenDroidPDFOpenOps.materializeToCache(context, uri, isFileUri ? targetFile : null);
                tmpFile = materialized;
                OpenDroidPDFOpenOps.cleanupPreviousMaterialization(previousTemp, tmpFile, new File(context.getCacheDir(), "content"));
                super.init(context, materialized.getAbsolutePath());
                return;
            }

            if (targetFile != null && targetFile.isFile())
            {
                super.init(context, targetFile.getAbsolutePath());
                return;
            }
        }

    /**
     * Returns the last password accepted by {@link #authenticatePassword(String)}, or null if the
     * current document is not password-protected or hasn't been unlocked yet.
     *
     * <p>Stored only in-memory for the lifetime of this core instance.</p>
     */
    public synchronized String getDocumentPasswordOrNull() {
        return documentPassword;
    }

    @Override
    public synchronized boolean authenticatePassword(String password) {
        boolean ok = super.authenticatePassword(password);
        if (ok) {
            documentPassword = password;
        }
        return ok;
    }

    public synchronized Uri export(Context context) throws java.io.IOException, java.io.FileNotFoundException, Exception
        {
            return OpenDroidPDFSaveOps.export(this, context);
        }

    public synchronized void save(Context context) throws java.io.IOException, java.io.FileNotFoundException, Exception
        {
            OpenDroidPDFSaveOps.save(this, context);
        }

    public synchronized void saveAs(Context context, Uri uri) throws java.io.IOException, java.io.FileNotFoundException, Exception
        {
            OpenDroidPDFSaveOps.saveAs(this, context, uri);
        }

    public synchronized <T extends Context & TemporaryUriPermission.TemporaryUriPermissionProvider> boolean canSaveToUriViaContentResolver(T context, Uri uri) {
        return OpenDroidPDFSaveOps.canSaveToUriViaContentResolver(context, uri);
    }

    public synchronized boolean canSaveToUriAsFile(Context context, Uri uri) {
        return OpenDroidPDFSaveOps.canSaveToUriAsFile(context, uri);
    }

    public synchronized <T extends Context & TemporaryUriPermission.TemporaryUriPermissionProvider> boolean canSaveToCurrentUri(T context) {
        return OpenDroidPDFSaveOps.canSaveToCurrentUri(this, context);
    }

    public synchronized Uri getUri(){
        return uri;
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        OpenDroidPDFSaveOps.onDestroy(this);
    }

    public synchronized boolean deleteDocument(Context context) {
        return OpenDroidPDFSaveOps.deleteDocument(this, context);
    }

    public synchronized static void createEmptyDocument(Context context, Uri uri) throws java.io.IOException, java.io.FileNotFoundException {
        OpenDroidPDFSaveOps.createEmptyDocument(context, uri);
    }

    @Override
    public synchronized boolean insertBlankPageBefore(int position) {
        setHasAdditionalChanges(true);
        return super.insertBlankPageBefore(position);
    }


    public static synchronized <T extends Context & TemporaryUriPermission.TemporaryUriPermissionProvider> boolean canReadFromUri(T context, Uri uri) {
        return OpenDroidPDFOpenOps.canReadFromUri(context, uri);
    }

    public synchronized String getFileName(Context context, Uri uri) {
        return OpenDroidPDFOpenOps.getFileName(context, uri);
    }
}
