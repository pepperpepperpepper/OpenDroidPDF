package org.opendroidpdf.app.hosts;

import android.content.Context;

import androidx.annotation.NonNull;

import org.opendroidpdf.DebugAutotestRunner;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Small adapter for DebugAutotestRunner.Host so OpenDroidPDFActivity can avoid
 * a large anonymous inner class. Keeps the activity slim.
 */
public final class DebugAutotestHostAdapter implements DebugAutotestRunner.Host {
    private final OpenDroidPDFActivity activity;
    private final MuPdfRepository repository;
    private final MuPDFReaderView docView;

    public DebugAutotestHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                    @NonNull MuPdfRepository repository,
                                    @NonNull MuPDFReaderView docView) {
        this.activity = activity;
        this.repository = repository;
        this.docView = docView;
    }

    @NonNull @Override public MuPDFReaderView getDocView() { return docView; }
    @NonNull @Override public MuPdfRepository getRepository() { return repository; }
    @NonNull @Override public Context getContext() { return activity.getApplicationContext(); }
    @Override public void onSharedPreferenceChanged(@NonNull String key) {
        activity.onSharedPreferenceChanged(
                activity.getSharedPreferences(org.opendroidpdf.SettingsActivity.SHARED_PREFERENCES_STRING,
                        android.content.Context.MODE_MULTI_PROCESS),
                key);
    }
    @Override public void commitPendingInkToCoreBlocking() { activity.commitPendingInkToCoreBlocking(); }
    @Override public boolean isAutoTestRan() { return activity.isAutoTestRanFlag(); }
    @Override public void markAutoTestRan() { activity.markAutoTestRanFlag(); }
    @NonNull @Override public String appName() { return activity.getString(org.opendroidpdf.R.string.app_name); }
}
