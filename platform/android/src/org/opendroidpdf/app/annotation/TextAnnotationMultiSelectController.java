package org.opendroidpdf.app.annotation;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.R;
import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Document-scoped multi-select helper for text annotations (embedded FreeText/Text + sidecar notes).
 *
 * <p>Scope:
 * - Per-page: a selection set only applies to the page it was created on.
 * - Geometry operations: align and distribute (horizontal/vertical) of selected boxes.
 * - Safe defaults: locked annotations are skipped; operations require 2+ items.</p>
 */
public final class TextAnnotationMultiSelectController {

    public enum Action {
        ALIGN_LEFT,
        ALIGN_CENTER_HORIZONTAL,
        ALIGN_RIGHT,
        ALIGN_TOP,
        ALIGN_CENTER_VERTICAL,
        ALIGN_BOTTOM,
        DISTRIBUTE_HORIZONTAL,
        DISTRIBUTE_VERTICAL,
        CLEAR
    }

    public interface Host {
        @NonNull AppCompatActivity activity();
        @Nullable MuPDFPageView currentPageView();
        void showInfo(@NonNull String message);
        void invalidateQuickActions();
    }

    private static final class Item {
        final int pageIndex;
        final boolean sidecar;
        final long objectNumber;
        @Nullable final String sidecarId;
        RectF bounds; // doc-relative

        Item(int pageIndex, long objectNumber, @Nullable String sidecarId, boolean sidecar, @NonNull RectF bounds) {
            this.pageIndex = pageIndex;
            this.objectNumber = objectNumber;
            this.sidecarId = sidecarId;
            this.sidecar = sidecar;
            this.bounds = bounds;
        }
    }

    private final Host host;
    private final ArrayList<Item> items = new ArrayList<>();
    private int pageIndex = -1;
    private boolean grouped = false;

    public TextAnnotationMultiSelectController(@NonNull Host host) {
        this.host = host;
    }

    public void clear() {
        items.clear();
        pageIndex = -1;
        grouped = false;
        host.invalidateQuickActions();
    }

    public int size() { return items.size(); }

    public boolean canApplyOnPage(int page) {
        return pageIndex >= 0 && pageIndex == page && items.size() >= 2;
    }

    public boolean isGrouped() { return grouped && items.size() >= 2; }

    public boolean addCurrentSelection() {
        MuPDFPageView pv = host.currentPageView();
        if (pv == null) {
            host.showInfo(host.activity().getString(R.string.text_multi_select_need_selection));
            return false;
        }

        final int page = pv.pageNumber();
        if (pageIndex >= 0 && pageIndex != page) {
            host.showInfo(host.activity().getString(R.string.text_multi_select_same_page));
            return false;
        }

        final Selection resolved = resolveSelection(pv);
        if (resolved == null) {
            host.showInfo(host.activity().getString(R.string.text_multi_select_need_selection));
            return false;
        }

        if (pageIndex < 0) pageIndex = page;

        // De-duplicate by id.
        for (Item it : items) {
            if (it.sidecar == resolved.sidecar
                    && ((resolved.sidecar && safeEquals(it.sidecarId, resolved.sidecarId))
                        || (!resolved.sidecar && it.objectNumber == resolved.objectNumber))) {
                it.bounds = new RectF(resolved.bounds); // refresh bounds
                host.showInfo(host.activity().getString(R.string.text_multi_select_added_format,
                        items.size(), pageIndex + 1));
                host.invalidateQuickActions();
                return true;
            }
        }

        items.add(new Item(page, resolved.objectNumber, resolved.sidecarId, resolved.sidecar, new RectF(resolved.bounds)));
        host.showInfo(host.activity().getString(R.string.text_multi_select_added_format, items.size(), pageIndex + 1));
        host.invalidateQuickActions();
        return true;
    }

    /**
     * Toggles grouped move for the current selection set. Automatically adds the active selection
     * into the set if it belongs to the same page.
     */
    public boolean toggleGroupForCurrentSelection() {
        MuPDFPageView pv = host.currentPageView();
        if (pv == null) {
            host.showInfo(host.activity().getString(R.string.text_multi_select_need_selection));
            return false;
        }
        // Best-effort: add the current selection into the set before grouping.
        addCurrentSelection();

        if (!canApplyOnPage(pv.pageNumber())) {
            grouped = false;
            host.showInfo(host.activity().getString(R.string.text_multi_select_group_need_two));
            host.invalidateQuickActions();
            return false;
        }

        grouped = !grouped;
        host.showInfo(host.activity().getString(
                grouped ? R.string.text_multi_select_group_on_format : R.string.text_multi_select_group_off));
        host.invalidateQuickActions();
        return grouped;
    }

