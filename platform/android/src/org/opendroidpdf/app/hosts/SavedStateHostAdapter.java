package org.opendroidpdf.app.hosts;

import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.lifecycle.ActivityComposition;
import org.opendroidpdf.app.lifecycle.SavedStateHelper;

/** Adapter for {@link SavedStateHelper.Host} that delegates to {@link OpenDroidPDFActivity}. */
public final class SavedStateHostAdapter implements SavedStateHelper.Host {
    private final OpenDroidPDFActivity activity;

    public SavedStateHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Override public ActivityComposition.Composition compositionOrNull() { return activity.getComposition(); }
    @Nullable @Override public MuPDFReaderView docViewOrNull() { return activity.getDocView(); }

    @Override
    public void applySavedUiState(int pageBefore,
                                  float normScale,
                                  float normX,
                                  float normY,
                                  @Nullable Parcelable docViewState,
                                  @Nullable String latestSearch) {
        activity.applySavedUiState(pageBefore, normScale, normX, normY, docViewState, latestSearch);
    }
}
