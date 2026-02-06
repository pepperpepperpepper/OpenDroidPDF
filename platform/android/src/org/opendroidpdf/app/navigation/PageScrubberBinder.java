package org.opendroidpdf.app.navigation;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.SystemClock;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFCore;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.MuPDFView;
import org.opendroidpdf.PageView;
import org.opendroidpdf.R;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.core.MuPdfController;

import kotlinx.coroutines.Job;

/**
 * Shared scrubber binding logic for:
 * <ul>
 *   <li>the on-page scrubber (bottom overlay)</li>
 *   <li>the Navigate &amp; View sheet page switcher</li>
 * </ul>
 *
 * <p>This centralizes the "thumbnail-only preview while dragging" behavior so future tuning
 * (pixel budget, cancel heuristics, settle behavior) is single-owned.</p>
 */
public final class PageScrubberBinder {
    private PageScrubberBinder() {}

    public interface UiUpdater {
        /** Called whenever scrubber progress changes (user-driven or programmatic). */
        void onScrubberProgress(int pageIndex, int totalPages, boolean fromUser);
    }

    public static void bind(@NonNull SeekBar seekBar,
                            @NonNull MuPDFReaderView docView,
                            int totalPages,
                            int initialPage,
                            @Nullable ImageView preview,
                            @Nullable MuPdfController controller,
                            @Nullable UiUpdater uiUpdater,
                            @Nullable Runnable onUserInteraction) {
        if (totalPages <= 1) return;
        PageScrubberBinderImpl impl = (PageScrubberBinderImpl) seekBar.getTag(R.id.page_scrubber_binder_tag);
        if (impl == null) {
            impl = new PageScrubberBinderImpl(seekBar);
            seekBar.setTag(R.id.page_scrubber_binder_tag, impl);
        }
        impl.bind(docView, totalPages, initialPage, preview, controller, uiUpdater, onUserInteraction);
    }

    /**
     * Begin a user-driven scrub session without synthesizing MotionEvents.
     *
     * <p>Used by alternate scrubber UIs (e.g. the edge tab) to drive the same underlying
     * SeekBar binder logic while avoiding per-move allocations and width quantization.</p>
     */
    public static void beginUserScrub(@NonNull SeekBar seekBar, int startPageIndex) {
        PageScrubberBinderImpl impl = (PageScrubberBinderImpl) seekBar.getTag(R.id.page_scrubber_binder_tag);
        if (impl == null) return;
        impl.beginExternalScrub(startPageIndex);
    }

    /** Update the current user-driven scrub target (must be preceded by {@link #beginUserScrub}). */
    public static void updateUserScrub(@NonNull SeekBar seekBar, int pageIndex) {
        PageScrubberBinderImpl impl = (PageScrubberBinderImpl) seekBar.getTag(R.id.page_scrubber_binder_tag);
        if (impl == null) return;
        impl.updateExternalScrub(pageIndex);
    }

    /** End a user-driven scrub session (typically on ACTION_UP). */
    public static void endUserScrub(@NonNull SeekBar seekBar, int finalPageIndex) {
        PageScrubberBinderImpl impl = (PageScrubberBinderImpl) seekBar.getTag(R.id.page_scrubber_binder_tag);
        if (impl == null) return;
        impl.endExternalScrub(finalPageIndex);
    }

    private static final class PageScrubberBinderImpl {
        private static final String TAG_SCRUB_PREVIEW = "ScrubPreview";
        private static final int SCRUB_THROTTLE_MS = 30;
        private static final long PREVIEW_MAX_PIXELS = 25_000L;
        private static final long PREVIEW_ABORT_AFTER_MS = 50L;
        private static final int PREVIEW_ABORT_DELTA_PAGES = 1;

        @NonNull private final SeekBar seekBar;

        @Nullable private MuPDFReaderView docView;
        private int totalPages = 0;
        @Nullable private UiUpdater uiUpdater;
        @Nullable private Runnable onUserInteraction;

        @Nullable private ImageView preview;
        @Nullable private MuPdfController controller;