    /** Debug/test hook: translate all items on the current page by a fixed delta. */
    public boolean debugTranslateAll(float dx, float dy) {
        MuPDFPageView pv = host.currentPageView();
        if (pv == null) return false;
        List<Item> onPage = new ArrayList<>();
        for (Item it : items) {
            if (it.pageIndex == pv.pageNumber()) onPage.add(it);
        }
        if (onPage.isEmpty()) return false;
        ArrayList<RectF> targets = new ArrayList<>(onPage.size());
        for (Item it : onPage) {
            RectF next = new RectF(it.bounds);
            next.offset(dx, dy);
            targets.add(next);
        }
        boolean ok = applyBounds(pv, onPage, targets);
        if (BuildConfig.DEBUG) {
            StringBuilder sb = new StringBuilder();
            for (Item it : onPage) {
                sb.append(String.format(Locale.US,
                        "[page=%d sidecar=%b obj=%d sid=%s b=%.1f,%.1f,%.1f,%.1f] ",
                        it.pageIndex, it.sidecar, it.objectNumber, it.sidecarId,
                        it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom));
            }
            android.util.Log.d("OpenDroidPDF/Debug",
                    "text-multi-debug-translate dx=" + dx + " dy=" + dy
                            + " page=" + pv.pageNumber() + " ok=" + ok + " items=" + sb);
        }
        return ok;
    }

