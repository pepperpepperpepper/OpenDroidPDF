package org.opendroidpdf.app.helpers;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.opendroidpdf.R;

/** Small helper to centralize persistable URI permission handling. */
public final class UriPermissionHelper {
    private UriPermissionHelper() {}

    public static void tryTakePersistablePermissions(Context context, Uri uri) {
        if (context == null || uri == null) return;
        if (android.os.Build.VERSION.SDK_INT < 19) return;
        ContentResolver cr = context.getContentResolver();
        try {
            cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignore) {
        } finally {
            try {
                cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) {
                try {
                    Log.i(context.getString(R.string.app_name),
                          "Failed to take persistable write uri permissions for "+uri+" Exception: "+e);
                } catch (Throwable ignore2) {}
            }
        }
    }
}

