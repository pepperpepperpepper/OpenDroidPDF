package org.opendroidpdf.app.shell;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.opendroidpdf.R;
import org.opendroidpdf.app.DashboardFragment;

/**
 * Acrobat-style app shell: bottom navigation + global "+" create/import menu.
 */
public final class AppShellFragment extends Fragment {
    private static final String KEY_SELECTED_ITEM_ID = "selectedItemId";

    private int selectedItemId = R.id.app_shell_nav_home;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_app_shell, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState != null) {
            selectedItemId = savedInstanceState.getInt(KEY_SELECTED_ITEM_ID, R.id.app_shell_nav_home);
        }

        BottomNavigationView bottomNav = view.findViewById(R.id.app_shell_bottom_nav);
        FloatingActionButton fab = view.findViewById(R.id.app_shell_fab);

        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(item -> {
                selectTab(item.getItemId());
                return true;
            });
            bottomNav.setSelectedItemId(selectedItemId);
        } else {
            // Should never happen, but avoid a blank screen.
            selectTab(R.id.app_shell_nav_home);
        }

        if (fab != null) {
            fab.setOnClickListener(v -> showCreateImportMenu());
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_ITEM_ID, selectedItemId);
    }

    /**
     * Called by the host controller when the dashboard/app-shell becomes visible.
     */
    public void onShown() {
        // Refresh the Home surface so recents update after returning from a document.
        if (selectedItemId == R.id.app_shell_nav_home) {
            Fragment f = getChildFragmentManager().findFragmentById(R.id.app_shell_content_container);
            if (f instanceof DashboardFragment) {
                ((DashboardFragment) f).renderDashboard();
            }
        }
    }

    private void selectTab(int itemId) {
        selectedItemId = itemId;

        Fragment fragment;
        String tag;
        switch (itemId) {
            case R.id.app_shell_nav_files:
                tag = "files";
                fragment = getChildFragmentManager().findFragmentByTag(tag);
                if (fragment == null) fragment = new FilesTabFragment();
                break;
            case R.id.app_shell_nav_shared:
                tag = "shared";
                fragment = getChildFragmentManager().findFragmentByTag(tag);
                if (fragment == null) {
                    fragment = PlaceholderTabFragment.newInstance(
                            getString(R.string.app_shell_tab_shared),
                            getString(R.string.app_shell_shared_placeholder));
                }
                break;
            case R.id.app_shell_nav_search:
                tag = "search";
                fragment = getChildFragmentManager().findFragmentByTag(tag);
                if (fragment == null) {
                    fragment = PlaceholderTabFragment.newInstance(
                            getString(R.string.app_shell_tab_search),
                            getString(R.string.app_shell_search_placeholder));
                }
                break;
            case R.id.app_shell_nav_home:
            default:
                DashboardFragment existing =
                        (DashboardFragment) getChildFragmentManager().findFragmentByTag("home");
                fragment = existing != null ? existing : new DashboardFragment();
                tag = "home";
                break;
        }

        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.app_shell_content_container, fragment, tag)
                .commitNowAllowingStateLoss();

        if (fragment instanceof DashboardFragment) {
            ((DashboardFragment) fragment).renderDashboard();
        }
    }

    private void showCreateImportMenu() {
        if (getContext() == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View content = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_app_shell_fab_menu, null, false);

        View open = content.findViewById(R.id.app_shell_fab_action_open);
        View create = content.findViewById(R.id.app_shell_fab_action_new);

        if (open != null) {
            open.setOnClickListener(v -> {
                dialog.dismiss();
                DashboardFragment.DashboardHost host = dashboardHostOrNull();
                if (host != null) host.onOpenDocumentRequested();
            });
        }
        if (create != null) {
            create.setOnClickListener(v -> {
                dialog.dismiss();
                DashboardFragment.DashboardHost host = dashboardHostOrNull();
                if (host != null) host.onCreateNewDocumentRequested();
            });
        }

        dialog.setContentView(content);
        dialog.show();
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
