package org.opendroidpdf.app.comments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.core.MuPdfRepository;

import java.util.List;

/**
 * Next/Previous comment navigation (Acrobat-ish), shared across embedded + sidecar annotations.
 */
public final class CommentsNavigationController {

    public static final class SelectionKey {
        final CommentsIndex.Backend backend;
        final long embeddedObjectNumber;
        @Nullable final SidecarSelectionController.Kind sidecarKind;
        @Nullable final String sidecarId;

        SelectionKey(CommentsIndex.Backend backend,
                     long embeddedObjectNumber,
                     @Nullable SidecarSelectionController.Kind sidecarKind,
                     @Nullable String sidecarId) {
            this.backend = backend;
            this.embeddedObjectNumber = embeddedObjectNumber;
            this.sidecarKind = sidecarKind;
            this.sidecarId = sidecarId;
        }
    }

    public void navigate(@NonNull AppCompatActivity activity,
                         @NonNull MuPDFReaderView docView,
                         @NonNull MuPdfRepository repo,
                         @Nullable SidecarAnnotationProvider sidecarProvider,
                         int direction) {
        final int dir = direction >= 0 ? 1 : -1;
        final int currentPage = docView.getSelectedItemPosition();
        final SelectionKey selectionKey = selectionKeyFromCurrent(docView);

        navigateFrom(activity, docView, repo, sidecarProvider, dir, selectionKey, currentPage);
    }

    @Nullable
    public static SelectionKey captureCurrentSelection(@NonNull MuPDFReaderView docView) {
        return selectionKeyFromCurrent(docView);
    }

    public void navigateFrom(@NonNull AppCompatActivity activity,
                             @NonNull MuPDFReaderView docView,
                             @NonNull MuPdfRepository repo,
                             @Nullable SidecarAnnotationProvider sidecarProvider,
                             int direction,
                             @Nullable SelectionKey selectionKey,
                             int currentPage) {
        final int dir = direction >= 0 ? 1 : -1;
        final int page = Math.max(0, currentPage);
        AppCoroutines.launchIo(AppCoroutines.ioScope(), () -> {
            List<CommentsIndex.Entry> entries = CommentsIndex.load(repo, sidecarProvider);
            AppCoroutines.launchMain(AppCoroutines.mainScope(), () -> {
                if (entries == null || entries.isEmpty()) return;
                int idx = findStartIndex(entries, selectionKey, page, dir);
                if (idx < 0) return;
                int next = idx + dir;
                if (next < 0) next = entries.size() - 1;
                if (next >= entries.size()) next = 0;
                CommentsNavigator.jumpTo(docView, entries.get(next));
            });
        });
    }

    @Nullable
    private static SelectionKey selectionKeyFromCurrent(@NonNull MuPDFReaderView docView) {
        try {
            android.view.View v = docView.getSelectedView();
            if (!(v instanceof MuPDFPageView)) return null;
            MuPDFPageView pageView = (MuPDFPageView) v;

            SidecarSelectionController.Selection sidecarSel = pageView.selectedSidecarSelectionOrNull();
            if (sidecarSel != null && sidecarSel.id != null && !sidecarSel.id.trim().isEmpty()) {
                return new SelectionKey(
                        CommentsIndex.Backend.SIDECAR,
                        -1L,
                        sidecarSel.kind,
                        sidecarSel.id);
            }

            Annotation embedded = pageView.textAnnotationDelegate().selectedEmbeddedAnnotationOrNull();
            if (embedded != null && embedded.objectNumber > 0L) {
                return new SelectionKey(CommentsIndex.Backend.EMBEDDED, embedded.objectNumber, null, null);
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static int findStartIndex(@NonNull List<CommentsIndex.Entry> entries,
                                      @Nullable SelectionKey selected,
                                      int currentPage,
                                      int dir) {
        if (entries.isEmpty()) return -1;

        if (selected != null) {
            for (int i = 0; i < entries.size(); i++) {
                CommentsIndex.Entry e = entries.get(i);
                if (e == null) continue;
                if (selected.backend != e.backend) continue;
                switch (selected.backend) {
                    case EMBEDDED:
                        if (selected.embeddedObjectNumber > 0L && selected.embeddedObjectNumber == e.embeddedObjectNumber) return i;
                        break;
                    case SIDECAR:
                        if (selected.sidecarKind == e.sidecarKind && selected.sidecarId != null && selected.sidecarId.equals(e.sidecarId)) return i;
                        break;
                }
            }
        }

        // No explicit selection: anchor on current page.
        if (dir > 0) {
            for (int i = 0; i < entries.size(); i++) {
                CommentsIndex.Entry e = entries.get(i);
                if (e != null && e.pageIndex >= currentPage) return i - 1; // so +1 lands on this entry
            }
            return entries.size() - 1;
        } else {
            for (int i = entries.size() - 1; i >= 0; i--) {
                CommentsIndex.Entry e = entries.get(i);
                if (e != null && e.pageIndex <= currentPage) return i + 1; // so -1 lands on this entry
            }
            return 0;
        }
    }
}
