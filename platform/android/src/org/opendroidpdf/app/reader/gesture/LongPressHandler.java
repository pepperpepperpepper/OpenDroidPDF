package org.opendroidpdf.app.reader.gesture;

import android.app.Activity;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.appcompat.widget.PopupMenu;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.Annotation;
import org.opendroidpdf.R;

/**
 * Handles long-press scheduling and dispatch for MuPDFReaderView.
 */
class LongPressHandler {
    private static final int MENU_ACTION_COMMENTS = 1;
    private static final int MENU_ACTION_DRAW = 2;
    private static final int MENU_ACTION_ADD_TEXT = 3;
    private static final int MENU_ACTION_FILL_SIGN = 4;

    interface Host {
        MuPDFPageView currentPageView();
        ReaderMode currentMode();
        void requestMode(ReaderMode mode);
        void onNumberOfStrokesChanged(int strokes);
        View rootView();
    }

    private final Context context;
    private final CoroutineScope scope;
    private final Host host;
    private Job longPressJob;
    private Job selectionRetryJob;
    private MotionEvent startEvent;
    private boolean startUseStylus;
    private boolean startOnSelectedTextAnnotation;

    LongPressHandler(Context context, CoroutineScope scope, Host host) {
        this.context = context;
        this.scope = scope;
        this.host = host;
    }

    void onDown(MotionEvent e, boolean useStylus) {
        MuPDFPageView cv = host.currentPageView();
        if (cv == null) return;
        ReaderMode mode = host.currentMode();
        if (!isLongPressMode(mode)) return;
        if (cv.hitsLeftMarker(e.getX(), e.getY()) || cv.hitsRightMarker(e.getX(), e.getY())) return;

        // If a text annotation is currently selected and the user presses inside its bounds,
        // treat the gesture as "annotation interaction" rather than "select underlying text".
        // Keep the selection stable and do not enter text selection mode.
        if (mode == ReaderMode.VIEWING || mode == ReaderMode.SELECTING) {
            try {
                float scale = cv.getScale();
                if (scale > 0f) {
                    float docX = (e.getX() - cv.getLeft()) / scale;
                    float docY = (e.getY() - cv.getTop()) / scale;
                    Annotation selected = cv.textAnnotationDelegate().selectedEmbeddedAnnotationOrNull();
                    if (selected != null && (selected.type == Annotation.Type.FREETEXT || selected.type == Annotation.Type.TEXT)) {
                        if (selected.contains(docX, docY)) {
                            startEvent = e;
                            startUseStylus = useStylus;
                            startOnSelectedTextAnnotation = true;
                            scheduleLongPress(useStylus);
                            return;
                        }
                    }
                    org.opendroidpdf.app.selection.SidecarSelectionController.Selection sel = cv.selectedSidecarSelectionOrNull();
                    if (sel != null
                            && sel.kind == org.opendroidpdf.app.selection.SidecarSelectionController.Kind.NOTE
                            && sel.bounds != null
                            && sel.bounds.contains(docX, docY)) {
                        startEvent = e;
                        startUseStylus = useStylus;
                        startOnSelectedTextAnnotation = true;
                        scheduleLongPress(useStylus);
                        return;
                    }
                }
            } catch (Throwable ignore) {
            }
        }

        // New interaction: cancel any pending async selection retries from a prior long-press.
        AppCoroutines.cancel(selectionRetryJob);
        selectionRetryJob = null;

        startEvent = e;
        startUseStylus = useStylus;
        startOnSelectedTextAnnotation = false;
        scheduleLongPress(useStylus);
    }

    void cancelIfMoved(MotionEvent e1) {
        if (startEvent == null) return;
        float slop = ViewConfiguration.get(context).getScaledTouchSlop();
        if (Math.abs(startEvent.getX() - e1.getX()) > slop ||
            Math.abs(startEvent.getY() - e1.getY()) > slop) {
            cancel();
        }
    }

    void onUpOrCancel() {
        cancel();
    }

    private void scheduleLongPress(boolean useStylus) {
        // Cancel only the pending job; keep startEvent/startUseStylus for the new press.
        AppCoroutines.cancel(longPressJob);
        longPressJob = null;
        long delay = ViewConfiguration.getLongPressTimeout() * (useStylus ? 2 : 1);
        longPressJob = AppCoroutines.launchMainDelayed(scope, delay, this::handleLongPress);
    }

    private void handleLongPress() {
        MuPDFPageView cv = host.currentPageView();
        if (cv == null || startEvent == null) return;

        if (startOnSelectedTextAnnotation) {
            cancel();
            return;
        }

        ReaderMode mode = host.currentMode();
        if (mode == ReaderMode.VIEWING || mode == ReaderMode.SELECTING) {
            selectText(cv);
        }
        cancel();
    }

