package org.opendroidpdf.app.diagnostics;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Tracks whether the prior run ended cleanly and whether a document was opened.
 * Used to prompt users to share logs when a device-only crash can't be reproduced.
 */
public final class SessionDiagnostics {
    private static final String PREFS = "odp.session_diagnostics";
    private static final String KEY_CLEAN_EXIT = "clean_exit";
    private static final String KEY_DOC_OPENED = "doc_opened";

    public static final class PreviousSession {
        public final boolean cleanExit;
        public final boolean docOpened;

        PreviousSession(boolean cleanExit, boolean docOpened) {
            this.cleanExit = cleanExit;
            this.docOpened = docOpened;
        }
    }

    private SessionDiagnostics() {}

    @Nullable
    public static PreviousSession beginNewSession(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean prevClean = prefs.getBoolean(KEY_CLEAN_EXIT, true);
        boolean prevDocOpened = prefs.getBoolean(KEY_DOC_OPENED, false);
        prefs.edit()
                .putBoolean(KEY_CLEAN_EXIT, false)
                .putBoolean(KEY_DOC_OPENED, false)
                .apply();
        return new PreviousSession(prevClean, prevDocOpened);
    }

    public static void markDocumentOpened(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DOC_OPENED, true)
                .apply();
    }

    public static void markCleanExit(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CLEAN_EXIT, true)
                .apply();
    }
}

