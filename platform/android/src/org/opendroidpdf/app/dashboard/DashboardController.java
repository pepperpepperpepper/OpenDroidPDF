package org.opendroidpdf.app.dashboard;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import org.opendroidpdf.app.shell.AppShellFragment;

/**
 * Owns dashboard fragment orchestration so the Activity only delegates state changes.
 */
public class DashboardController {
    private static final String TAG_FRAGMENT_APP_SHELL = "org.opendroidpdf.app.shell.AppShellFragment";

    private final FragmentManager fragmentManager;
    private final int containerId;

    public DashboardController(FragmentManager fragmentManager, int containerId) {
        this.fragmentManager = fragmentManager;
        this.containerId = containerId;
    }

    public boolean isDashboardShown() {
        androidx.fragment.app.Fragment current = fragmentManager.findFragmentById(containerId);
        return current instanceof AppShellFragment;
    }

    public void showDashboard() {
        AppShellFragment fragment = getAppShellFragment();
        if (fragment == null || !fragment.isAdded()) {
            fragment = new AppShellFragment();
            FragmentTransaction transaction = fragmentManager
                    .beginTransaction()
                    .replace(containerId, fragment, TAG_FRAGMENT_APP_SHELL);
            commitTransaction(transaction);
        }
        fragment.onShown();
    }

    public void hideDashboard() {
        // No-op for now: the document-host swap removes this fragment from view.
    }

    private AppShellFragment getAppShellFragment() {
        androidx.fragment.app.Fragment fragment = fragmentManager.findFragmentById(containerId);
        if (fragment instanceof AppShellFragment) {
            return (AppShellFragment) fragment;
        }
        fragment = fragmentManager.findFragmentByTag(TAG_FRAGMENT_APP_SHELL);
        if (fragment instanceof AppShellFragment) {
            return (AppShellFragment) fragment;
        }
        return null;
    }

    private void commitTransaction(FragmentTransaction transaction) {
        if (fragmentManager.isStateSaved()) {
            transaction.commitAllowingStateLoss();
            fragmentManager.executePendingTransactions();
        } else {
            transaction.commitNow();
        }
    }
}
