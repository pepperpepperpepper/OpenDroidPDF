package org.opendroidpdf.app.helpers;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * Normalizes inbound intents into shapes the rest of the app already understands.
 *
 * Today, most of the app expects documents to arrive as ACTION_VIEW with intent.getData() set.
 * Many external apps use ACTION_SEND / ACTION_SEND_MULTIPLE with EXTRA_STREAM instead, so we
 * convert those into an equivalent ACTION_VIEW intent.
 */
public final class IncomingIntentNormalizer {
    private IncomingIntentNormalizer() {}

    @Nullable
    public static Intent normalize(@Nullable Intent intent) {
        if (intent == null) return null;

        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = firstStreamUri(intent);
            if (uri != null) return asViewIntent(intent, uri);
            return intent;
        }
        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            Uri uri = firstMultipleStreamUri(intent);
            if (uri != null) return asViewIntent(intent, uri);
            return intent;
        }
        return intent;
    }

    @Nullable
    private static Uri firstStreamUri(Intent intent) {
        try {
            Object extra = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (extra instanceof Uri) return (Uri) extra;
        } catch (Throwable ignore) {}

        try {
            ClipData clip = intent.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                Uri uri = clip.getItemAt(0).getUri();
                if (uri != null) return uri;
            }
        } catch (Throwable ignore) {}

        return null;
    }

    @Nullable
    private static Uri firstMultipleStreamUri(Intent intent) {
        try {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null) {
                for (Uri uri : uris) {
                    if (uri != null) return uri;
                }
            }
        } catch (Throwable ignore) {}

        try {
            ClipData clip = intent.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                Uri uri = clip.getItemAt(0).getUri();
                if (uri != null) return uri;
            }
        } catch (Throwable ignore) {}

        return null;
    }

    private static Intent asViewIntent(Intent original, Uri uri) {
        Intent out = new Intent(Intent.ACTION_VIEW);

        String type = null;
        try { type = original.getType(); } catch (Throwable ignore) {}
        if (type != null) {
            out.setDataAndType(uri, type);
        } else {
            out.setData(uri);
        }

        // Preserve any URI permission grants from the original intent.
        try { out.addFlags(original.getFlags()); } catch (Throwable ignore) {}

        // Keep a breadcrumb for debugging.
        try { out.putExtra("org.opendroidpdf.EXTRA_ORIGINAL_ACTION", original.getAction()); } catch (Throwable ignore) {}

        return out;
    }
}

