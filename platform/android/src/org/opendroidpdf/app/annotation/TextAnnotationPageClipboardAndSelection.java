package org.opendroidpdf.app.annotation;

import android.content.ClipData;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.RectF;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.app.overlay.ItemSelectionHandles;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

import java.util.List;

final class TextAnnotationPageClipboardAndSelection {
    private final TextAnnotationPageDelegate.Host host;

    TextAnnotationPageClipboardAndSelection(@NonNull TextAnnotationPageDelegate.Host host) {
        this.host = host;
    }

    @Nullable
    Annotation selectedEmbeddedAnnotationOrNull() {
        if (host.sidecarSessionOrNull() != null) return null;
        Annotation[] annots = host.embeddedAnnotationsOrNull();
        if (annots == null || annots.length == 0) return null;

        // Prefer stable identity if available.
        long objectId = -1L;
        try { objectId = host.selectionManager().selectedObjectNumber(); } catch (Throwable ignore) { objectId = -1L; }
        if (objectId > 0L) {
            Annotation byId = findAnnotationByObjectNumber(annots, objectId);
            if (byId != null) return byId;
        }

        int idx = host.selectionManager().selectedIndex();
        if (idx < 0 || idx >= annots.length) return null;
        return annots[idx];
    }

    @Nullable
    SidecarNote sidecarNoteById(@NonNull String noteId) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return null;
        if (noteId == null || noteId.trim().isEmpty()) return null;
        try {
            List<SidecarNote> notes = sidecar.notesForPage(host.pageNumber());
            if (notes == null || notes.isEmpty()) return null;
            for (SidecarNote n : notes) {
                if (n != null && noteId.equals(n.id)) return n;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    @Nullable
    String sidecarNoteTextById(@NonNull String noteId) {
        SidecarNote note = sidecarNoteById(noteId);
        return note != null ? note.text : null;
    }

    /** Selects an embedded PDF annotation by stable object number, if present on this page. */
    boolean selectEmbeddedAnnotationByObjectNumber(long objectNumber) {
        if (objectNumber <= 0L) return false;
        Annotation[] annots = host.embeddedAnnotationsOrNull();
        if (annots == null || annots.length == 0) return false;
        for (int i = 0; i < annots.length; i++) {
            Annotation a = annots[i];
            if (a == null || a.objectNumber != objectNumber) continue;
            try {
                RectF bounds = new RectF(a);
                host.selectionManager().select(i, objectNumber, bounds, host.selectionUiBridge().selectionBoxHost());
            } catch (Throwable ignore) {
                try { host.setAnnotationSelectionBox(new RectF(a)); } catch (Throwable ignore2) {}
            }
            return true;
        }
        return false;
    }

    /** Selects a sidecar note by id, if present on this page. */
    boolean selectSidecarNoteById(@NonNull String noteId) {
        if (noteId == null || noteId.trim().isEmpty()) return false;
        try {
            return host.sidecarSelectionController().selectNoteById(noteId);
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Selects a sidecar highlight by id, if present on this page. */
    boolean selectSidecarHighlightById(@NonNull String highlightId) {
        if (highlightId == null || highlightId.trim().isEmpty()) return false;
        try {
            return host.sidecarSelectionController().selectHighlightById(highlightId);
        } catch (Throwable ignore) {
            return false;
        }
    }

    @Nullable
    static RectF offsetAndClampDocBounds(@NonNull Resources res,
                                         float scale,
                                         float docW,
                                         float docH,
                                         @NonNull RectF boundsDoc) {
        if (scale <= 0f) return null;

        float density = res.getDisplayMetrics().density;
        float offsetDoc = (16f * density) / scale;

        RectF r = new RectF(boundsDoc);
        r.offset(offsetDoc, offsetDoc);

        // Clamp to document bounds.
        float w = r.width();
        float h = r.height();
        if (w > docW) w = docW;
        if (h > docH) h = docH;

        if (r.left < 0f) r.offset(-r.left, 0f);
        if (r.top < 0f) r.offset(0f, -r.top);
        if (r.right > docW) r.offset(docW - r.right, 0f);
        if (r.bottom > docH) r.offset(0f, docH - r.bottom);

        r.left = Math.max(0f, Math.min(docW - w, r.left));
        r.top = Math.max(0f, Math.min(docH - h, r.top));
        r.right = Math.min(docW, r.left + w);
        r.bottom = Math.min(docH, r.top + h);

        // Enforce a minimum edge so selection handles remain usable.
        float minEdgeDoc = ItemSelectionHandles.minEdgePx(res) / scale;
        if (r.width() < minEdgeDoc) r.right = Math.min(docW, r.left + minEdgeDoc);
        if (r.height() < minEdgeDoc) r.bottom = Math.min(docH, r.top + minEdgeDoc);

        return r;
    }

    @Nullable
    static RectF offsetAndClampDocBoundsWithSteps(@NonNull Resources res,
                                                  float scale,
                                                  float docW,
                                                  float docH,
                                                  @NonNull RectF boundsDoc,
                                                  int offsetSteps) {
        if (scale <= 0f) return null;

        float density = res.getDisplayMetrics().density;
        float step = Math.max(0, offsetSteps);
        float offsetDoc = (16f * density * step) / scale;

        RectF r = new RectF(boundsDoc);
        r.offset(offsetDoc, offsetDoc);

        // Clamp to document bounds.
        float w = r.width();
        float h = r.height();
        if (w > docW) w = docW;
        if (h > docH) h = docH;

        if (r.left < 0f) r.offset(-r.left, 0f);
        if (r.top < 0f) r.offset(0f, -r.top);
        if (r.right > docW) r.offset(docW - r.right, 0f);
        if (r.bottom > docH) r.offset(0f, docH - r.bottom);

        r.left = Math.max(0f, Math.min(docW - w, r.left));
        r.top = Math.max(0f, Math.min(docH - h, r.top));
        r.right = Math.min(docW, r.left + w);
        r.bottom = Math.min(docH, r.top + h);

        // Enforce a minimum edge so selection handles remain usable.
        float minEdgeDoc = ItemSelectionHandles.minEdgePx(res) / scale;
        if (r.width() < minEdgeDoc) r.right = Math.min(docW, r.left + minEdgeDoc);
        if (r.height() < minEdgeDoc) r.bottom = Math.min(docH, r.top + minEdgeDoc);

        return r;
    }

    static void copyPlainTextToSystemClipboard(@NonNull Context context, @NonNull String text) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null) return;
                cm.setPrimaryClip(ClipData.newPlainText(context.getPackageName(), text));
            } else {
                @SuppressWarnings("deprecation")
                android.text.ClipboardManager cm =
                        (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null) return;
                cm.setText(text);
            }
        } catch (Throwable ignore) {
        }
    }

    @Nullable
    private static Annotation findAnnotationByObjectNumber(@Nullable Annotation[] annots, long objectId) {
        if (annots == null || objectId <= 0L) return null;
        for (Annotation a : annots) {
            if (a != null && a.objectNumber == objectId) return a;
        }
        return null;
    }
}

