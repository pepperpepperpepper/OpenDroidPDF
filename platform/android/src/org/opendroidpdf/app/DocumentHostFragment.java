package org.opendroidpdf.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import org.opendroidpdf.R;

/**
 * Container fragment for the document reader stack (MuPDFReaderView, overlays, etc.).
 * Phase 2 migrates the corresponding logic out of the activity into controllers/adapters.
 */
public class DocumentHostFragment extends Fragment {
    private static final String TAG = "DocumentHostFragment";
    private FrameLayout documentContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_document_host, container, false);
        documentContainer = root.findViewById(R.id.document_host_container);
        applyReaderChromeWindowInsets(root);
        Log.i(TAG, "onCreateView(): container=" + container + " documentContainer=" + documentContainer);
        return root;
    }

    private static void applyReaderChromeWindowInsets(@NonNull View root) {
        final View findBar = root.findViewById(R.id.find_in_document_bar);
        final View scrubber = root.findViewById(R.id.page_scrubber_container);
        final View quickActions = root.findViewById(R.id.reader_quick_actions_bar);
        final View selectionBar = root.findViewById(R.id.reader_selection_actions_bar);
        final View annotBar = root.findViewById(R.id.reader_annot_actions_bar);
        final View addTextBar = root.findViewById(R.id.reader_add_text_actions_bar);

        final int baseScrubberBottomMarginPx;
        if (scrubber != null && scrubber.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            baseScrubberBottomMarginPx = ((ViewGroup.MarginLayoutParams) scrubber.getLayoutParams()).bottomMargin;
        } else {
            baseScrubberBottomMarginPx = 0;
        }

        final int baseQuickActionsPaddingBottomPx = quickActions != null ? quickActions.getPaddingBottom() : 0;
        final int baseSelectionPaddingBottomPx = selectionBar != null ? selectionBar.getPaddingBottom() : 0;
        final int baseAnnotPaddingBottomPx = annotBar != null ? annotBar.getPaddingBottom() : 0;
        final int baseAddTextPaddingBottomPx = addTextBar != null ? addTextBar.getPaddingBottom() : 0;
        final int baseFindBarPaddingTopPx = findBar != null ? findBar.getPaddingTop() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            final int topInset = Math.max(0, bars.top);
            final int bottomInset = Math.max(0, bars.bottom);

            if (findBar != null) {
                int wantTopPadding = baseFindBarPaddingTopPx + topInset;
                if (findBar.getPaddingTop() != wantTopPadding) {
                    findBar.setPadding(
                            findBar.getPaddingLeft(),
                            wantTopPadding,
                            findBar.getPaddingRight(),
                            findBar.getPaddingBottom());
                }
            }

            if (quickActions != null) {
                int wantBottomPadding = baseQuickActionsPaddingBottomPx + bottomInset;
                if (quickActions.getPaddingBottom() != wantBottomPadding) {
                    quickActions.setPadding(
                            quickActions.getPaddingLeft(),
                            quickActions.getPaddingTop(),
                            quickActions.getPaddingRight(),
                            wantBottomPadding);
                }
            }

            if (selectionBar != null) {
                int wantBottomPadding = baseSelectionPaddingBottomPx + bottomInset;
                if (selectionBar.getPaddingBottom() != wantBottomPadding) {
                    selectionBar.setPadding(
                            selectionBar.getPaddingLeft(),
                            selectionBar.getPaddingTop(),
                            selectionBar.getPaddingRight(),
                            wantBottomPadding);
                }
            }

            if (annotBar != null) {
                int wantBottomPadding = baseAnnotPaddingBottomPx + bottomInset;
                if (annotBar.getPaddingBottom() != wantBottomPadding) {
                    annotBar.setPadding(
                            annotBar.getPaddingLeft(),
                            annotBar.getPaddingTop(),
                            annotBar.getPaddingRight(),
                            wantBottomPadding);
                }
            }

            if (addTextBar != null) {
                int wantBottomPadding = baseAddTextPaddingBottomPx + bottomInset;
                if (addTextBar.getPaddingBottom() != wantBottomPadding) {
                    addTextBar.setPadding(
                            addTextBar.getPaddingLeft(),
                            addTextBar.getPaddingTop(),
                            addTextBar.getPaddingRight(),
                            wantBottomPadding);
                }
            }

            if (scrubber != null && scrubber.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) scrubber.getLayoutParams();
                boolean bottomBarVisible =
                        (quickActions != null && quickActions.getVisibility() == View.VISIBLE)
                                || (selectionBar != null && selectionBar.getVisibility() == View.VISIBLE)
                                || (annotBar != null && annotBar.getVisibility() == View.VISIBLE)
                                || (addTextBar != null && addTextBar.getVisibility() == View.VISIBLE);
                int wantBottomMargin = baseScrubberBottomMarginPx + (bottomBarVisible ? 0 : bottomInset);
                if (p.bottomMargin != wantBottomMargin) {
                    p.bottomMargin = wantBottomMargin;
                    scrubber.setLayoutParams(p);
                }
            }

            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Nullable
    public FrameLayout getDocumentContainer() {
        return documentContainer;
    }
}
