package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.DocViewFactory;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.ui.ActionBarHost;

/** Binds {@link DocViewFactory.Host} to the activity. */
public final class DocViewFactoryHostAdapter implements DocViewFactory.Host {
    private final OpenDroidPDFActivity activity;
    private final ActionBarHost actionBarHost;

    public DocViewFactoryHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
        this.actionBarHost = new ActionBarHostAdapter(activity);
    }

    @Override public AppCompatActivity activity() { return activity; }
    @Override public ActionBarHost actionBarHost() { return actionBarHost; }
    @Override public AlertDialog.Builder alertBuilder() { return activity.getAlertBuilder(); }
    @Override public void setTitle() { activity.setTitle(); }

    @Override
    public void rememberPreLinkHitViewport(int page, float scale, float x, float y) {
        activity.rememberPreLinkHitViewport(page, scale, x, y);
    }
}
