package org.opendroidpdf.app.util;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.IOException;

public final class PathUtils {
    private PathUtils() {}

    public static boolean isUriInAppPrivateStorage(Context ctx, Uri uri) {
        if (uri == null) return false;
        if (!"file".equalsIgnoreCase(uri.getScheme())) return false;
        String path = uri.getPath();
        if (path == null) return false;
        return isPathWithinDir(path, ctx.getFilesDir())
                || isPathWithinDir(path, ctx.getCacheDir())
                || isPathWithinDir(path, ctx.getNoBackupFilesDir())
                || isPathWithinDir(path, ctx.getExternalFilesDir(null))
                || isPathWithinDir(path, ctx.getExternalCacheDir());
    }

    public static boolean isPathWithinDir(String path, File dir) {
        if (path == null || dir == null) return false;
        try {
            File target = new File(path).getCanonicalFile();
            File root = dir.getCanonicalFile();
            String rootPath = root.getPath();
            return target.getPath().equals(rootPath) || target.getPath().startsWith(rootPath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }
}

