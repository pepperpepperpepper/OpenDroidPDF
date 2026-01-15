package org.opendroidpdf;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.annotation.TextAnnotationPageDelegate;
import org.opendroidpdf.app.reader.ReaderComposition;
import org.opendroidpdf.app.selection.PageSelectionCoordinator;
import org.opendroidpdf.app.selection.SelectionActionRouter;
import org.opendroidpdf.app.selection.SelectionUiBridge;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarNote;
import org.opendroidpdf.core.MuPdfController;

import java.util.Objects;

final class MuPDFPageViewTextAnnotations {
    private static final String TAG = "MuPDFPageViewTextAnnots";

    interface Host {
        @NonNull ViewGroup viewGroup();
        @NonNull Context context();
        float scale();
        int viewWidthPx();
        int viewHeightPx();
        int pageNumber();
        void requestLayoutSafe();
        void invalidateOverlaySafe();
        void addTextAnnotationFromUi(@NonNull Annotation annotation);
    }

    private final Host host;
    private final MuPdfController muPdfController;
    private final ReaderComposition composition;
    @Nullable private final SidecarAnnotationSession sidecarSession;
    private final SidecarSelectionController sidecarSelectionController;
    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager;
    private final SelectionUiBridge selectionUiBridge;
    private final SelectionActionRouter selectionRouter;
    private final PageSelectionCoordinator selectionCoordinator;
    private final TextAnnotationPageDelegate textAnnotationDelegate;
    private final MuPDFPageViewWidgets widgets;

    private boolean textResizeHandlesEnabled = false;
    @Nullable private String lastSelectionKey;

    @Nullable private EditText inlineTextAnnotEditor;
    @Nullable private Rect inlineTextAnnotEditorBoundsPx;
    @Nullable private InlineTextAnnotState inlineTextAnnotState;
    private boolean inlineTextAnnotSubmitting = false;
    private boolean suppressInlineTextAnnotFocusLoss = false;

    private static final class InlineTextAnnotState {
        public final int pageNumber;
        public final long objectNumber;
        @Nullable public final String sidecarNoteId;
        @NonNull public final Annotation draft;
        @Nullable public final String priorText;

        InlineTextAnnotState(int pageNumber,
                             long objectNumber,
                             @Nullable String sidecarNoteId,
                             @NonNull Annotation draft,
                             @Nullable String priorText) {
            this.pageNumber = pageNumber;
            this.objectNumber = objectNumber;
            this.sidecarNoteId = sidecarNoteId;
            this.draft = draft;
            this.priorText = priorText;
        }
    }

    MuPDFPageViewTextAnnotations(@NonNull Host host,
                                @NonNull MuPdfController muPdfController,
                                @NonNull ReaderComposition composition,
                                @Nullable SidecarAnnotationSession sidecarSession,
                                @NonNull SidecarSelectionController sidecarSelectionController,
                                @NonNull org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager,
                                @NonNull SelectionUiBridge selectionUiBridge,
                                @NonNull SelectionActionRouter selectionRouter,
                                @NonNull PageSelectionCoordinator selectionCoordinator,
                                @NonNull TextAnnotationPageDelegate textAnnotationDelegate,
                                @NonNull MuPDFPageViewWidgets widgets) {
        this.host = host;
        this.muPdfController = muPdfController;
        this.composition = composition;
        this.sidecarSession = sidecarSession;
        this.sidecarSelectionController = sidecarSelectionController;
        this.selectionManager = selectionManager;
        this.selectionUiBridge = selectionUiBridge;
        this.selectionRouter = selectionRouter;
        this.selectionCoordinator = selectionCoordinator;
        this.textAnnotationDelegate = textAnnotationDelegate;
        this.widgets = widgets;
    }

    void onSetItemSelectBox() {
        // Reset resize-handles whenever the selection changes (including to "no selection").
        String key = currentSelectionKeyOrNull();
        if (!Objects.equals(key, lastSelectionKey)) {
            textResizeHandlesEnabled = false;
            lastSelectionKey = key;
        }
    }

