package org.opendroidpdf.app.dashboard;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import org.opendroidpdf.app.DashboardFragment;

/**
 * Owns dashboard fragment orchestration so the Activity only delegates state changes.
 */
public class DashboardController {
    private static final String TAG_FRAGMENT_DASHBOARD = "org.opendroidpdf.app.DashboardFragment";

    private final FragmentManager fragmentManager;
    private final int containerId;

    public DashboardController(FragmentManager fragmentManager, int containerId) {
        this.fragmentManager = fragmentManager;
        this.containerId = containerId;
    }

    public boolean isDashboardShown() {
        androidx.fragment.app.Fragment current = fragmentManager.findFragmentById(containerId);
        return current instanceof DashboardFragment;
    }

    public void showDashboard() {
        DashboardFragment fragment = getDashboardFragment();
        if (fragment == null || !fragment.isAdded()) {
            fragment = new DashboardFragment();
            FragmentTransaction transaction = fragmentManager
                    .beginTransaction()
                    .replace(containerId, fragment, TAG_FRAGMENT_DASHBOARD);
            commitTransaction(transaction);
        }
        fragment.renderDashboard();
    }

    public void hideDashboard() {
        DashboardFragment fragment = getDashboardFragment();
        if (fragment != null) {
            fragment.clearDashboard();
        }
    }

    private DashboardFragment getDashboardFragment() {
        androidx.fragment.app.Fragment fragment = fragmentManager.findFragmentById(containerId);
        if (fragment instanceof DashboardFragment) {
            return (DashboardFragment) fragment;
        }
        fragment = fragmentManager.findFragmentByTag(TAG_FRAGMENT_DASHBOARD);
        if (fragment instanceof DashboardFragment) {
            return (DashboardFragment) fragment;
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
