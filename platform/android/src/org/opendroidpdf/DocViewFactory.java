package org.opendroidpdf;

import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.app.ui.ActionBarHost;
import org.opendroidpdf.app.ui.ActionBarMode;

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
            @Override
            public void setMode(Mode m) {
                super.setMode(m);
                if (actionBarHost == null) return;
                switch (m) {
                    case Viewing: actionBarHost.setMode(ActionBarMode.Main); break;
                    case Searching: actionBarHost.setMode(ActionBarMode.Search); break;
                    case Drawing:
                    case Erasing: actionBarHost.setMode(ActionBarMode.Annot); break;
                    case Selecting: actionBarHost.setMode(ActionBarMode.Selection); break;
                    case AddingTextAnnot: actionBarHost.setMode(ActionBarMode.AddingTextAnnot); break;
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
                final AlertDialog.Builder builder = host != null ? host.alertBuilder() : null;
                if (builder == null || activity == null) return;
                final AlertDialog dialog = builder.create();
                final View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_text_input, null, false);
                final EditText input = dialogView.findViewById(R.id.dialog_text_input);
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_NORMAL|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                input.setHint(activity.getString(R.string.add_text_placeholder));
                input.setGravity(Gravity.TOP | Gravity.START);
                input.setHorizontallyScrolling(false);
                input.setBackgroundDrawable(null);
                if(annot != null && annot.text != null) input.setText(annot.text);
                dialog.setView(dialogView);
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, activity.getString(R.string.save),
                        (d, which) -> {
                            ((MuPDFPageView)getSelectedView()).deleteSelectedAnnotation();
                            annot.text = input.getText().toString();
                            addTextAnnotion(annot);
                            dialog.setOnCancelListener(null);
                        });
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getString(R.string.cancel),
                        (d, which) -> {
                            ((MuPDFPageView)getSelectedView()).deselectAnnotation();
                            dialog.setOnCancelListener(null);
                        });
                dialog.setOnCancelListener(di -> ((MuPDFPageView)getSelectedView()).deselectAnnotation());
                dialog.show();
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
