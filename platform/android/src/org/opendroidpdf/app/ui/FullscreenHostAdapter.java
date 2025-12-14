package org.opendroidpdf.app.ui;

import org.opendroidpdf.OpenDroidPDFActivity;

/** Adapter so FullscreenController.Host calls stay out of the activity surface. */
public final class FullscreenHostAdapter implements org.opendroidpdf.app.ui.FullscreenController.Host {
    private final OpenDroidPDFActivity activity;

    public FullscreenHostAdapter(OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Override public androidx.appcompat.app.AppCompatActivity getActivity() { return activity; }
    @Override public org.opendroidpdf.MuPDFReaderView getDocView() { return activity.getDocView(); }
    @Override public org.opendroidpdf.OpenDroidPDFCore getCore() { return activity.getCore(); }
    @Override public void saveViewport(android.net.Uri uri) { if (activity.getViewportController() != null) activity.getViewportController().saveViewport(uri); }
    @Override public void setupDocView() { activity.setupDocView(); }
    @Override public void setActionBarModeHidden() { activity.getActionBarModeDelegate().setHidden(); }
    @Override public void setActionBarModeMainIfHidden() { activity.getActionBarModeDelegate().setMainIfHidden(); }
    @Override public void invalidateOptionsMenu() { activity.invalidateOptionsMenuSafely(); }
}
