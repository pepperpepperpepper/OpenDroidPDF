package org.opendroidpdf.app.annotation;

import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.R;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.selection.SidecarSelectionController;

/**
 * Shows a small contextual toolbar near the selected text annotation (FreeText or sidecar note).
 *
 * <p>Designed for Acrobat-like ergonomics: quick access to properties/lock/fit/duplicate/delete
 * without relying solely on the top app bar.</p>
 */
public final class TextAnnotationQuickActionsController {
    public interface Host {
        @NonNull AppCompatActivity activity();
        @Nullable MuPDFPageView currentPageView();
        void showInfo(@NonNull String message);
        void invalidateQuickActions();
    }

    private static final float MARGIN_DP = 8f;

    private final Host host;

    @Nullable private PopupWindow popup;
    @Nullable private View content;
    @Nullable private ImageButton propertiesButton;
    @Nullable private ImageButton duplicateButton;
    @Nullable private ImageButton multiAddButton;
    @Nullable private ImageButton multiAlignButton;
    @Nullable private ImageButton multiGroupButton;
    @Nullable private ImageButton lockButton;
    @Nullable private ImageButton fitButton;
    @Nullable private ImageButton deleteButton;
    @Nullable private TextAnnotationStyleController styleController;
    @Nullable private TextAnnotationMultiSelectController multiSelect;

    public void setMultiSelectController(@Nullable TextAnnotationMultiSelectController controller) {
        this.multiSelect = controller;
        try { refresh(); } catch (Throwable ignore) {}
    }

    public TextAnnotationQuickActionsController(@NonNull Host host) {
        this.host = host;
    }

    public void dismiss() {
        PopupWindow p = popup;
        if (p != null && p.isShowing()) {
            try {
                p.dismiss();
            } catch (Throwable ignore) {
            }
        }
    }

    public void refresh() {
        final Host host = this.host;
        if (host == null) return;
        final AppCompatActivity activity = host.activity();
        final MuPDFPageView pageView = host.currentPageView();
        if (activity == null || pageView == null) {
            dismiss();
            return;
        }
        if (!pageView.areCommentsVisible()) {
            dismiss();
            return;
        }

        final RectF boxDoc = pageView.getItemSelectBox();
        if (boxDoc == null) {
            dismiss();
            return;
        }

        final SelectionKind kind = resolveSelectionKind(pageView);
        if (kind == SelectionKind.NONE) {
            dismiss();
            return;
        }

        ensureUi(activity);
        if (popup == null || content == null) {
            dismiss();
            return;
        }
        updateButtonState(pageView, kind);
        showOrMovePopup(activity, pageView, boxDoc);
    }

    private enum SelectionKind {
        NONE,
        EMBEDDED_FREETEXT,
        EMBEDDED_TEXT,
        SIDECAR_NOTE,
    }

    @NonNull
    private static SelectionKind resolveSelectionKind(@NonNull MuPDFPageView pageView) {
        try {
            Annotation embedded = pageView.textAnnotationDelegate().selectedEmbeddedAnnotationOrNull();
            if (embedded != null) {
                if (embedded.type == Annotation.Type.FREETEXT) return SelectionKind.EMBEDDED_FREETEXT;
                if (embedded.type == Annotation.Type.TEXT) return SelectionKind.EMBEDDED_TEXT;
            }
        } catch (Throwable ignore) {
        }
        try {
            SidecarSelectionController.Selection sel = pageView.selectedSidecarSelectionOrNull();
            if (sel != null && sel.kind == SidecarSelectionController.Kind.NOTE) return SelectionKind.SIDECAR_NOTE;
        } catch (Throwable ignore) {
        }
        return SelectionKind.NONE;
    }

