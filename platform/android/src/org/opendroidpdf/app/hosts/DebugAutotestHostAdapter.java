package org.opendroidpdf.app.hosts;

import android.content.Context;

import androidx.annotation.NonNull;

import org.opendroidpdf.DebugAutotestRunner;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.app.services.PenPreferencesService;

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
    @NonNull @Override public PenPreferencesService penPreferences() {
        org.opendroidpdf.app.lifecycle.ActivityComposition.Composition comp = activity.getComposition();
        if (comp != null && comp.penPreferences != null) return comp.penPreferences;
        return org.opendroidpdf.app.AppServices.init(activity.getApplication()).penPreferences();
    }
    @Override public void onSharedPreferenceChanged(@NonNull String key) {
        org.opendroidpdf.app.lifecycle.ActivityComposition.Composition comp = activity.getComposition();
        if (comp != null && comp.preferencesCoordinator != null) {
            comp.preferencesCoordinator.refreshAndApply();
        }
    }
    @Override public void commitPendingInkToCoreBlocking() { activity.commitPendingInkToCoreBlocking(); }
    @Override public boolean isAutoTestRan() { return activity.isAutoTestRanFlag(); }
    @Override public void markAutoTestRan() { activity.markAutoTestRanFlag(); }
    @NonNull @Override public String appName() { return activity.getString(org.opendroidpdf.R.string.app_name); }
}
