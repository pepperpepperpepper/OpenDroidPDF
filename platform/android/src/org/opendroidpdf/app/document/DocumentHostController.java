package org.opendroidpdf.app.document;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import org.opendroidpdf.R;
import org.opendroidpdf.app.DocumentHostFragment;

/**
 * Encapsulates the document-host fragment lifecycle to slim the activity.
 */
public class DocumentHostController {
    private static final String TAG_FRAGMENT_DOCUMENT_HOST = "org.opendroidpdf.app.DocumentHostFragment";

    private final FragmentManager fragmentManager;
    private final int containerId;

    public DocumentHostController(FragmentManager fragmentManager, int containerId) {
        this.fragmentManager = fragmentManager;
        this.containerId = containerId;
    }

    public DocumentHostFragment getFragment() {
        androidx.fragment.app.Fragment fragment = fragmentManager.findFragmentById(containerId);
        if (fragment instanceof DocumentHostFragment) {
            return (DocumentHostFragment) fragment;
        }
        fragment = fragmentManager.findFragmentByTag(TAG_FRAGMENT_DOCUMENT_HOST);
        if (fragment instanceof DocumentHostFragment) {
            return (DocumentHostFragment) fragment;
        }
        return null;
    }

    public DocumentHostFragment ensureFragment() {
        DocumentHostFragment fragment = getFragment();
        if (fragment != null && fragment.isAdded()) {
            return fragment;
        }
        fragment = new DocumentHostFragment();
        FragmentTransaction transaction = fragmentManager
                .beginTransaction()
                .replace(containerId, fragment, TAG_FRAGMENT_DOCUMENT_HOST);
        commit(transaction);
        return fragment;
    }

    private void commit(FragmentTransaction transaction) {
        if (fragmentManager.isStateSaved()) {
            transaction.commitAllowingStateLoss();
            fragmentManager.executePendingTransactions();
        } else {
            transaction.commitNow();
        }
    }
}
