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
        public enum Action {
            /**
             * Offer to install (or re-install) the Office Pack companion APK.
             *
             * <p>This is used when conversion is unavailable due to missing or mismatched Office Pack.</p>
             */
            INSTALL_OFFICE_PACK
        }

        @Nullable private final Uri pdfUri;
        @Nullable private final String userMessage;
        @Nullable private final Action action;

        private Result(@Nullable Uri pdfUri, @Nullable String userMessage, @Nullable Action action) {
            this.pdfUri = pdfUri;
            this.userMessage = userMessage;
            this.action = action;
        }

        @NonNull
        public static Result success(@NonNull Uri pdfUri) {
            return new Result(pdfUri, null, null);
        }

        @NonNull
        public static Result unavailable(@NonNull String userMessage) {
            return new Result(null, userMessage, null);
        }

        @NonNull
        public static Result unavailable(@NonNull String userMessage, @Nullable Action action) {
            return new Result(null, userMessage, action);
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

        @Nullable
        public Action actionOrNull() {
            return action;
        }
    }

    @NonNull
    Result importToPdf(@NonNull Context context, @NonNull Uri wordUri);
}
