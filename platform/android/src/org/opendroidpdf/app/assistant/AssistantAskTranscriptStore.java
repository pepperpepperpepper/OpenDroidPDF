package org.opendroidpdf.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory transcript store for Assistant Ask mode, keyed by the current document session.
 *
 * <p>This intentionally does not persist to disk.</p>
 */
final class AssistantAskTranscriptStore {
    static final class Message {
        @NonNull final String text;
        final boolean isUser;
        @Nullable final int[] citationNumbers;
        @Nullable final int[] citationPages1Based;
        @Nullable final String[] relatedQuestions;

        Message(@NonNull String text,
                boolean isUser,
                @Nullable int[] citationNumbers,
                @Nullable int[] citationPages1Based,
                @Nullable String[] relatedQuestions) {
            this.text = text != null ? text : "";
            this.isUser = isUser;
            this.citationNumbers = citationNumbers;
            this.citationPages1Based = citationPages1Based;
            this.relatedQuestions = relatedQuestions;
        }
    }

    private static final int MAX_SESSIONS = 8;
    private static final int MAX_MESSAGES_PER_SESSION = 200;

    private static final class Session {
        final ArrayList<Message> messages = new ArrayList<>();
        long version = 1;
    }

    private static final LinkedHashMap<String, Session> sessions =
            new LinkedHashMap<String, Session>(MAX_SESSIONS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
                    return size() > MAX_SESSIONS;
                }
            };

    private AssistantAskTranscriptStore() {}

    static synchronized boolean hasMessages(@NonNull String documentKey) {
        Session s = sessions.get(documentKey);
        return s != null && !s.messages.isEmpty();
    }

    @NonNull
    static synchronized List<Message> snapshot(@NonNull String documentKey) {
        Session s = sessions.get(documentKey);
        if (s == null || s.messages.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(s.messages);
    }

    static synchronized long appendUser(@NonNull String documentKey, @NonNull String text) {
        Session s = sessionForKey(documentKey);
        appendLocked(s, new Message(text, true, null, null, null));
        return s.version;
    }

    static synchronized long appendAssistant(@NonNull String documentKey,
                                            @NonNull String text,
                                            @Nullable int[] citationNumbers,
                                            @Nullable int[] citationPages1Based,
                                            @Nullable String[] relatedQuestions) {
        Session s = sessionForKey(documentKey);
        int[] nums = cloneIntArray(citationNumbers);
        int[] pages = cloneIntArray(citationPages1Based);
        if (nums != null && pages != null && nums.length != pages.length) {
            nums = null;
            pages = null;
        }
        String[] related = cloneStringArray(relatedQuestions);
        if (related != null && related.length == 0) related = null;
        appendLocked(s, new Message(text, false, nums, pages, related));
        return s.version;
    }

    static synchronized void clear(@NonNull String documentKey) {
        Session s = sessionForKey(documentKey);
        s.messages.clear();
        s.version++;
        if (s.version <= 0) s.version = 1;
    }

    static synchronized boolean isVersion(@NonNull String documentKey, long version) {
        Session s = sessions.get(documentKey);
        return s != null && s.version == version;
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

    private static void appendLocked(@NonNull Session session, @NonNull Message msg) {
        session.messages.add(msg);
        int extra = session.messages.size() - MAX_MESSAGES_PER_SESSION;
        if (extra > 0) {
            session.messages.subList(0, extra).clear();
        }
    }

    @Nullable
    private static int[] cloneIntArray(@Nullable int[] arr) {
        if (arr == null) return null;
        int[] copy = new int[arr.length];
        System.arraycopy(arr, 0, copy, 0, arr.length);
        return copy;
    }

    @Nullable
    private static String[] cloneStringArray(@Nullable String[] arr) {
        if (arr == null) return null;
        String[] copy = new String[arr.length];
        System.arraycopy(arr, 0, copy, 0, arr.length);
        return copy;
    }
}
