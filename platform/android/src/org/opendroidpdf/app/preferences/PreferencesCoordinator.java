package org.opendroidpdf.app.preferences;

import android.app.Activity;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.services.PenPreferencesService;

/**
 * Single owner for preference snapshots and their application to the active activity/docView/core.
 * <p>
 * Stores own persistence; this class owns "how settings affect runtime objects".
 */
public final class PreferencesCoordinator {

    public interface Host {
        Activity activity();
        void setSaveFlags(boolean saveOnStop, boolean saveOnDestroy, int numberRecentFiles);
        @Nullable MuPDFReaderView docViewOrNull();
        @Nullable OpenDroidPDFCore coreOrNull();
    }

    private final Host host;
    private final AppPrefsStore appPrefsStore;
    private final ViewerPrefsStore viewerPrefsStore;
    private final EditorPrefsStore editorPrefsStore;
    private final PenPreferencesService penPreferences;

    // Cached snapshots for view/controller consumption (avoid SharedPreferences reads in views).
    private volatile PenPrefsSnapshot cachedPenPrefs;
    private volatile EditorPrefsSnapshot cachedEditorPrefs;

    public PreferencesCoordinator(Host host,
                                  AppPrefsStore appPrefsStore,
                                  ViewerPrefsStore viewerPrefsStore,
                                  EditorPrefsStore editorPrefsStore,
                                  PenPreferencesService penPreferences) {
        this.host = host;
        this.appPrefsStore = appPrefsStore;
        this.viewerPrefsStore = viewerPrefsStore;
        this.editorPrefsStore = editorPrefsStore;
        this.penPreferences = penPreferences;

        // Best-effort eager snapshot to avoid nulls before first refreshAndApply().
        try {
            this.cachedPenPrefs = penPreferences != null ? penPreferences.get() : null;
        } catch (Throwable ignore) {
            this.cachedPenPrefs = null;
        }
        try {
            this.cachedEditorPrefs = editorPrefsStore != null ? editorPrefsStore.load() : null;
        } catch (Throwable ignore) {
            this.cachedEditorPrefs = null;
        }
    }

    /** Current pen prefs snapshot (cached; refreshed on {@link #refreshAndApply()}). */
    public PenPrefsSnapshot penPrefsSnapshot() {
        PenPrefsSnapshot snap = cachedPenPrefs;
        if (snap != null) return snap;
        snap = penPreferences.get();
        cachedPenPrefs = snap;
        return snap;
    }

    /** Current editor prefs snapshot (cached; refreshed on {@link #refreshAndApply()}). */
    public EditorPrefsSnapshot editorPrefsSnapshot() {
        EditorPrefsSnapshot snap = cachedEditorPrefs;
        if (snap != null) return snap;
        snap = editorPrefsStore.load();
        cachedEditorPrefs = snap;
        return snap;
    }

    /** Reloads snapshots from stores and applies them to the current activity/docView/core. */
    public void refreshAndApply() {
        AppPrefsSnapshot app = appPrefsStore.load();
        ViewerPrefsSnapshot viewer = viewerPrefsStore.load();
        PenPrefsSnapshot pen = penPreferences.get();
        EditorPrefsSnapshot editor = editorPrefsStore.load();
        cachedPenPrefs = pen;
        cachedEditorPrefs = editor;

        applyKeepScreenOn(host.activity(), app.keepScreenOn);
        host.setSaveFlags(app.saveOnStop, app.saveOnDestroy, app.numberRecentFiles);

        MuPDFReaderView docView = host.docViewOrNull();
        if (docView != null) {
            docView.applyViewerPrefs(viewer);
        }

        OpenDroidPDFCore core = host.coreOrNull();
        if (core != null) {
            // Pen settings must be applied via the service snapshot so native/core settings can't drift.
            PenNativeSettingsApplier.apply(core, pen);
            AnnotationNativeSettingsApplier.apply(core, editor);
        }
    }

    /** Apply current preferences to the new core (e.g., after opening a document). */
    public void applyToCore(@Nullable OpenDroidPDFCore core) {
        if (core == null) return;
        PenNativeSettingsApplier.apply(core, penPrefsSnapshot());
        AnnotationNativeSettingsApplier.apply(core, editorPrefsSnapshot());
    }

    /** Apply current viewer preferences to the docView (e.g., after creating/attaching it). */
    public void applyToDocView(@Nullable MuPDFReaderView docView) {
        if (docView == null) return;
        docView.applyViewerPrefs(viewerPrefsStore.load());
    }

    private static void applyKeepScreenOn(Activity activity, boolean keepScreenOn) {
        if (activity == null) return;
        if (keepScreenOn) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
}
