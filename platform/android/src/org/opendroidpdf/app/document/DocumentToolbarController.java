package org.opendroidpdf.app.document;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.Adapter;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.R;
import org.opendroidpdf.MuPDFPageAdapter;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.ui.UiUtils;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;

/**
 * Centralizes the wiring for the primary document toolbar so that the activity simply delegates
 * menu inflation/state and action handling per feature.
 */
public class DocumentToolbarController {

    public interface Host {
        boolean hasDocumentLoaded();
        boolean hasDocumentView();
        boolean isViewingNoteDocument();
        boolean isLinkBackAvailable();
        boolean areCommentsVisible();
        boolean isReadingModeEnabled();
        @NonNull androidx.appcompat.app.AppCompatActivity getActivity();
        @NonNull AlertDialog.Builder alertBuilder();
        @NonNull MuPDFReaderView getDocView();
        void requestAddBlankPage();
        void requestOrganizePages();
        void requestFullscreen();
        void requestSetReadingModeEnabled(boolean enabled);
        void requestSettings();
        void requestReadingSettings();
        void requestTableOfContents();
        void requestPrint();
        void requestShare();
        void requestSaveCopy();
        void requestShareLinearized();
        void requestShareEncrypted();
        void requestShareFlattened();
        void requestSaveLinearized();
        void requestSaveEncrypted();
        void requestImportAnnotations();
        void requestExportAnnotations();
        void requestSearchMode();
        void requestDashboard();
        void requestCommentsList();
        void requestSetCommentsVisible(boolean visible);
        void requestNavigateComment(int direction);
        void requestDeleteNote();
        void requestSaveDialog();
        void requestFillSign();
        void requestLinkBackNavigation();
    }

    private final Host host;

    public DocumentToolbarController(@NonNull Host host) {
        this.host = host;
    }

