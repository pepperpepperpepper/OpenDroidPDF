package org.opendroidpdf.app.notes;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFCore;

/**
 * Handles note/document deletion and restart flow, extracted from the activity
 * to reduce monolithic code and centralize note operations.
 */
public final class NotesController {

    public interface Host {
        @Nullable OpenDroidPDFCore getCore();
        @NonNull Context getContext();
        void restartToDashboard();
    }

    private final Host host;

    public NotesController(@NonNull Host host) {
        this.host = host;
    }

    public void requestDeleteNote() {
        OpenDroidPDFCore core = host.getCore();
        if (core == null) return;
        try {
            core.deleteDocument(host.getContext());
        } catch (Throwable ignore) {
            // best-effort: even if deletion fails, proceed with restart
        }
        host.restartToDashboard();
    }
}

