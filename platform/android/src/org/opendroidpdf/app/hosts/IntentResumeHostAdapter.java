package org.opendroidpdf.app.hosts;

import android.content.Intent;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.helpers.IntentResumeDelegate;

/** Adapter for {@link IntentResumeDelegate.Host} delegating to the activity. */
public final class IntentResumeHostAdapter implements IntentResumeDelegate.Host {
    private final OpenDroidPDFActivity activity;

    public IntentResumeHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Override
    public void invalidateOptionsMenuSafely() {
        activity.invalidateOptionsMenuSafely();
    }

    @Override
    public void setIntent(Intent intent) {
        activity.setIntent(intent);
    }
}