    /**
     * Applies a translation to the grouped items (if grouping is active) when the anchor item moves.
     *
     * @return {@code true} if grouping was active and all moves succeeded; {@code false} otherwise.
     */
    public boolean applyGroupTranslation(@NonNull MuPDFPageView pv,
                                         long anchorObjectNumber,
                                         @Nullable String anchorSidecarId,
                                         float dx,
                                         float dy,
                                         boolean markUserResized) {
        if (!isGrouped()) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d("OpenDroidPDF/Debug", "text-multi-group-translate skipped: not grouped");
            }
            return false;
        }
        if (!canApplyOnPage(pv.pageNumber())) {
            host.showInfo(host.activity().getString(R.string.text_multi_select_group_navigate, pageIndex + 1));
            if (BuildConfig.DEBUG) {
                android.util.Log.d("OpenDroidPDF/Debug", "text-multi-group-translate skipped: wrong page pageIndex="
                        + pageIndex + " current=" + pv.pageNumber());
            }
            return false;
        }
        if (Math.abs(dx) < 1e-4f && Math.abs(dy) < 1e-4f) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d("OpenDroidPDF/Debug", "text-multi-group-translate skipped: tiny delta");
            }
            return true;
        }

        boolean ok = true;
        for (Item it : items) {
            if (it.pageIndex != pv.pageNumber()) continue;
            RectF target = new RectF(it.bounds);
            target.offset(dx, dy);

            // Skip committing the anchor again; just refresh its cached bounds.
            if (!it.sidecar && anchorObjectNumber > 0 && it.objectNumber == anchorObjectNumber) {
                it.bounds = new RectF(target);
                continue;
            }
            if (it.sidecar && it.sidecarId != null && it.sidecarId.equals(anchorSidecarId)) {
                it.bounds = new RectF(target);
                continue;
            }

            boolean applied = applySingle(pv, it, target, markUserResized);
            ok = ok && applied;
            if (applied) it.bounds = new RectF(target);
        }

        // Refresh the anchor entry if it is in the set.
        updateBoundsForItem(anchorObjectNumber, anchorSidecarId, pv.pageNumber(), null, dx, dy);
        host.invalidateQuickActions();
        if (BuildConfig.DEBUG) {
            StringBuilder sb = new StringBuilder();
            for (Item it : items) {
                sb.append(String.format(Locale.US,
                        "[page=%d sidecar=%b obj=%d sid=%s b=%.1f,%.1f,%.1f,%.1f] ",
                        it.pageIndex, it.sidecar, it.objectNumber, it.sidecarId,
                        it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom));
            }
            android.util.Log.d("OpenDroidPDF/Debug",
                    "text-multi-group-translate dx=" + dx + " dy=" + dy
                            + " page=" + pv.pageNumber() + " grouped=" + grouped
                            + " items=" + sb);
        }
        return ok;
    }

    /** Keeps cached bounds fresh when an item is moved individually. */
    public void updateBoundsForItem(long objectNumber,
                                    @Nullable String sidecarId,
                                    int page,
                                    @Nullable RectF absoluteBounds,
                                    float dx,
                                    float dy) {
        if (items.isEmpty()) return;
        for (Item it : items) {
            if (it.pageIndex != page) continue;
            if (matches(it, objectNumber, sidecarId)) {
                if (absoluteBounds != null) {
                    it.bounds = new RectF(absoluteBounds);
                } else {
                    it.bounds = new RectF(it.bounds);
                    it.bounds.offset(dx, dy);
                }
                break;
            }
        }
    }

    public void showAlignDistributePicker() {
        AppCompatActivity activity = host.activity();
        MuPDFPageView pv = host.currentPageView();
        if (pv == null) {
            host.showInfo(activity.getString(R.string.text_multi_select_need_selection));
            return;
        }
        if (!canApplyOnPage(pv.pageNumber())) {
            host.showInfo(activity.getString(R.string.text_multi_select_need_two));
            return;
        }

        final String[] labels = activity.getResources().getStringArray(R.array.text_multi_select_actions);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.text_multi_select_align_distribute)
                .setItems(labels, (d, which) -> {
                    Action action = actionForIndex(which);
                    if (action != null) apply(action);
                })
                .setNegativeButton(R.string.dismiss, (d, w) -> {})
                .show();
    }

    public boolean apply(@NonNull Action action) {
        if (action == Action.CLEAR) {
            clear();
            return true;
        }

        MuPDFPageView pv = host.currentPageView();
        if (pv == null) {
            host.showInfo(host.activity().getString(R.string.text_multi_select_need_selection));
            return false;
        }
        if (!canApplyOnPage(pv.pageNumber())) {
            host.showInfo(host.activity().getString(R.string.text_multi_select_navigate, pageIndex + 1));
            return false;
        }

        List<Item> onPage = new ArrayList<>();
        for (Item it : items) {
            if (it.pageIndex == pv.pageNumber()) onPage.add(it);
        }
        if (onPage.size() < 2) {
            host.showInfo(host.activity().getString(R.string.text_multi_select_need_two));
            return false;
        }

        if (BuildConfig.DEBUG) {
            StringBuilder sb = new StringBuilder();
            for (Item it : onPage) {
                sb.append(String.format(Locale.US,
                        "[page=%d sidecar=%b obj=%d sid=%s b=%.1f,%.1f,%.1f,%.1f] ",
                        it.pageIndex, it.sidecar, it.objectNumber, it.sidecarId,
                        it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom));
            }
            android.util.Log.d("OpenDroidPDF/Debug",
                    "text-multi-apply-pre " + action + " page=" + pv.pageNumber() + " items=" + sb);
        }

        boolean ok;
        switch (action) {
            case ALIGN_LEFT:
                ok = alignLeft(pv, onPage); break;
            case ALIGN_CENTER_HORIZONTAL:
                ok = alignCenterX(pv, onPage); break;
            case ALIGN_RIGHT:
                ok = alignRight(pv, onPage); break;
            case ALIGN_TOP:
                ok = alignTop(pv, onPage); break;
            case ALIGN_CENTER_VERTICAL:
                ok = alignCenterY(pv, onPage); break;
            case ALIGN_BOTTOM:
                ok = alignBottom(pv, onPage); break;
            case DISTRIBUTE_HORIZONTAL:
                ok = distributeHorizontal(pv, onPage); break;
            case DISTRIBUTE_VERTICAL:
                ok = distributeVertical(pv, onPage); break;
            default:
                ok = false;
        }

        if (!ok) {
            host.showInfo(host.activity().getString(R.string.text_multi_select_apply_failed));
        } else if (BuildConfig.DEBUG) {
            StringBuilder sb = new StringBuilder();
            for (Item it : onPage) {
                sb.append(String.format(Locale.US,
                        "[page=%d sidecar=%b obj=%d sid=%s b=%.1f,%.1f,%.1f,%.1f] ",
                        it.pageIndex, it.sidecar, it.objectNumber, it.sidecarId,
                        it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom));
            }
            android.util.Log.d("OpenDroidPDF/Debug",
                    "text-multi-apply-post " + action + " page=" + pv.pageNumber() + " items=" + sb);
        }
        host.invalidateQuickActions();
        return ok;
    }

    @Nullable
	    private Selection resolveSelection(@NonNull MuPDFPageView pv) {
	        try {
	            Annotation embedded = pv.textAnnotationDelegate().selectedEmbeddedAnnotationOrNull();
	            if (embedded != null
	                    && (embedded.type == Annotation.Type.FREETEXT || embedded.type == Annotation.Type.TEXT)
	                    && embedded.objectNumber > 0L) {
	                if (pv.textAnnotationDelegate().isFreeTextPositionLocked(embedded.objectNumber)) {
	                    host.showInfo(host.activity().getString(R.string.text_multi_select_locked));
	                    return null;
	                }
	                if (embedded.width() <= 0 || embedded.height() <= 0) return null;
	                return new Selection(false, embedded.objectNumber, null, new RectF(embedded));
            }
        } catch (Throwable ignore) {
        }

        try {
	            SidecarSelectionController.Selection sel = pv.selectedSidecarSelectionOrNull();
	            if (sel != null && sel.kind == SidecarSelectionController.Kind.NOTE
	                    && sel.id != null && sel.bounds != null) {
	                SidecarNote note = pv.textAnnotationDelegate().sidecarNoteById(sel.id);
	                if (note != null && note.lockPositionSize) {
	                    host.showInfo(host.activity().getString(R.string.text_multi_select_locked));
	                    return null;
	                }
	                return new Selection(true, 0L, sel.id, new RectF(sel.bounds));
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private boolean alignLeft(@NonNull MuPDFPageView pv, @NonNull List<Item> items) {
        float target = minLeft(items);
        return applyDelta(pv, items, (b) -> target - b.left, (b) -> 0f);
    }

    private boolean alignRight(@NonNull MuPDFPageView pv, @NonNull List<Item> items) {
        float target = maxRight(items);
        return applyDelta(pv, items, (b) -> target - b.right, (b) -> 0f);
    }

    private boolean alignTop(@NonNull MuPDFPageView pv, @NonNull List<Item> items) {
        float target = minTop(items);
        return applyDelta(pv, items, (b) -> 0f, (b) -> target - b.top);
    }

    private boolean alignBottom(@NonNull MuPDFPageView pv, @NonNull List<Item> items) {
        float target = maxBottom(items);
        return applyDelta(pv, items, (b) -> 0f, (b) -> target - b.bottom);
    }

    private boolean alignCenterX(@NonNull MuPDFPageView pv, @NonNull List<Item> items) {
        float target = (minLeft(items) + maxRight(items)) / 2f;
        return applyDelta(pv, items, (b) -> target - b.centerX(), (b) -> 0f);
    }

    private boolean alignCenterY(@NonNull MuPDFPageView pv, @NonNull List<Item> items) {
        float target = (minTop(items) + maxBottom(items)) / 2f;
        return applyDelta(pv, items, (b) -> 0f, (b) -> target - b.centerY());
    }

    private boolean distributeHorizontal(@NonNull MuPDFPageView pv, @NonNull List<Item> list) {
        List<Item> items = new ArrayList<>(list);
        Collections.sort(items, Comparator.comparingDouble(it -> it.bounds.left));
        float minLeft = minLeft(items);
        float maxRight = maxRight(items);
        float totalWidth = 0f;
        for (Item it : items) totalWidth += Math.max(0f, it.bounds.width());
        final float MIN_SPACE = 24f;
        float rawSpace = (maxRight - minLeft - totalWidth) / (items.size() - 1);
        float space = rawSpace < MIN_SPACE ? MIN_SPACE : rawSpace;

        final ArrayList<RectF> targets = new ArrayList<>(items.size());
        float cursor = minLeft;
        for (Item it : items) {
            RectF next = new RectF(it.bounds);
            float dx = cursor - next.left;
            next.offset(dx, 0f);
            targets.add(next);
            cursor += next.width() + space;
        }
        return applyBounds(pv, items, targets);
    }

    private boolean distributeVertical(@NonNull MuPDFPageView pv, @NonNull List<Item> list) {
        List<Item> items = new ArrayList<>(list);
        Collections.sort(items, Comparator.comparingDouble(it -> it.bounds.top));
        float minTop = minTop(items);
        float maxBottom = maxBottom(items);
        float totalHeight = 0f;
        for (Item it : items) totalHeight += Math.max(0f, it.bounds.height());
        float space = Math.max(0f, (maxBottom - minTop - totalHeight) / (items.size() - 1));

        final ArrayList<RectF> targets = new ArrayList<>(items.size());
        float cursor = minTop;
        for (Item it : items) {
            RectF next = new RectF(it.bounds);
            float dy = cursor - next.top;
            next.offset(0f, dy);
            targets.add(next);
            cursor += next.height() + space;
        }
        return applyBounds(pv, items, targets);
    }

    private boolean applyDelta(@NonNull MuPDFPageView pv,
                               @NonNull List<Item> items,
                               @NonNull Delta dx,
                               @NonNull Delta dy) {
        ArrayList<RectF> targets = new ArrayList<>(items.size());
        for (Item it : items) {
            RectF next = new RectF(it.bounds);
            next.offset(dx.apply(next), dy.apply(next));
            targets.add(next);
        }
        return applyBounds(pv, items, targets);
    }

    private boolean applyBounds(@NonNull MuPDFPageView pv,
                                @NonNull List<Item> items,
                                @NonNull List<RectF> targets) {
        if (items.size() != targets.size()) return false;

        boolean allOk = true;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            RectF target = targets.get(i);
            boolean ok = applySingle(pv, it, target, true);
            allOk = allOk && ok;
            if (ok) it.bounds = new RectF(target);
        }
        return allOk;
    }

    private boolean applySingle(@NonNull MuPDFPageView pv,
                                @NonNull Item it,
                                @NonNull RectF target,
                                boolean markUserResized) {
        if (target == null || target.width() <= 0f || target.height() <= 0f) return false;

        if (!it.sidecar) {
            if (pv.textAnnotationDelegate().isFreeTextPositionLocked(it.objectNumber)) {
                host.showInfo(host.activity().getString(R.string.text_multi_select_locked));
                return false;
            }
            return pv.textAnnotationDelegate().commitTextAnnotationRectByObjectNumber(it.objectNumber, target, markUserResized);
        }

        if (it.sidecarId == null) return false;
        SidecarNote note = pv.textAnnotationDelegate().sidecarNoteById(it.sidecarId);
        if (note != null && note.lockPositionSize) {
            host.showInfo(host.activity().getString(R.string.text_multi_select_locked));
            return false;
        }
        return pv.textAnnotationDelegate().commitSidecarNoteBounds(it.sidecarId, target, markUserResized);
    }

    private static float minLeft(List<Item> items) {
        float min = Float.MAX_VALUE;
        for (Item it : items) min = Math.min(min, it.bounds.left);
        return min == Float.MAX_VALUE ? 0f : min;
    }

    private static float maxRight(List<Item> items) {
        float max = -Float.MAX_VALUE;
        for (Item it : items) max = Math.max(max, it.bounds.right);
        return max == -Float.MAX_VALUE ? 0f : max;
    }

    private static float minTop(List<Item> items) {
        float min = Float.MAX_VALUE;
        for (Item it : items) min = Math.min(min, it.bounds.top);
        return min == Float.MAX_VALUE ? 0f : min;
    }

    private static float maxBottom(List<Item> items) {
        float max = -Float.MAX_VALUE;
        for (Item it : items) max = Math.max(max, it.bounds.bottom);
        return max == -Float.MAX_VALUE ? 0f : max;
    }

    @Nullable
    private static Action actionForIndex(int index) {
        switch (index) {
            case 0: return Action.ALIGN_LEFT;
            case 1: return Action.ALIGN_CENTER_HORIZONTAL;
            case 2: return Action.ALIGN_RIGHT;
            case 3: return Action.ALIGN_TOP;
            case 4: return Action.ALIGN_CENTER_VERTICAL;
            case 5: return Action.ALIGN_BOTTOM;
            case 6: return Action.DISTRIBUTE_HORIZONTAL;
            case 7: return Action.DISTRIBUTE_VERTICAL;
            case 8: return Action.CLEAR;
            default: return null;
        }
    }

    private static boolean safeEquals(@Nullable String a, @Nullable String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private static boolean matches(@NonNull Item it, long objectNumber, @Nullable String sidecarId) {
        if (it.sidecar) {
            return it.sidecarId != null && it.sidecarId.equals(sidecarId);
        }
        return objectNumber > 0 && it.objectNumber == objectNumber;
    }

    private static final class Selection {
        final boolean sidecar;
        final long objectNumber;
        @Nullable final String sidecarId;
        @NonNull final RectF bounds;

        Selection(boolean sidecar, long objectNumber, @Nullable String sidecarId, @NonNull RectF bounds) {
            this.sidecar = sidecar;
            this.objectNumber = objectNumber;
            this.sidecarId = sidecarId;
            this.bounds = bounds;
        }
    }

    private interface Delta { float apply(@NonNull RectF b); }
}
