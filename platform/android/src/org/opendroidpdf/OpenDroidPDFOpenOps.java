package org.opendroidpdf;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OpenDroidPDFOpenOps {
    private OpenDroidPDFOpenOps() {
    }

    private static final long CACHE_PRUNE_THRESHOLD_MS = 3L * 24L * 60L * 60L * 1000L; // prune temp copies older than ~3 days

    static boolean isPathInsideAppStorage(Context context, File path) {
        try {
            File filesDir = context.getFilesDir();
            File cacheDir = context.getCacheDir();
            return isChildOf(path, filesDir) || isChildOf(path, cacheDir);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isChildOf(File child, File possibleParent) throws Exception {
        if (child == null || possibleParent == null)
            return false;
        File parent = child.getCanonicalFile();
        File targetParent = possibleParent.getCanonicalFile();
        while (parent != null) {
            if (parent.equals(targetParent))
                return true;
            parent = parent.getParentFile();
        }
        return false;
    }

    static File materializeToCache(Context context, Uri uri, File fileFallback) throws Exception {
        String displayName = getFileName(context, uri);
        if (displayName == null || displayName.trim().length() == 0) {
            if (fileFallback != null)
                displayName = fileFallback.getName();
            else
                displayName = "document.pdf";
        }
        displayName = displayName.replace('/', '_').replace('\\', '_');

        File cacheRoot = new File(context.getCacheDir(), "content");
        if (!cacheRoot.exists() && !cacheRoot.mkdirs())
            throw new Exception("unable to create cache root at " + cacheRoot.getAbsolutePath());

        File uniqueDir = null;
        for (int attempt = 0; attempt < 32; attempt++) {
            File candidate = new File(cacheRoot, UUID.randomUUID().toString());
            if (!candidate.exists() && candidate.mkdirs()) {
                uniqueDir = candidate;
                break;
            }
        }
        if (uniqueDir == null)
            throw new Exception("unable to create temporary directory for " + uri.toString());

        File contentCache = new File(uniqueDir, displayName);

        InputStream is = null;
        OutputStream os = null;
        ParcelFileDescriptor pfd = null;
        try {
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null)
                    is = new FileInputStream(pfd.getFileDescriptor());
                if (is == null)
                    is = context.getContentResolver().openInputStream(uri);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                if (fileFallback == null)
                    throw new Exception("unable to resolve file fallback for uri " + uri.toString());
                try {
                    is = new FileInputStream(fileFallback);
                } catch (SecurityException | FileNotFoundException fileException) {
                    ParcelFileDescriptor alternativePfd = openFileDescriptorForFileUri(context, fileFallback);
                    if (alternativePfd != null) {
                        pfd = alternativePfd;
                        is = new FileInputStream(pfd.getFileDescriptor());
                    } else {
                        throw fileException;
                    }
                }
            } else {
                throw new Exception("unsupported uri scheme " + uri.getScheme());
            }

            if (is == null)
                throw new Exception("unable to open input stream to uri " + uri.toString());

            os = new FileOutputStream(contentCache, false);
            copyStream(is, os);
        } catch (SecurityException | FileNotFoundException securityException) {
            deleteRecursively(contentCache);
            deleteRecursively(uniqueDir);
            throw new Exception("Unable to read \"" + uri.toString() + "\". Please re-select the document using the system file picker.", securityException);
        } finally {
            try { if (os != null) os.close(); } catch (Exception ignore) {}
            try { if (is != null) is.close(); } catch (Exception ignore) {}
            try { if (pfd != null) pfd.close(); } catch (Exception ignore) {}
        }

        long now = System.currentTimeMillis();
        uniqueDir.setLastModified(now);
        contentCache.setLastModified(now);
        pruneOldCacheDirs(cacheRoot, uniqueDir, now);

        return contentCache;
    }

    private static ParcelFileDescriptor openFileDescriptorForFileUri(Context context, File file) {
        if (file == null)
            return null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                ParcelFileDescriptor docPfd = openFileDescriptorViaDocumentsContract(context, file);
                if (docPfd != null)
                    return docPfd;
            } catch (Exception ignored) {
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ParcelFileDescriptor mediaStorePfd = openFileDescriptorViaMediaStore(context, file);
                if (mediaStorePfd != null)
                    return mediaStorePfd;
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static ParcelFileDescriptor openFileDescriptorViaDocumentsContract(Context context, File file) throws FileNotFoundException {
        File externalRoot = Environment.getExternalStorageDirectory();
        if (externalRoot == null)
            return null;

        String rootPath = externalRoot.getAbsolutePath();
        String absolutePath = file.getAbsolutePath();
        if (!absolutePath.startsWith(rootPath))
            return null;

        String relativePath = absolutePath.substring(rootPath.length());
        if (relativePath.startsWith(File.separator))
            relativePath = relativePath.substring(1);

        try {
            String documentId = "primary:" + relativePath.replace(File.separatorChar, '/');
            Uri documentUri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentId);
            return context.getContentResolver().openFileDescriptor(documentUri, "r");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ParcelFileDescriptor openFileDescriptorViaMediaStore(Context context, File file) throws FileNotFoundException {
        File externalRoot = Environment.getExternalStorageDirectory();
        if (externalRoot == null)
            return null;

        String rootPath = externalRoot.getAbsolutePath();
        String absolutePath = file.getAbsolutePath();
        if (!absolutePath.startsWith(rootPath))
            return null;

        String relativePath = absolutePath.substring(rootPath.length());
        if (relativePath.startsWith(File.separator))
            relativePath = relativePath.substring(1);

        int lastSlash = relativePath.lastIndexOf('/');
        String parent = lastSlash >= 0 ? relativePath.substring(0, lastSlash + 1) : "";
        String name = lastSlash >= 0 ? relativePath.substring(lastSlash + 1) : relativePath;

        Uri filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        Cursor cursor = null;
        try {
            String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?";
            String[] selectionArgs = new String[]{parent, name};
            cursor = context.getContentResolver().query(filesUri, new String[]{MediaStore.MediaColumns._ID}, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                Uri contentUri = ContentUris.withAppendedId(filesUri, id);
                return context.getContentResolver().openFileDescriptor(contentUri, "r");
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return null;
    }

    static void cleanupPreviousMaterialization(File previousTemp, File currentTemp, File cacheRoot) {
        if (previousTemp == null || cacheRoot == null)
            return;
        if (currentTemp != null && previousTemp.equals(currentTemp))
            return;
        try {
            if (isChildOf(previousTemp, cacheRoot)) {
                File parent = previousTemp.getParentFile();
                if (parent != null)
                    deleteRecursively(parent);
                else
                    previousTemp.delete();
            }
        } catch (Exception ignore) {
        }
    }

    private static void pruneOldCacheDirs(File cacheRoot, File activeDir, long now) {
        if (cacheRoot == null || !cacheRoot.exists())
            return;
        File[] children = cacheRoot.listFiles();
        if (children == null)
            return;
        for (File child : children) {
            if (child == null || !child.isDirectory())
                continue;
            if (activeDir != null && child.equals(activeDir))
                continue;
            long age = now - child.lastModified();
            if (age > CACHE_PRUNE_THRESHOLD_MS) {
                deleteRecursively(child);
            }
        }
    }

    static void deleteRecursively(File fileOrDir) {
        if (fileOrDir == null || !fileOrDir.exists())
            return;
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        fileOrDir.delete();
    }

    static void copyStream(InputStream input, OutputStream output) throws java.io.IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    static <T extends Context & TemporaryUriPermission.TemporaryUriPermissionProvider> boolean canReadFromUri(T context, Uri uri) {
        boolean haveReadPermissionToUri = false;
        try {
            if (Build.VERSION.SDK_INT >= 19) {
                for (UriPermission permission : (context).getContentResolver().getPersistedUriPermissions()) {
                    if (permission.isReadPermission() && permission.getUri().equals(uri)) {
                        haveReadPermissionToUri = true;
                        break;
                    }
                }
            }

            if (!haveReadPermissionToUri) {
                if (context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED)
                    haveReadPermissionToUri = true;
            }
        } catch (Exception e) {
//            Log.i(context.getString(R.string.app_name), "exception while trying to figure out permissions: "+e);
            return false;
        }

        if (!haveReadPermissionToUri && uri.toString().startsWith("file://")) {
            File file = new File(Uri.decode(uri.getEncodedPath()));
            if (file.isFile() && file.isFile() && file.canRead())
                haveReadPermissionToUri = true;
        }
        return haveReadPermissionToUri;
    }

    static String getFileName(Context context, Uri uri) {
        String displayName = null;
        if (uri.toString().startsWith("content://")) //Uri points to a content provider
        {
            Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null); //This should be done asynchonously

            if (cursor != null && cursor.moveToFirst()) {
                //Try to get the display name/title
                int displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                if (displayNameIndex >= 0) displayName = cursor.getString(displayNameIndex);
                if (displayName == null) {
                    int titleIndex = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE);
                    if (titleIndex >= 0) displayName = cursor.getString(titleIndex);
                }
                cursor.close();
            }

            //Some programms encode parts of the filename in utf-8 base 64 encoding if the filename contains special charcters. This can look like this: '=?UTF-8?B?[text here]==?=' Here we decode such cases:
            if (displayName != null) {
                Pattern utf8BPattern = Pattern.compile("=\\?UTF-8\\?B\\?(.+)\\?=");
                Matcher matcher = utf8BPattern.matcher(displayName);
                while (matcher.find()) {
                    String base64 = matcher.group(1);
                    byte[] data = Base64.decode(base64, Base64.DEFAULT);
                    String decodedText = "";
                    try {
                        decodedText = new String(data, "UTF-8");
                    } catch (Exception e) {
                    }
                    displayName = displayName.replace(matcher.group(), decodedText);
                }
            }
        } else {
            File file = new File(Uri.decode(uri.getEncodedPath()));
            if (file.isFile())
                displayName = file.getName();
        }

        if (displayName == null || displayName.equals(""))
            displayName = context.getString(R.string.unknown_file_name);

        return displayName;
    }
}