        // Preview mode state.
        private final Object cookieLock = new Object();
        private final Object cacheLock = new Object();
        private final LruCache<Integer, Bitmap> previewCache = new LruCache<>(64);
        private boolean logPreviewMetrics = false;
        private long lastPreviewRequestAtMs = 0L;

        private int requestedTarget = -1;
        private int pendingTarget = -1;
        private int generation = 0;
        private int activeRenderGen = 0;
        private int activeRenderTarget = -1;
        private long activeRenderStartedAtMs = 0L;
        @Nullable private MuPDFCore.Cookie renderCookie = null;
        @Nullable private Job renderJob = null;

        private int settleTarget = -1;
        private int settleAttempts = 0;
        @Nullable private Runnable settleToFullRes = null;
        @Nullable private Runnable renderPreview = null;

        // Live mode state.
        private int livePendingTarget = -1;
        private long liveLastRequestUptimeMs = 0L;
        @Nullable private Runnable throttledNavigate = null;
        private int liveSettleTarget = -1;
        private int liveSettleAttempts = 0;
        @Nullable private Runnable liveSettleToFullRes = null;

        @Nullable private SeekBar.OnSeekBarChangeListener installedListener = null;

        // External driver state (e.g. the edge tab).
        private boolean externalScrubActive = false;
        private int externalScrubProgress = -1;

        private PageScrubberBinderImpl(@NonNull SeekBar seekBar) {
            this.seekBar = seekBar;
        }

        void bind(@NonNull MuPDFReaderView docView,
                  int totalPages,
                  int initialPage,
                  @Nullable ImageView preview,
                  @Nullable MuPdfController controller,
                  @Nullable UiUpdater uiUpdater,
                  @Nullable Runnable onUserInteraction) {
            if (totalPages <= 1) return;

            boolean docChanged = this.docView != docView;
            boolean controllerChanged = this.controller != controller;

            this.docView = docView;
            this.totalPages = totalPages;
            this.preview = preview;
            this.controller = controller;
            this.uiUpdater = uiUpdater;
            this.onUserInteraction = onUserInteraction;
            this.logPreviewMetrics = android.util.Log.isLoggable(TAG_SCRUB_PREVIEW, android.util.Log.DEBUG);

            if (docChanged || controllerChanged) {
                resetPreviewState();
            }

            int clampedInitial = clampPage(initialPage, totalPages);
            seekBar.setMax(Math.max(0, totalPages - 1));
            if (seekBar.getProgress() != clampedInitial) {
                seekBar.setProgress(clampedInitial);
            }

            boolean wantPreviewMode = preview != null && controller != null;
            if (wantPreviewMode) {
                ensurePreviewModeListener();
            } else {
                ensureLiveModeListener(clampedInitial);
            }
        }

        void beginExternalScrub(int startPageIndex) {
            SeekBar.OnSeekBarChangeListener l = installedListener;
            if (l == null) return;
            int start = clampPage(startPageIndex, totalPages);
            externalScrubActive = true;
            externalScrubProgress = start;
            try {
                if (seekBar.getProgress() != start) seekBar.setProgress(start);
            } catch (Throwable ignore) {}
            try { l.onStartTrackingTouch(seekBar); } catch (Throwable ignore) {}
            try { l.onProgressChanged(seekBar, start, true); } catch (Throwable ignore) {}
        }

        void updateExternalScrub(int pageIndex) {
            SeekBar.OnSeekBarChangeListener l = installedListener;
            if (l == null) return;
            int clamped = clampPage(pageIndex, totalPages);
            if (!externalScrubActive) {
                beginExternalScrub(clamped);
                return;
            }
            if (externalScrubProgress == clamped) return;
            externalScrubProgress = clamped;
            try { l.onProgressChanged(seekBar, clamped, true); } catch (Throwable ignore) {}
        }

