package org.opendroidpdf.app.annotation;

import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

final class TextAnnotationMultiSelectGeometry {
    private interface Delta { float apply(@NonNull RectF b); }

    private TextAnnotationMultiSelectGeometry() {
    }

    static ArrayList<RectF> alignLeftTargets(@NonNull List<TextAnnotationMultiSelectController.Item> items) {
        float target = minLeft(items);
        return applyDeltaTargets(items, (b) -> target - b.left, (b) -> 0f);
    }

    static ArrayList<RectF> alignRightTargets(@NonNull List<TextAnnotationMultiSelectController.Item> items) {
        float target = maxRight(items);
        return applyDeltaTargets(items, (b) -> target - b.right, (b) -> 0f);
    }

    static ArrayList<RectF> alignTopTargets(@NonNull List<TextAnnotationMultiSelectController.Item> items) {
        float target = minTop(items);
        return applyDeltaTargets(items, (b) -> 0f, (b) -> target - b.top);
    }

    static ArrayList<RectF> alignBottomTargets(@NonNull List<TextAnnotationMultiSelectController.Item> items) {
        float target = maxBottom(items);
        return applyDeltaTargets(items, (b) -> 0f, (b) -> target - b.bottom);
    }

    static ArrayList<RectF> alignCenterXTargets(@NonNull List<TextAnnotationMultiSelectController.Item> items) {
        float target = (minLeft(items) + maxRight(items)) / 2f;
        return applyDeltaTargets(items, (b) -> target - b.centerX(), (b) -> 0f);
    }

    static ArrayList<RectF> alignCenterYTargets(@NonNull List<TextAnnotationMultiSelectController.Item> items) {
        float target = (minTop(items) + maxBottom(items)) / 2f;
        return applyDeltaTargets(items, (b) -> 0f, (b) -> target - b.centerY());
    }

    static ArrayList<RectF> distributeHorizontalTargets(@NonNull List<TextAnnotationMultiSelectController.Item> itemsSortedByLeft) {
        float minLeft = minLeft(itemsSortedByLeft);
        float maxRight = maxRight(itemsSortedByLeft);
        float totalWidth = 0f;
        for (TextAnnotationMultiSelectController.Item it : itemsSortedByLeft) totalWidth += Math.max(0f, it.bounds.width());
        // Distribute within the existing selection bounds. When items overlap or the span is too
        // small for positive spacing, allow a negative "space" so items remain within the
        // original bounding box instead of failing to apply.
        float space = (maxRight - minLeft - totalWidth) / (itemsSortedByLeft.size() - 1);

        final ArrayList<RectF> targets = new ArrayList<>(itemsSortedByLeft.size());
        float cursor = minLeft;
        for (TextAnnotationMultiSelectController.Item it : itemsSortedByLeft) {
            RectF next = new RectF(it.bounds);
            float dx = cursor - next.left;
            next.offset(dx, 0f);
            targets.add(next);
            cursor += next.width() + space;
        }
        return targets;
    }

    static ArrayList<RectF> distributeVerticalTargets(@NonNull List<TextAnnotationMultiSelectController.Item> itemsSortedByTop) {
        float minTop = minTop(itemsSortedByTop);
        float maxBottom = maxBottom(itemsSortedByTop);
        float totalHeight = 0f;
        for (TextAnnotationMultiSelectController.Item it : itemsSortedByTop) totalHeight += Math.max(0f, it.bounds.height());
        // Mirror horizontal behavior: keep the distribution within the selection bounds even if
        // items overlap by allowing negative spacing.
        float space = (maxBottom - minTop - totalHeight) / (itemsSortedByTop.size() - 1);

        final ArrayList<RectF> targets = new ArrayList<>(itemsSortedByTop.size());
        float cursor = minTop;
        for (TextAnnotationMultiSelectController.Item it : itemsSortedByTop) {
            RectF next = new RectF(it.bounds);
            float dy = cursor - next.top;
            next.offset(0f, dy);
            targets.add(next);
            cursor += next.height() + space;
        }
        return targets;
    }

    private static ArrayList<RectF> applyDeltaTargets(@NonNull List<TextAnnotationMultiSelectController.Item> items,
                                                      @NonNull Delta dx,
                                                      @NonNull Delta dy) {
        ArrayList<RectF> targets = new ArrayList<>(items.size());
        for (TextAnnotationMultiSelectController.Item it : items) {
            RectF next = new RectF(it.bounds);
            next.offset(dx.apply(next), dy.apply(next));
            targets.add(next);
        }
        return targets;
    }

    private static float minLeft(@NonNull List<TextAnnotationMultiSelectController.Item> items) {
        float min = Float.MAX_VALUE;
        for (TextAnnotationMultiSelectController.Item it : items) min = Math.min(min, it.bounds.left);
        return min == Float.MAX_VALUE ? 0f : min;
    }

    private static float maxRight(@NonNull List<TextAnnotationMultiSelectController.Item> items) {
        float max = -Float.MAX_VALUE;
        for (TextAnnotationMultiSelectController.Item it : items) max = Math.max(max, it.bounds.right);
        return max == -Float.MAX_VALUE ? 0f : max;
    }

    private static float minTop(@NonNull List<TextAnnotationMultiSelectController.Item> items) {
        float min = Float.MAX_VALUE;
        for (TextAnnotationMultiSelectController.Item it : items) min = Math.min(min, it.bounds.top);
        return min == Float.MAX_VALUE ? 0f : min;
    }

    private static float maxBottom(@NonNull List<TextAnnotationMultiSelectController.Item> items) {
        float max = -Float.MAX_VALUE;
        for (TextAnnotationMultiSelectController.Item it : items) max = Math.max(max, it.bounds.bottom);
        return max == -Float.MAX_VALUE ? 0f : max;
    }
}
