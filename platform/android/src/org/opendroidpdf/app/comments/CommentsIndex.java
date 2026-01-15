package org.opendroidpdf.app.comments;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarHighlight;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.app.sidecar.model.SidecarNote;
import org.opendroidpdf.core.MuPdfRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Builds a sorted list of comment-style annotations for navigation and list UIs. */
public final class CommentsIndex {

    private CommentsIndex() {}

    public enum Backend { EMBEDDED, SIDECAR }

    public enum Bucket { NOTE, TEXT_BOX, MARKUP, INK, OTHER }

    public static final class Entry {
        @NonNull public final Backend backend;
        @NonNull public final Bucket bucket;
        public final int pageIndex;
        @NonNull public final RectF boundsDoc;
        public final long createdAtEpochMs;

        @Nullable public final Annotation.Type annotType;
        public final long embeddedObjectNumber;

        @Nullable public final SidecarSelectionController.Kind sidecarKind;
        @Nullable public final String sidecarId;

        @Nullable public final String searchText;

        public Entry(@NonNull Backend backend,
                     @NonNull Bucket bucket,
                     int pageIndex,
                     @NonNull RectF boundsDoc,
                     long createdAtEpochMs,
                     @Nullable Annotation.Type annotType,
                     long embeddedObjectNumber,
                     @Nullable SidecarSelectionController.Kind sidecarKind,
                     @Nullable String sidecarId,
                     @Nullable String searchText) {
            this.backend = backend;
            this.bucket = bucket;
            this.pageIndex = pageIndex;
            this.boundsDoc = boundsDoc;
            this.createdAtEpochMs = createdAtEpochMs;
            this.annotType = annotType;
            this.embeddedObjectNumber = embeddedObjectNumber;
            this.sidecarKind = sidecarKind;
            this.sidecarId = sidecarId;
            this.searchText = searchText;
        }
    }

    @NonNull
    public static List<Entry> load(@NonNull MuPdfRepository repo,
                                   @Nullable SidecarAnnotationProvider sidecarProvider) {
        final int pages;
        try {
            pages = Math.max(0, repo.getPageCount());
        } catch (Throwable t) {
            return Collections.emptyList();
        }

        final SidecarAnnotationSession sidecar =
                (sidecarProvider instanceof SidecarAnnotationSession) ? (SidecarAnnotationSession) sidecarProvider : null;

        ArrayList<Entry> out = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
            if (sidecar != null) {
                try {
                    for (SidecarNote n : sidecar.notesForPage(pageIndex)) {
                        if (n == null || n.bounds == null || n.id == null) continue;
                        String text = n.text != null ? n.text : "";
                        out.add(new Entry(
                                Backend.SIDECAR,
                                Bucket.NOTE,
                                pageIndex,
                                new RectF(n.bounds),
                                n.createdAtEpochMs,
                                Annotation.Type.TEXT,
                                -1L,
                                SidecarSelectionController.Kind.NOTE,
                                n.id,
                                text));
                    }
                } catch (Throwable ignore) {
                }

                try {
                    for (SidecarHighlight h : sidecar.highlightsForPage(pageIndex)) {
                        if (h == null || h.id == null || h.quadPoints == null || h.quadPoints.length < 4) continue;
                        RectF bounds = quadUnion(h.quadPoints);
                        if (bounds == null) continue;
                        String quote = h.quote != null ? h.quote : "";
                        out.add(new Entry(
                                Backend.SIDECAR,
                                Bucket.MARKUP,
                                pageIndex,
                                bounds,
                                h.createdAtEpochMs,
                                h.type,
                                -1L,
                                SidecarSelectionController.Kind.HIGHLIGHT,
                                h.id,
                                quote));
                    }
                } catch (Throwable ignore) {
                }

                try {
                    for (SidecarInkStroke s : sidecar.inkStrokesForPage(pageIndex)) {
                        if (s == null || s.id == null || s.points == null || s.points.length < 2) continue;
                        RectF bounds = pointsBounds(s.points);
                        if (bounds == null) continue;
                        out.add(new Entry(
                                Backend.SIDECAR,
                                Bucket.INK,
                                pageIndex,
                                bounds,
                                s.createdAtEpochMs,
                                Annotation.Type.INK,
                                -1L,
                                null,
                                s.id,
                                null));
                    }
                } catch (Throwable ignore) {
                }
            }

            Annotation[] annots;
            try {
                annots = repo.loadAnnotations(pageIndex);
            } catch (Throwable t) {
                annots = null;
            }
            if (annots == null || annots.length == 0) continue;
            for (Annotation a : annots) {
                if (a == null || a.type == null) continue;
                if (!isCommentType(a.type)) continue;
                RectF bounds = new RectF(a);
                String text = a.text != null ? a.text : "";
                out.add(new Entry(
                        Backend.EMBEDDED,
                        bucketFor(a.type),
                        pageIndex,
                        bounds,
                        0L,
                        a.type,
                        a.objectNumber,
                        null,
                        null,
                        text));
            }
        }

