package org.opendroidpdf.app.lifecycle;

import android.os.Bundle;
import android.os.Parcelable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.ui.ActionBarMode;

/**
 * Restores UI-relevant state from savedInstanceState and applies it to the activity.
 */
public final class SavedStateHelper {
    private SavedStateHelper() {}

    public static void restore(OpenDroidPDFActivity activity, Bundle savedInstanceState) {
        if (activity == null || savedInstanceState == null) return;

        String modeStr = savedInstanceState.getString("ActionBarMode", ActionBarMode.Main.toString());
        ActionBarMode mode;
        try { mode = ActionBarMode.valueOf(modeStr); }
        catch (Throwable ignore) { mode = ActionBarMode.Main; }

        int pageBefore = savedInstanceState.getInt("PageBeforeInternalLinkHit", -1);
        float normScale = savedInstanceState.getFloat("NormalizedScaleBeforeInternalLinkHit", 1.0f);
        float normX = savedInstanceState.getFloat("NormalizedXScrollBeforeInternalLinkHit", 0f);
        float normY = savedInstanceState.getFloat("NormalizedYScrollBeforeInternalLinkHit", 0f);
        Parcelable docViewState = savedInstanceState.getParcelable("mDocView");
        String latestSearch = savedInstanceState.getString("latestTextInSearchBox", null);

        activity.applySavedUiState(mode, pageBefore, normScale, normX, normY, docViewState, latestSearch);
    }
}
