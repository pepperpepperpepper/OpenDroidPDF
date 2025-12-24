package org.opendroidpdf.app.document;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Builds viewer intents without hard-coding the activity class. This keeps call
 * sites decoupled from the concrete activity type and relies on intent filters.
 */
public final class DocumentViewerIntents {
    private DocumentViewerIntents() {}

    @NonNull
    public static Intent viewInApp(@NonNull Context context, @NonNull Uri uri) {
        return viewInApp(context, uri, /*title*/ null);
    }

    @NonNull
    public static Intent viewInApp(@NonNull Context context,
                                   @NonNull Uri uri,
                                   @Nullable String title) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String type = resolveMimeType(context, uri);
        if (type != null) {
            intent.setDataAndType(uri, type);
        } else {
            intent.setData(uri);
        }
        intent.setPackage(context.getPackageName());
        if (title != null) intent.putExtra(Intent.EXTRA_TITLE, title);
        return intent;
    }

    @Nullable
    private static String resolveMimeType(@NonNull Context context, @NonNull Uri uri) {
        try {
            String fromProvider = context.getContentResolver().getType(uri);
            if (fromProvider != null && !fromProvider.trim().isEmpty()) return fromProvider;
        } catch (Throwable ignore) {}

        String s = uri.toString();
        if (s == null) return null;
        String lower = s.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".epub")) return "application/epub+zip";
        return "*/*";
    }
}