    private void ensureUi(@NonNull AppCompatActivity activity) {
        if (popup != null && content != null) return;

        LayoutInflater inflater = LayoutInflater.from(activity);
        View v = inflater.inflate(R.layout.popup_text_annot_quick_actions, null, false);
        content = v;
        propertiesButton = v.findViewById(R.id.text_quick_actions_properties);
        duplicateButton = v.findViewById(R.id.text_quick_actions_duplicate);
        multiAddButton = v.findViewById(R.id.text_quick_actions_multi_add);
        multiAlignButton = v.findViewById(R.id.text_quick_actions_multi_align);
        multiGroupButton = v.findViewById(R.id.text_quick_actions_multi_group);
        lockButton = v.findViewById(R.id.text_quick_actions_lock);
        fitButton = v.findViewById(R.id.text_quick_actions_fit);
        deleteButton = v.findViewById(R.id.text_quick_actions_delete);

        PopupWindow p = new PopupWindow(
                v,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false /* focusable */);
        p.setClippingEnabled(true);
        // Required to avoid BadTokenException on some devices when updating outside touch behavior.
        p.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        popup = p;

        if (propertiesButton != null) {
            propertiesButton.setOnClickListener(v1 -> {
                TextAnnotationStyleController controller = styleController(activity);
                if (controller != null) controller.show();
            });
        }
        if (duplicateButton != null) {
            duplicateButton.setOnClickListener(v12 -> {
                MuPDFPageView pv = host.currentPageView();
                if (pv == null) return;
                boolean ok = false;
                try {
                    ok = pv.textAnnotationDelegate().duplicateSelectedTextAnnotation();
                } catch (Throwable ignore) {
                    ok = false;
                }
                if (!ok) host.showInfo(activity.getString(R.string.select_text_annot_to_move));
                // The selection may change asynchronously after reloads; re-anchor the popup.
                scheduleRefresh(activity);
            });
        }
        if (multiAddButton != null) {
            multiAddButton.setOnClickListener(v1 -> {
                TextAnnotationMultiSelectController ms = multiSelect;
                if (ms != null) ms.addCurrentSelection();
            });
        }
        if (multiAlignButton != null) {
            multiAlignButton.setOnClickListener(v1 -> {
                TextAnnotationMultiSelectController ms = multiSelect;
                if (ms != null) ms.showAlignDistributePicker();
            });
        }
        if (multiGroupButton != null) {
            multiGroupButton.setOnClickListener(v1 -> {
                TextAnnotationMultiSelectController ms = multiSelect;
                if (ms != null) ms.toggleGroupForCurrentSelection();
            });
        }
        if (lockButton != null) {
            lockButton.setOnClickListener(v13 -> {
                MuPDFPageView pv = host.currentPageView();
                if (pv == null) return;
                boolean lockPos = false;
                boolean lockContents = false;
                try { lockPos = pv.textAnnotationDelegate().selectedTextAnnotationLockPositionSizeOrDefault(); } catch (Throwable ignore) { lockPos = false; }
                try { lockContents = pv.textAnnotationDelegate().selectedTextAnnotationLockContentsOrDefault(); } catch (Throwable ignore) { lockContents = false; }
                boolean ok = false;
                try { ok = pv.textAnnotationDelegate().applyTextLocksToSelectedTextAnnotation(!lockPos, lockContents); } catch (Throwable ignore) { ok = false; }
                if (!ok) host.showInfo(activity.getString(R.string.select_text_annot_to_style));
                scheduleRefresh(activity);
            });
        }
        if (fitButton != null) {
            fitButton.setOnClickListener(v14 -> {
                MuPDFPageView pv = host.currentPageView();
                if (pv == null) return;
                boolean ok = false;
                try { ok = pv.textAnnotationDelegate().fitSelectedTextAnnotationToText(); } catch (Throwable ignore) { ok = false; }
                if (!ok) host.showInfo(activity.getString(R.string.select_text_annot_to_style));
                scheduleRefresh(activity);
            });
        }
        if (deleteButton != null) {
            deleteButton.setOnClickListener(v15 -> {
                MuPDFPageView pv = host.currentPageView();
                if (pv == null) return;
                try { pv.deleteSelectedAnnotation(); } catch (Throwable ignore) {}
                dismiss();
            });
        }
    }

    @Nullable
    private TextAnnotationStyleController styleController(@NonNull AppCompatActivity activity) {
        if (styleController != null) return styleController;
        try {
            styleController = new TextAnnotationStyleController(
                    AppServices.get().textStylePreferences(),
                    new TextAnnotationStyleController.Host() {
                        @NonNull @Override public android.content.Context getContext() { return activity; }
                        @NonNull @Override public LayoutInflater getLayoutInflater() { return activity.getLayoutInflater(); }
                        @Nullable @Override public MuPDFPageView activePageViewOrNull() { return host.currentPageView(); }
                        @Override public void showAnnotationInfo(@NonNull String message) { host.showInfo(message); }
                    });
            return styleController;
        } catch (Throwable t) {
            return null;
        }
    }

    private void scheduleRefresh(@NonNull AppCompatActivity activity) {
        try {
            View root = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
            if (root == null) return;
            root.removeCallbacks(delayedRefresh);
            root.postDelayed(delayedRefresh, 120L);
        } catch (Throwable ignore) {
        }
    }

    private final Runnable delayedRefresh = new Runnable() {
        @Override public void run() {
            try { refresh(); } catch (Throwable ignore) {}
        }
    };

