package org.opendroidpdf.app.document;

import android.content.Intent;
import android.net.Uri;

/**
 * Centralizes intents and constants for installing/updating the XFA Pack companion app.
 */
public final class XfaPackInstallIntents {
    private XfaPackInstallIntents() {}

    public static final String XFA_PACK_PACKAGE = XfaPackConversionPipeline.XFA_PACK_PACKAGE;

    // Canonical self-hosted repo URL (see DEPLOYMENT-FDROID.md).
    public static final String FDROID_REPO_URL = OfficePackInstallIntents.FDROID_REPO_URL;

    /** Best-effort deep link into the F-Droid client to show the package details page. */
    public static Intent newOpenXfaPackInFdroidIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("fdroid://package/" + XFA_PACK_PACKAGE));
    }

    /** Opens the repo URL in a browser so the user can add/update the repository in F-Droid. */
    public static Intent newOpenRepoUrlIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(FDROID_REPO_URL));
    }
}