        void endExternalScrub(int finalPageIndex) {
            SeekBar.OnSeekBarChangeListener l = installedListener;
            if (l == null) return;
            int end = clampPage(finalPageIndex, totalPages);
            externalScrubActive = false;
            externalScrubProgress = end;
            try {
                if (seekBar.getProgress() != end) seekBar.setProgress(end);
            } catch (Throwable ignore) {}
            try { l.onStopTrackingTouch(seekBar); } catch (Throwable ignore) {}
        }

        private void ensurePreviewModeListener() {
            if (installedListener instanceof PreviewModeListener) return;
            cancelLiveModeCallbacks();
            resetPreviewState();
            installedListener = new PreviewModeListener();
            seekBar.setOnSeekBarChangeListener(installedListener);
        }

        private void ensureLiveModeListener(int initialPage) {
            if (installedListener instanceof LiveModeListener) return;
            cancelPreviewRendersAndCallbacks(false);
            cancelLiveModeCallbacks();
            installedListener = new LiveModeListener(initialPage);
            seekBar.setOnSeekBarChangeListener(installedListener);
        }

        private void resetPreviewState() {
            cancelPreviewRendersAndCallbacks(true);
            previewCache.evictAll();
            requestedTarget = -1;
            pendingTarget = -1;
            generation++;
            activeRenderGen = 0;
            activeRenderTarget = -1;
            activeRenderStartedAtMs = 0L;
            settleTarget = -1;
            settleAttempts = 0;
            renderPreview = null;
            settleToFullRes = null;
            try {
                if (preview != null) preview.setVisibility(android.view.View.GONE);
            } catch (Throwable ignore) {}
        }

        private void cancelLiveModeCallbacks() {
            try { seekBar.removeCallbacks(throttledNavigate); } catch (Throwable ignore) {}
            try { seekBar.removeCallbacks(liveSettleToFullRes); } catch (Throwable ignore) {}
            livePendingTarget = -1;
            liveLastRequestUptimeMs = 0L;
            liveSettleTarget = -1;
            liveSettleAttempts = 0;
        }

        private void cancelPreviewRendersAndCallbacks(boolean abortCookie) {
            try { seekBar.removeCallbacks(settleToFullRes); } catch (Throwable ignore) {}
            generation++;

            if (abortCookie) {
                synchronized (cookieLock) {
                    MuPDFCore.Cookie cookie = renderCookie;
                    if (cookie != null) {
                        try { cookie.abort(); } catch (Throwable ignore) {}
                    }
                }
            }
            try { AppCoroutines.cancel(renderJob); } catch (Throwable ignore) {}
            renderJob = null;
            activeRenderGen = 0;
            activeRenderTarget = -1;
            activeRenderStartedAtMs = 0L;
        }

        private void callUserInteraction() {
            try { if (onUserInteraction != null) onUserInteraction.run(); } catch (Throwable ignore) {}
        }

        private void navigateToPage(int pageIndex) {
            if (docView == null) return;
            int clamped = clampPage(pageIndex, totalPages);
            try { docView.setDisplayedViewIndex(clamped, true); } catch (Throwable ignore) {}
            try { docView.setNormalizedScroll(0.0f, 0.0f); } catch (Throwable ignore) {}
        }

        private void settleToFullResCommon(@NonNull MuPDFReaderView docView, int want) {
            try {
                android.view.View v = docView.getSelectedView();
                if (v instanceof MuPDFView) ((MuPDFView) v).redraw(true);
                if (v instanceof PageView) ((PageView) v).loadDeferredPageDataAfterScrub();
            } catch (Throwable ignore) {}
        }

