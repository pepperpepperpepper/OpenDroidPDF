package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.document.DocumentToolbarController;
import org.opendroidpdf.app.document.DocumentType;
import org.opendroidpdf.app.epub.EpubTocParser;
import org.opendroidpdf.app.navigation.DashboardDelegate;
import org.opendroidpdf.app.navigation.LinkBackHelper;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;

/** Adapter so DocumentToolbarController.Host doesn't bloat the activity. */
public final class DocumentToolbarHostAdapter implements DocumentToolbarController.Host {
    private final OpenDroidPDFActivity activity;
    private final DocumentViewHostAdapter documentViewHostAdapter;
    private final LinkBackHelper linkBackHelper;
    private final ExportController exportController;

    public DocumentToolbarHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                      @NonNull DocumentViewHostAdapter documentViewHostAdapter,
                                      @NonNull ExportController exportController,
                                      @NonNull LinkBackHelper linkBackHelper) {
        this.activity = activity;
        this.documentViewHostAdapter = documentViewHostAdapter;
        this.exportController = exportController;
        this.linkBackHelper = linkBackHelper;
    }

    @Override public boolean hasDocumentLoaded() { return activity.hasDocumentLoaded(); }
    @Override public boolean hasDocumentView() { return activity.getDocView() != null; }
    @Override public boolean isViewingNoteDocument() { return activity.isCurrentNoteDocument(); }
    @Override public boolean isLinkBackAvailable() { return activity.isLinkBackAvailable(); }
    @NonNull @Override public androidx.appcompat.app.AppCompatActivity getActivity() { return activity; }
    @NonNull @Override public AlertDialog.Builder alertBuilder() { return activity.getAlertBuilder(); }
    @NonNull @Override public MuPDFReaderView getDocView() { return activity.getDocView(); }
    @Override public void requestAddBlankPage() {
        org.opendroidpdf.core.MuPdfRepository repo = activity.getRepository();
        org.opendroidpdf.MuPDFReaderView doc = activity.getDocView();
        if (repo == null || doc == null) return;
        if (repo.insertBlankPageAtEnd()) {
            int lastPage = Math.max(0, repo.getPageCount() - 1);
            doc.setDisplayedViewIndex(lastPage, true);
            doc.setScale(1.0f);
            doc.setNormalizedScroll(0.0f, 0.0f);
            activity.invalidateOptionsMenuSafely();
        }
    }
    @Override public void requestFullscreen() {
        new org.opendroidpdf.app.ui.FullscreenController()
                .enterFullscreen(new org.opendroidpdf.app.hosts.FullscreenHostAdapter(activity));
    }
    @Override public void requestSettings() {
        android.content.Intent intent = new android.content.Intent(activity, org.opendroidpdf.SettingsActivity.class);
        activity.startActivity(intent);
        activity.overridePendingTransition(org.opendroidpdf.R.animator.enter_from_left, org.opendroidpdf.R.animator.fade_out);
    }
    @Override public void requestReadingSettings() {
        org.opendroidpdf.app.lifecycle.ActivityComposition.Composition comp = activity.getComposition();
        if (comp == null || comp.reflowPrefsStore == null) return;
        new org.opendroidpdf.app.reflow.ReflowSettingsController(activity, documentViewHostAdapter, comp.reflowPrefsStore, comp.documentViewDelegate)
                .showForCurrentDocument();
    }
    @Override public void requestTableOfContents() {
        org.opendroidpdf.OpenDroidPDFCore core = activity.getCore();
        org.opendroidpdf.MuPDFReaderView doc = activity.getDocView();
        if (core == null || doc == null) return;
        try {
            org.opendroidpdf.OutlineItem[] outline = core.getOutline();
            if (outline == null || outline.length == 0) {
                if (DocumentType.fromFileFormat(core.fileFormat()) == DocumentType.EPUB) {
                    if (showEpubTocOrFallback(core, doc)) return;
                }
                activity.showInfo(activity.getString(org.opendroidpdf.R.string.toc_empty));
                return;
            }
            CharSequence[] items = new CharSequence[outline.length];
            int[] pages = new int[outline.length];
            for (int i = 0; i < outline.length; i++) {
                org.opendroidpdf.OutlineItem it = outline[i];
                pages[i] = it.page;
                StringBuilder sb = new StringBuilder();
                int indent = Math.max(0, it.level);
                for (int j = 0; j < indent; j++) sb.append("  ");
                sb.append(it.title != null ? it.title : "");
                items[i] = sb.toString();
            }
            new androidx.appcompat.app.AlertDialog.Builder(activity)
                    .setTitle(org.opendroidpdf.R.string.menu_toc)
                    .setItems(items, (d, which) -> {
                        int page = pages[which];
                        doc.setDisplayedViewIndex(page, true);
                        doc.setNormalizedScroll(0.0f, 0.0f);
                        activity.invalidateOptionsMenuSafely();
                    })
                    .show();
        } catch (Throwable t) {
            activity.showInfo(activity.getString(org.opendroidpdf.R.string.toc_empty));
        }
    }
    @Override public void requestPrint() {
        if (maybeBlockExportForReflowMismatch()) return;
        if (exportController != null) exportController.printDoc();
    }
    @Override public void requestShare() {
        if (maybeBlockExportForReflowMismatch()) return;
        if (exportController != null) exportController.shareDoc();
    }
    @Override public void requestSearchMode() { new org.opendroidpdf.SearchModeHostAdapter(activity).requestSearchMode(); }
    @Override public void requestDashboard() {
        DashboardDelegate dd = activity.getDashboardDelegate();
        if (dd != null) dd.showDashboardIfAvailable();
    }
    @Override public void requestDeleteNote() {
        org.opendroidpdf.app.notes.NotesController nc = activity.getNotesController();
        if (nc != null) nc.requestDeleteNote();
    }
    @Override public void requestSaveDialog() {
        if (exportController != null) exportController.saveDoc();
    }
    @Override public void requestLinkBackNavigation() {
        new LinkBackHostAdapter(activity, linkBackHelper).requestLinkBackNavigation();
    }

    /**
     * If the user changed EPUB layout after annotating, export under the current layout can
     * silently omit marks. Block export and offer a one-tap switch back to the annotated layout.
     */
    private boolean maybeBlockExportForReflowMismatch() {
        if (documentViewHostAdapter.currentDocumentType() != DocumentType.EPUB) return false;
        SidecarAnnotationProvider provider = documentViewHostAdapter.sidecarAnnotationProviderOrNull();
        if (!(provider instanceof SidecarAnnotationSession)) return false;
        SidecarAnnotationSession session = (SidecarAnnotationSession) provider;

        if (!session.hasAnnotationsInOtherLayouts()) return false;
        // If there are annotations in this layout too, allow exporting the visible state.
        if (session.hasAnyAnnotationsInCurrentLayout()) return false;

        org.opendroidpdf.app.lifecycle.ActivityComposition.Composition comp = activity.getComposition();
        if (comp == null || comp.reflowPrefsStore == null) {
            activity.showInfo(activity.getString(org.opendroidpdf.R.string.reflow_annotations_hidden));
            return true;
        }

        if (comp.reflowPrefsStore.loadAnnotatedLayoutOrNull(session.docId()) == null) {
            activity.showInfo(activity.getString(org.opendroidpdf.R.string.reflow_annotations_hidden));
            return true;
        }

        org.opendroidpdf.app.ui.UiStateDelegate ui = activity.getUiStateDelegate();
        if (ui != null) {
            ui.showReflowLayoutMismatchBanner(
                    org.opendroidpdf.R.string.reflow_annotations_hidden,
                    () -> new org.opendroidpdf.app.reflow.ReflowSettingsController(activity, documentViewHostAdapter, comp.reflowPrefsStore, comp.documentViewDelegate)
                            .applyAnnotatedLayoutForCurrentDocument());
        } else {
            activity.showInfo(activity.getString(org.opendroidpdf.R.string.reflow_annotations_hidden));
        }
        return true;
    }

    private boolean showEpubTocOrFallback(org.opendroidpdf.OpenDroidPDFCore core,
                                         org.opendroidpdf.MuPDFReaderView doc) {
        String path = core.getPath();
        if (path == null || path.trim().isEmpty()) return false;

        java.util.List<EpubTocParser.TocEntry> toc = EpubTocParser.parseFromEpubPath(path);
        if (toc.isEmpty()) return false;

        java.util.ArrayList<CharSequence> items = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> pages = new java.util.ArrayList<>();
        for (EpubTocParser.TocEntry e : toc) {
            int page = core.resolveLinkPage(e.href);
            if (page < 0) continue;
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < Math.max(0, e.level); j++) sb.append("  ");
            sb.append(e.title);
            items.add(sb.toString());
            pages.add(page);
        }
        if (items.isEmpty()) return false;

        CharSequence[] itemsArr = items.toArray(new CharSequence[0]);
        int[] pagesArr = new int[pages.size()];
        for (int i = 0; i < pages.size(); i++) pagesArr[i] = pages.get(i);

        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle(org.opendroidpdf.R.string.menu_toc)
                .setItems(itemsArr, (d, which) -> {
                    int page = pagesArr[which];
                    doc.setDisplayedViewIndex(page, true);
                    doc.setNormalizedScroll(0.0f, 0.0f);
                    activity.invalidateOptionsMenuSafely();
                })
                .show();
        return true;
    }
}
