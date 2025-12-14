package org.opendroidpdf.app.hosts;

import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.ui.FullscreenController;

/**
 * Host adapter for FullscreenController to keep the anonymous inner class out of the activity.
 */
public class FullscreenHostAdapter implements FullscreenController.Host {
    private final OpenDroidPDFActivity activity;

    public FullscreenHostAdapter(OpenDroidPDFActivity activity) { this.activity = activity; }

    @Override public AppCompatActivity getActivity() { return activity; }
    @Override public MuPDFReaderView getDocView() { return activity.getDocView(); }
    @Override public OpenDroidPDFCore getCore() { return activity.getCore(); }
    @Override public void saveViewport(@androidx.annotation.NonNull android.net.Uri uri) {
        if (activity.getViewportController() != null) {
            activity.getViewportController().saveViewport(uri);
        }
    }
    @Override public void setupDocView() { activity.setupDocView(); }
    @Override public void setActionBarModeHidden() { activity.getActionBarModeDelegate().setHidden(); }
    @Override public void setActionBarModeMainIfHidden() { activity.getActionBarModeDelegate().setMainIfHidden(); }
    @Override public void invalidateOptionsMenu() { activity.invalidateOptionsMenuSafely(); }
}
