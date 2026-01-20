package org.opendroidpdf;

import androidx.core.content.FileProvider;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import android.os.ParcelFileDescriptor;

final class OpenDroidPDFSaveOps {
    private OpenDroidPDFSaveOps() {
    }

    private static final String TAG = "OpenDroidPDFCore";

    static Uri export(OpenDroidPDFCore core, Context context) throws java.io.IOException, java.io.FileNotFoundException, Exception {
        Uri oldUri = core.uri;
        String oldPath = core.getPath();
        String oldFileName = core.getFileName();
        boolean oldHasChanges = core.hasChanges();

        //If no tmpflie has been created or the file name has changed,
        //creat a new tmpFile and, if appropriate, remeber the old tmpFile
        //to delete it after the core has saved to the new location.
        File oldTmpFile = null;
        boolean needsNewTmp = (core.tmpFile == null) || !core.tmpFile.getName().equals(oldFileName);
        // Also rotate if the current tmpFile lives under the materialized content cache;
        // exports should go to cache/tmpfiles to avoid clobbering materialized source.
        if (!needsNewTmp) {
            try {
                File cacheContentRoot = new File(context.getCacheDir(), "content").getCanonicalFile();
                File current = core.tmpFile.getCanonicalFile();
                needsNewTmp = OpenDroidPDFOpenOps.isChildOf(current, cacheContentRoot);
            } catch (Exception ignore) { needsNewTmp = true; }
        }
        if (needsNewTmp) {
            oldTmpFile = core.tmpFile;
            File cacheDir = new File(context.getCacheDir(), "tmpfiles");
            cacheDir.mkdirs();
            File uniqueDirInCacheDir = null;
            int i = 0;
            do {
                uniqueDirInCacheDir = new File(cacheDir, Integer.toString(i));
                i++;
            } while (uniqueDirInCacheDir == null || uniqueDirInCacheDir.exists());

            uniqueDirInCacheDir.mkdirs();
            core.tmpFile = new File(uniqueDirInCacheDir, oldFileName);
        }

        // Native saveAsInternal returns 1 on success, 0 on failure.
        if (core.saveAs(core.tmpFile.getPath()) == 0)
            throw new java.io.IOException("native code failed to save to tmp file: " + core.tmpFile.getPath());

        //Delete old tmp file if we created a new one
        if (oldTmpFile != null)
            oldTmpFile.delete();

        //reinit because the MuPDFCore core gets useless after saveIntenal()
        core.init(context, Uri.fromFile(core.tmpFile));
        //But now the Uri, as well as mFilenName and mPath in the superclass are wrong, so we repair this
        core.uri = oldUri;
        core.relocate(oldPath, oldFileName);
        core.setHasAdditionalChanges(oldHasChanges);

        return FileProvider.getUriForFile(context, "org.opendroidpdf.fileprovider", core.tmpFile);
    }

    static void save(OpenDroidPDFCore core, Context context) throws java.io.IOException, java.io.FileNotFoundException, Exception {
        saveAs(core, context, core.uri);
    }

    static void saveAs(OpenDroidPDFCore core, Context context, Uri uri) throws java.io.IOException, java.io.FileNotFoundException, Exception {
        ParcelFileDescriptor pfd = null;
        FileOutputStream fileOutputStream = null;
        FileInputStream fileInputStream = null;
        try {
            //Export to tmpFile
            export(core, context);

            //Open the result as fileInputStream
            fileInputStream = new FileInputStream(core.tmpFile);

            //Open FileOutputStream to actual destination
            try {
                pfd = context.getContentResolver().openFileDescriptor(uri, "w");
                if (pfd != null)
                    fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
            } catch (Exception e) {
                String path = uri.getPath();
                File file = null;
                if (path != null)
                    file = new File(path);
                if (file != null)
                    fileOutputStream = new FileOutputStream(file);
            } finally {
                if (fileOutputStream == null)
                    throw new java.io.IOException("Unable to open output stream to given uri: " + uri);
            }
            OpenDroidPDFOpenOps.copyStream(fileInputStream, fileOutputStream);
//                Log.i(context.getString(R.string.app_name), "copyStream() succesfull");
        } catch (java.io.FileNotFoundException e) {
            Log.e(TAG, "Exception for uri=" + uri);
            throw e;
        } catch (java.io.IOException e) {
            throw e;
        } finally {
            if (fileInputStream != null) fileInputStream.close();
            if (fileOutputStream != null) fileOutputStream.close();
            if (pfd != null) pfd.close();
        }
        //remeber the new uri and tell the core that all changes were saved
        core.uri = uri;

        core.relocate(uri.getPath(), OpenDroidPDFOpenOps.getFileName(context, uri));

        core.setHasAdditionalChanges(false);
    }

