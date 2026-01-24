package org.opendroidpdf.app.sidecar;

import android.graphics.PointF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.reflow.ReflowPrefsSnapshot;
import org.opendroidpdf.app.reflow.ReflowPrefsStore;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SidecarInkOps {
    private final String docId;
    @Nullable private final String layoutProfileId;
    private final SidecarAnnotationStore store;
    @Nullable private final ReflowPrefsStore reflowPrefsStore;
    @Nullable private final ReflowPrefsSnapshot reflowPrefsSnapshot;

    private final Map<Integer, List<SidecarInkStroke>> inkCache = new HashMap<>();

    SidecarInkOps(@NonNull String docId,
                  @Nullable String layoutProfileId,
                  @NonNull SidecarAnnotationStore store,
                  @Nullable ReflowPrefsStore reflowPrefsStore,
                  @Nullable ReflowPrefsSnapshot reflowPrefsSnapshot) {
        this.docId = docId;
        this.layoutProfileId = layoutProfileId;
        this.store = store;
        this.reflowPrefsStore = reflowPrefsStore;
        this.reflowPrefsSnapshot = reflowPrefsSnapshot;
    }

    void clearCache() { inkCache.clear(); }

    @NonNull
    List<SidecarInkStroke> inkStrokesForPage(int pageIndex) {
        List<SidecarInkStroke> cached = inkCache.get(pageIndex);
        if (cached != null) return cached;
        List<SidecarInkStroke> loaded = store.listInk(docId, pageIndex, layoutProfileId);
        List<SidecarInkStroke> ro = Collections.unmodifiableList(loaded);
        inkCache.put(pageIndex, ro);
        return ro;
    }

    @NonNull
    List<SidecarInkStroke> addInkFromArcs(int pageIndex,
                                         @NonNull PointF[][] arcs,
                                         int color,
                                         float thickness,
                                         long createdAtEpochMs) {
        ArrayList<SidecarInkStroke> toInsert = new ArrayList<>();
        for (PointF[] arc : arcs) {
            if (arc == null || arc.length < 2) continue;
            String id = UUID.randomUUID().toString();
            toInsert.add(new SidecarInkStroke(id, pageIndex, layoutProfileId, color, thickness, createdAtEpochMs, arc));
        }
        if (!toInsert.isEmpty()) {
            store.insertInk(docId, toInsert);
            SidecarReflowUtils.recordAnnotatedLayoutIfPossible(docId, layoutProfileId, reflowPrefsStore, reflowPrefsSnapshot);
            // Replace cached list with a new copy that includes the insertions.
            List<SidecarInkStroke> current = new ArrayList<>(inkStrokesForPage(pageIndex));
            current.addAll(toInsert);
            inkCache.put(pageIndex, Collections.unmodifiableList(current));
        }
        return toInsert;
    }

    @Nullable
    SidecarInkStroke removeInkStroke(int pageIndex, @NonNull String strokeId) {
        List<SidecarInkStroke> current = new ArrayList<>(inkStrokesForPage(pageIndex));
        SidecarInkStroke removed = null;
        for (int i = 0; i < current.size(); i++) {
            SidecarInkStroke s = current.get(i);
            if (s != null && strokeId.equals(s.id)) {
                removed = s;
                current.remove(i);
                break;
            }
        }
        if (removed != null) {
            store.deleteInk(docId, strokeId);
            inkCache.put(pageIndex, Collections.unmodifiableList(current));
        }
        return removed;
    }

    void restoreInkStroke(@NonNull SidecarInkStroke stroke) {
        store.insertInk(docId, java.util.Collections.singletonList(stroke));
        List<SidecarInkStroke> current = new ArrayList<>(inkStrokesForPage(stroke.pageIndex));
        current.add(stroke);
        inkCache.put(stroke.pageIndex, Collections.unmodifiableList(current));
    }
}

