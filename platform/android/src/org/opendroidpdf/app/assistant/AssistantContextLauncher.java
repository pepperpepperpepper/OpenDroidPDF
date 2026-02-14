package org.opendroidpdf.app.assistant;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;
import org.opendroidpdf.core.MuPdfRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AssistantContextLauncher {
    private static final int MAX_CONTEXT_CHARS_WITHOUT_PROMPT = 25_000;
    private static final int MAX_CONTEXT_CHARS_TRUNCATED = 25_000;
    private static final int MAX_DOCUMENT_CONTEXT_CHARS = 200_000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private AssistantContextLauncher() {}

    public static void launch(@NonNull OpenDroidPDFActivity activity) {
        if (activity == null) return;

        MuPdfRepository repo = activity.getRepository();
        if (repo == null || activity.getDocView() == null) {
            try { activity.showInfo("Open a document first."); } catch (Throwable ignore) {}
            activity.startActivity(new android.content.Intent(activity, AssistantActivity.class));
            return;
        }

        final int pageIndex = safeSelectedPageIndex(activity);

        String selection = AssistantContextTextExtractor.selectionTextOrNull(activity.getDocView(), repo, activity.getSelectedPageView());
        if (selection != null && !selection.trim().isEmpty()) {
            try {
                org.opendroidpdf.app.selection.DocumentTextSelection sel = activity.getDocView() != null
                        ? activity.getDocView().getDocumentTextSelectionOrNull()
                        : null;
                if (sel != null) {
                    String header = sel.startPage == sel.endPage
                            ? "Page " + (sel.startPage + 1) + ":\n"
                            : "Pages " + (sel.startPage + 1) + "-" + (sel.endPage + 1) + ":\n";
                    selection = header + selection;
                }
            } catch (Throwable ignore) {
            }
            maybeLaunchWithLargeContextPrompt(
                    activity,
                    new AssistantContextSnapshot(AssistantContextSnapshot.Kind.SELECTION,
                            activity.currentDocumentNameOrAppName(),
                            pageIndex,
                            selection,
                            false)
            );
            return;
        }

        // No selection: ask what scope to load.
        AlertDialog.Builder builder = activity.getAlertBuilder();
        if (builder == null) builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.assistant_context_picker_title)
                .setMessage(R.string.assistant_context_picker_message)
                .setItems(new CharSequence[]{
                        activity.getString(R.string.assistant_context_option_whole_document),
                        activity.getString(R.string.assistant_context_option_current_page),
                        activity.getString(R.string.assistant_context_option_no_context)
                }, (dialog, which) -> {
                    if (which == 0) {
                        loadWholeDocumentAndLaunch(activity, repo, pageIndex);
                    } else if (which == 1) {
                        AssistantContextTextExtractor.TextResult page = AssistantContextTextExtractor.pageText(
                                repo,
                                pageIndex,
                                MAX_DOCUMENT_CONTEXT_CHARS
                        );
                        maybeLaunchWithLargeContextPrompt(
                                activity,
                                new AssistantContextSnapshot(
                                        AssistantContextSnapshot.Kind.PAGE,
                                        activity.currentDocumentNameOrAppName(),
                                        pageIndex,
                                        page.text,
                                        page.truncated
                                )
                        );
                    } else {
                        AssistantContextStore.set(null);
                        activity.startActivity(new android.content.Intent(activity, AssistantActivity.class));
                    }
                })
                .show();
    }

    private static void maybeLaunchWithLargeContextPrompt(@NonNull OpenDroidPDFActivity activity,
                                                         @NonNull AssistantContextSnapshot snapshot) {
        String text = snapshot.text();
        if (text == null) {
            AssistantContextStore.set(snapshot);
            activity.startActivity(new android.content.Intent(activity, AssistantActivity.class));
            return;
        }

        if (text.length() <= MAX_CONTEXT_CHARS_WITHOUT_PROMPT) {
            AssistantContextStore.set(snapshot);
            activity.startActivity(new android.content.Intent(activity, AssistantActivity.class));
            return;
        }

        AlertDialog.Builder builder = activity.getAlertBuilder();
        if (builder == null) builder = new AlertDialog.Builder(activity);

        String message = "Loaded " + text.length() + " characters.\n\n"
                + "Large contexts can be slow/expensive when using a remote model.";
        builder.setTitle(R.string.assistant_context_too_large_title)
                .setMessage(message)
                .setPositiveButton(R.string.assistant_context_use_anyway, (d, w) -> {
                    AssistantContextStore.set(snapshot);
                    activity.startActivity(new android.content.Intent(activity, AssistantActivity.class));
                })
                .setNeutralButton(activity.getString(R.string.assistant_context_use_truncated, MAX_CONTEXT_CHARS_TRUNCATED), (d, w) -> {
                    String truncated = text.substring(0, Math.min(MAX_CONTEXT_CHARS_TRUNCATED, text.length()));
                    AssistantContextStore.set(new AssistantContextSnapshot(
                            snapshot.kind(),
                            snapshot.documentTitle(),
                            snapshot.pageIndex(),
                            truncated,
                            true
                    ));
                    activity.startActivity(new android.content.Intent(activity, AssistantActivity.class));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void loadWholeDocumentAndLaunch(@NonNull OpenDroidPDFActivity activity,
                                                  @NonNull MuPdfRepository repo,
                                                  int pageIndex) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_context_picker_title)
                .setMessage(R.string.assistant_context_loading)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> cancelled.set(true))
                .create();
        progress.show();

        executor.execute(() -> {
            AssistantContextTextExtractor.TextResult result = AssistantContextTextExtractor.documentText(
                    repo,
                    MAX_DOCUMENT_CONTEXT_CHARS,
                    cancelled
            );

            activity.runOnUiThread(() -> {
                try { progress.dismiss(); } catch (Throwable ignore) {}
                if (cancelled.get()) return;
                if (isActivityInvalid(activity)) return;

                AssistantContextSnapshot snapshot = new AssistantContextSnapshot(
                        AssistantContextSnapshot.Kind.DOCUMENT,
                        activity.currentDocumentNameOrAppName(),
                        pageIndex,
                        result.text,
                        result.truncated
                );
                maybeLaunchWithLargeContextPrompt(activity, snapshot);
            });
        });
    }

    private static boolean isActivityInvalid(OpenDroidPDFActivity activity) {
        try {
            if (activity.isFinishing()) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                return activity.isDestroyed();
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    private static int safeSelectedPageIndex(OpenDroidPDFActivity activity) {
        try {
            if (activity != null && activity.getDocView() != null) {
                return activity.getDocView().getSelectedItemPosition();
            }
        } catch (Throwable ignore) {
        }
        return 0;
    }
}
