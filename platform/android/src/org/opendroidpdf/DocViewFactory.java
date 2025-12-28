package org.opendroidpdf;

import android.view.KeyEvent;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.app.annotation.TextAnnotationController;
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
            private final TextAnnotationController textAnnotationController = new TextAnnotationController(new TextAnnotationController.Host() {
                @Override public AppCompatActivity activity() { return activity; }
                @Override public AlertDialog.Builder alertBuilder() { return host != null ? host.alertBuilder() : null; }
                @Override public MuPDFPageView currentPageView() {
                    final View selected = getSelectedView();
                    return selected instanceof MuPDFPageView ? (MuPDFPageView) selected : null;
                }
            });

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
            }

            @Override
            protected void onTapMainDocArea() {
                if (actionBarHost.isEdit() || actionBarHost.isAddingTextAnnot()) {
                    actionBarHost.setMode(ActionBarMode.Main);
                    actionBarHost.invalidateOptionsMenuSafely();
                }
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
            protected void onDocMotion() { }

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
                        actionBarHost.setMode(ActionBarMode.Edit);
                        actionBarHost.invalidateOptionsMenuSafely();
                        break;
                    case TextAnnotation:
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
            }

            @Override
            public boolean onKeyDown(int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
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
        };
    }
}
