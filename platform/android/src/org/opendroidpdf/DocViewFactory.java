package org.opendroidpdf;

import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

/**
 * Factory for creating the MuPDFReaderView with OpenDroidPDFActivity wiring.
 * Kept minimal to match existing inline overrides.
 */
public final class DocViewFactory {
    private DocViewFactory() {}

    public static MuPDFReaderView create(final OpenDroidPDFActivity activity) {
        return new MuPDFReaderView(activity) {
            @Override
            public void setMode(Mode m) {
                super.setMode(m);
                switch (m) {
                    case Viewing: activity.setActionBarModeMain(); break;
                    case Drawing:
                    case Erasing: activity.setActionBarModeAnnot(); break;
                    case Selecting: activity.setActionBarModeSelection(); break;
                    case AddingTextAnnot: activity.setActionBarModeAddingTextAnnot(); break;
                }
                activity.invalidateOptionsMenu();
            }

            @Override
            protected void onMoveToChild(int pageNumber) {
                activity.setTitle();
                if (activity.isActionBarModeEdit()) {
                    activity.setActionBarModeMain();
                    activity.invalidateOptionsMenu();
                }
            }

            @Override
            protected void onTapMainDocArea() {
                if (activity.isActionBarModeEdit() || activity.isActionBarModeAddingTextAnnot()) {
                    activity.setActionBarModeMain();
                    activity.invalidateOptionsMenu();
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
                final AlertDialog dialog = activity.getAlertBuilder().create();
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
                switch(item){
                    case Annotation:
                    case InkAnnotation:
                        activity.setActionBarModeEdit();
                        activity.invalidateOptionsMenu();
                        activity.setSelectedAnnotationEditable(((MuPDFPageView)getSelectedView()).selectedAnnotationIsEditable());
                        break;
                    case TextAnnotation:
                        break;
                    case Nothing:
                        if(!activity.isActionBarModeSearchOrHidden()) {
                            activity.setActionBarModeMain();
                            activity.invalidateOptionsMenu();
                        }
                        break;
                    case LinkInternal:
                        if(linksEnabled()) {
                            activity.rememberPreLinkHitViewport(getSelectedItemPosition(), getNormalizedScale(), getNormalizedXScroll(), getNormalizedYScroll());
                        }
                        activity.setActionBarModeMain();
                        activity.invalidateOptionsMenu();
                        break;
                }
            }

            @Override
            public boolean onKeyDown(int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if(!activity.isActionBarModeSearchOrHidden()) {
                        activity.setActionBarModeMain();
                        activity.invalidateOptionsMenu();
                    }
                    return false;
                }
                return super.onKeyDown(keyCode, event);
            }

            @Override
            protected void onNumberOfStrokesChanged(int numberOfStrokes) {
                activity.invalidateOptionsMenu();
            }
        };
    }
}
