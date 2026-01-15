package org.opendroidpdf;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.reader.ReaderComposition;
import org.opendroidpdf.app.widget.WidgetAreasLoader;
import org.opendroidpdf.app.widget.WidgetUiBridge;
import org.opendroidpdf.core.WidgetAreasCallback;
import org.opendroidpdf.core.WidgetController;
import org.opendroidpdf.widget.WidgetUiController;

final class MuPDFPageViewWidgets {

    interface Host {
        @NonNull ViewGroup viewGroup();
        @NonNull Context context();
        @NonNull android.content.res.Resources resources();
        float scale();
        int viewWidthPx();
        int viewHeightPx();
        void requestLayoutSafe();
        void invalidateOverlay();
    }

    private final Host host;
    private final WidgetController widgetController;
    private final WidgetUiController widgetUiController;
    private final WidgetAreasLoader widgetAreasLoader;
    private final org.opendroidpdf.app.signature.SignatureFlowController signatureFlow;

    @Nullable private RectF[] widgetAreas;
    @Nullable private WidgetController.WidgetJob passClickJob;

    @Nullable private EditText inlineWidgetEditor;
    @Nullable private Rect inlineWidgetEditorBoundsPx;

    private final WidgetUiBridge.InlineTextEditorHost inlineTextEditorHost =
            new WidgetUiBridge.InlineTextEditorHost() {
                @Override public void showInlineTextEditor(EditText editor, Rect boundsPx) {
                    if (editor == null || boundsPx == null) return;
                    try {
                        android.view.ViewParent p = editor.getParent();
                        ViewGroup hostView = host.viewGroup();
                        if (p instanceof ViewGroup && p != hostView) {
                            ((ViewGroup) p).removeView(editor);
                        }
                    } catch (Throwable ignore) {
                    }

                    inlineWidgetEditor = editor;
                    inlineWidgetEditorBoundsPx = new Rect(boundsPx);
                    try {
                        ViewGroup hostView = host.viewGroup();
                        if (editor.getParent() != hostView) {
                            hostView.addView(editor, new ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT));
                        }
                        editor.bringToFront();
                        editor.setVisibility(View.VISIBLE);
                    } catch (Throwable ignore) {
                    }

                    try { host.requestLayoutSafe(); } catch (Throwable ignore) {}
                }

