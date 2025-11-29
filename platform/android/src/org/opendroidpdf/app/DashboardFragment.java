package org.opendroidpdf.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.opendroidpdf.R;

/**
 * Hosts the entry/dashboard UI so it can be managed independently of the document view.
 * Logic will migrate here during Phase 2; for now we only expose references to the existing
 * views so the legacy Activity code can continue to populate the layout.
 */
public class DashboardFragment extends Fragment {
    private ScrollView scrollView;
    private LinearLayout entryLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_dashboard, container, false);
        scrollView = root.findViewById(R.id.entry_screen_scroll_view);
        entryLayout = root.findViewById(R.id.entry_screen_layout);
        return root;
    }

    @Nullable
    public ScrollView getScrollView() {
        return scrollView;
    }

    @Nullable
    public LinearLayout getEntryLayout() {
        return entryLayout;
    }
}
