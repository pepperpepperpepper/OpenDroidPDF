package org.opendroidpdf.app.toolbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.opendroidpdf.R;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.document.DocumentToolbarController;
import org.opendroidpdf.app.search.SearchToolbarController;
import org.opendroidpdf.app.ui.ActionBarMode;
import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.app.debug.DebugActionsController;

/**
 * Centralizes menu inflation/prep so {@link org.opendroidpdf.OpenDroidPDFActivity}
 * stays slimmer.
 */
public final class ToolbarMenuDelegate {
    private ToolbarMenuDelegate() {}

    public static boolean onCreateOptionsMenu(@NonNull AppCompatActivity activity,
                                              @NonNull ActionBarMode mode,
                                              ToolbarStateController stateController,
                                              DocumentToolbarController documentToolbarController,
                                              AnnotationToolbarController annotationToolbarController,
                                              SearchToolbarController searchToolbarController,
                                              Menu menu) {
        final MenuInflater inflater = activity.getMenuInflater();
        if (stateController != null) {
            // Reset search text when entering Search mode, mirroring legacy behavior
            if (mode == ActionBarMode.Search) {
                // handled inside controller via state cache if needed
            }
            stateController.onCreateOptionsMenu(
                    mode,
                    menu,
                    inflater,
                    documentToolbarController,
                    annotationToolbarController,
                    searchToolbarController);
        } else {
            inflater.inflate(R.menu.empty_menu, menu);
        }
        return true;
    }

    public static boolean onPrepareOptionsMenu(ToolbarStateController stateController,
                                               Menu menu) {
        if (stateController != null) {
            stateController.onPrepareOptionsMenu(menu);
        }
        return true;
    }

    public static boolean onOptionsItemSelected(@NonNull org.opendroidpdf.OpenDroidPDFActivity host,
                                                @NonNull MenuItem item,
                                                DocumentToolbarController documentToolbarController,
                                                AnnotationToolbarController annotationToolbarController,
                                                SearchToolbarController searchToolbarController) {
        if (BuildConfig.DEBUG && DebugActionsController.onOptionsItemSelected(host, item)) {
            return true;
        }
        if (documentToolbarController != null && documentToolbarController.handleMenuItem(item)) return true;
        if (annotationToolbarController != null && annotationToolbarController.handleOptionsItem(item)) return true;
        if (searchToolbarController != null && searchToolbarController.handleMenuItem(item)) return true;
        return false;
    }
}
