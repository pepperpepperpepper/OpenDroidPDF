package org.opendroidpdf.app.document;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.opendroidpdf.R;

/**
 * Placeholder Word import pipeline.
 *
 * <p>W0 requires that selecting a Word document never crashes and produces a clear message.
 * Real conversion is implemented in later slices (W1/W2).</p>
 */
public final class NoOpWordImportPipeline implements WordImportPipeline {
    @NonNull
    @Override
    public Result importToPdf(@NonNull Context context, @NonNull Uri wordUri) {
        return Result.unavailable(context.getString(R.string.word_import_unavailable));
    }
}

