package org.opendroidpdf.app.document;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Converts Word documents (.doc/.docx) into a PDF derivative for viewing/annotation.
 *
 * <p>In v1 this is expected to be implemented via an optional companion APK ("Office Pack") on
 * Android, and via a host dependency (e.g., LibreOffice) on Desktop/Linux.</p>
 */
public interface WordImportPipeline {

    final class Result {
        @Nullable private final Uri pdfUri;
        @Nullable private final String userMessage;

        private Result(@Nullable Uri pdfUri, @Nullable String userMessage) {
            this.pdfUri = pdfUri;
            this.userMessage = userMessage;
        }

        @NonNull
        public static Result success(@NonNull Uri pdfUri) {
            return new Result(pdfUri, null);
        }

        @NonNull
        public static Result unavailable(@NonNull String userMessage) {
            return new Result(null, userMessage);
        }

        public boolean isSuccess() {
            return pdfUri != null;
        }

        @Nullable
        public Uri pdfUriOrNull() {
            return pdfUri;
        }

        @Nullable
        public String userMessageOrNull() {
            return userMessage;
        }
    }

    @NonNull
    Result importToPdf(@NonNull Context context, @NonNull Uri wordUri);
}