        private void ensureRenderPreviewRunnable() {
            if (renderPreview != null) return;
            renderPreview = new Runnable() {
                @Override public void run() {
                    if (preview == null || controller == null) return;
                    int target = pendingTarget;
                    if (target < 0) return;

                    Bitmap cached;
                    synchronized (cacheLock) { cached = previewCache.get(target); }
                    if (cached != null) {
                        pendingTarget = -1;
                        showPreviewBitmap(target, cached, true);
                        return;
                    }

                    // Coalesce renders: if one is already in flight, keep the latest target queued.
                    try {
                        Job job = renderJob;
                        if (job != null && job.isActive()) return;
                    } catch (Throwable ignore) {}

                    pendingTarget = -1;
                    final int gen = ++generation;
                    activeRenderGen = gen;
                    final int renderTarget = target;
                    activeRenderTarget = renderTarget;
                    activeRenderStartedAtMs = SystemClock.uptimeMillis();

                    PointF size = null;
                    try { size = controller.pageSize(renderTarget); } catch (Throwable ignore) { size = null; }
                    float ratio = 1.294f;
                    if (size != null && size.x > 0f && size.y > 0f) {
                        ratio = size.y / size.x;
                    }
                    ratio = Math.max(0.15f, Math.min(8.0f, ratio));

                    int w;
                    int h;
                    try {
                        double ww = Math.sqrt((double) PREVIEW_MAX_PIXELS / (double) ratio);
                        w = Math.max(1, (int) Math.round(ww));
                        h = Math.max(1, (int) Math.round(w * ratio));
                    } catch (Throwable ignore) {
                        w = 160;
                        h = 210;
                    }
                    final int fw = w;
                    final int fh = h;

                    final MuPDFCore.Cookie cookie = controller.newRenderCookie();
                    synchronized (cookieLock) {
                        renderCookie = cookie;
                    }
                    renderJob = AppCoroutines.launchIo(AppCoroutines.ioScope(), () -> {
                        Bitmap bm = null;
                        try {
                            bm = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
                            try { bm.eraseColor(Color.WHITE); } catch (Throwable ignore) {}
                            controller.drawPage(bm, renderTarget, fw, fh, 0, 0, fw, fh, cookie);
                        } catch (Throwable ignore) {
                            bm = null;
                        } finally {
                            synchronized (cookieLock) {
                                if (renderCookie == cookie) {
                                    renderCookie = null;
                                }
                                try { cookie.destroy(); } catch (Throwable ignore) {}
                            }
                        }
                        if (cookie.aborted()) bm = null;
                        final Bitmap ready = bm;
                        try {
                            preview.post(() -> {
                                if (ready != null) {
                                    synchronized (cacheLock) { previewCache.put(renderTarget, ready); }
                                }
                                if (ready != null && generation == gen && requestedTarget == renderTarget) {
                                    showPreviewBitmap(renderTarget, ready, false);
                                }
                                try {
                                    if (activeRenderGen == gen) {
                                        renderJob = null;
                                        activeRenderGen = 0;
                                        activeRenderTarget = -1;
                                        activeRenderStartedAtMs = 0L;
                                    }
                                    if (renderPreview != null) renderPreview.run();
                                } catch (Throwable ignore) {}
                            });
                        } catch (Throwable ignore) {}
                    });
                }
            };
        }

        private void showPreviewBitmap(int renderTarget, @NonNull Bitmap bm, boolean cached) {
            if (preview == null) return;
            try {
                preview.setImageBitmap(bm);
                preview.setVisibility(android.view.View.VISIBLE);
                if (logPreviewMetrics) {
                    long dt = SystemClock.uptimeMillis() - lastPreviewRequestAtMs;
                    android.util.Log.d(TAG_SCRUB_PREVIEW,
                            "show page=" + (renderTarget + 1) + " dtMs=" + dt + " cached=" + cached);
                }
            } catch (Throwable ignore) {}
        }

        private final class PreviewModeListener implements SeekBar.OnSeekBarChangeListener {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int clamped = clampPage(progress, totalPages);
                try {
                    if (uiUpdater != null) uiUpdater.onScrubberProgress(clamped, totalPages, fromUser);
                } catch (Throwable ignore) {}
                if (!fromUser) return;

                requestedTarget = clamped;
                lastPreviewRequestAtMs = SystemClock.uptimeMillis();
                pendingTarget = clamped;

