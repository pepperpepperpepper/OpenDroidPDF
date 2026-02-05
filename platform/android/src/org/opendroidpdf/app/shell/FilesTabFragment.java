package org.opendroidpdf.app.shell;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.opendroidpdf.R;
import org.opendroidpdf.app.DashboardFragment;

/**
 * Minimal “Files” surface. For now, this focuses on the key Acrobat affordance:
 * a primary open/create entry point (also available via the global "+").
 */
public final class FilesTabFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_app_shell_files, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View open = view.findViewById(R.id.app_shell_files_action_open);
        View create = view.findViewById(R.id.app_shell_files_action_new);

        open.setOnClickListener(v -> {
            DashboardFragment.DashboardHost host = dashboardHostOrNull();
            if (host != null) host.onOpenDocumentRequested();
        });
        create.setOnClickListener(v -> {
            DashboardFragment.DashboardHost host = dashboardHostOrNull();
            if (host != null) host.onCreateNewDocumentRequested();
        });
    }

    @Nullable
    private DashboardFragment.DashboardHost dashboardHostOrNull() {
        try {
            if (getActivity() instanceof DashboardFragment.DashboardHost) {
                return (DashboardFragment.DashboardHost) getActivity();
            }
        } catch (Throwable ignore) {
        }
        return null;
    }
}

