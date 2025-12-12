package org.opendroidpdf.app.hosts;

import android.content.Intent;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.notes.NotesController;

/**
 * Host adapter for NotesController.
 */
public class NotesHostAdapter implements NotesController.Host {
    private final OpenDroidPDFActivity activity;

    public NotesHostAdapter(OpenDroidPDFActivity activity) { this.activity = activity; }

    @Override public org.opendroidpdf.OpenDroidPDFCore getCore() { return activity.getCore(); }
    @Override public android.content.Context getContext() { return activity; }
    @Override public void restartToDashboard() {
        Intent restartIntent = new Intent(activity, OpenDroidPDFActivity.class);
        restartIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        restartIntent.setAction(Intent.ACTION_MAIN);
        activity.startActivity(restartIntent);
        activity.finish();
    }
}