                // Abort stale renders so the preview doesn't "fall behind" on fast drags.
                try {
                    Job job = renderJob;
                    if (job != null && job.isActive()) {
                        int active = activeRenderTarget;
                        long started = activeRenderStartedAtMs;
                        long now = SystemClock.uptimeMillis();
                        if (active >= 0 && active != clamped && started > 0L) {
                            if ((now - started) >= PREVIEW_ABORT_AFTER_MS && Math.abs(clamped - active) >= PREVIEW_ABORT_DELTA_PAGES) {
                                synchronized (cookieLock) {
                                    MuPDFCore.Cookie cookie = renderCookie;
                                    if (cookie != null) {
                                        try { cookie.abort(); } catch (Throwable ignore) {}
                                    }
                                }
                                try { AppCoroutines.cancel(job); } catch (Throwable ignore) {}
                            }
                        }
                    }
                } catch (Throwable ignore) {}

                ensureRenderPreviewRunnable();
                try { if (renderPreview != null) renderPreview.run(); } catch (Throwable ignore) {}
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                callUserInteraction();
                try { if (settleToFullRes != null) seekBar.removeCallbacks(settleToFullRes); } catch (Throwable ignore) {}
                settleTarget = -1;
                settleAttempts = 0;

                MuPDFReaderView dv = docView;
                if (dv != null) {
                    try { dv.setScrubbing(true); } catch (Throwable ignore) {}
                }
                try { if (preview != null) preview.setVisibility(android.view.View.GONE); } catch (Throwable ignore) {}

                generation++; // ignore late thumbnail updates from a previous drag
                cancelPreviewRendersAndCallbacks(true);
                activeRenderGen = 0;
                activeRenderTarget = -1;
                activeRenderStartedAtMs = 0L;

                int start = clampPage(seekBar != null ? seekBar.getProgress() : 0, totalPages);
                requestedTarget = start;
                pendingTarget = start;
                lastPreviewRequestAtMs = SystemClock.uptimeMillis();

                ensureRenderPreviewRunnable();
                try { if (renderPreview != null) renderPreview.run(); } catch (Throwable ignore) {}
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                callUserInteraction();
                int target = clampPage(seekBar != null ? seekBar.getProgress() : 0, totalPages);
                try {
                    if (uiUpdater != null) uiUpdater.onScrubberProgress(target, totalPages, true);
                } catch (Throwable ignore) {}

                // Ensure preview matches the final target.
                generation++;
                cancelPreviewRendersAndCallbacks(true);
                requestedTarget = target;
                pendingTarget = target;
                lastPreviewRequestAtMs = SystemClock.uptimeMillis();
                ensureRenderPreviewRunnable();
                try { if (renderPreview != null) renderPreview.run(); } catch (Throwable ignore) {}

                settleTarget = target;
                settleAttempts = 0;

                MuPDFReaderView dv = docView;
                if (dv != null) {
                    try { dv.setScrubbing(true); } catch (Throwable ignore) {}
                }
                navigateToPage(target);

                if (settleToFullRes == null) {
                    settleToFullRes = new Runnable() {
                        @Override public void run() {
                            MuPDFReaderView dv = docView;
                            if (dv == null) return;
                            int want = settleTarget;
                            if (want < 0) return;

                            int tries = settleAttempts++;
                            if (tries > 30) {
                                settleTarget = -1;
                                try { dv.setScrubbing(false); } catch (Throwable ignore) {}
                                try { if (preview != null) preview.setVisibility(android.view.View.GONE); } catch (Throwable ignore) {}
                                cancelPreviewRendersAndCallbacks(true);
                                return;
                            }

                            int cur = -1;
                            try { cur = dv.getSelectedItemPosition(); } catch (Throwable ignore) { cur = -1; }
                            if (cur == want) {
                                settleTarget = -1;
                                try { dv.setScrubbing(false); } catch (Throwable ignore) {}
                                try { if (preview != null) preview.setVisibility(android.view.View.GONE); } catch (Throwable ignore) {}
                                generation++;
                                settleToFullResCommon(dv, want);
                                return;
                            }

                            try { seekBar.postDelayed(this, 50); } catch (Throwable ignore) {}
                        }
                    };
                }
                try { seekBar.postDelayed(settleToFullRes, 50); } catch (Throwable ignore) {}
            }
        }

