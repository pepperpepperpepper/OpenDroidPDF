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
        applyScrubberWindowInsets(root);
        Log.i(TAG, "onCreateView(): container=" + container + " documentContainer=" + documentContainer);
        return root;
    }

    private static void applyScrubberWindowInsets(@NonNull View root) {
        final View scrubber = root.findViewById(R.id.page_scrubber_container);
        if (scrubber == null) return;
        final ViewGroup.LayoutParams lp = scrubber.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) return;
        final ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
        final int baseBottomMarginPx = mlp.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(scrubber, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.LayoutParams cur = v.getLayoutParams();
            if (cur instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) cur;
                int want = baseBottomMarginPx + Math.max(0, bars.bottom);
                if (p.bottomMargin != want) {
                    p.bottomMargin = want;
                    v.setLayoutParams(p);
                }
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(scrubber);
    }

    @Nullable
    public FrameLayout getDocumentContainer() {
        return documentContainer;
    }
}
