package org.opendroidpdf.app.util;

import android.content.Intent;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import org.opendroidpdf.TemporaryUriPermission;

/**
 * Keeps TemporaryUriPermission bookkeeping out of the activity body.
 */
public final class TempUriPermissionDelegate {
    private final ArrayList<TemporaryUriPermission> permissions = new ArrayList<>();

    public ArrayList<TemporaryUriPermission> list() {
        return permissions;
    }

    public void remember(@NonNull Intent intent) {
        permissions.add(new TemporaryUriPermission(intent));
    }
}
