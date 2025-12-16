package org.opendroidpdf.app.document;

import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.lifecycle.ActivityComposition;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.app.services.SearchService;

/**
 * Centralises document/core setup and teardown so OpenDroidPDFActivity can stay slimmer.
 */
public class DocumentLifecycleManager {
    private final OpenDroidPDFActivity activity;
    private final CoreInstanceCoordinator coreCoordinator;
    private final ActivityComposition.Composition comp;
    private final AppServices appServices;
    private final SearchService searchService;

    public DocumentLifecycleManager(OpenDroidPDFActivity activity,
                                    CoreInstanceCoordinator coreCoordinator,
                                    ActivityComposition.Composition comp) {
        this.activity = activity;
        this.coreCoordinator = coreCoordinator;
        this.comp = comp;
        this.appServices = comp != null ? comp.appServices : null;
        this.searchService = comp != null ? comp.searchService : null;
    }

    public boolean hasCore() { return coreCoordinator != null && coreCoordinator.hasCore(); }
    public boolean hasRepository() { return coreCoordinator != null && coreCoordinator.hasRepository(); }
    @Nullable public MuPdfRepository getRepository() { return coreCoordinator != null ? coreCoordinator.getRepository() : null; }
    @Nullable public MuPdfController getMuPdfController() { return coreCoordinator != null ? coreCoordinator.getMuPdfController() : null; }
    @Nullable public Uri currentDocumentUri() { return coreCoordinator != null ? coreCoordinator.currentDocumentUri() : null; }
    public String currentDocumentName() { return coreCoordinator != null ? coreCoordinator.currentDocumentName(activity) : activity.getString(org.opendroidpdf.R.string.app_name); }
    public boolean hasUnsavedChanges() { return coreCoordinator != null && coreCoordinator.hasUnsavedChanges(); }
    public boolean canSaveToCurrentUri() { return coreCoordinator != null && coreCoordinator.canSaveToCurrentUri(activity); }

    public void resetDocumentStateForIntent() {
        if (coreCoordinator != null && coreCoordinator.getCore() != null) {
            coreCoordinator.destroyCoreNow(appServices, activity.alertBuilder());
        }
        if (searchService != null) searchService.clearDocument();
        if (comp != null && comp.documentViewDelegate != null) {
            comp.documentViewDelegate.markDocViewNeedsNewAdapter();
        }
    }

    public void setupCore(Intent intent) {
        if (coreCoordinator == null || comp == null || comp.documentSetupController == null) return;
        if (coreCoordinator.getCore() != null) return;

        comp.documentViewDelegate.markDocViewNeedsNewAdapter();
        Uri uri = intent != null ? intent.getData() : null;
        comp.documentSetupController.setupCore(activity, uri);
    }

    public void setupSearchSession(@Nullable MuPDFReaderView docView) {
        if (comp == null || comp.documentSetupController == null || docView == null) return;
        comp.documentSetupController.setupSearchSession(docView);
    }

    public void setCoreInstance(OpenDroidPDFCore newCore) {
        if (coreCoordinator != null) {
            coreCoordinator.setCoreInstance(newCore, appServices, activity.alertBuilder());
        }
    }

    public void setCoreFromLastNonConfig(OpenDroidPDFCore last) {
        if (coreCoordinator != null) {
            coreCoordinator.setCoreFromLastNonConfig(last);
            if (coreCoordinator.getCore() != null && comp != null && comp.documentViewDelegate != null) {
                comp.documentViewDelegate.markDocViewNeedsNewAdapter();
            }
        }
    }

    public void destroyCoreNow() {
        if (coreCoordinator != null) {
            coreCoordinator.destroyCoreNow(appServices, activity.alertBuilder());
        }
        if (searchService != null) searchService.clearDocument();
    }
}
