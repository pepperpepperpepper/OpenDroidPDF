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
    }

    /** Reloads snapshots from stores and applies them to the current activity/docView/core. */
    public void refreshAndApply() {
        AppPrefsSnapshot app = appPrefsStore.load();
        ViewerPrefsSnapshot viewer = viewerPrefsStore.load();
        EditorPrefsSnapshot editor = editorPrefsStore.load();

        applyKeepScreenOn(host.activity(), app.keepScreenOn);
        host.setSaveFlags(app.saveOnStop, app.saveOnDestroy, app.numberRecentFiles);

        MuPDFReaderView docView = host.docViewOrNull();
        if (docView != null) {
            docView.applyViewerPrefs(viewer);
        }

        OpenDroidPDFCore core = host.coreOrNull();
        if (core != null) {
            // Pen settings must be applied via the service snapshot so native/core settings can't drift.
            PenNativeSettingsApplier.apply(core, penPreferences.get());
            AnnotationNativeSettingsApplier.apply(core, editor);
        }
    }

    /** Apply current preferences to the new core (e.g., after opening a document). */
    public void applyToCore(@Nullable OpenDroidPDFCore core) {
        if (core == null) return;
        PenNativeSettingsApplier.apply(core, penPreferences.get());
        AnnotationNativeSettingsApplier.apply(core, editorPrefsStore.load());
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

