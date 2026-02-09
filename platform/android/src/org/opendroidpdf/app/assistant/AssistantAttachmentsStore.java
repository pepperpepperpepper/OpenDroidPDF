package org.opendroidpdf.app.assistant;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory attachments store for Assistant multi-doc context, keyed by the current document session.
 *
 * <p>This intentionally does not persist to disk.</p>
 */
final class AssistantAttachmentsStore {
    static final class Attachment {
        @NonNull final String uriString;
        @NonNull final String displayName;

        Attachment(@NonNull String uriString, @NonNull String displayName) {
            this.uriString = uriString != null ? uriString : "";
            this.displayName = displayName != null ? displayName : "";
        }

        @NonNull
        Uri uri() { return Uri.parse(uriString); }
    }

    private static final int MAX_SESSIONS = 8;
    private static final int MAX_ATTACHMENTS_PER_SESSION = 8;

    private static final class Session {
        final ArrayList<Attachment> attachments = new ArrayList<>();
    }

    private static final LinkedHashMap<String, Session> sessions =
            new LinkedHashMap<String, Session>(MAX_SESSIONS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
                    return size() > MAX_SESSIONS;
                }
            };

    private AssistantAttachmentsStore() {}

    @NonNull
    static synchronized List<Attachment> snapshot(@NonNull String documentKey) {
        Session s = sessions.get(documentKey);
        if (s == null || s.attachments.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(s.attachments);
    }

    static synchronized int count(@NonNull String documentKey) {
        Session s = sessions.get(documentKey);
        return s != null ? s.attachments.size() : 0;
    }

    static synchronized boolean add(@NonNull String documentKey,
                                    @NonNull Uri uri,
                                    @Nullable String displayNameOrNull) {
        if (uri == null) return false;
        String uriString = uri.toString();
        if (uriString == null || uriString.trim().isEmpty()) return false;
        String name = displayNameOrNull != null ? displayNameOrNull.trim() : "";
        if (name.isEmpty()) name = uriString;

        Session s = sessionForKey(documentKey);
        for (int i = 0; i < s.attachments.size(); i++) {
            Attachment a = s.attachments.get(i);
            if (a != null && uriString.equals(a.uriString)) return false;
        }
        if (s.attachments.size() >= MAX_ATTACHMENTS_PER_SESSION) return false;
        s.attachments.add(new Attachment(uriString, name));
        return true;
    }

    static synchronized void remove(@NonNull String documentKey, @NonNull Uri uri) {
        if (uri == null) return;
        String uriString = uri.toString();
        if (uriString == null || uriString.trim().isEmpty()) return;
        Session s = sessions.get(documentKey);
        if (s == null || s.attachments.isEmpty()) return;
        for (int i = s.attachments.size() - 1; i >= 0; i--) {
            Attachment a = s.attachments.get(i);
            if (a != null && uriString.equals(a.uriString)) {
                s.attachments.remove(i);
            }
        }
    }

    static synchronized void clear(@NonNull String documentKey) {
        Session s = sessions.get(documentKey);
        if (s == null) return;
        s.attachments.clear();
    }

    @NonNull
    private static Session sessionForKey(@NonNull String documentKey) {
        Session s = sessions.get(documentKey);
        if (s == null) {
            s = new Session();
            sessions.put(documentKey, s);
        }
        return s;
    }
}