    @Nullable
    private String currentSelectionKeyOrNull() {
        try {
            SidecarSelectionController.Selection sel = selectedSidecarSelectionOrNull();
            if (sel != null && sel.kind == SidecarSelectionController.Kind.NOTE
                    && sel.id != null && !sel.id.trim().isEmpty()) {
                return "sidecar:" + sel.id;
            }
        } catch (Throwable ignore) {
        }
        try {
            long obj = selectionManager.selectedObjectNumber();
            if (obj > 0L) return "obj:" + obj;
        } catch (Throwable ignore) {
        }
        try {
            int idx = selectionManager.selectedIndex();
            if (idx >= 0) return "idx:" + idx;
        } catch (Throwable ignore) {
        }
        return null;
    }

    boolean textResizeHandlesEnabled() {
        return textResizeHandlesEnabled;
    }

    boolean setTextResizeHandlesEnabled(boolean enabled) {
        if (enabled == textResizeHandlesEnabled) return true;
        if (enabled && !hasSelectedTextAnnotation()) return false;
        textResizeHandlesEnabled = enabled;
        host.invalidateOverlaySafe();
        return true;
    }

    boolean toggleTextResizeHandlesEnabled() {
        return setTextResizeHandlesEnabled(!textResizeHandlesEnabled);
    }

    private boolean hasSelectedTextAnnotation() {
        try {
            if (sidecarSession != null) {
                SidecarSelectionController.Selection sel = selectedSidecarSelectionOrNull();
                return sel != null && sel.kind == SidecarSelectionController.Kind.NOTE;
            }
        } catch (Throwable ignore) {
        }
        Annotation.Type selectedType = null;
        try { selectedType = selectionRouter.selectedAnnotationType(); } catch (Throwable ignore) { selectedType = null; }
        return selectedType == Annotation.Type.FREETEXT || selectedType == Annotation.Type.TEXT;
    }

    boolean showItemSelectionHandles() {
        // Show handles for text boxes that support direct manipulation:
        // - embedded PDF FreeText/Text
        // - sidecar notes (EPUB / read-only PDFs)
        if (sidecarSession != null) {
            SidecarSelectionController.Selection sel = sidecarSelectionController.selectionOrNull();
            return sel != null && sel.kind == SidecarSelectionController.Kind.NOTE;
        }

        Annotation.Type selectedType = null;
        try { selectedType = selectionRouter.selectedAnnotationType(); } catch (Throwable ignore) { selectedType = null; }
        return selectedType == Annotation.Type.FREETEXT || selectedType == Annotation.Type.TEXT;
    }

    boolean showItemResizeHandles() {
        // Resize handles are an explicit mode; keep them hidden by default to avoid accidental resizes.
        return textResizeHandlesEnabled && showItemSelectionHandles();
    }

    void onAnnotationsLoaded(@Nullable Annotation[] annotations) {
        if (sidecarSession != null) return;

        // If we have a stable object id selection, re-resolve it across reloads so "tap-to-edit"
        // and direct manipulation don't accidentally target a different annotation after a refresh.
        long objectId = -1L;
        try { objectId = selectionManager.selectedObjectNumber(); } catch (Throwable ignore) { objectId = -1L; }
        if (objectId > 0L) {
            int idx = -1;
            if (annotations != null) {
                for (int i = 0; i < annotations.length; i++) {
                    Annotation a = annotations[i];
                    if (a != null && a.objectNumber == objectId) {
                        idx = i;
                        break;
                    }
                }
            }
            if (idx >= 0 && annotations != null) {
                selectionManager.setSelectedIndex(idx);
                RectF bounds = new RectF(annotations[idx]);
                selectionManager.select(idx, objectId, bounds, selectionUiBridge.selectionBoxHost());
                host.invalidateOverlaySafe();
            } else if (selectionManager.hasSelection()) {
                // Selected annotation disappeared (deleted) or could not be resolved.
                selectionManager.deselect(selectionUiBridge.selectionBoxHost());
                host.invalidateOverlaySafe();
            }
            return;
        }

        // If selection is index-only, ensure it stays in-bounds after reloads.
        int idx = selectionManager.selectedIndex();
        if (idx >= 0) {
            if (annotations == null || idx >= annotations.length) {
                selectionManager.deselect(selectionUiBridge.selectionBoxHost());
                host.invalidateOverlaySafe();
            } else {
                // Opportunistically capture a stable object id if it exists.
                long newId = annotations[idx] != null ? annotations[idx].objectNumber : -1L;
                if (newId > 0L) {
                    RectF bounds = new RectF(annotations[idx]);
                    selectionManager.select(idx, newId, bounds, selectionUiBridge.selectionBoxHost());
                    host.invalidateOverlaySafe();
                }
            }
        }
    }

