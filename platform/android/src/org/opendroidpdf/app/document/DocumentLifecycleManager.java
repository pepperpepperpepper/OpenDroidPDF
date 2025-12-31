package org.opendroidpdf.app.document;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.lifecycle.ActivityComposition;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.app.services.SearchService;
import org.opendroidpdf.app.diagnostics.AppLog;
import org.opendroidpdf.app.diagnostics.SessionDiagnostics;

/**
 * Centralises document/core setup and teardown so the host activity can stay slimmer.
 */
public class DocumentLifecycleManager {
    public interface Host {
        Context context();
        @Nullable androidx.appcompat.app.AlertDialog.Builder alertBuilder();
    }

    private final Host host;
    private final CoreInstanceCoordinator coreCoordinator;
    private final ActivityComposition.Composition comp;
    private final AppServices appServices;
    private final SearchService searchService;
    @Nullable private DocumentIdentity currentDocumentIdentity;
    private boolean canSaveToCurrentUriCached;
    private boolean canSaveToCurrentUriCacheValid;
    private boolean saveToCurrentUriDisabledByPolicy;
    private boolean saveToCurrentUriFailureOverride;
    private DocumentOrigin currentDocumentOrigin = DocumentOrigin.NATIVE;

    public DocumentLifecycleManager(Host host,
                                    CoreInstanceCoordinator coreCoordinator,
                                    ActivityComposition.Composition comp) {
        this.host = host;
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
    public String currentDocumentName() {
        Context ctx = host != null ? host.context() : null;
        if (coreCoordinator != null && ctx != null) return coreCoordinator.currentDocumentName(ctx);
        return ctx != null ? ctx.getString(org.opendroidpdf.R.string.app_name) : "OpenDroidPDF";
    }
    public boolean hasUnsavedChanges() { return coreCoordinator != null && coreCoordinator.hasUnsavedChanges(); }
    public boolean canSaveToCurrentUri() {
        if (saveToCurrentUriDisabledByPolicy) return false;
        if (saveToCurrentUriFailureOverride) return false;
        if (!canSaveToCurrentUriCacheValid) {
            canSaveToCurrentUriCached = computeCanSaveToCurrentUri();
            canSaveToCurrentUriCacheValid = true;
        }
        return canSaveToCurrentUriCached;
    }

    public void setSaveToCurrentUriDisabledByPolicy(boolean disabled) {
        saveToCurrentUriDisabledByPolicy = disabled;
    }

    public boolean isSaveToCurrentUriDisabledByPolicy() {
        return saveToCurrentUriDisabledByPolicy;
    }

    public boolean markSaveToCurrentUriFailureOverride() {
        if (saveToCurrentUriFailureOverride) return false;
        saveToCurrentUriFailureOverride = true;
        return true;
    }

    public boolean clearSaveToCurrentUriFailureOverride() {
        if (!saveToCurrentUriFailureOverride) return false;
        saveToCurrentUriFailureOverride = false;
        return true;
    }

    public void setCurrentDocumentIdentity(@Nullable DocumentIdentity identity) {
        this.currentDocumentIdentity = identity;
    }

    public void setCurrentDocumentOrigin(@Nullable DocumentOrigin origin) {
        this.currentDocumentOrigin = origin != null ? origin : DocumentOrigin.NATIVE;
    }

    public DocumentOrigin currentDocumentOrigin() {
        return currentDocumentOrigin != null ? currentDocumentOrigin : DocumentOrigin.NATIVE;
    }

    @Nullable
    public DocumentIdentity currentDocumentIdentityOrNull() {
        ensureCurrentDocumentIdentity();
        return currentDocumentIdentity;
    }

    private void ensureCurrentDocumentIdentity() {
        if (currentDocumentIdentity != null) return;
        Uri uri = currentDocumentUri();
        if (uri == null) return;
        try {
            Context ctx = host != null ? host.context() : null;
            if (ctx != null) currentDocumentIdentity = DocumentIdentityResolver.resolve(ctx, uri);
        } catch (Throwable ignore) {
        }
    }

    public DocumentState documentState() {
        Context ctx = host != null ? host.context() : null;
        if (coreCoordinator == null) {
            return DocumentState.empty(ctx != null ? ctx.getString(org.opendroidpdf.R.string.app_name) : "OpenDroidPDF");
        }

        MuPdfRepository repo = coreCoordinator.getRepository();
        Uri uri = repo != null ? repo.getDocumentUri() : coreCoordinator.currentDocumentUri();
        String name = repo != null ? repo.getDocumentName()
                : (ctx != null ? coreCoordinator.currentDocumentName(ctx) : "OpenDroidPDF");
        int pageCount = repo != null ? repo.getPageCount() : 0;
        boolean dirty = repo != null ? repo.hasUnsavedChanges() : coreCoordinator.hasUnsavedChanges();
        boolean canSave = canSaveToCurrentUri();
        return new DocumentState(uri, name, pageCount, dirty, canSave, currentDocumentOrigin());
    }

    public void resetDocumentStateForIntent() {
        if (coreCoordinator != null && coreCoordinator.getCore() != null) {
            coreCoordinator.destroyCoreNow(appServices, host != null ? host.alertBuilder() : null);
        }
        currentDocumentIdentity = null;
        currentDocumentOrigin = DocumentOrigin.NATIVE;
        saveToCurrentUriDisabledByPolicy = false;
        saveToCurrentUriFailureOverride = false;
        invalidateSaveCapabilityCache();
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
        Context ctx = host != null ? host.context() : null;
        if (ctx != null) {
            SessionDiagnostics.markDocumentOpened(ctx);
            if (uri != null) AppLog.i("DocumentLifecycle", "setupCore uri=" + uri + " scheme=" + uri.getScheme());
        }
        if (ctx != null) comp.documentSetupController.setupCore(ctx, uri);
    }

    public void setupSearchSession(@Nullable MuPDFReaderView docView) {
        if (comp == null || comp.documentSetupController == null || docView == null) return;
        comp.documentSetupController.setupSearchSession(docView);
    }

    public void setCoreInstance(OpenDroidPDFCore newCore) {
        if (coreCoordinator != null) {
            coreCoordinator.setCoreInstance(newCore, appServices, host != null ? host.alertBuilder() : null);
        }
        currentDocumentIdentity = null;
        currentDocumentOrigin = DocumentOrigin.NATIVE;
        saveToCurrentUriDisabledByPolicy = false;
        saveToCurrentUriFailureOverride = false;
        invalidateSaveCapabilityCache();
    }

    public void setCoreFromLastNonConfig(OpenDroidPDFCore last) {
        if (coreCoordinator == null) return;
        coreCoordinator.setCoreInstance(last, appServices, host != null ? host.alertBuilder() : null);
        currentDocumentIdentity = null;
        currentDocumentOrigin = DocumentOrigin.NATIVE;
        saveToCurrentUriDisabledByPolicy = false;
        saveToCurrentUriFailureOverride = false;
        invalidateSaveCapabilityCache();
        if (coreCoordinator.getCore() != null && comp != null && comp.documentViewDelegate != null) {
            comp.documentViewDelegate.markDocViewNeedsNewAdapter();
        }
    }

    public void destroyCoreNow() {
        if (coreCoordinator != null) {
            coreCoordinator.destroyCoreNow(appServices, host != null ? host.alertBuilder() : null);
        }
        currentDocumentIdentity = null;
        currentDocumentOrigin = DocumentOrigin.NATIVE;
        saveToCurrentUriDisabledByPolicy = false;
        saveToCurrentUriFailureOverride = false;
        invalidateSaveCapabilityCache();
        if (searchService != null) searchService.clearDocument();
    }

    /** Recomputes and caches whether we can save back to the document's current URI. */
    public void refreshSaveCapabilityCache() {
        canSaveToCurrentUriCached = computeCanSaveToCurrentUri();
        canSaveToCurrentUriCacheValid = true;
    }

    /** Invalidates the cached save-capability state; next access recomputes it. */
    public void invalidateSaveCapabilityCache() {
        canSaveToCurrentUriCacheValid = false;
    }

    private boolean computeCanSaveToCurrentUri() {
        return coreCoordinator != null && coreCoordinator.canSaveToCurrentUri();
    }
}