    public void showNavigateViewSheet() {
        AppCompatActivity activity = host != null ? host.getActivity() : null;
        if (activity == null) return;
        MuPDFReaderView docView = host.hasDocumentView() ? host.getDocView() : null;
        if (docView == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_navigate_view_sheet, null);
        dialog.setContentView(root);

        bindPageSwitcherControls(root, docView);

        // Navigate
        View toc = root.findViewById(R.id.navigate_view_action_toc);
        if (toc != null) {
            toc.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestTableOfContents();
            });
        }
        View gotoPage = root.findViewById(R.id.navigate_view_action_goto_page);
        if (gotoPage != null) {
            gotoPage.setOnClickListener(v -> {
                dialog.dismiss();
                org.opendroidpdf.app.dialog.Dialogs.showGoToPage(
                        activity,
                        host.alertBuilder(),
                        docView);
            });
        }

        // View
        View fullscreen = root.findViewById(R.id.navigate_view_action_fullscreen);
        if (fullscreen != null) {
            fullscreen.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestFullscreen();
            });
        }

        SwitchCompat readingModeSwitch = root.findViewById(R.id.navigate_view_switch_reading_mode);
        View readingModeRow = root.findViewById(R.id.navigate_view_row_reading_mode);
        if (readingModeRow != null && readingModeSwitch != null) {
            readingModeRow.setOnClickListener(v -> readingModeSwitch.toggle());
            try { readingModeSwitch.setChecked(host.isReadingModeEnabled()); } catch (Throwable ignore) { readingModeSwitch.setChecked(false); }
            readingModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> host.requestSetReadingModeEnabled(isChecked));
        }

        SwitchCompat showAnnotationsSwitch = root.findViewById(R.id.navigate_view_switch_show_annotations);
        View showAnnotationsRow = root.findViewById(R.id.navigate_view_row_show_annotations);
        if (showAnnotationsRow != null && showAnnotationsSwitch != null) {
            showAnnotationsRow.setOnClickListener(v -> showAnnotationsSwitch.toggle());
            try { showAnnotationsSwitch.setChecked(host.areCommentsVisible()); } catch (Throwable ignore) { showAnnotationsSwitch.setChecked(true); }
            showAnnotationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> host.requestSetCommentsVisible(isChecked));
        }

        View annotations = root.findViewById(R.id.navigate_view_action_annotations);
        if (annotations != null) {
            annotations.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestCommentsList();
            });
        }

        // Sidecar-only note marker toggle (EPUB + read-only PDFs).
        View noteMarkersRow = root.findViewById(R.id.navigate_view_row_note_markers);
        SwitchCompat noteMarkersSwitch = root.findViewById(R.id.navigate_view_switch_note_markers);
        boolean sidecarAvailable = hasSidecarSession(docView);
        if (noteMarkersRow != null) noteMarkersRow.setVisibility(sidecarAvailable ? View.VISIBLE : View.GONE);
        if (noteMarkersRow != null && noteMarkersSwitch != null) {
            noteMarkersRow.setOnClickListener(v -> noteMarkersSwitch.toggle());
            noteMarkersSwitch.setChecked(docView.areSidecarNotesStickyModeEnabled());
            noteMarkersSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try { docView.setSidecarNotesStickyModeEnabled(isChecked); } catch (Throwable ignore) {}
                try { activity.invalidateOptionsMenu(); } catch (Throwable ignore) {}
            });
        }

        // PDF-only forms highlight toggle.
        View formsRow = root.findViewById(R.id.navigate_view_row_forms_highlight);
        SwitchCompat formsSwitch = root.findViewById(R.id.navigate_view_switch_forms_highlight);
        boolean isPdf = currentDocumentType(activity) == DocumentType.PDF;
        if (formsRow != null) formsRow.setVisibility(isPdf ? View.VISIBLE : View.GONE);
        if (formsRow != null && formsSwitch != null) {
            formsRow.setOnClickListener(v -> formsSwitch.toggle());
            formsSwitch.setChecked(docView.isFormFieldHighlightEnabled());
        }

        // Forms navigation (contextual when highlight is enabled).
        View formPrev = root.findViewById(R.id.navigate_view_action_form_previous);
        View formNext = root.findViewById(R.id.navigate_view_action_form_next);
        boolean formsNavVisible = isPdf && docView.isFormFieldHighlightEnabled();
        if (formPrev != null) {
            formPrev.setVisibility(formsNavVisible ? View.VISIBLE : View.GONE);
            formPrev.setOnClickListener(v -> {
                dialog.dismiss();
                try { docView.navigateFormField(-1); } catch (Throwable ignore) {}
            });
        }
        if (formNext != null) {
            formNext.setVisibility(formsNavVisible ? View.VISIBLE : View.GONE);
            formNext.setOnClickListener(v -> {
                dialog.dismiss();
                try { docView.navigateFormField(1); } catch (Throwable ignore) {}
            });
        }
        if (formsSwitch != null) {
            formsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try { docView.setFormFieldHighlightEnabled(isChecked); } catch (Throwable ignore) {}
                if (formPrev != null) formPrev.setVisibility((isPdf && isChecked) ? View.VISIBLE : View.GONE);
                if (formNext != null) formNext.setVisibility((isPdf && isChecked) ? View.VISIBLE : View.GONE);
                try { activity.invalidateOptionsMenu(); } catch (Throwable ignore) {}
            });
        }

        View readingSettings = root.findViewById(R.id.navigate_view_action_reading_settings);
        boolean isEpub = currentDocumentType(activity) == DocumentType.EPUB;
        if (readingSettings != null) {
            readingSettings.setVisibility(isEpub ? View.VISIBLE : View.GONE);
            readingSettings.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestReadingSettings();
            });
        }

        // Document
        View save = root.findViewById(R.id.navigate_view_action_save);
        if (save != null) {
            boolean canSaveToCurrentUri = false;
            try {
                if (activity instanceof OpenDroidPDFActivity) {
                    OpenDroidPDFActivity oda = (OpenDroidPDFActivity) activity;
                    canSaveToCurrentUri = oda.canSaveToCurrentUri();
                }
            } catch (Throwable ignore) {}
            boolean canSaveChanges = isPdf && canSaveToCurrentUri;
            save.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            save.setAlpha(canSaveChanges ? 1f : 0.5f);
            save.setOnClickListener(v -> {
                if (canSaveChanges) {
                    dialog.dismiss();
                    host.requestSaveDialog();
                    return;
                }
                try {
                    UiUtils.showInfo(activity, activity.getString(R.string.save_changes_unavailable_use_export));
                } catch (Throwable ignore) {}
                dialog.dismiss();
                showExportSheet();
            });
        }

        View addBlankPage = root.findViewById(R.id.navigate_view_action_add_blank_page);
        if (addBlankPage != null) {
            addBlankPage.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            addBlankPage.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestAddBlankPage();
            });
        }

        View organizePages = root.findViewById(R.id.navigate_view_action_organize_pages);
        if (organizePages != null) {
            organizePages.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            organizePages.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestOrganizePages();
            });
        }

        View deleteNote = root.findViewById(R.id.navigate_view_action_delete_note);
        if (deleteNote != null) {
            boolean visible = false;
            try { visible = host.isViewingNoteDocument(); } catch (Throwable ignore) { visible = false; }
            deleteNote.setVisibility(visible ? View.VISIBLE : View.GONE);
            deleteNote.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestDeleteNote();
            });
        }

        dialog.show();
    }

    private static void bindPageSwitcherControls(@NonNull View root, @NonNull MuPDFReaderView docView) {
        final View row = root.findViewById(R.id.navigate_view_row_page_switcher);
        if (row == null) return;

        int pageCount = 0;
        try {
            Adapter adapter = docView.getAdapter();
            pageCount = adapter != null ? adapter.getCount() : 0;
        } catch (Throwable ignore) {
            pageCount = 0;
        }
        if (pageCount <= 1) {
            row.setVisibility(View.GONE);
            return;
        }

        final int totalPages = pageCount;
        final ImageButton prev = row.findViewById(R.id.navigate_view_page_prev);
        final ImageButton next = row.findViewById(R.id.navigate_view_page_next);
        final TextView label = row.findViewById(R.id.navigate_view_page_label);
        final SeekBar seek = row.findViewById(R.id.navigate_view_page_seek);

        int initialPage = 0;
        try { initialPage = docView.getSelectedItemPosition(); } catch (Throwable ignore) { initialPage = 0; }
        initialPage = clampPage(initialPage, totalPages);

        if (seek != null) {
            seek.setMax(Math.max(0, totalPages - 1));
            seek.setProgress(initialPage);
        }
        updatePageSwitcherUi(prev, next, label, seek, initialPage, totalPages);

        if (prev != null) {
            prev.setOnClickListener(v -> {
                int target = 0;
                try { target = (seek != null ? seek.getProgress() : docView.getSelectedItemPosition()) - 1; } catch (Throwable ignore) { target = 0; }
                target = clampPage(target, totalPages);
                if (seek != null) seek.setProgress(target);
                updatePageSwitcherUi(prev, next, label, seek, target, totalPages);
                navigateToPage(docView, target, totalPages);
            });
        }
        if (next != null) {
            next.setOnClickListener(v -> {
                int target = 0;
                try { target = (seek != null ? seek.getProgress() : docView.getSelectedItemPosition()) + 1; } catch (Throwable ignore) { target = 0; }
                target = clampPage(target, totalPages);
                if (seek != null) seek.setProgress(target);
                updatePageSwitcherUi(prev, next, label, seek, target, totalPages);
                navigateToPage(docView, target, totalPages);
            });
        }

        if (seek != null) {
            // Optional: thumbnail-only preview while dragging (minimap-style), then render the full
            // page on release. This avoids expensive page switches/renders while the user is still
            // scrubbing back-and-forth.
            final android.widget.ImageView preview = row.findViewById(R.id.navigate_view_page_preview);
            org.opendroidpdf.core.MuPdfController muPdfController = null;
            try {
                android.content.Context ctx = row.getContext();
                if (ctx instanceof OpenDroidPDFActivity) {
                    muPdfController = ((OpenDroidPDFActivity) ctx).getMuPdfController();
                } else if (docView.getContext() instanceof OpenDroidPDFActivity) {
                    muPdfController = ((OpenDroidPDFActivity) docView.getContext()).getMuPdfController();
                }
            } catch (Throwable ignore) {
                muPdfController = null;
            }
				            final org.opendroidpdf.core.MuPdfController controller = muPdfController;
				            if (preview != null && controller != null) {
				                final long previewMaxPixels = 25_000L;
				                final boolean logPreviewMetrics = android.util.Log.isLoggable("ScrubPreview", android.util.Log.DEBUG);
				                final long[] lastPreviewRequestAtMs = new long[] { 0L };
				                final Object cookieLock = new Object();
				                final Object cacheLock = new Object();
				                final android.util.LruCache<Integer, android.graphics.Bitmap> previewCache = new android.util.LruCache<>(64);
			                final int[] requestedTarget = new int[] { -1 };
			                final int[] activeRenderTarget = new int[] { -1 };
			                final long[] activeRenderStartedAtMs = new long[] { 0L };
			                final int[] pendingTarget = new int[] { -1 };
			                final int[] generation = new int[] { 0 };
			                final int[] activeRenderGen = new int[] { 0 };
		                final org.opendroidpdf.MuPDFCore.Cookie[] renderCookie = new org.opendroidpdf.MuPDFCore.Cookie[] { null };
		                final kotlinx.coroutines.Job[] renderJob = new kotlinx.coroutines.Job[] { null };
	                final int[] settleTarget = new int[] { -1 };
	                final int[] settleAttempts = new int[] { 0 };
	                final Runnable[] settleToFullRes = new Runnable[] { null };

	                final Runnable[] renderPreview = new Runnable[] { null };
	                renderPreview[0] = new Runnable() {
	                    @Override public void run() {
	                        int target = pendingTarget[0];
	                        if (target < 0) return;

	                        android.graphics.Bitmap cached = null;
	                        synchronized (cacheLock) { cached = previewCache.get(target); }
		                        if (cached != null) {
		                            pendingTarget[0] = -1;
		                            try {
		                                preview.setImageBitmap(cached);
		                                preview.setVisibility(android.view.View.VISIBLE);
		                                if (logPreviewMetrics) {
		                                    long dt = android.os.SystemClock.uptimeMillis() - lastPreviewRequestAtMs[0];
		                                    android.util.Log.d("ScrubPreview", "show page=" + (target + 1) + " dtMs=" + dt + " cached=true");
		                                }
		                            } catch (Throwable ignore) {}
		                            return;
		                        }

	                        // Coalesce renders: if one is already in flight, keep the latest target queued and
	                        // start it when the current render finishes (avoids cancel/redo thrash while dragging).
	                        try {
	                            kotlinx.coroutines.Job job = renderJob[0];
	                            if (job != null && job.isActive()) return;
	                        } catch (Throwable ignore) {}

		                        pendingTarget[0] = -1;
		                        final int gen = ++generation[0];
		                        activeRenderGen[0] = gen;
		                        final int renderTarget = target;
		                        activeRenderTarget[0] = renderTarget;
		                        activeRenderStartedAtMs[0] = android.os.SystemClock.uptimeMillis();

		                        android.graphics.PointF size = null;
		                        try { size = controller.pageSize(renderTarget); } catch (Throwable ignore) { size = null; }
		                        float ratio = 1.294f;
		                        if (size != null && size.x > 0f && size.y > 0f) {
	                            ratio = size.y / size.x;
	                        }
                        ratio = Math.max(0.15f, Math.min(8.0f, ratio));
                        int w = 1;
                        int h = 1;
                        try {
                            double ww = Math.sqrt((double) previewMaxPixels / (double) ratio);
                            w = Math.max(1, (int) Math.round(ww));
                            h = Math.max(1, (int) Math.round(w * ratio));
	                        } catch (Throwable ignore) {
	                            w = 160;
	                            h = 210;
	                        }
	                        final int fw = w;
	                        final int fh = h;
	                        final org.opendroidpdf.MuPDFCore.Cookie cookie = controller.newRenderCookie();
	                        synchronized (cookieLock) {
	                            renderCookie[0] = cookie;
	                        }
	                        renderJob[0] = org.opendroidpdf.app.AppCoroutines.launchIo(org.opendroidpdf.app.AppCoroutines.ioScope(), new Runnable() {
		                            @Override public void run() {
		                                android.graphics.Bitmap bm = null;
		                                try {
		                                    bm = android.graphics.Bitmap.createBitmap(fw, fh, android.graphics.Bitmap.Config.ARGB_8888);
		                                    try { bm.eraseColor(android.graphics.Color.WHITE); } catch (Throwable ignore) {}
		                                    controller.drawPage(bm, renderTarget, fw, fh, 0, 0, fw, fh, cookie);
		                                } catch (Throwable ignore) {
		                                    bm = null;
		                                } finally {
	                                    synchronized (cookieLock) {
	                                        if (renderCookie[0] == cookie) {
                                            renderCookie[0] = null;
	                                        }
	                                        try { cookie.destroy(); } catch (Throwable ignore) {}
	                                    }
	                                }
	                                if (cookie.aborted()) bm = null;
		                                final android.graphics.Bitmap ready = bm;
		                                try {
		                                    preview.post(() -> {
		                                        if (ready != null) {
		                                            synchronized (cacheLock) { previewCache.put(renderTarget, ready); }
		                                        }
				                                        if (ready != null && generation[0] == gen && requestedTarget[0] == renderTarget) {
				                                            try {
				                                                preview.setImageBitmap(ready);
				                                                preview.setVisibility(android.view.View.VISIBLE);
				                                                if (logPreviewMetrics) {
				                                                    long dt = android.os.SystemClock.uptimeMillis() - lastPreviewRequestAtMs[0];
				                                                    android.util.Log.d("ScrubPreview", "show page=" + (renderTarget + 1) + " dtMs=" + dt + " cached=false");
				                                                }
				                                            } catch (Throwable ignore) {}
				                                        }
		                                        try {
		                                            if (activeRenderGen[0] == gen) {
		                                                renderJob[0] = null;
		                                                activeRenderGen[0] = 0;
		                                                activeRenderTarget[0] = -1;
		                                                activeRenderStartedAtMs[0] = 0L;
		                                            }
		                                            if (renderPreview[0] != null) renderPreview[0].run();
		                                        } catch (Throwable ignore) {}
		                                    });
		                                } catch (Throwable ignore) {
	                                }
	                            }
	                        });
	                    }
	                };

                seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			                    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			                        int clamped = clampPage(progress, totalPages);
				                        updatePageSwitcherUi(prev, next, label, seek, clamped, totalPages);
				                        if (!fromUser) return;
				                        requestedTarget[0] = clamped;
				                        lastPreviewRequestAtMs[0] = android.os.SystemClock.uptimeMillis();
				                        pendingTarget[0] = clamped;
				                        try {
				                            final long abortAfterMs = 50L;
				                            final int abortDeltaPages = 1;
			                            kotlinx.coroutines.Job job = renderJob[0];
			                            if (job != null && job.isActive()) {
			                                int active = activeRenderTarget[0];
			                                long started = activeRenderStartedAtMs[0];
			                                long now = android.os.SystemClock.uptimeMillis();
			                                if (active >= 0 && active != clamped && started > 0L) {
			                                    if ((now - started) >= abortAfterMs && Math.abs(clamped - active) >= abortDeltaPages) {
			                                        synchronized (cookieLock) {
			                                            org.opendroidpdf.MuPDFCore.Cookie cookie = renderCookie[0];
			                                            if (cookie != null) {
			                                                try { cookie.abort(); } catch (Throwable ignore) {}
			                                            }
			                                        }
			                                        try { org.opendroidpdf.app.AppCoroutines.cancel(job); } catch (Throwable ignore) {}
			                                    }
			                                }
			                            }
			                        } catch (Throwable ignore) {}
				                        if (renderPreview[0] != null) renderPreview[0].run();
				                    }

		                    @Override public void onStartTrackingTouch(SeekBar seekBar) {
		                        try { if (seekBar != null && settleToFullRes[0] != null) seekBar.removeCallbacks(settleToFullRes[0]); } catch (Throwable ignore) {}
		                        settleTarget[0] = -1;
		                        settleAttempts[0] = 0;
			                        try { docView.setScrubbing(true); } catch (Throwable ignore) {}
				                        try { preview.setVisibility(android.view.View.GONE); } catch (Throwable ignore) {}
				                        requestedTarget[0] = clampPage(seekBar != null ? seekBar.getProgress() : 0, totalPages);
				                        pendingTarget[0] = clampPage(seekBar != null ? seekBar.getProgress() : 0, totalPages);
				                        lastPreviewRequestAtMs[0] = android.os.SystemClock.uptimeMillis();
				                        generation[0]++; // ignore late thumbnail updates from a previous drag
			                        synchronized (cookieLock) {
		                            org.opendroidpdf.MuPDFCore.Cookie cookie = renderCookie[0];
		                            if (cookie != null) {
	                                try { cookie.abort(); } catch (Throwable ignore) {}
	                            }
	                        }
		                        try { org.opendroidpdf.app.AppCoroutines.cancel(renderJob[0]); } catch (Throwable ignore) {}
		                        renderJob[0] = null;
		                        activeRenderGen[0] = 0;
		                        activeRenderTarget[0] = -1;
		                        activeRenderStartedAtMs[0] = 0L;
		                        if (renderPreview[0] != null) renderPreview[0].run();
		                    }

	                    @Override public void onStopTrackingTouch(SeekBar seekBar) {
	                        int target = seekBar != null ? seekBar.getProgress() : 0;
	                        target = clampPage(target, totalPages);
	                        updatePageSwitcherUi(prev, next, label, seek, target, totalPages);
	                        generation[0]++; // ignore late thumbnail updates from in-flight renders; prioritize final target
	                        synchronized (cookieLock) {
	                            org.opendroidpdf.MuPDFCore.Cookie cookie = renderCookie[0];
	                            if (cookie != null) {
	                                try { cookie.abort(); } catch (Throwable ignore) {}
	                            }
	                        }
		                        try { org.opendroidpdf.app.AppCoroutines.cancel(renderJob[0]); } catch (Throwable ignore) {}
		                        renderJob[0] = null;
			                        activeRenderGen[0] = 0;
			                        activeRenderTarget[0] = -1;
				                        activeRenderStartedAtMs[0] = 0L;
				                        requestedTarget[0] = target;
				                        pendingTarget[0] = target;
				                        lastPreviewRequestAtMs[0] = android.os.SystemClock.uptimeMillis();
				                        if (renderPreview[0] != null) renderPreview[0].run(); // ensure preview matches the final target

		                        settleTarget[0] = target;
                        settleAttempts[0] = 0;
                        try { docView.setScrubbing(true); } catch (Throwable ignore) {}
                        navigateToPage(docView, target, totalPages);

                        if (settleToFullRes[0] == null) {
                            settleToFullRes[0] = new Runnable() {
                                @Override public void run() {
                                    int want = settleTarget[0];
                                    if (want < 0) return;
                                    int tries = settleAttempts[0]++;
                                    if (tries > 30) {
                                        settleTarget[0] = -1;
                                        try { docView.setScrubbing(false); } catch (Throwable ignore) {}
                                        try { preview.setVisibility(android.view.View.GONE); } catch (Throwable ignore) {}
                                        generation[0]++; // ignore late thumbnail updates
                                        synchronized (cookieLock) {
                                            org.opendroidpdf.MuPDFCore.Cookie cookie = renderCookie[0];
                                            if (cookie != null) {
                                                try { cookie.abort(); } catch (Throwable ignore) {}
                                            }
                                        }
                                        try { org.opendroidpdf.app.AppCoroutines.cancel(renderJob[0]); } catch (Throwable ignore) {}
                                        renderJob[0] = null;
                                        return;
                                    }
                                    int cur = -1;
                                    try { cur = docView.getSelectedItemPosition(); } catch (Throwable ignore) { cur = -1; }
                                    if (cur == want) {
                                        settleTarget[0] = -1;
                                        try { docView.setScrubbing(false); } catch (Throwable ignore) {}
                                        try { preview.setVisibility(android.view.View.GONE); } catch (Throwable ignore) {}
                                        generation[0]++; // ignore late thumbnail updates
                                        try {
                                            android.view.View v = docView.getSelectedView();
                                            if (v instanceof org.opendroidpdf.MuPDFView) {
                                                ((org.opendroidpdf.MuPDFView) v).redraw(true);
                                            }
                                            if (v instanceof org.opendroidpdf.PageView) {
                                                ((org.opendroidpdf.PageView) v).loadDeferredPageDataAfterScrub();
                                            }
                                        } catch (Throwable ignore) {
                                        }
                                        return;
                                    }
                                    try { if (seekBar != null) seekBar.postDelayed(this, 50); } catch (Throwable ignore) {}
                                }
                            };
                        }
                        try { if (seekBar != null) seekBar.postDelayed(settleToFullRes[0], 50); } catch (Throwable ignore) {}
                    }
                });
                return;
            }

            // Live scrubbing: while dragging, navigate with a small throttle so the visible page
            // tracks the thumb without issuing a full page switch for every tiny movement.
            final int scrubThrottleMs = 30;
            final int[] pendingTarget = new int[] { -1 };
            final int[] lastRequestedTarget = new int[] { initialPage };
            final long[] lastRequestUptimeMs = new long[] { 0L };
            final int[] settleTarget = new int[] { -1 };
            final int[] settleAttempts = new int[] { 0 };
            final Runnable[] settleToFullRes = new Runnable[] { null };
            final Runnable throttledNavigate = new Runnable() {
                @Override
                public void run() {
                    int target = pendingTarget[0];
                    if (target < 0) return;
                    pendingTarget[0] = -1;
                    lastRequestUptimeMs[0] = android.os.SystemClock.uptimeMillis();
                    int cur = -1;
                    try { cur = docView.getSelectedItemPosition(); } catch (Throwable ignore) { cur = -1; }
                    if (cur == target) {
                        lastRequestedTarget[0] = target;
                        return;
                    }
                    lastRequestedTarget[0] = target;
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        android.util.Log.d("Scrubber", "navigate target=" + target
                                + " cur=" + cur
                                + " scrubbing=" + docView.isScrubbing()
                                + " t=" + lastRequestUptimeMs[0]);
                    }
                    try { docView.setDisplayedViewIndex(target, true); } catch (Throwable ignore) {}
                }
            };

            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int clamped = clampPage(progress, totalPages);
                    updatePageSwitcherUi(prev, next, label, seek, clamped, totalPages);
                    if (!fromUser) {
                        // Keep our throttle state aligned with programmatic updates (buttons),
                        // otherwise we can accidentally skip legitimate user scrubs.
                        lastRequestedTarget[0] = clamped;
                        return;
                    }
                    pendingTarget[0] = clamped;
                    long now = android.os.SystemClock.uptimeMillis();
                    long since = now - lastRequestUptimeMs[0];
                    try { seekBar.removeCallbacks(throttledNavigate); } catch (Throwable ignore) {}
                    if (since >= scrubThrottleMs) {
                        throttledNavigate.run();
                    } else {
                        try { seekBar.postDelayed(throttledNavigate, scrubThrottleMs - since); } catch (Throwable ignore) {}
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {
                    try { if (seekBar != null && settleToFullRes[0] != null) seekBar.removeCallbacks(settleToFullRes[0]); } catch (Throwable ignore) {}
                    settleTarget[0] = -1;
                    settleAttempts[0] = 0;
                    try { docView.setScrubbing(true); } catch (Throwable ignore) {}
                    try { lastRequestedTarget[0] = docView.getSelectedItemPosition(); } catch (Throwable ignore) { lastRequestedTarget[0] = clampPage(seekBar != null ? seekBar.getProgress() : 0, totalPages); }
                    lastRequestUptimeMs[0] = 0L;
                }
                @Override public void onStopTrackingTouch(SeekBar seekBar) {
                    int target = seekBar != null ? seekBar.getProgress() : 0;
                    target = clampPage(target, totalPages);
                    updatePageSwitcherUi(prev, next, label, seek, target, totalPages);
                    try { seekBar.removeCallbacks(throttledNavigate); } catch (Throwable ignore) {}
                    pendingTarget[0] = target;
                    throttledNavigate.run();
                    try { docView.setNormalizedScroll(0.0f, 0.0f); } catch (Throwable ignore) {}

                    settleTarget[0] = target;
                    settleAttempts[0] = 0;
                    if (settleToFullRes[0] == null) {
                        settleToFullRes[0] = new Runnable() {
                            @Override public void run() {
                                int want = settleTarget[0];
                                if (want < 0) return;
                                int tries = settleAttempts[0]++;
                                if (tries > 30) {
                                    settleTarget[0] = -1;
                                    try { docView.setScrubbing(false); } catch (Throwable ignore) {}
                                    return;
                                }
                                int cur = -1;
                                try { cur = docView.getSelectedItemPosition(); } catch (Throwable ignore) { cur = -1; }
	                                if (cur == want) {
	                                    settleTarget[0] = -1;
	                                    try { docView.setScrubbing(false); } catch (Throwable ignore) {}
	                                    try {
	                                        android.view.View v = docView.getSelectedView();
	                                        if (v instanceof org.opendroidpdf.MuPDFView) {
	                                            ((org.opendroidpdf.MuPDFView) v).redraw(true);
	                                        }
	                                        if (v instanceof org.opendroidpdf.PageView) {
	                                            ((org.opendroidpdf.PageView) v).loadDeferredPageDataAfterScrub();
	                                        }
	                                    } catch (Throwable ignore) {
	                                    }
	                                    return;
	                                }
                                try { if (seekBar != null) seekBar.postDelayed(this, 50); } catch (Throwable ignore) {}
                            }
                        };
                    }
                    try { if (seekBar != null) seekBar.postDelayed(settleToFullRes[0], 50); } catch (Throwable ignore) {}
                }
            });
        }
    }

    private static void updatePageSwitcherUi(ImageButton prev,
                                            ImageButton next,
                                            TextView label,
                                            SeekBar seek,
                                            int pageIndex,
                                            int totalPages) {
        if (totalPages <= 0) return;
        int clamped = clampPage(pageIndex, totalPages);
        if (label != null) {
            label.setText(String.format(Locale.getDefault(), "%d / %d", clamped + 1, totalPages));
        }
        if (prev != null) {
            boolean enabled = clamped > 0;
            prev.setEnabled(enabled);
            prev.setAlpha(enabled ? 1f : 0.35f);
        }
        if (next != null) {
            boolean enabled = clamped < totalPages - 1;
            next.setEnabled(enabled);
            next.setAlpha(enabled ? 1f : 0.35f);
        }
        if (seek != null && seek.getProgress() != clamped) {
            seek.setProgress(clamped);
        }
    }

    private static void navigateToPage(@NonNull MuPDFReaderView docView, int pageIndex, int totalPages) {
        int clamped = clampPage(pageIndex, totalPages);
        try { docView.setDisplayedViewIndex(clamped, true); } catch (Throwable ignore) {}
        try { docView.setNormalizedScroll(0.0f, 0.0f); } catch (Throwable ignore) {}
    }

    private static int clampPage(int pageIndex, int totalPages) {
        if (totalPages <= 0) return 0;
        if (pageIndex < 0) return 0;
        if (pageIndex > totalPages - 1) return totalPages - 1;
        return pageIndex;
    }

    public void showExportSheet() {
        AppCompatActivity activity = host != null ? host.getActivity() : null;
        if (activity == null) return;
        if (!host.hasDocumentLoaded()) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_export_sheet, null);
        dialog.setContentView(root);

        DocumentType docType = currentDocumentType(activity);
        boolean isPdf = docType == DocumentType.PDF;
        boolean isEpub = docType == DocumentType.EPUB;
        boolean canExport = isPdf || isEpub;

        boolean canSaveToCurrentUri = false;
        try {
            if (activity instanceof OpenDroidPDFActivity) {
                canSaveToCurrentUri = ((OpenDroidPDFActivity) activity).canSaveToCurrentUri();
            }
        } catch (Throwable ignore) {}
        boolean sidecarAvailable = isEpub || (isPdf && !canSaveToCurrentUri);

        View shareCopy = root.findViewById(R.id.export_action_share_copy);
        if (shareCopy != null) {
            shareCopy.setAlpha(canExport ? 1f : 0.5f);
            shareCopy.setOnClickListener(v -> {
                if (!canExport) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_not_available)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestShare();
            });
        }

        View saveCopy = root.findViewById(R.id.export_action_save_copy);
        if (saveCopy != null) {
            saveCopy.setAlpha(canExport ? 1f : 0.5f);
            saveCopy.setOnClickListener(v -> {
                if (!canExport) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_not_available)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestSaveCopy();
            });
        }

        View print = root.findViewById(R.id.export_action_print);
        if (print != null) {
            print.setAlpha(canExport ? 1f : 0.5f);
            print.setOnClickListener(v -> {
                if (!canExport) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_not_available)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestPrint();
            });
        }

        View advancedContainer = root.findViewById(R.id.export_sheet_advanced_container);
        TextView advancedToggle = root.findViewById(R.id.export_action_advanced_toggle);
        boolean showAdvanced = canExport && (isPdf || isEpub);
        if (advancedToggle != null) advancedToggle.setVisibility(showAdvanced ? View.VISIBLE : View.GONE);
        if (!showAdvanced && advancedContainer != null) advancedContainer.setVisibility(View.GONE);
        if (advancedContainer != null && advancedToggle != null) {
            advancedToggle.setOnClickListener(v -> {
                boolean showing = advancedContainer.getVisibility() == View.VISIBLE;
                advancedContainer.setVisibility(showing ? View.GONE : View.VISIBLE);
                advancedToggle.setText(showing ? R.string.export_sheet_action_advanced_options
                        : R.string.export_sheet_action_hide_advanced_options);
            });
        }

        View shareLinear = root.findViewById(R.id.export_action_share_linearized);
        if (shareLinear != null) {
            boolean enabled = isPdf && BuildConfig.ENABLE_QPDF_OPS;
            shareLinear.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            shareLinear.setAlpha(enabled ? 1f : 0.5f);
            shareLinear.setOnClickListener(v -> {
                if (!enabled) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_option_requires_qpdf)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestShareLinearized();
            });
        }

        View shareEncrypted = root.findViewById(R.id.export_action_share_encrypted);
        if (shareEncrypted != null) {
            boolean enabled = isPdf && BuildConfig.ENABLE_QPDF_OPS;
            shareEncrypted.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            shareEncrypted.setAlpha(enabled ? 1f : 0.5f);
            shareEncrypted.setOnClickListener(v -> {
                if (!enabled) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_option_requires_qpdf)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestShareEncrypted();
            });
        }

        View shareFlattened = root.findViewById(R.id.export_action_share_flattened);
        if (shareFlattened != null) {
            shareFlattened.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            shareFlattened.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestShareFlattened();
            });
        }

        View saveLinear = root.findViewById(R.id.export_action_save_linearized);
        if (saveLinear != null) {
            boolean enabled = isPdf && BuildConfig.ENABLE_QPDF_OPS;
            saveLinear.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            saveLinear.setAlpha(enabled ? 1f : 0.5f);
            saveLinear.setOnClickListener(v -> {
                if (!enabled) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_option_requires_qpdf)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestSaveLinearized();
            });
        }

        View saveEncrypted = root.findViewById(R.id.export_action_save_encrypted);
        if (saveEncrypted != null) {
            boolean enabled = isPdf && BuildConfig.ENABLE_QPDF_OPS;
            saveEncrypted.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            saveEncrypted.setAlpha(enabled ? 1f : 0.5f);
            saveEncrypted.setOnClickListener(v -> {
                if (!enabled) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_option_requires_qpdf)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestSaveEncrypted();
            });
        }

        View exportAnnotations = root.findViewById(R.id.export_action_export_annotations);
        if (exportAnnotations != null) {
            exportAnnotations.setVisibility(canExport ? View.VISIBLE : View.GONE);
            exportAnnotations.setAlpha(sidecarAvailable ? 1f : 0.5f);
            exportAnnotations.setOnClickListener(v -> {
                if (!sidecarAvailable) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_sidecar_only)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestExportAnnotations();
            });
        }

        View importAnnotations = root.findViewById(R.id.export_action_import_annotations);
        if (importAnnotations != null) {
            importAnnotations.setVisibility(canExport ? View.VISIBLE : View.GONE);
            importAnnotations.setAlpha(sidecarAvailable ? 1f : 0.5f);
            importAnnotations.setOnClickListener(v -> {
                if (!sidecarAvailable) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_sidecar_only)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestImportAnnotations();
            });
        }

        dialog.show();
    }

    public void inflateMainMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // Inflate only; state is now managed centrally by ToolbarStateController.
        inflater.inflate(R.menu.main_menu, menu);
    }

    public boolean handleMenuItem(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_addpage:
                host.requestAddBlankPage();
                return true;
            case R.id.menu_fullscreen:
                host.requestFullscreen();
                return true;
            case R.id.menu_settings:
                host.requestSettings();
                return true;
            case R.id.menu_reading_settings:
                host.requestReadingSettings();
                return true;
            case R.id.menu_print:
                host.requestPrint();
                return true;
            case R.id.menu_share:
                showExportSheet();
                return true;
            case R.id.menu_share_linearized:
                host.requestShareLinearized();
                return true;
            case R.id.menu_share_encrypted:
                host.requestShareEncrypted();
                return true;
            case R.id.menu_share_flattened:
                host.requestShareFlattened();
                return true;
            case R.id.menu_save_linearized:
                host.requestSaveLinearized();
                return true;
            case R.id.menu_save_encrypted:
                host.requestSaveEncrypted();
                return true;
            case R.id.menu_import_annotations:
                host.requestImportAnnotations();
                return true;
            case R.id.menu_export_annotations:
                host.requestExportAnnotations();
                return true;
            case R.id.menu_search:
                host.requestSearchMode();
                return true;
            case R.id.menu_open:
                host.requestDashboard();
                return true;
            case R.id.menu_delete_note:
                host.requestDeleteNote();
                return true;
            case R.id.menu_save:
                host.requestSaveDialog();
                return true;
            case R.id.menu_fill_sign:
                host.requestFillSign();
                return true;
            case R.id.menu_forms:
                if (!host.hasDocumentView()) return true;
                org.opendroidpdf.MuPDFReaderView docView = host.getDocView();
                if (docView != null) {
                    boolean enabled = !docView.isFormFieldHighlightEnabled();
                    docView.setFormFieldHighlightEnabled(enabled);
                    item.setChecked(enabled);
                    try { host.getActivity().invalidateOptionsMenu(); } catch (Throwable ignore) {}
                }
                return true;
            case R.id.menu_form_previous:
                if (!host.hasDocumentView()) return true;
                org.opendroidpdf.MuPDFReaderView docPrev = host.getDocView();
                if (docPrev != null) docPrev.navigateFormField(-1);
                return true;
            case R.id.menu_form_next:
                if (!host.hasDocumentView()) return true;
                org.opendroidpdf.MuPDFReaderView docNext = host.getDocView();
                if (docNext != null) docNext.navigateFormField(1);
                return true;
            case R.id.menu_gotopage:
                org.opendroidpdf.app.dialog.Dialogs.showGoToPage(
                        host.getActivity(),
                        host.alertBuilder(),
                        host.getDocView());
                return true;
            case R.id.menu_toc:
                host.requestTableOfContents();
                return true;
            case R.id.menu_comments:
                host.requestCommentsList();
                return true;
            case R.id.menu_show_comments: {
                boolean next = true;
                try { next = !host.areCommentsVisible(); } catch (Throwable ignore) { next = true; }
                host.requestSetCommentsVisible(next);
                item.setChecked(next);
                try { host.getActivity().invalidateOptionsMenu(); } catch (Throwable ignore) {}
                return true;
            }
            case R.id.menu_sticky_notes: {
                if (!host.hasDocumentView()) return true;
                org.opendroidpdf.MuPDFReaderView stickyDocView = host.getDocView();
                if (stickyDocView != null) {
                    boolean enabled = !stickyDocView.areSidecarNotesStickyModeEnabled();
                    stickyDocView.setSidecarNotesStickyModeEnabled(enabled);
                    item.setChecked(enabled);
                    try { host.getActivity().invalidateOptionsMenu(); } catch (Throwable ignore) {}
                }
                return true;
            }
            case R.id.menu_comment_previous:
                host.requestNavigateComment(-1);
                return true;
            case R.id.menu_comment_next:
                host.requestNavigateComment(1);
                return true;
            case R.id.menu_linkback:
                host.requestLinkBackNavigation();
                return true;
            default:
                return false;
        }
    }

    // Visibility/enablement is handled by ToolbarStateController.onPrepareOptionsMenu

    private static DocumentType currentDocumentType(@NonNull AppCompatActivity activity) {
        try {
            if (activity instanceof OpenDroidPDFActivity) {
                OpenDroidPDFCore core = ((OpenDroidPDFActivity) activity).getCore();
                return core != null ? DocumentType.fromFileFormat(core.fileFormat()) : DocumentType.OTHER;
            }
        } catch (Throwable ignore) {
        }
        return DocumentType.OTHER;
    }

    private static boolean hasSidecarSession(@NonNull MuPDFReaderView docView) {
        try {
            Adapter adapter = docView.getAdapter();
            if (adapter instanceof MuPDFPageAdapter) {
                return ((MuPDFPageAdapter) adapter).sidecarSessionOrNull() != null;
            }
        } catch (Throwable ignore) {
        }
        return false;
    }
}