    @Nullable
    SidecarSelectionController.Selection selectedSidecarSelectionOrNull() {
        return sidecarSelectionController != null ? sidecarSelectionController.selectionOrNull() : null;
    }

    void forwardTextAnnotation(@Nullable Annotation annotation) {
        if (annotation == null) return;
        try {
            // Respect the "lock contents" flag for edits.
            if (sidecarSession != null) {
                SidecarSelectionController.Selection sel = sidecarSelectionController.selectionOrNull();
                if (sel != null && sel.kind == SidecarSelectionController.Kind.NOTE) {
                    SidecarNote note = textAnnotationDelegate.sidecarNoteById(sel.id);
                    if (note != null && note.lockContents) {
                        try { Toast.makeText(host.context(), R.string.text_locked_contents, Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
                        return;
                    }
                }
            } else if (annotation.objectNumber > 0L && textAnnotationDelegate.embeddedFreeTextContentsLocked(annotation.objectNumber)) {
                try { Toast.makeText(host.context(), R.string.text_locked_contents, Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
                return;
            }

            composition.textAnnotationRequester().requestTextAnnotationFromUserInput(annotation);
        } catch (Throwable t) {
            android.util.Log.e(TAG, "Failed to open text annotation editor", t);
        }
    }

    void deleteSelectedAnnotation() {
        try {
            if (sidecarSession == null) {
                Annotation sel = textAnnotationDelegate.selectedEmbeddedAnnotationOrNull();
                if (sel != null && sel.type == Annotation.Type.FREETEXT && sel.objectNumber > 0L) {
                    boolean ok = false;
                    try { ok = textAnnotationDelegate.deleteEmbeddedFreeTextByObjectNumberWithUndo(sel.objectNumber); } catch (Throwable ignore) { ok = false; }
                    if (ok) {
                        try { selectionCoordinator.deselectAnnotation(); } catch (Throwable ignore) {}
                        return;
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        selectionCoordinator.deleteSelectedAnnotation();
    }

    void editSelectedAnnotation() {
        // Sidecar notes already own edit via SidecarSelectionController.
        try {
            if (sidecarSelectionController != null && sidecarSelectionController.editSelected()) return;
        } catch (Throwable ignore) {
        }

        if (sidecarSession == null) {
            Annotation.Type selectedType = null;
            try { selectedType = selectionRouter.selectedAnnotationType(); } catch (Throwable ignore) { selectedType = null; }
            if (selectedType == Annotation.Type.FREETEXT || selectedType == Annotation.Type.TEXT) {
                try {
                    Annotation target = textAnnotationDelegate.selectedEmbeddedAnnotationOrNull();
                    if (target != null) {
                        forwardTextAnnotation(target);
                        return;
                    }
                } catch (Throwable ignore) {
                }
            }
        }

        selectionRouter.editSelectedAnnotation();
    }

    void onLayout() {
        layoutInlineEditor(inlineTextAnnotEditor, inlineTextAnnotEditorBoundsPx);
    }

    void onDispatchTouchEvent(@Nullable MotionEvent ev) {
        try {
            if (ev != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                EditText textEditor = inlineTextAnnotEditor;
                if (textEditor != null && textEditor.getParent() == host.viewGroup()) {
                    float x = ev.getX();
                    float y = ev.getY();
                    if (x < textEditor.getLeft() || x > textEditor.getRight() || y < textEditor.getTop() || y > textEditor.getBottom()) {
                        textEditor.clearFocus();
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    boolean showInlineTextAnnotationEditor(@NonNull Annotation annotation) {
        if (annotation == null) return false;

        // If the annotation has Acrobat rich contents, prefer the dialog flow which shows a warning
        // and avoids accidental destructive rewrites.
        if (annotation.type == Annotation.Type.FREETEXT && annotation.objectNumber > 0L) {
            try {
                if (muPdfController.rawRepository().hasFreeTextRichContentsByObjectNumber(host.pageNumber(), annotation.objectNumber)) {
                    return false;
                }
            } catch (Throwable ignore) {
            }
        }

        // Avoid overlapping inline editors (forms/widgets vs annotations).
        widgets.dismissInlineTextEditor();
        dismissInlineTextAnnotationEditor();

        final int page = host.pageNumber();
        final long objectNumber = annotation.objectNumber;
        String sidecarNoteId = null;
        SidecarNote sidecarNote = null;
        if (sidecarSession != null) {
            try {
                SidecarSelectionController.Selection sel = sidecarSelectionController.selectionOrNull();
                if (sel != null && sel.kind == SidecarSelectionController.Kind.NOTE && sel.id != null && !sel.id.trim().isEmpty()) {
                    sidecarNoteId = sel.id;
                    sidecarNote = textAnnotationDelegate.sidecarNoteById(sel.id);
                }
            } catch (Throwable ignore) {
                sidecarNoteId = null;
                sidecarNote = null;
            }
        }

        // Rotated text annotations currently fall back to the dialog editor (simpler + avoids awkward overlay mapping).
        if (sidecarNote != null) {
            try {
                if (sidecarNote.rotationDeg != 0) return false;
            } catch (Throwable ignore) {
            }
        } else if (objectNumber > 0L && annotation.type == Annotation.Type.FREETEXT) {
            try {
                int rot = muPdfController.rawRepository().getFreeTextRotationByObjectNumber(page, objectNumber);
                if ((rot % 360) != 0) return false;
            } catch (Throwable ignore) {
            }
        }

        RectF boundsDoc = sidecarNote != null && sidecarNote.bounds != null ? new RectF(sidecarNote.bounds) : new RectF(annotation);
        Rect boundsPx = textAnnotBoundsDocToViewPx(boundsDoc);
        if (boundsPx == null) return false;

        ensureInlineTextAnnotEditor();
        final EditText editor = inlineTextAnnotEditor;
        if (editor == null) return false;

        String startText = sidecarNote != null ? sidecarNote.text : annotation.text;
        Annotation draft = new Annotation(annotation.left, annotation.top, annotation.right, annotation.bottom, annotation.type, annotation.arcs, startText, annotation.objectNumber);
        inlineTextAnnotState = new InlineTextAnnotState(page, objectNumber, sidecarNoteId, draft, startText);
        inlineTextAnnotEditorBoundsPx = new Rect(boundsPx);

        configureInlineTextAnnotEditor(editor, inlineTextAnnotState, sidecarNote);

        try {
            editor.setText(startText != null ? startText : "");
            if (editor.getText() != null) {
                editor.setSelection(editor.getText().length());
            }
        } catch (Throwable ignore) {
        }

        try {
            ViewGroup hostView = host.viewGroup();
            if (editor.getParent() != hostView) {
                hostView.addView(editor, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            editor.bringToFront();
            editor.setVisibility(View.VISIBLE);
        } catch (Throwable ignore) {
        }

        try { host.requestLayoutSafe(); } catch (Throwable ignore) {}

        try {
            editor.requestFocus();
            editor.post(() -> {
                try {
                    InputMethodManager imm = (InputMethodManager) host.context().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
                } catch (Throwable ignore) {
                }
            });
        } catch (Throwable ignore) {
        }

        return true;
    }

    void dismissInlineTextAnnotationEditor() {
        dismissInlineTextAnnotationEditorInternal(true);
    }

    private void dismissInlineTextAnnotationEditorInternal(boolean hideKeyboard) {
        final EditText editor = inlineTextAnnotEditor;
        if (editor != null) {
            try {
                suppressInlineTextAnnotFocusLoss = true;
                editor.clearFocus();
            } catch (Throwable ignore) {
            }
            if (hideKeyboard) {
                try {
                    InputMethodManager imm = (InputMethodManager) host.context().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
                } catch (Throwable ignore) {
                }
            }
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
        }
        inlineTextAnnotEditorBoundsPx = null;
        inlineTextAnnotState = null;
        inlineTextAnnotSubmitting = false;
        suppressInlineTextAnnotFocusLoss = false;
    }

    private void ensureInlineTextAnnotEditor() {
        if (inlineTextAnnotEditor != null) return;
        final EditText editor = new EditText(host.context());
        try { editor.setId(R.id.dialog_text_input); } catch (Throwable ignore) {}

        try {
            editor.setSingleLine(false);
            editor.setMinLines(1);
            editor.setMaxLines(Integer.MAX_VALUE);
            editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            editor.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editor.setHorizontallyScrolling(false);
        } catch (Throwable ignore) {
        }

        // Prevent default EditText chrome; the selection box already provides affordance.
        try { editor.setBackgroundDrawable(null); } catch (Throwable ignore) {}
        try { editor.setPadding(0, 0, 0, 0); } catch (Throwable ignore) {}

        editor.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitInlineTextAnnotEdit();
                return true;
            }
            return false;
        });

        editor.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) return;
            if (suppressInlineTextAnnotFocusLoss) return;
            commitInlineTextAnnotEdit();
        });

        inlineTextAnnotEditor = editor;
    }

    private void commitInlineTextAnnotEdit() {
        final EditText editor = inlineTextAnnotEditor;
        final InlineTextAnnotState state = inlineTextAnnotState;
        if (editor == null || state == null) return;
        if (inlineTextAnnotSubmitting) return;
        inlineTextAnnotSubmitting = true;

        final String nextText;
        try {
            nextText = editor.getText() != null ? editor.getText().toString() : "";
        } catch (Throwable t) {
            dismissInlineTextAnnotationEditorInternal(true);
            return;
        }

        try {
            InputMethodManager imm = (InputMethodManager) host.context().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        } catch (Throwable ignore) {
        }

        // If the view has been recycled to a different page, don't commit.
        if (host.pageNumber() != state.pageNumber) {
            dismissInlineTextAnnotationEditorInternal(false);
            return;
        }

        if (state.objectNumber > 0L) {
            if (Objects.equals(state.priorText, nextText)) {
                dismissInlineTextAnnotationEditorInternal(false);
                return;
            }
            try { textAnnotationDelegate.updateTextAnnotationContentsByObjectNumber(state.objectNumber, nextText); } catch (Throwable ignore) {}
            dismissInlineTextAnnotationEditorInternal(false);
            return;
        }

        if (state.sidecarNoteId != null && !state.sidecarNoteId.trim().isEmpty()) {
            try { textAnnotationDelegate.updateSidecarNoteTextById(state.sidecarNoteId, nextText); } catch (Throwable ignore) {}
            dismissInlineTextAnnotationEditorInternal(false);
            return;
        }

        // Draft/new annotation: don't add empty placeholders.
        if (nextText == null || nextText.trim().isEmpty()) {
            dismissInlineTextAnnotationEditorInternal(false);
            return;
        }
        try {
            Annotation draft = state.draft;
            draft.text = nextText;
            host.addTextAnnotationFromUi(draft);
        } catch (Throwable ignore) {
        }
        dismissInlineTextAnnotationEditorInternal(false);
    }

    private void configureInlineTextAnnotEditor(@NonNull EditText editor,
                                                @NonNull InlineTextAnnotState state,
                                                @Nullable SidecarNote sidecarNote) {
        if (editor == null || state == null) return;
        float scale = host.scale();
        if (scale <= 0f) scale = 1f;

        float fontSizeDoc = 12.0f;
        int fontFamily = org.opendroidpdf.app.annotation.TextFontFamily.SANS;
        int styleFlags = 0;
        int gravity = android.view.Gravity.START | android.view.Gravity.TOP;
        int textColor = 0xFF111111;

        if (sidecarNote != null) {
            try { fontSizeDoc = sidecarNote.fontSize > 0f ? sidecarNote.fontSize : 12.0f; } catch (Throwable ignore) { fontSizeDoc = 12.0f; }
            try { fontFamily = sidecarNote.fontFamily; } catch (Throwable ignore) { fontFamily = org.opendroidpdf.app.annotation.TextFontFamily.SANS; }
            try { styleFlags = sidecarNote.fontStyleFlags; } catch (Throwable ignore) { styleFlags = 0; }
            try { textColor = sidecarNote.color != 0 ? sidecarNote.color : 0xFF111111; } catch (Throwable ignore) { textColor = 0xFF111111; }
        } else if (state.objectNumber > 0L && state.draft.type == Annotation.Type.FREETEXT) {
            int baseDpi = 160;
            float fontPt = 12.0f;
            int align = 0;
            try { baseDpi = muPdfController.rawRepository().getBaseResolutionDpi(); } catch (Throwable ignore) { baseDpi = 160; }
            try { fontPt = muPdfController.rawRepository().getFreeTextFontSizeByObjectNumber(state.pageNumber, state.objectNumber); } catch (Throwable ignore) { fontPt = 12.0f; }
            try { fontFamily = muPdfController.rawRepository().getFreeTextFontFamilyByObjectNumber(state.pageNumber, state.objectNumber); } catch (Throwable ignore) { fontFamily = org.opendroidpdf.app.annotation.TextFontFamily.SANS; }
            try { styleFlags = muPdfController.rawRepository().getFreeTextStyleFlagsByObjectNumber(state.pageNumber, state.objectNumber); } catch (Throwable ignore) { styleFlags = 0; }
            try { align = muPdfController.rawRepository().getFreeTextAlignmentByObjectNumber(state.pageNumber, state.objectNumber); } catch (Throwable ignore) { align = 0; }
            align = Math.max(0, Math.min(2, align));
            if (align == 1) gravity = android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.TOP;
            else if (align == 2) gravity = android.view.Gravity.END | android.view.Gravity.TOP;
            else gravity = android.view.Gravity.START | android.view.Gravity.TOP;

            float dpi = baseDpi > 0 ? (float) baseDpi : 160f;
            fontSizeDoc = fontPt * (dpi / 72f);
        }

        float fontPx = Math.max(8f, fontSizeDoc * scale);
        try { editor.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontPx); } catch (Throwable ignore) {}
        try { editor.setTextColor(textColor); } catch (Throwable ignore) {}
        try { editor.setGravity(gravity); } catch (Throwable ignore) {}

        Typeface base = org.opendroidpdf.app.annotation.TextFontFamily.typeface(fontFamily);
        int tfStyle = org.opendroidpdf.app.annotation.TextStyleFlags.typefaceStyle(styleFlags);
        try { editor.setTypeface(Typeface.create(base, tfStyle)); } catch (Throwable ignore) {}

        try { editor.setPaintFlags(editor.getPaintFlags() & ~(android.graphics.Paint.UNDERLINE_TEXT_FLAG | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG)); } catch (Throwable ignore) {}
        try {
            if (org.opendroidpdf.app.annotation.TextStyleFlags.isUnderline(styleFlags)) {
                editor.setPaintFlags(editor.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            }
            if (org.opendroidpdf.app.annotation.TextStyleFlags.isStrikethrough(styleFlags)) {
                editor.setPaintFlags(editor.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }
        } catch (Throwable ignore) {
        }
    }

    @Nullable
    private Rect textAnnotBoundsDocToViewPx(@NonNull RectF docBounds) {
        float scale = host.scale();
        if (scale <= 0f) return null;

        int left = Math.round(docBounds.left * scale);
        int top = Math.round(docBounds.top * scale);
        int right = Math.round(docBounds.right * scale);
        int bottom = Math.round(docBounds.bottom * scale);

        float density = host.context().getResources().getDisplayMetrics().density;
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

        if (right <= left || bottom <= top) return null;
        return new Rect(left, top, right, bottom);
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
}
