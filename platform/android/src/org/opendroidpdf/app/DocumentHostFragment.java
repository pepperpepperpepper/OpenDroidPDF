package org.opendroidpdf.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
        Log.i(TAG, "onCreateView(): container=" + container + " documentContainer=" + documentContainer);
        return root;
    }

    @Nullable
    public FrameLayout getDocumentContainer() {
        return documentContainer;
    }
}
