package org.opendroidpdf.app.document;

import android.content.Intent;
import android.net.Uri;

/**
 * Centralizes intents and constants for installing/updating the Office Pack companion app.
 */
public final class OfficePackInstallIntents {
    private OfficePackInstallIntents() {}

    public static final String OFFICE_PACK_PACKAGE = "org.opendroidpdf.officepack";

    // Canonical self-hosted repo URL (see DEPLOYMENT-FDROID.md).
    public static final String FDROID_REPO_URL = "https://fdroid.uh-oh.wtf/repo";

    /**
     * Best-effort deep link into the F-Droid client to show the package details page.
     *
     * <p>If the device doesn't have F-Droid (or doesn't support the scheme), callers should fall
     * back to {@link #newOpenRepoUrlIntent()}.</p>
     */
    public static Intent newOpenOfficePackInFdroidIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("fdroid://package/" + OFFICE_PACK_PACKAGE));
    }

    /** Opens the repo URL in a browser so the user can add/update the repository in F-Droid. */
    public static Intent newOpenRepoUrlIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(FDROID_REPO_URL));
    }
}

