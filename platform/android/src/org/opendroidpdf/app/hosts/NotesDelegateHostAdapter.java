package org.opendroidpdf.app.hosts;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.notes.NotesDelegate;

import java.util.concurrent.Callable;

/** Adapter for {@link NotesDelegate.Host} that delegates to {@link OpenDroidPDFActivity}. */
public final class NotesDelegateHostAdapter implements NotesDelegate.Host {
    private final OpenDroidPDFActivity activity;

    public NotesDelegateHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @NonNull @Override public Context context() { return activity; }
    @Override public void checkSaveThenCall(@NonNull Callable<Void> callable) { activity.checkSaveThenCall(callable); }
    @Override public void startActivity(@NonNull Intent intent) { activity.startActivity(intent); }
    @Override public void overridePendingTransition(int enterAnim, int exitAnim) { activity.overridePendingTransition(enterAnim, exitAnim); }
    @Override public void hideDashboard() { activity.hideDashboard(); }
    @Override public void finish() { activity.finish(); }
}
