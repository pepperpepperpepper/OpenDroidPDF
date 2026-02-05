package org.opendroidpdf.app.readaloud;

import android.content.SharedPreferences;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.Adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.TextProcessor;
import org.opendroidpdf.TextWord;
import org.opendroidpdf.app.preferences.PreferencesNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Best-effort Read aloud controller powered by Android's TextToSpeech.
 *
 * <p>Reads line-by-line so we can follow/highlight using the existing text-line data from MuPDF.</p>
 */
public final class ReadAloudController {

    public interface Host {
        @NonNull AppCompatActivity activity();
        @Nullable MuPDFReaderView docViewOrNull();
        void invalidateReadAloudUi();
        void showInfo(@NonNull String message);
    }

    private static final String UTTERANCE_PREFIX = "ra_";

    private final Host host;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable private TextToSpeech tts;
    private boolean ttsInitInProgress = false;
    private boolean ttsReady = false;
    private final ArrayList<Runnable> pendingAfterTtsReady = new ArrayList<>();

    private long sessionCounter = 0L;
    private long activeSession = -1L;

    private boolean active = false;
    private boolean playing = false;
    private boolean selectionOnly = false;
    private int startPageIndex = -1;
    private int pageIndex = -1;
    private int lineIndex = 0;
    @Nullable private List<Line> selectionLines;

    private final Runnable speakNextRunnable = new Runnable() {
        @Override public void run() {
            speakNext();
        }
    };

    private final UtteranceProgressListener progressListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {
            // no-op
        }

        @Override
        public void onDone(String utteranceId) {
            final long token = sessionTokenFromUtteranceId(utteranceId);
            mainHandler.post(() -> {
                if (!active || !playing) return;
                if (token != activeSession) return;
                lineIndex++;
                speakNext();
            });
        }