    private void selectText(MuPDFPageView cv) {
        final MuPDFPageView target = cv;
        int[] locationOnScreen = new int[] {0, 0};
        host.rootView().getLocationOnScreen(locationOnScreen);
        final float rawX = startEvent != null ? startEvent.getRawX() : 0f;
        final float rawY = startEvent != null ? startEvent.getRawY() : 0f;
        cv.deselectAnnotation();
        cv.deselectText();
        // Use a small-but-not-tiny box to improve hit rate (especially on reflow docs).
        final float x0 = startEvent.getX();
        final float y0 = startEvent.getRawY() - locationOnScreen[1];
        final float x1 = x0 + 12;
        final float y1 = y0 + 12;
        cv.selectText(
                x0,
                y0,
                x1,
                y1);

        // Text extraction runs async; on fresh loads the first selection attempt can race
        // and incorrectly fail. Retry for a short window so long-press selection is reliable.
        boolean selectedNow = cv.hasTextSelected();
        if (selectedNow) {
            host.requestMode(ReaderMode.SELECTING);
            return;
        }

        AppCoroutines.cancel(selectionRetryJob);
        selectionRetryJob = AppCoroutines.launchMainDelayed(scope, 120, new Runnable() {
            int attempts = 0;

            @Override public void run() {
                // Give up if the page changed or mode changed out of selection-compatible states.
                MuPDFPageView current = host.currentPageView();
                ReaderMode m = host.currentMode();
                if (current != target || (m != ReaderMode.VIEWING && m != ReaderMode.SELECTING)) {
                    selectionRetryJob = null;
                    return;
                }

                if (target.hasTextSelected()) {
                    host.requestMode(ReaderMode.SELECTING);
                    selectionRetryJob = null;
                    return;
                }

                attempts++;
                if (attempts >= 8) {
                    target.deselectText();
                    host.requestMode(ReaderMode.VIEWING);
                    selectionRetryJob = null;
                    showBlankSpaceContextMenu(rawX, rawY);
                    return;
                }

                // Reschedule.
                selectionRetryJob = AppCoroutines.launchMainDelayed(scope, 120, this);
            }
        });
    }

    private boolean isLongPressMode(ReaderMode mode) {
        return mode == ReaderMode.VIEWING || mode == ReaderMode.SELECTING;
    }

    private void showBlankSpaceContextMenu(float rawX, float rawY) {
        if (!(context instanceof Activity)) return;
        Activity activity = (Activity) context;

        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        int[] contentLoc = new int[] {0, 0};
        content.getLocationOnScreen(contentLoc);
        int x = Math.round(rawX - contentLoc[0]);
        int y = Math.round(rawY - contentLoc[1]);

        int w = content.getWidth();
        int h = content.getHeight();
        if (w > 0) x = Math.max(0, Math.min(w, x));
        if (h > 0) y = Math.max(0, Math.min(h, y));

        final View anchor = new View(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
        lp.leftMargin = x;
        lp.topMargin = y;

        try {
            content.addView(anchor, lp);
        } catch (Throwable ignore) {
            return;
        }

        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add(0, MENU_ACTION_COMMENTS, 0, R.string.menu_comments);
        menu.getMenu().add(0, MENU_ACTION_DRAW, 1, R.string.menu_draw);
        menu.getMenu().add(0, MENU_ACTION_ADD_TEXT, 2, R.string.menu_add_text_annot);
        menu.getMenu().add(0, MENU_ACTION_FILL_SIGN, 3, R.string.menu_fill_sign);

        // Disable Fill & Sign if the quick action is disabled (e.g., non-PDF documents).
        try {
            View fill = activity.findViewById(R.id.quick_action_fill_sign);
            if (fill != null && !fill.isEnabled()) {
                menu.getMenu().findItem(MENU_ACTION_FILL_SIGN).setEnabled(false);
            }
        } catch (Throwable ignore) {
        }

        menu.setOnMenuItemClickListener(item -> {
            try { ensureReaderChromeVisible(); } catch (Throwable ignore) {}
            int viewId;
            switch (item.getItemId()) {
                case MENU_ACTION_COMMENTS: viewId = R.id.quick_action_comments; break;
                case MENU_ACTION_DRAW: viewId = R.id.quick_action_draw; break;
                case MENU_ACTION_ADD_TEXT: viewId = R.id.quick_action_add_text; break;
                case MENU_ACTION_FILL_SIGN: viewId = R.id.quick_action_fill_sign; break;
                default: viewId = 0;
            }
            if (viewId != 0) {
                try {
                    View v = activity.findViewById(viewId);
                    if (v != null && v.isEnabled()) v.performClick();
                } catch (Throwable ignore) {
                }
            }
            return true;
        });

        menu.setOnDismissListener(m -> {
            try { content.removeView(anchor); } catch (Throwable ignore) {}
        });

        try {
            menu.show();
        } catch (Throwable t) {
            try { content.removeView(anchor); } catch (Throwable ignore) {}
        }
    }

    private void ensureReaderChromeVisible() {
        if (!(context instanceof org.opendroidpdf.OpenDroidPDFActivity)) return;
        org.opendroidpdf.OpenDroidPDFActivity activity = (org.opendroidpdf.OpenDroidPDFActivity) context;
        try {
            androidx.appcompat.app.ActionBar bar = activity.getSupportActionBar();
            boolean showing = bar != null && bar.isShowing();
            if (!showing) activity.toggleReaderChrome();
        } catch (Throwable ignore) {
        }
    }

    private void cancel() {
        AppCoroutines.cancel(longPressJob);
        longPressJob = null;
        startEvent = null;
        startUseStylus = false;
        startOnSelectedTextAnnotation = false;
    }
}