                @Override public void hideInlineTextEditor(EditText editor) {
                    if (editor == null) return;
                    try {
                        ViewGroup hostView = host.viewGroup();
                        if (editor.getParent() == hostView) {
                            hostView.removeView(editor);
                        } else {
                            android.view.ViewParent p = editor.getParent();
                            if (p instanceof ViewGroup) ((ViewGroup) p).removeView(editor);
                        }
                    } catch (Throwable ignore) {
                    }
                    if (inlineWidgetEditor == editor) {
                        inlineWidgetEditor = null;
                        inlineWidgetEditorBoundsPx = null;
                    }
                    try { host.requestLayoutSafe(); } catch (Throwable ignore) {}
                }
            };

    MuPDFPageViewWidgets(@NonNull Host host,
                         @NonNull FilePicker.FilePickerSupport filePickerSupport,
                         @NonNull ReaderComposition composition,
                         @NonNull WidgetController widgetController,
                         @NonNull Runnable changeReporterRunner) {
        this.host = host;
        this.widgetController = widgetController;
        this.widgetUiController = composition.newWidgetUiController();
        this.widgetAreasLoader = composition.newWidgetAreasLoader();

        org.opendroidpdf.app.signature.SignatureFlowController.FilePickerLauncher pickerLauncher =
                callback -> {
                    FilePicker picker = new FilePicker(filePickerSupport) {
                        @Override void onPick(Uri uri) { callback.onPick(uri); }
                    };
                    picker.pick();
                };

        this.signatureFlow = composition.newSignatureFlow(pickerLauncher, () -> changeReporterRunner.run());
    }

    void setChangeReporter(@NonNull Runnable reporter) {
        widgetUiController.setChangeReporter(reporter);
    }

    @Nullable
    RectF[] widgetAreas() { return widgetAreas; }

    void setWidgetJob(@Nullable WidgetController.WidgetJob job) {
        if (passClickJob != null) passClickJob.cancel();
        passClickJob = job;
    }

    void onSetPage(int pageNumber) {
        try { widgetUiController.dismissInlineTextEditor(); } catch (Throwable ignore) {}
        widgetUiController.setPageNumber(pageNumber);
        widgetAreasLoader.load(pageNumber, new WidgetAreasCallback() {
            @Override public void onResult(RectF[] areas) {
                widgetAreas = areas;
                host.invalidateOverlay();
            }
        });
    }

    void onLayout() {
        layoutInlineEditor(inlineWidgetEditor, inlineWidgetEditorBoundsPx);
    }

    void onDispatchTouchEvent(@Nullable MotionEvent ev) {
        try {
            if (ev != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                EditText editor = inlineWidgetEditor;
                if (editor != null && editor.getParent() == host.viewGroup()) {
                    float x = ev.getX();
                    float y = ev.getY();
                    if (x < editor.getLeft() || x > editor.getRight() || y < editor.getTop() || y > editor.getBottom()) {
                        editor.clearFocus();
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    void dismissInlineTextEditor() {
        try { widgetUiController.dismissInlineTextEditor(); } catch (Throwable ignore) {}
    }

    void debugShowTextWidgetDialog() { widgetUiController.showTextDialog("", 0f, 0f); }

    void debugShowChoiceWidgetDialog() {
        widgetUiController.showChoiceDialog(new String[] {"One", "Two", "Three"}, new String[] {"Two"}, false, false, 0f, 0f);
    }

    void invokeTextDialog(@Nullable String text, float docRelX, float docRelY) {
        RectF hit = widgetAreaAt(docRelX, docRelY);
        Rect boundsPx = hit != null ? widgetBoundsDocToViewPx(hit) : null;
        if (boundsPx != null) {
            widgetUiController.showInlineTextEditor(inlineTextEditorHost, text, boundsPx, docRelX, docRelY);
            return;
        }
        widgetUiController.showTextDialog(text, docRelX, docRelY);
    }

    void invokeChoiceDialog(final String[] options,
                            final String[] selected,
                            boolean multiSelect,
                            boolean editable,
                            float docRelX,
                            float docRelY) {
        widgetUiController.showChoiceDialog(options, selected, multiSelect, editable, docRelX, docRelY);
    }

    void setWidgetFieldNavigationRequester(@Nullable WidgetUiBridge.FieldNavigationRequester requester) {
        try {
            widgetUiController.setFieldNavigationRequester(requester);
        } catch (Throwable ignore) {
        }
    }

    void warnNoSignatureSupport() { signatureFlow.showNoSignatureSupport(); }
    void invokeSigningDialog() { signatureFlow.showSigningDialog(); }
    void invokeSignatureCheckingDialog() { signatureFlow.checkFocusedSignature(); }

    void releaseResources() {
        if (passClickJob != null) {
            passClickJob.cancel();
            passClickJob = null;
        }
        try { widgetAreasLoader.release(); } catch (Throwable ignore) {}
        try { widgetUiController.release(); } catch (Throwable ignore) {}
        try { signatureFlow.release(); } catch (Throwable ignore) {}
    }

    private void layoutInlineEditor(@Nullable EditText editor, @Nullable Rect bounds) {
        if (editor == null || bounds == null) return;
        if (editor.getParent() != host.viewGroup()) return;
        int w = Math.max(1, bounds.width());
        int h = Math.max(1, bounds.height());
        try {
            editor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            editor.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
        } catch (Throwable ignore) {
        }
    }

    @Nullable
    private RectF widgetAreaAt(float docRelX, float docRelY) {
        RectF[] areas = widgetAreas;
        if (areas == null) return null;
        for (RectF r : areas) {
            if (r != null && r.contains(docRelX, docRelY)) return r;
        }
        return null;
    }

    @Nullable
    private Rect widgetBoundsDocToViewPx(@NonNull RectF docBounds) {
        float scale = host.scale();
        if (scale <= 0f) return null;

        int left = Math.round(docBounds.left * scale);
        int top = Math.round(docBounds.top * scale);
        int right = Math.round(docBounds.right * scale);
        int bottom = Math.round(docBounds.bottom * scale);

        float density = host.resources().getDisplayMetrics().density;
        int pad = (int) (2f * density + 0.5f);
        left -= pad;
        top -= pad;
        right += pad;
        bottom += pad;

        int w = host.viewWidthPx();
        int h = host.viewHeightPx();
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(w, right);
        bottom = Math.min(h, bottom);

        int minH = (int) (48f * density + 0.5f);
        if (bottom - top < minH) {
            int cy = (top + bottom) / 2;
            top = cy - (minH / 2);
            bottom = top + minH;
            top = Math.max(0, top);
            bottom = Math.min(h, bottom);
        }

        if (right <= left || bottom <= top) return null;
        return new Rect(left, top, right, bottom);
    }
}