    private void updateButtonState(@NonNull MuPDFPageView pageView, @NonNull SelectionKind kind) {
        boolean hasSelection = kind != SelectionKind.NONE;

        if (fitButton != null) {
            boolean showFit = (kind == SelectionKind.EMBEDDED_FREETEXT);
            fitButton.setVisibility(showFit ? View.VISIBLE : View.GONE);
        }

        boolean lockPos = false;
        try { lockPos = pageView.textAnnotationDelegate().selectedTextAnnotationLockPositionSizeOrDefault(); } catch (Throwable ignore) { lockPos = false; }

        if (fitButton != null && fitButton.getVisibility() == View.VISIBLE) {
            fitButton.setEnabled(!lockPos);
            fitButton.setAlpha(lockPos ? 0.35f : 1.0f);
        }

        if (lockButton != null) {
            lockButton.setImageResource(lockPos ? R.drawable.ic_lock_white_24dp : R.drawable.ic_lock_open_white_24dp);
            lockButton.setAlpha(1.0f);
        }

        boolean multiVisible = multiSelect != null && hasSelection;
        if (multiAddButton != null) {
            multiAddButton.setVisibility(multiVisible ? View.VISIBLE : View.GONE);
        }
        if (multiAlignButton != null) {
            multiAlignButton.setVisibility(multiVisible ? View.VISIBLE : View.GONE);
            if (multiVisible) {
                boolean enabled = multiSelect != null && multiSelect.size() >= 2 && multiSelect.canApplyOnPage(pageView.pageNumber());
                multiAlignButton.setEnabled(enabled);
                multiAlignButton.setAlpha(enabled ? 1.0f : 0.35f);
            }
        }
        if (multiGroupButton != null) {
            multiGroupButton.setVisibility(multiVisible ? View.VISIBLE : View.GONE);
            if (multiVisible) {
                boolean enabled = multiSelect != null && multiSelect.canApplyOnPage(pageView.pageNumber());
                boolean grouped = enabled && multiSelect.isGrouped();
                multiGroupButton.setEnabled(enabled || (multiSelect != null && multiSelect.size() > 0));
                multiGroupButton.setSelected(grouped);
                multiGroupButton.setAlpha(enabled ? 1.0f : 0.35f);
            }
        }
    }

    private void showOrMovePopup(@NonNull AppCompatActivity activity,
                                 @NonNull MuPDFPageView pageView,
                                 @NonNull RectF boxDoc) {
        PopupWindow p = popup;
        View v = content;
        if (p == null || v == null) return;

        int[] pvLoc = new int[2];
        try {
            pageView.getLocationInWindow(pvLoc);
        } catch (Throwable t) {
            dismiss();
            return;
        }

        float scale = 0f;
        try { scale = pageView.getScale(); } catch (Throwable ignore) { scale = 0f; }
        if (scale <= 0f) {
            dismiss();
            return;
        }

        float leftPxF = pvLoc[0] + (boxDoc.left * scale);
        float topPxF = pvLoc[1] + (boxDoc.top * scale);
        float rightPxF = pvLoc[0] + (boxDoc.right * scale);
        float bottomPxF = pvLoc[1] + (boxDoc.bottom * scale);

        int leftPx = Math.round(Math.min(leftPxF, rightPxF));
        int rightPx = Math.round(Math.max(leftPxF, rightPxF));
        int topPx = Math.round(Math.min(topPxF, bottomPxF));
        int bottomPx = Math.round(Math.max(topPxF, bottomPxF));

        // Measure popup content so we can place it without overlapping the selection box.
        try {
            v.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        } catch (Throwable ignore) {
        }
        int pw = v.getMeasuredWidth();
        int ph = v.getMeasuredHeight();
        if (pw <= 0 || ph <= 0) return;

        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor == null) return;

        Rect frame = new Rect();
        try {
            decor.getWindowVisibleDisplayFrame(frame);
        } catch (Throwable ignore) {
            frame.set(0, 0, decor.getWidth(), decor.getHeight());
        }

        float density = activity.getResources().getDisplayMetrics().density;
        int marginPx = Math.max(1, Math.round(MARGIN_DP * density));

        int cx = leftPx + ((rightPx - leftPx) / 2);
        int x = cx - (pw / 2);
        x = clamp(x, frame.left + marginPx, frame.right - marginPx - pw);

        // Prefer placing the popup above the selection; if it doesn't fit, place below.
        int yAbove = topPx - ph - marginPx;
        int yBelow = bottomPx + marginPx;
        int y = (yAbove >= (frame.top + marginPx)) ? yAbove : yBelow;
        y = clamp(y, frame.top + marginPx, frame.bottom - marginPx - ph);

        if (!p.isShowing()) {
            try {
                p.showAtLocation(decor, Gravity.NO_GRAVITY, x, y);
            } catch (Throwable t) {
                dismiss();
            }
        } else {
            try {
                p.update(x, y, -1, -1);
            } catch (Throwable t) {
                dismiss();
            }
        }
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