        private final class LiveModeListener implements SeekBar.OnSeekBarChangeListener {
            LiveModeListener(int initialPage) {
                livePendingTarget = -1;
                liveLastRequestUptimeMs = 0L;
                liveSettleTarget = -1;
                liveSettleAttempts = 0;
                throttledNavigate = new Runnable() {
                    @Override public void run() {
                        int target = livePendingTarget;
                        if (target < 0) return;
                        livePendingTarget = -1;
                        liveLastRequestUptimeMs = SystemClock.uptimeMillis();
                        MuPDFReaderView dv = docView;
                        if (dv == null) return;
                        int cur = -1;
                        try { cur = dv.getSelectedItemPosition(); } catch (Throwable ignore) { cur = -1; }
                        if (cur == target) return;
                        try { dv.setDisplayedViewIndex(target, true); } catch (Throwable ignore) {}
                    }
                };
            }

            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int clamped = clampPage(progress, totalPages);
                try {
                    if (uiUpdater != null) uiUpdater.onScrubberProgress(clamped, totalPages, fromUser);
                } catch (Throwable ignore) {}
                if (!fromUser) return;

                livePendingTarget = clamped;
                long now = SystemClock.uptimeMillis();
                long since = now - liveLastRequestUptimeMs;
                try { seekBar.removeCallbacks(throttledNavigate); } catch (Throwable ignore) {}
                if (since >= SCRUB_THROTTLE_MS) {
                    try { if (throttledNavigate != null) throttledNavigate.run(); } catch (Throwable ignore) {}
                } else {
                    try { seekBar.postDelayed(throttledNavigate, SCRUB_THROTTLE_MS - since); } catch (Throwable ignore) {}
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                callUserInteraction();
                try { if (liveSettleToFullRes != null) seekBar.removeCallbacks(liveSettleToFullRes); } catch (Throwable ignore) {}
                liveSettleTarget = -1;
                liveSettleAttempts = 0;
                MuPDFReaderView dv = docView;
                if (dv != null) {
                    try { dv.setScrubbing(true); } catch (Throwable ignore) {}
                }
                liveLastRequestUptimeMs = 0L;
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                callUserInteraction();
                int target = clampPage(seekBar != null ? seekBar.getProgress() : 0, totalPages);
                try {
                    if (uiUpdater != null) uiUpdater.onScrubberProgress(target, totalPages, true);
                } catch (Throwable ignore) {}
                try { seekBar.removeCallbacks(throttledNavigate); } catch (Throwable ignore) {}
                livePendingTarget = target;
                try { if (throttledNavigate != null) throttledNavigate.run(); } catch (Throwable ignore) {}
                navigateToPage(target);

                liveSettleTarget = target;
                liveSettleAttempts = 0;
                if (liveSettleToFullRes == null) {
                    liveSettleToFullRes = new Runnable() {
                        @Override public void run() {
                            MuPDFReaderView dv = docView;
                            if (dv == null) return;
                            int want = liveSettleTarget;
                            if (want < 0) return;
                            int tries = liveSettleAttempts++;
                            if (tries > 30) {
                                liveSettleTarget = -1;
                                try { dv.setScrubbing(false); } catch (Throwable ignore) {}
                                return;
                            }
                            int cur = -1;
                            try { cur = dv.getSelectedItemPosition(); } catch (Throwable ignore) { cur = -1; }
                            if (cur == want) {
                                liveSettleTarget = -1;
                                try { dv.setScrubbing(false); } catch (Throwable ignore) {}
                                settleToFullResCommon(dv, want);
                                return;
                            }
                            try { seekBar.postDelayed(this, 50); } catch (Throwable ignore) {}
                        }
                    };
                }
                try { seekBar.postDelayed(liveSettleToFullRes, 50); } catch (Throwable ignore) {}
            }
        }
    }

    private static int clampPage(int pageIndex, int totalPages) {
        if (totalPages <= 0) return 0;
        if (pageIndex < 0) return 0;
        if (pageIndex > totalPages - 1) return totalPages - 1;
        return pageIndex;
    }
}
