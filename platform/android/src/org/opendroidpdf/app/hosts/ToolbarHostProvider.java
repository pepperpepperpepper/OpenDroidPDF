package org.opendroidpdf.app.hosts;

import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.hosts.ToolbarHostAdapter.Provider;

public class ToolbarHostProvider implements Provider {
    private final OpenDroidPDFActivity activity;

    public ToolbarHostProvider(OpenDroidPDFActivity activity) { this.activity = activity; }

    @Override public boolean hasOpenDocument() { return activity.hasCore(); }
    @Override public boolean hasUnsavedChanges() { return activity.getRepository() != null && activity.getRepository().hasUnsavedChanges(); }
    @Override public boolean hasDocumentView() { return activity.getDocView() != null; }
    @Override public boolean hasLinkTarget() { return activity.isLinkBackAvailable(); }
    @Override public boolean isViewingNoteDocument() { return activity.isCurrentNoteDocument(); }
    @Override public boolean isPreparingOptionsMenu() { return activity.isPreparingOptionsMenu(); }
    @Override public MuPDFPageView currentPageView() { return activity.currentPageViewPublic(); }
    @Override public void invalidateOptionsMenu() { activity.invalidateOptionsMenuSafely(); }
}