        @Override
        public void onError(String utteranceId) {
            final long token = sessionTokenFromUtteranceId(utteranceId);
            mainHandler.post(() -> {
                if (token != activeSession) return;
                stop();
                try {
                    host.showInfo(host.activity().getString(R.string.read_aloud_tts_unavailable));
                } catch (Throwable ignore) {
                }
            });
        }
    };

    public ReadAloudController(@NonNull Host host) {
        this.host = host;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPlaying() {
        return active && playing;
    }

    public void toggleFromMenu() {
        if (active) stop();
        else startFromCurrentPage();
    }

    public void togglePlayPause() {
        if (!active) {
            startFromCurrentPage();
            return;
        }
        if (playing) pause();
        else resume();
    }

    public void startFromCurrentPage() {
        start(false);
    }

    public void startFromSelection() {
        start(true);
    }

    public void pause() {
        if (!active) return;
        playing = false;
        stopTts();
        invalidateUi();
    }

    public void resume() {
        if (!active) return;
        playing = true;
        invalidateUi();
        speakNext();
    }

    public void stop() {
        active = false;
        playing = false;
        selectionOnly = false;
        selectionLines = null;
        startPageIndex = -1;
        pageIndex = -1;
        lineIndex = 0;
        activeSession = ++sessionCounter;
        mainHandler.removeCallbacks(speakNextRunnable);

        stopTts();
        clearHighlight();
        invalidateUi();
    }

    public void shutdown() {
        stop();
        TextToSpeech t = tts;
        tts = null;
        ttsReady = false;
        ttsInitInProgress = false;
        pendingAfterTtsReady.clear();
        if (t != null) {
            try { t.shutdown(); } catch (Throwable ignore) {}
        }
    }

    private void start(boolean preferSelection) {
        ensureTtsReady(() -> startInternal(preferSelection));
    }

    private void startInternal(boolean preferSelection) {
        MuPDFReaderView docView = host.docViewOrNull();
        if (docView == null) return;

        activeSession = ++sessionCounter;
        active = true;
        playing = true;
        selectionOnly = preferSelection;
        pageIndex = docView.getSelectedItemPosition();
        startPageIndex = pageIndex;
        lineIndex = 0;
        selectionLines = null;

        if (preferSelection) {
            List<Line> lines = buildSelectionLines(docView);
            if (lines == null || lines.isEmpty()) {
                stop();
                try { host.showInfo(host.activity().getString(R.string.no_text_selected)); } catch (Throwable ignore) {}
                return;
            }
            selectionLines = lines;
        }

        invalidateUi();
        speakNext();
    }

    private void speakNext() {
        if (!active || !playing) return;

        MuPDFReaderView docView = host.docViewOrNull();
        if (docView == null) {
            stop();
            return;
        }

        if (selectionOnly) {
            List<Line> lines = selectionLines;
            if (lines == null || lines.isEmpty()) {
                stop();
                return;
            }
            if (lineIndex >= lines.size()) {
                stop();
                return;
            }
            Line line = lines.get(lineIndex);
            ensureDisplayedPage(docView, line.pageIndex);
            speakLine(docView, line.pageIndex, line.text, line.boxes, line.bounds);
            return;
        }

        Adapter adapter = docView.getAdapter();
        int pageCount = adapter != null ? adapter.getCount() : 0;
        if (pageCount <= 0) {
            stop();
            return;
        }
        if (pageIndex < 0 || pageIndex >= pageCount) {
            stop();
            return;
        }

        if (!ensureDisplayedPage(docView, pageIndex)) {
            scheduleSpeakNext(60L);
            return;
        }

        View v = docView.getSelectedView();
        if (!(v instanceof MuPDFPageView)) {
            scheduleSpeakNext(60L);
            return;
        }
        MuPDFPageView pv = (MuPDFPageView) v;
        if (pv.pageNumber() != pageIndex) {
            scheduleSpeakNext(60L);
            return;
        }

        TextWord[][] lines = pv.textLines();
        if (lines.length == 0) {
            if (pageIndex == startPageIndex) {
                stop();
                try { host.showInfo(host.activity().getString(R.string.read_aloud_no_text)); } catch (Throwable ignore) {}
            } else {
                advanceToNextPage(docView);
            }
            return;
        }

        int safety = 0;
        while (lineIndex < lines.length && safety++ < 400) {
            LineData data = lineDataForWords(lines[lineIndex]);
            if (data != null) {
                speakLine(docView, pageIndex, data.text, data.boxes, data.bounds);
                return;
            }
            lineIndex++;
        }

        if (pageIndex == startPageIndex) {
            stop();
            try { host.showInfo(host.activity().getString(R.string.read_aloud_no_text)); } catch (Throwable ignore) {}
            return;
        }
        advanceToNextPage(docView);
    }

    private void advanceToNextPage(@NonNull MuPDFReaderView docView) {
        Adapter adapter = docView.getAdapter();
        int pageCount = adapter != null ? adapter.getCount() : 0;
        if (pageCount <= 0) {
            stop();
            return;
        }
        if (pageIndex + 1 >= pageCount) {
            stop();
            return;
        }
        pageIndex++;
        lineIndex = 0;
        docView.setDisplayedViewIndex(pageIndex, true);
        scheduleSpeakNext(80L);
    }

    private void scheduleSpeakNext(long delayMs) {
        mainHandler.removeCallbacks(speakNextRunnable);
        mainHandler.postDelayed(speakNextRunnable, delayMs);
    }

    private boolean ensureDisplayedPage(@NonNull MuPDFReaderView docView, int pageIndex) {
        if (docView.getSelectedItemPosition() == pageIndex) return true;
        try { docView.setDisplayedViewIndex(pageIndex, true); } catch (Throwable ignore) {}
        return false;
    }

    private void speakLine(@NonNull MuPDFReaderView docView,
                           int pageIndex,
                           @NonNull String text,
                           @NonNull RectF[] boxes,
                           @NonNull RectF bounds) {
        TextToSpeech t = tts;
        if (t == null || !ttsReady) {
            stop();
            try {
                host.showInfo(host.activity().getString(R.string.read_aloud_tts_unavailable));
            } catch (Throwable ignore) {
            }
            return;
        }

        // Follow/highlight.
        try { docView.setReadAloudHighlight(pageIndex, boxes); } catch (Throwable ignore) {}
        try {
            docView.doNextScrollWithCenter();
            docView.setDocRelXScroll(bounds.centerX());
            docView.setDocRelYScroll(bounds.centerY());
            docView.resetupChildren();
        } catch (Throwable ignore) {
        }

        final String utteranceId = utteranceId(activeSession, pageIndex, lineIndex);
        try {
            Bundle params = new Bundle();
            t.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        } catch (Throwable ttsError) {
            stop();
            try { host.showInfo(host.activity().getString(R.string.read_aloud_tts_unavailable)); } catch (Throwable ignore) {}
        }
    }

    private void clearHighlight() {
        MuPDFReaderView doc = host.docViewOrNull();
        if (doc == null) return;
        try { doc.clearReadAloudHighlight(); } catch (Throwable ignore) {}
    }

    private void stopTts() {
        TextToSpeech t = tts;
        if (t == null) return;
        try { t.stop(); } catch (Throwable ignore) {}
    }

    private void invalidateUi() {
        try { host.invalidateReadAloudUi(); } catch (Throwable ignore) {}
    }

    private void ensureTtsReady(@NonNull Runnable onReady) {
        if (tts != null && ttsReady) {
            onReady.run();
            return;
        }
        pendingAfterTtsReady.add(onReady);
        if (ttsInitInProgress) return;

        final AppCompatActivity activity = host.activity();
        if (activity == null) {
            pendingAfterTtsReady.clear();
            return;
        }

        ttsInitInProgress = true;
        try {
            tts = new TextToSpeech(activity, status -> mainHandler.post(() -> {
                ttsInitInProgress = false;
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    ttsReady = false;
                    pendingAfterTtsReady.clear();
                    try { host.showInfo(activity.getString(R.string.read_aloud_tts_unavailable)); } catch (Throwable ignore) {}
                    return;
                }
                ttsReady = true;
                try { tts.setOnUtteranceProgressListener(progressListener); } catch (Throwable ignore) {}
                try { tts.setSpeechRate(loadTtsRate(activity)); } catch (Throwable ignore) {}
                try { tts.setLanguage(Locale.getDefault()); } catch (Throwable ignore) {}

                ArrayList<Runnable> toRun = new ArrayList<>(pendingAfterTtsReady);
                pendingAfterTtsReady.clear();
                for (Runnable r : toRun) {
                    try { r.run(); } catch (Throwable ignore) {}
                }
            }));
        } catch (Throwable t) {
            ttsInitInProgress = false;
            ttsReady = false;
            pendingAfterTtsReady.clear();
            try { host.showInfo(activity.getString(R.string.read_aloud_tts_unavailable)); } catch (Throwable ignore) {}
        }
    }

    private static float loadTtsRate(@NonNull AppCompatActivity activity) {
        if (activity == null) return 1.0f;
        try {
            SharedPreferences prefs =
                    activity.getSharedPreferences(PreferencesNames.CURRENT, AppCompatActivity.MODE_MULTI_PROCESS);
            String raw = null;
            try { raw = prefs.getString(SettingsActivity.PREF_TTS_RATE, "1.0"); } catch (ClassCastException e) {
                try { prefs.edit().remove(SettingsActivity.PREF_TTS_RATE).apply(); } catch (Throwable ignore) {}
                raw = "1.0";
            }
            if (raw == null || raw.trim().isEmpty()) raw = "1.0";
            float rate = Float.parseFloat(raw.trim());
            if (rate < 0.1f) rate = 0.1f;
            if (rate > 3.0f) rate = 3.0f;
            return rate;
        } catch (Throwable ignore) {
            return 1.0f;
        }
    }

    @Nullable
    private static LineData lineDataForWords(@Nullable TextWord[] words) {
        if (words == null || words.length == 0) return null;
        ArrayList<RectF> boxes = new ArrayList<>(words.length);
        StringBuilder sb = new StringBuilder();
        RectF bounds = null;
        for (TextWord w : words) {
            if (w == null || w.w == null) continue;
            String s = w.w.trim();
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(s);
            RectF r = new RectF(w);
            r.inset(-1f, -1f);
            boxes.add(r);
            if (bounds == null) bounds = new RectF(r);
            else bounds.union(r);
        }
        if (sb.length() == 0 || bounds == null || boxes.isEmpty()) return null;
        return new LineData(sb.toString(), boxes.toArray(new RectF[0]), bounds);
    }

    @Nullable
    private static List<Line> buildSelectionLines(@NonNull MuPDFReaderView docView) {
        View v = docView.getSelectedView();
        if (!(v instanceof MuPDFPageView)) return null;
        MuPDFPageView pv = (MuPDFPageView) v;
        if (!pv.hasSelection()) return null;

        ArrayList<Line> out = new ArrayList<>();
        pv.processSelectedText(new TextProcessor() {
            final StringBuilder sb = new StringBuilder();
            final ArrayList<RectF> boxes = new ArrayList<>();
            RectF bounds = null;

            @Override public void onStartLine() {
                sb.setLength(0);
                boxes.clear();
                bounds = null;
            }

            @Override public void onWord(TextWord word) {
                if (word == null || word.w == null) return;
                String s = word.w.trim();
                if (s.isEmpty()) return;
                if (sb.length() > 0) sb.append(' ');
                sb.append(s);
                RectF r = new RectF(word);
                r.inset(-1f, -1f);
                boxes.add(r);
                if (bounds == null) bounds = new RectF(r);
                else bounds.union(r);
            }

            @Override public void onEndLine() {
                if (sb.length() == 0 || bounds == null || boxes.isEmpty()) return;
                out.add(new Line(
                        pv.pageNumber(),
                        sb.toString(),
                        boxes.toArray(new RectF[0]),
                        bounds));
            }

            @Override public void onEndText() {}
        });
        return out;
    }

    private static String utteranceId(long session, int pageIndex, int lineIndex) {
        return UTTERANCE_PREFIX + session + "_" + pageIndex + "_" + lineIndex;
    }

    private static long sessionTokenFromUtteranceId(@Nullable String utteranceId) {
        if (utteranceId == null) return -1L;
        if (!utteranceId.startsWith(UTTERANCE_PREFIX)) return -1L;
        int start = UTTERANCE_PREFIX.length();
        int end = utteranceId.indexOf('_', start);
        if (end <= start) return -1L;
        try {
            return Long.parseLong(utteranceId.substring(start, end));
        } catch (Throwable ignore) {
            return -1L;
        }
    }

    private static final class LineData {
        @NonNull final String text;
        @NonNull final RectF[] boxes;
        @NonNull final RectF bounds;

        LineData(@NonNull String text, @NonNull RectF[] boxes, @NonNull RectF bounds) {
            this.text = text;
            this.boxes = boxes;
            this.bounds = bounds;
        }
    }

    private static final class Line {
        final int pageIndex;
        @NonNull final String text;
        @NonNull final RectF[] boxes;
        @NonNull final RectF bounds;

        Line(int pageIndex, @NonNull String text, @NonNull RectF[] boxes, @NonNull RectF bounds) {
            this.pageIndex = pageIndex;
            this.text = text;
            this.boxes = boxes;
            this.bounds = bounds;
        }
    }
}