        out.sort(new EntryComparator());
        return out;
    }

    private static final class EntryComparator implements Comparator<Entry> {
        @Override
        public int compare(Entry a, Entry b) {
            if (a == b) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            if (a.pageIndex != b.pageIndex) return Integer.compare(a.pageIndex, b.pageIndex);
            float at = a.boundsDoc != null ? a.boundsDoc.top : 0f;
            float bt = b.boundsDoc != null ? b.boundsDoc.top : 0f;
            if (Math.abs(at - bt) > 0.001f) return Float.compare(at, bt);
            float al = a.boundsDoc != null ? a.boundsDoc.left : 0f;
            float bl = b.boundsDoc != null ? b.boundsDoc.left : 0f;
            return Float.compare(al, bl);
        }
    }

    private static boolean isCommentType(@NonNull Annotation.Type type) {
        switch (type) {
            case FREETEXT:
            case TEXT:
            case HIGHLIGHT:
            case UNDERLINE:
            case STRIKEOUT:
            case SQUIGGLY:
            case INK:
                return true;
            default:
                return false;
        }
    }

    @NonNull
    private static Bucket bucketFor(@NonNull Annotation.Type type) {
        switch (type) {
            case TEXT:
                return Bucket.NOTE;
            case FREETEXT:
                return Bucket.TEXT_BOX;
            case HIGHLIGHT:
            case UNDERLINE:
            case STRIKEOUT:
            case SQUIGGLY:
                return Bucket.MARKUP;
            case INK:
                return Bucket.INK;
            default:
                return Bucket.OTHER;
        }
    }

    @Nullable
    private static RectF quadUnion(@NonNull PointF[] quadPoints) {
        if (quadPoints.length < 4) return null;
        RectF union = null;
        int n = quadPoints.length - (quadPoints.length % 4);
        for (int i = 0; i < n; i += 4) {
            RectF r = quadRect(quadPoints, i);
            if (r == null) continue;
            if (union == null) union = new RectF(r);
            else union.union(r);
        }
        return union;
    }

    @Nullable
    private static RectF quadRect(@NonNull PointF[] points, int start) {
        if (points.length < start + 4) return null;
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (int j = 0; j < 4; j++) {
            PointF p = points[start + j];
            if (p == null) continue;
            if (p.x < left) left = p.x;
            if (p.y < top) top = p.y;
            if (p.x > right) right = p.x;
            if (p.y > bottom) bottom = p.y;
        }
        if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(right) || !Float.isFinite(bottom)) return null;
        return new RectF(left, top, right, bottom);
    }

    @Nullable
    private static RectF pointsBounds(@NonNull PointF[] points) {
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (PointF p : points) {
            if (p == null) continue;
            if (p.x < left) left = p.x;
            if (p.y < top) top = p.y;
            if (p.x > right) right = p.x;
            if (p.y > bottom) bottom = p.y;
        }
        if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(right) || !Float.isFinite(bottom)) return null;
        if (right - left < 0.5f) right = left + 0.5f;
        if (bottom - top < 0.5f) bottom = top + 0.5f;
        return new RectF(left, top, right, bottom);
    }
}

