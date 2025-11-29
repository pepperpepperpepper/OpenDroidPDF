package org.opendroidpdf.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.opendroidpdf.R;

/**
 * Container fragment for the document reader stack (MuPDFReaderView, overlays, etc.).
 * Phase 2 will gradually migrate the corresponding logic out of OpenDroidPDFActivity.
 */
public class DocumentHostFragment extends Fragment {
    private FrameLayout documentContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_document_host, container, false);
        documentContainer = root.findViewById(R.id.document_host_container);
        return root;
    }

    @Nullable
    public FrameLayout getDocumentContainer() {
        return documentContainer;
    }
}
