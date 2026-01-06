package org.opendroidpdf.app.reader;

import android.graphics.RectF;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Provides "Next field / Previous field" navigation over AcroForm widget bounds.
 *
 * <p>This is a UI-level navigator: it scrolls the reader viewport to the target widget area
 * (and changes pages when needed). It does not attempt to "activate" widgets to avoid
 * accidental checkbox/radio toggles.</p>
 */
public final class FormFieldNavigator {

    public interface Host {
        int currentPage();
        void setDisplayedViewIndex(int page);
        void doNextScrollWithCenter();
        void setDocRelXScroll(float docRelXScroll);
        void setDocRelYScroll(float docRelYScroll);
        void resetupChildren();
    }

    public interface WidgetProvider {
        int pageCount();
        RectF[] widgetAreas(int pageIndex);
    }

    private static final Comparator<RectF> READING_ORDER = (a, b) -> {
        if (a == b) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        int top = Float.compare(a.top, b.top);
        if (top != 0) return top;
        return Float.compare(a.left, b.left);
    };

    private final Host host;
    private final WidgetProvider widgets;

    private int lastPage = -1;
    @Nullable private RectF lastRect;

    public FormFieldNavigator(Host host, WidgetProvider widgets) {
        if (host == null) throw new IllegalArgumentException("host required");
        if (widgets == null) throw new IllegalArgumentException("widgets required");
        this.host = host;
        this.widgets = widgets;
    }

    public void reset() {
        lastPage = -1;
        lastRect = null;
    }

    public boolean navigate(int direction) {
        int dir = direction >= 0 ? 1 : -1;
        int pages = widgets.pageCount();
        if (pages <= 0) return false;

        int startPage = clamp(lastPage >= 0 ? lastPage : host.currentPage(), 0, pages - 1);
        float startX;
        float startY;
        if (lastRect != null) {
            // Use the rect's reading-order anchor (top/left) so "previous" never re-selects
            // the same field due to centerY being below top.
            startX = lastRect.left;
            startY = lastRect.top;
        } else if (dir > 0) {
            startX = Float.NEGATIVE_INFINITY;
            startY = Float.NEGATIVE_INFINITY;
        } else {
            startX = Float.POSITIVE_INFINITY;
            startY = Float.POSITIVE_INFINITY;
        }

        return navigateFromLocation(startPage, startX, startY, dir);
    }

    public boolean navigateFromLocation(int pageIndex, float docRelX, float docRelY, int direction) {
        int dir = direction >= 0 ? 1 : -1;
        int pages = widgets.pageCount();
        if (pages <= 0) return false;
        int startPage = clamp(pageIndex, 0, pages - 1);

        Field target = findNextField(startPage, docRelX, docRelY, dir, pages);
        if (target == null) return false;

        lastPage = target.page;
        lastRect = new RectF(target.bounds);

        if (target.page != host.currentPage()) {
            host.setDisplayedViewIndex(target.page);
        }
        host.doNextScrollWithCenter();
        host.setDocRelXScroll(target.bounds.centerX());
        host.setDocRelYScroll(target.bounds.centerY());
        host.resetupChildren();
        return true;
    }

    @Nullable
    private Field findNextField(int startPage,
                                float docRelX,
                                float docRelY,
                                int direction,
                                int pageCount) {
        for (int page = startPage; page >= 0 && page < pageCount; page += direction) {
            List<RectF> areas = sortedAreas(widgets.widgetAreas(page));
            if (areas.isEmpty()) continue;

            if (page == startPage) {
                int containing = findContainingIndex(areas, docRelX, docRelY);
                if (containing >= 0) {
                    int nextIndex = containing + direction;
                    if (nextIndex >= 0 && nextIndex < areas.size()) {
                        return new Field(page, areas.get(nextIndex));
                    }
                    // No neighbor on this page; move to the next/previous page.
                    continue;
                }

                RectF candidate = direction > 0
                        ? firstAfter(areas, docRelX, docRelY)
                        : lastBefore(areas, docRelX, docRelY);
                if (candidate != null) {
                    return new Field(page, candidate);
                }
                continue;
            }

            RectF pick = direction > 0 ? areas.get(0) : areas.get(areas.size() - 1);
            return new Field(page, pick);
        }
        return null;
    }

    private static List<RectF> sortedAreas(@Nullable RectF[] areas) {
        if (areas == null || areas.length == 0) return Collections.emptyList();
        ArrayList<RectF> out = new ArrayList<>(areas.length);
        for (RectF r : areas) {
            if (r == null) continue;
            if (r.width() <= 0f || r.height() <= 0f) continue;
            out.add(r);
        }
        if (out.isEmpty()) return Collections.emptyList();
        Collections.sort(out, READING_ORDER);
        return out;
    }

    private static int findContainingIndex(List<RectF> areas, float x, float y) {
        for (int i = 0; i < areas.size(); i++) {
            RectF r = areas.get(i);
            if (r != null && r.contains(x, y)) return i;
        }
        return -1;
    }

    @Nullable
    private static RectF firstAfter(List<RectF> areas, float x, float y) {
        for (RectF r : areas) {
            if (r == null) continue;
            if (isAfter(r, x, y)) return r;
        }
        return null;
    }

    @Nullable
    private static RectF lastBefore(List<RectF> areas, float x, float y) {
        for (int i = areas.size() - 1; i >= 0; i--) {
            RectF r = areas.get(i);
            if (r == null) continue;
            if (isBefore(r, x, y)) return r;
        }
        return null;
    }

    private static boolean isAfter(RectF r, float x, float y) {
        return (r.top > y) || (r.top == y && r.left > x);
    }

    private static boolean isBefore(RectF r, float x, float y) {
        return (r.top < y) || (r.top == y && r.left < x);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static final class Field {
        final int page;
        final RectF bounds;

        Field(int page, RectF bounds) {
            this.page = page;
            this.bounds = bounds;
        }
    }
}
