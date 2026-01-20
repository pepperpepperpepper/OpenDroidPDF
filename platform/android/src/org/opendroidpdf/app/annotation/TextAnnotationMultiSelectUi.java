package org.opendroidpdf.app.annotation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.R;

final class TextAnnotationMultiSelectUi {
    private TextAnnotationMultiSelectUi() {
    }

    static void showAlignDistributePicker(@NonNull TextAnnotationMultiSelectController controller,
                                         @NonNull TextAnnotationMultiSelectController.Host host) {
        AppCompatActivity activity = host.activity();
        MuPDFPageView pv = host.currentPageView();
        if (pv == null) {
            host.showInfo(activity.getString(R.string.text_multi_select_need_selection));
            return;
        }
        if (!controller.canApplyOnPage(pv.pageNumber())) {
            host.showInfo(activity.getString(R.string.text_multi_select_need_two));
            return;
        }

        final String[] labels = activity.getResources().getStringArray(R.array.text_multi_select_actions);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.text_multi_select_align_distribute)
                .setItems(labels, (d, which) -> {
                    TextAnnotationMultiSelectController.Action action = actionForIndex(which);
                    if (action != null) controller.apply(action);
                })
                .setNegativeButton(R.string.dismiss, (d, w) -> {})
                .show();
    }

    @Nullable
    private static TextAnnotationMultiSelectController.Action actionForIndex(int index) {
        switch (index) {
            case 0: return TextAnnotationMultiSelectController.Action.ALIGN_LEFT;
            case 1: return TextAnnotationMultiSelectController.Action.ALIGN_CENTER_HORIZONTAL;
            case 2: return TextAnnotationMultiSelectController.Action.ALIGN_RIGHT;
            case 3: return TextAnnotationMultiSelectController.Action.ALIGN_TOP;
            case 4: return TextAnnotationMultiSelectController.Action.ALIGN_CENTER_VERTICAL;
            case 5: return TextAnnotationMultiSelectController.Action.ALIGN_BOTTOM;
            case 6: return TextAnnotationMultiSelectController.Action.DISTRIBUTE_HORIZONTAL;
            case 7: return TextAnnotationMultiSelectController.Action.DISTRIBUTE_VERTICAL;
            case 8: return TextAnnotationMultiSelectController.Action.CLEAR;
            default: return null;
        }
    }
}