    static <T extends Context & TemporaryUriPermission.TemporaryUriPermissionProvider> boolean canSaveToUriViaContentResolver(T context, Uri uri) {
        boolean haveWritePermissionToUri = false;
        try {
            for (TemporaryUriPermission permission : (context).getTemporaryUriPermissions()) {
//                Log.i(context.getString(R.string.app_name), "checking saved temporary permission for "+permission.getUri()+" while uri="+uri+" write permission is "+permission.isWritePermission()+" and uris are equal "+permission.getUri().equals(uri));
                if (permission.isWritePermission() && permission.getUri().equals(uri)) {
                    haveWritePermissionToUri = true;
                    break;
                }
            }
            if (!haveWritePermissionToUri) {
                if (android.os.Build.VERSION.SDK_INT >= 19) {
                    for (UriPermission permission : (context).getContentResolver().getPersistedUriPermissions()) {
                        if (permission.isWritePermission() && permission.getUri().equals(uri)) {
                            haveWritePermissionToUri = true;
                            break;
                        }
                    }
                } else {
                    if (context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                        haveWritePermissionToUri = true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(context.getString(R.string.app_name), "exception while trying to figure out permissions: " + e);
            return false;
        }
        if (!haveWritePermissionToUri)
            return false;

        // If we have write permission, treat the URI as writable.
        //
        // Probing writability by opening an output stream/file descriptor is unreliable:
        // many SAF providers do not support "rw"/"wa" even though "w" (used by saveAs())
        // works. If saving later fails (revoked permission, provider error), we surface it
        // and fall back to Save As.
        return true;
    }

    static boolean canSaveToUriAsFile(Context context, Uri uri) {
        try {
            //The way we use here to determine whether we can write to a file is error prone but I have so far not found a better way
            if (uri.toString().startsWith("content:"))
                return false;
            File file = new File(Uri.decode(uri.getEncodedPath()));
            if (file.exists() && file.isFile() && file.canWrite())
                return true;
            else
                return false;
        } catch (Exception e) {
            return false;
        }
    }

    static <T extends Context & TemporaryUriPermission.TemporaryUriPermissionProvider> boolean canSaveToCurrentUri(OpenDroidPDFCore core, T context) {
        return canSaveToUriViaContentResolver(context, core.getUri()) || canSaveToUriAsFile(context, core.getUri());
    }

    static void onDestroy(OpenDroidPDFCore core) {
        if (core.tmpFile != null) {
            File cacheDirFile = (MuPDFCore.cachDir != null) ? new File(MuPDFCore.cachDir) : null;
            boolean deleted = false;
            if (cacheDirFile != null) {
                try {
                    File contentRoot = new File(cacheDirFile, "content");
                    File tmpfilesRoot = new File(cacheDirFile, "tmpfiles");
                    if (OpenDroidPDFOpenOps.isChildOf(core.tmpFile, contentRoot) || OpenDroidPDFOpenOps.isChildOf(core.tmpFile, tmpfilesRoot)) {
                        File parent = core.tmpFile.getParentFile();
                        if (parent != null && parent.exists() && parent.getParentFile() != null) {
                            OpenDroidPDFOpenOps.deleteRecursively(parent);
                            deleted = true;
                        }
                    }
                } catch (Exception ignore) {
                }
            }
            if (!deleted) {
                core.tmpFile.delete();
            }
            core.tmpFile = null;
        }
    }

    static boolean deleteDocument(OpenDroidPDFCore core, Context context) {
        try {
            context.getContentResolver().delete(core.uri, null, null);
        } catch (Exception e) {
            try {
                File file = new File(Uri.decode(core.uri.getEncodedPath()));
                file.delete();
            } catch (Exception e2) {
                return false;
            }
        }
        return true;
    }

    static void createEmptyDocument(Context context, Uri uri) throws java.io.IOException, java.io.FileNotFoundException {
        FileOutputStream fileOutputStream = null;
        try {
            String path = uri.getPath();
            File file = null;
            if (path != null)
                file = new File(path);
            if (file != null)
                fileOutputStream = new FileOutputStream(file);

            if (fileOutputStream == null)
                throw new java.io.IOException("Unable to open output stream to given uri: " + uri.getPath());

            String newline = System.getProperty("line.separator");
            String minimalPDF =
                    "%PDF-1.1" + newline +
                            "\u00a5\u00b1\u00eb" + newline +
                            "1 0 obj " + newline +
                            "<<" + newline +
                            "/Type /Catalog" + newline +
                            "/Pages 2 0 R" + newline +
                            ">>" + newline +
                            "endobj " + newline +
                            "2 0 obj " + newline +
                            "<<" + newline +
                            "/Kids [3 0 R]" + newline +
                            "/Type /Pages" + newline +
                            "/MediaBox [0 0 595 841]" + newline +
                            "/Count 1" + newline +
                            ">>" + newline +
                            "endobj " + newline +
                            "3 0 obj " + newline +
                            "<<" + newline +
                            "/Resources " + newline +
                            "<<" + newline +
                            "/Font " + newline +
                            "<<" + newline +
                            "/F1 " + newline +
                            "<<" + newline +
                            "/Subtype /Type1" + newline +
                            "/Type /Font" + newline +
                            "/BaseFont /Times-Roman" + newline +
                            ">>" + newline +
                            ">>" + newline +
                            ">>" + newline +
                            "/Parent 2 0 R" + newline +
                            "/Type /Page" + newline +
                            "/MediaBox [0 0 595 841]" + newline +
                            ">>" + newline +
                            "endobj xref" + newline +
                            "0 4" + newline +
                            "0000000000 65535 f " + newline +
                            "0000000015 00000 n " + newline +
                            "0000000066 00000 n " + newline +
                            "0000000149 00000 n " + newline +
                            "trailer" + newline +
                            "" + newline +
                            "<<" + newline +
                            "/Root 1 0 R" + newline +
                            "/Size 4" + newline +
                            ">>" + newline +
                            "startxref" + newline +
                            "314" + newline +
                            "%%EOF" + newline;
            byte[] buffer = minimalPDF.getBytes();
            fileOutputStream.write(buffer, 0, buffer.length);
        } catch (java.io.FileNotFoundException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw e;
        } finally {
            if (fileOutputStream != null) fileOutputStream.close();
        }
    }
}

