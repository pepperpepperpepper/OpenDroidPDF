package org.opendroidpdf;

import android.view.KeyEvent;
import android.view.View;
import android.widget.Adapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.app.annotation.TextAnnotationController;
import org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController;
import org.opendroidpdf.app.annotation.TextAnnotationQuickActionsController;
import org.opendroidpdf.app.ui.ActionBarHost;
import org.opendroidpdf.app.ui.ActionBarMode;
import org.opendroidpdf.app.reader.gesture.ReaderMode;

/**
 * Factory for creating the MuPDFReaderView with activity wiring.
 * Kept minimal to match existing inline overrides.
 */
public final class DocViewFactory {
    private DocViewFactory() {}

    public interface Host {
        AppCompatActivity activity();
        ActionBarHost actionBarHost();
        AlertDialog.Builder alertBuilder();
        void setTitle();
        void rememberPreLinkHitViewport(int page, float scale, float x, float y);
    }

    public static MuPDFReaderView create(final Host host) {
        final AppCompatActivity activity = host != null ? host.activity() : null;
        final ActionBarHost actionBarHost = host != null ? host.actionBarHost() : null;
        return new MuPDFReaderView(activity) {
            private final MuPDFReaderView self = this;
            private final TextAnnotationController textAnnotationController = new TextAnnotationController(new TextAnnotationController.Host() {
                @Override public AppCompatActivity activity() { return activity; }
                @Override public AlertDialog.Builder alertBuilder() { return host != null ? host.alertBuilder() : null; }
                @Override public MuPDFPageView currentPageView() {
                    final View selected = getSelectedView();
                    return selected instanceof MuPDFPageView ? (MuPDFPageView) selected : null;
                }
                @Override public MuPDFReaderView docViewOrNull() { return self; }
                @Override public org.opendroidpdf.core.MuPdfRepository repoOrNull() {
                    if (activity instanceof OpenDroidPDFActivity) {
                        try { return ((OpenDroidPDFActivity) activity).getRepository(); } catch (Throwable ignore) {}
                    }
                    return null;
                }
                @Override public org.opendroidpdf.app.sidecar.SidecarAnnotationProvider sidecarAnnotationProviderOrNull() {
                    try {
                        Adapter adapter = getAdapter();
                        if (adapter instanceof MuPDFPageAdapter) {
                            return ((MuPDFPageAdapter) adapter).sidecarSessionOrNull();
                        }
                    } catch (Throwable ignore) {
                    }
                    return null;
                }
            });
            private final TextAnnotationQuickActionsController textQuickActions =
                    new TextAnnotationQuickActionsController(new TextAnnotationQuickActionsController.Host() {
                        @NonNull @Override public AppCompatActivity activity() { return activity; }
                        @Override public MuPDFPageView currentPageView() {
                            final View selected = getSelectedView();
                            return selected instanceof MuPDFPageView ? (MuPDFPageView) selected : null;
                        }
                        @Override public void requestMode(@NonNull ReaderMode mode) {
                            try { self.setMode(mode); } catch (Throwable ignore) {}
                        }
                        @Override public void showInfo(@NonNull String message) {
                            try {
                                if (activity instanceof OpenDroidPDFActivity) {
                                    ((OpenDroidPDFActivity) activity).showInfo(message);
                                    return;
                                }
                            } catch (Throwable ignore) {
                            }
                            try {
                                android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show();
                            } catch (Throwable ignore) {
                            }
                        }
                        @Override public void invalidateQuickActions() {
                            try { textQuickActions.refresh(); } catch (Throwable ignore) {}
                        }
                    });
            private final TextAnnotationMultiSelectController textMultiSelect =
                    new TextAnnotationMultiSelectController(new TextAnnotationMultiSelectController.Host() {
                        @NonNull @Override public AppCompatActivity activity() { return activity; }
                        @Override public MuPDFPageView currentPageView() {
                            final View selected = getSelectedView();
                            return selected instanceof MuPDFPageView ? (MuPDFPageView) selected : null;
                        }
                        @Override public void showInfo(@NonNull String message) {
                            try {
                                if (activity instanceof OpenDroidPDFActivity) {
                                    ((OpenDroidPDFActivity) activity).showInfo(message);
                                    return;
                                }
                            } catch (Throwable ignore) {
                            }
                            try { android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
                        }
                        @Override public void invalidateQuickActions() {
                            try { textQuickActions.refresh(); } catch (Throwable ignore) {}
                        }
                    });
            {
                try { textQuickActions.setMultiSelectController(textMultiSelect); } catch (Throwable ignore) {}
                try { setTextAnnotationMultiSelectController(textMultiSelect); } catch (Throwable ignore) {}
            }

            @Override
            public void setMode(ReaderMode m) {
                super.setMode(m);
                if (actionBarHost == null) return;
                switch (m) {
                    case VIEWING: actionBarHost.setMode(ActionBarMode.Main); break;
                    case SEARCHING: actionBarHost.setMode(ActionBarMode.Search); break;
                    case DRAWING:
                    case ERASING: actionBarHost.setMode(ActionBarMode.Annot); break;
                    case SELECTING: actionBarHost.setMode(ActionBarMode.Selection); break;
                    case ADDING_TEXT_ANNOT: actionBarHost.setMode(ActionBarMode.AddingTextAnnot); break;
                }
                actionBarHost.invalidateOptionsMenuSafely();
            }

            @Override
            protected void onMoveToChild(int pageNumber) {
                if (host != null) host.setTitle();
                if (actionBarHost.isEdit()) {
                    actionBarHost.setMode(ActionBarMode.Main);
                    actionBarHost.invalidateOptionsMenuSafely();
                }
                try { textQuickActions.dismiss(); } catch (Throwable ignore) {}
                try { textMultiSelect.clear(); } catch (Throwable ignore) {}
            }

            @Override
            protected void onTapMainDocArea() {
                if (actionBarHost.isEdit() || actionBarHost.isAddingTextAnnot()) {
                    actionBarHost.setMode(ActionBarMode.Main);
                    actionBarHost.invalidateOptionsMenuSafely();
                }
                try { textQuickActions.refresh(); } catch (Throwable ignore) {}
            }

            @Override
            protected void onTapTopLeftMargin() {
                org.opendroidpdf.app.navigation.NavigationUiHelper.tapTopLeft(activity, this);
            }

            @Override
            protected void onBottomRightMargin() {
                org.opendroidpdf.app.navigation.NavigationUiHelper.tapBottomRight(activity, this);
            }

            @Override
            protected void onDocMotion() {
                try { textQuickActions.refresh(); } catch (Throwable ignore) {}
            }

            @Override
            protected void addTextAnnotFromUserInput(final Annotation annot) {
                textAnnotationController.requestTextAnnotationFromUserInput(annot);
            }

            @Override
            protected void onHit(Hit item) {
                if (actionBarHost == null) return;
                switch(item){
                    case Annotation:
                    case InkAnnotation:
                    case TextAnnotation:
                        // Keep reading chrome stable; selection-specific actions are surfaced via
                        // the contextual quick-actions popup rather than a separate top-bar mode.
                        try {
                            if (getMode() == ReaderMode.VIEWING && !actionBarHost.isSearchOrHidden()) {
                                actionBarHost.setMode(ActionBarMode.Main);
                                actionBarHost.invalidateOptionsMenuSafely();
                            }
                        } catch (Throwable ignore) {}
                        break;
                    case Nothing:
                        if(!actionBarHost.isSearchOrHidden()) {
                            actionBarHost.setMode(ActionBarMode.Main);
                            actionBarHost.invalidateOptionsMenuSafely();
                        }
                        break;
                    case LinkInternal:
                        if(linksEnabled()) {
                            if (host != null) {
                                host.rememberPreLinkHitViewport(
                                        getSelectedItemPosition(),
                                        getNormalizedScale(),
                                        getNormalizedXScroll(),
                                        getNormalizedYScroll());
                            }
                        }
                        actionBarHost.setMode(ActionBarMode.Main);
                        actionBarHost.invalidateOptionsMenuSafely();
                        break;
                }
                try { textQuickActions.refresh(); } catch (Throwable ignore) {}
            }

            @Override
            public boolean onKeyDown(int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    try {
                        if (getMode() == ReaderMode.VIEWING) {
                            final View selected = getSelectedView();
                            if (selected instanceof MuPDFPageView) {
                                MuPDFPageView pv = (MuPDFPageView) selected;
                                if (pv.getItemSelectBox() != null) {
                                    try { pv.deselectAnnotation(); } catch (Throwable ignore) {}
                                    try { pv.deselectText(); } catch (Throwable ignore) {}
                                    try { textQuickActions.dismiss(); } catch (Throwable ignore) {}
                                    return true;
                                }
                            }
                        }
                    } catch (Throwable ignore) {}
                    if(!actionBarHost.isSearchOrHidden()) {
                        actionBarHost.setMode(ActionBarMode.Main);
                        actionBarHost.invalidateOptionsMenuSafely();
                    }
                    return false;
                }
                return super.onKeyDown(keyCode, event);
            }

            @Override
            protected void onNumberOfStrokesChanged(int numberOfStrokes) {
                if (actionBarHost != null) actionBarHost.invalidateOptionsMenu();
            }

            @Override
            protected void onDetachedFromWindow() {
                try { textQuickActions.dismiss(); } catch (Throwable ignore) {}
                super.onDetachedFromWindow();
            }
        };
    }
}
