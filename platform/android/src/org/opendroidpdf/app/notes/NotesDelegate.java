package org.opendroidpdf.app.notes;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Encapsulates notes creation/open helpers that previously lived in the activity.
 */
public final class NotesDelegate {
    public static final String NOTES_DIR_NAME = "OpenDroidPDFNotes";
    public static final String LEGACY_NOTES_DIR_NAME = "PenAndPDFNotes";

    public interface Host {
        @NonNull Context context();
        void checkSaveThenCall(@NonNull Callable<Void> callable);
        void startActivity(@NonNull Intent intent);
        void overridePendingTransition(int enterAnim, int exitAnim);
        void hideDashboard();
        void finish();
    }

    private final Host host;

    public NotesDelegate(@NonNull Host host) {
        this.host = host;
    }

    public static File getNotesDir(Context context) {
        return org.opendroidpdf.app.storage.NotesStorage.ensureNotesDir(context, NOTES_DIR_NAME, LEGACY_NOTES_DIR_NAME);
    }

    public static File getNotesDir(Context context, String currentName, String legacyName) {
        return org.opendroidpdf.app.storage.NotesStorage.ensureNotesDir(context, currentName, legacyName);
    }

    public void openNewDocument(String filename) throws IOException {
        final Context context = host.context();
        File dir = getNotesDir(context);
        File file = new File(dir, filename);
        final Uri uri = Uri.fromFile(file);
        OpenDroidPDFCore.createEmptyDocument(context, uri);
        host.checkSaveThenCall(new Callable<Void>() {
            public Void call() {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.setPackage(context.getPackageName());
                intent.putExtra(Intent.EXTRA_TITLE, filename);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                host.startActivity(intent);
                host.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                host.hideDashboard();
                host.finish();
                return null;
            }});
    }

    public boolean isCurrentNoteDocument(@NonNull Intent intent) {
        Uri data = intent.getData();
        if (data == null) return false;
        String encodedPath = data.getEncodedPath();
        if (encodedPath == null) return false;
        File recentFile = new File(Uri.decode(encodedPath));
        File notesDir = getNotesDir(host.context());
        return notesDir != null
                && recentFile != null
                && recentFile.getAbsolutePath().startsWith(notesDir.getAbsolutePath());
    }
}
