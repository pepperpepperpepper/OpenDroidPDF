package org.opendroidpdf.app.hosts;

import android.content.Intent;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.util.TempUriPermissionDelegate;
import org.opendroidpdf.TemporaryUriPermission;

import java.util.ArrayList;

/** Adapter so temp URI permission bookkeeping stays out of the activity surface. */
public final class TempUriPermissionHostAdapter {
    private final TempUriPermissionDelegate delegate;

    public TempUriPermissionHostAdapter(@NonNull TempUriPermissionDelegate delegate) {
        this.delegate = delegate;
    }

    public ArrayList<TemporaryUriPermission> list() { return delegate.list(); }
    public void remember(Intent intent) { if (intent != null) delegate.remember(intent); }
}
