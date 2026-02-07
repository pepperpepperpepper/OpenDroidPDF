package org.opendroidpdf.app.assistant;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.CheckBox;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.document.DocumentViewerIntents;
import org.opendroidpdf.app.preferences.PreferencesNames;
import org.opendroidpdf.app.reader.gesture.ReaderMode;
import org.opendroidpdf.core.MuPdfRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;

public final class AssistantSheetUi {
    private static final float PEEK_RATIO = 0.20f;
    private static final float HALF_RATIO = 0.60f;
    private static final float EXPANDED_OFFSET_RATIO = 0.20f; // 80% height.

    private static final int MAX_PREVIEW_CHARS = 25_000;
    private static final int MAX_ASK_HISTORY_MESSAGES = 12;
    private static final int MAX_ASK_HISTORY_CHARS = 4_000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final OkHttpClient http = new OkHttpClient();
    private static final WeakHashMap<OpenDroidPDFActivity, BottomSheetDialog> openDialogs = new WeakHashMap<>();
    private static final WeakHashMap<OpenDroidPDFActivity, SessionApproval> sessionApprovals = new WeakHashMap<>();

    private enum Scope { SELECTION, PAGE, DOCUMENT }

    private static final class SessionApproval {
        final String documentKey;
        final String providerId;

        SessionApproval(@NonNull String documentKey, @NonNull String providerId) {
            this.documentKey = documentKey;
            this.providerId = providerId;
        }
    }

    private AssistantSheetUi() {}

    public static void show(@NonNull OpenDroidPDFActivity activity) {
        if (activity == null) return;

        dismissIfOpen(activity);

        final MuPDFReaderView docView = activity.getDocView();
        final MuPdfRepository repo = activity.getRepository();
        if (docView == null) {
            try { activity.showInfo("Open a document first."); } catch (Throwable ignore) {}
            return;
        }
        final String documentKey = currentDocumentSessionKey(activity);

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        final View root = LayoutInflater.from(activity).inflate(R.layout.dialog_assistant_sheet, null);
        dialog.setContentView(root);
        openDialogs.put(activity, dialog);
        dialog.setOnDismissListener(d -> openDialogs.remove(activity));

        final FrameLayout[] bottomSheetHolder = new FrameLayout[1];
        final BottomSheetBehavior<?>[] behaviorHolder = new BottomSheetBehavior<?>[1];
        final AtomicBoolean showSources = new AtomicBoolean(true);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            bottomSheetHolder[0] = bottomSheet;
            if (bottomSheet == null) return;

            try {
                ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
                if (lp != null) {
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    bottomSheet.setLayoutParams(lp);
                }
            } catch (Throwable ignore) {}

            try {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behaviorHolder[0] = behavior;

                int screenHeight = 0;
                try { screenHeight = activity.getResources().getDisplayMetrics().heightPixels; } catch (Throwable ignore) { screenHeight = 0; }
                if (screenHeight > 0) {
                    behavior.setPeekHeight(Math.max(1, Math.round(screenHeight * PEEK_RATIO)));
                    behavior.setHalfExpandedRatio(HALF_RATIO);
                    behavior.setExpandedOffset(Math.max(0, Math.round(screenHeight * EXPANDED_OFFSET_RATIO)));
                }

                behavior.setFitToContents(false);
                behavior.setHideable(true);
                behavior.setSkipCollapsed(false);
                behavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);

                ImageButton expandToggle = root.findViewById(R.id.assistant_sheet_expand_toggle);
                if (expandToggle != null) {
                    updateExpandIcon(expandToggle, behavior.getState());
                }
                behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                    @Override public void onStateChanged(@NonNull View bs, int newState) {
                        ImageButton toggle = root.findViewById(R.id.assistant_sheet_expand_toggle);
                        if (toggle != null) updateExpandIcon(toggle, newState);
                    }
                    @Override public void onSlide(@NonNull View bs, float slideOffset) {}
                });
            } catch (Throwable ignore) {}
        });

        ImageButton close = root.findViewById(R.id.assistant_sheet_close);
        if (close != null) close.setOnClickListener(v -> dialog.dismiss());

        ImageButton expandToggle = root.findViewById(R.id.assistant_sheet_expand_toggle);
        if (expandToggle != null) {
            expandToggle.setOnClickListener(v -> {
                BottomSheetBehavior<?> behavior = behaviorHolder[0];
                if (behavior == null) return;
                int state = behavior.getState();
                if (state == BottomSheetBehavior.STATE_EXPANDED) {
                    behavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
                } else {
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            });
        }

        final ImageButton clearChat = root.findViewById(R.id.assistant_sheet_clear_chat);

        // Mode switching.
        final View contentFlipper = root.findViewById(R.id.assistant_sheet_content_flipper);
        RadioGroup modeGroup = root.findViewById(R.id.assistant_sheet_mode_group);
        if (modeGroup != null && contentFlipper instanceof android.widget.ViewFlipper) {
            android.widget.ViewFlipper flipper = (android.widget.ViewFlipper) contentFlipper;
            modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                boolean isAsk = checkedId == R.id.assistant_sheet_mode_ask;
                if (checkedId == R.id.assistant_sheet_mode_summary) {
                    flipper.setDisplayedChild(1);
                } else if (checkedId == R.id.assistant_sheet_mode_read_aloud) {
                    flipper.setDisplayedChild(2);
                } else {
                    flipper.setDisplayedChild(0);
                }
                if (clearChat != null) clearChat.setVisibility(isAsk ? View.VISIBLE : View.GONE);
            });
            try {
                if (clearChat != null) {
                    clearChat.setVisibility(modeGroup.getCheckedRadioButtonId() == R.id.assistant_sheet_mode_ask ? View.VISIBLE : View.GONE);
                }
            } catch (Throwable ignore) {}
        }

        final LinearLayout chatContainer = root.findViewById(R.id.assistant_sheet_chat_container);
        final ScrollView chatScroll = root.findViewById(R.id.assistant_sheet_chat_scroll);
        restoreAskTranscript(activity, documentKey, chatContainer, chatScroll, showSources.get(), behaviorHolder, docView);
        updateClearChatEnabled(clearChat, documentKey);
        if (clearChat != null) {
            clearChat.setOnClickListener(v -> clearAskChat(documentKey, chatContainer, clearChat));
        }

        // Options menu.
        ImageButton options = root.findViewById(R.id.assistant_sheet_options);
        if (options != null) {
            options.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(activity, v);
                popup.getMenuInflater().inflate(R.menu.assistant_sheet_options, popup.getMenu());
                try {
                    android.view.MenuItem toggle = popup.getMenu().findItem(R.id.assistant_sheet_action_toggle_sources);
                    if (toggle != null) {
                        toggle.setTitle(showSources.get() ? R.string.assistant_sheet_action_hide_sources : R.string.assistant_sheet_action_show_sources);
                    }
                } catch (Throwable ignore) {}
                try {
                    android.view.MenuItem requirePreviewAgain = popup.getMenu().findItem(R.id.assistant_sheet_action_require_preview_again);
                    if (requirePreviewAgain != null) {
                        AssistantLlmProviderConfig currentProvider = AssistantLlmProvidersStore.defaultProviderOrNull(activity);
                        boolean allowed = currentProvider != null && isSessionAllowed(activity, currentProvider);
                        requirePreviewAgain.setVisible(allowed);
                    }
                } catch (Throwable ignore) {}
                popup.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == R.id.assistant_sheet_action_new_chat || id == R.id.assistant_sheet_action_clear_chat) {
                        clearAskChat(documentKey, chatContainer, clearChat);
                        return true;
                    }
                    if (id == R.id.assistant_sheet_action_require_preview_again) {
                        clearSessionApproval(activity);
                        try {
                            TextView providerLine = root.findViewById(R.id.assistant_sheet_provider_line);
                            SharedPreferences prefs = activity.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
                            boolean enabled = safeGetBoolean(prefs, SettingsActivity.PREF_ASSISTANT_ENABLED, false);
                            AssistantLlmProviderConfig currentProvider = AssistantLlmProvidersStore.defaultProviderOrNull(activity);
                            if (providerLine != null) {
                                if (!enabled) {
                                    providerLine.setText(R.string.assistant_sheet_provider_disabled);
                                } else if (currentProvider == null) {
                                    providerLine.setText(R.string.assistant_sheet_provider_unconfigured);
                                } else {
                                    providerLine.setText(activity.getString(R.string.assistant_sheet_provider_configured, currentProvider.name()));
                                }
                            }
                        } catch (Throwable ignore) {}
                        return true;
                    }
                    if (id == R.id.assistant_sheet_action_toggle_sources) {
                        boolean next = !showSources.get();
                        showSources.set(next);
                        if (chatContainer != null) {
                            for (int i = 0; i < chatContainer.getChildCount(); i++) {
                                applySourcesVisibility(chatContainer.getChildAt(i), next);
                            }
                        }
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }

        // Scope selection.
        RadioGroup scopeGroup = root.findViewById(R.id.assistant_sheet_scope_group);
        final RadioButton scopeSelection = root.findViewById(R.id.assistant_sheet_scope_selection);
        final RadioButton scopePage = root.findViewById(R.id.assistant_sheet_scope_page);
        final RadioButton scopeDoc = root.findViewById(R.id.assistant_sheet_scope_document);

        final String selectionText = AssistantContextTextExtractor.selectionTextOrNull(activity.getSelectedPageView());
        final boolean hasSelection = selectionText != null && !selectionText.trim().isEmpty();
        if (scopeSelection != null) {
            scopeSelection.setEnabled(hasSelection);
            if (hasSelection) {
                scopeSelection.setChecked(true);
            }
        }
        if (!hasSelection && scopePage != null) scopePage.setChecked(true);

        final SharedPreferences prefs =
                activity.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
        final boolean allowWhole = safeGetBoolean(prefs, SettingsActivity.PREF_ASSISTANT_ALLOW_WHOLE_DOCUMENT, false);
        if (scopeDoc != null) {
            scopeDoc.setEnabled(allowWhole);
            if (!allowWhole && scopeDoc.isChecked() && scopePage != null) scopePage.setChecked(true);
        }

        final boolean enabled = safeGetBoolean(prefs, SettingsActivity.PREF_ASSISTANT_ENABLED, false);
        AssistantLlmProviderConfig provider = AssistantLlmProvidersStore.defaultProviderOrNull(activity);
        final boolean sessionAllowed = provider != null && isSessionAllowed(activity, provider);
        TextView providerLine = root.findViewById(R.id.assistant_sheet_provider_line);
        if (providerLine != null) {
            if (!enabled) {
                providerLine.setText(R.string.assistant_sheet_provider_disabled);
            } else if (provider == null) {
                providerLine.setText(R.string.assistant_sheet_provider_unconfigured);
            } else if (sessionAllowed) {
                providerLine.setText(activity.getString(R.string.assistant_sheet_provider_configured_allowed, provider.name()));
            } else {
                providerLine.setText(activity.getString(R.string.assistant_sheet_provider_configured, provider.name()));
            }
        }

        // Provider setup.
        Button setupProvider = root.findViewById(R.id.assistant_sheet_setup_provider);
        if (setupProvider != null) {
            setupProvider.setOnClickListener(v -> {
                try {
                    if (!isAssistantEnabled(prefs)) {
                        activity.startActivity(new Intent(activity, SettingsActivity.class));
                    } else {
                        activity.startActivity(new Intent(activity, AssistantProvidersActivity.class));
                    }
                } catch (Throwable ignore) {}
            });
        }

        // Preview text (privacy gate scaffolding).
        Button preview = root.findViewById(R.id.assistant_sheet_preview);
        if (preview != null) {
            preview.setOnClickListener(v -> showPreviewDialog(activity, repo, docView, currentScope(scopeGroup)));
        }

        // Prompt row actions.
        ImageButton attach = root.findViewById(R.id.assistant_sheet_attach);
        if (attach != null) {
            attach.setOnClickListener(v -> {
                try { activity.showInfo(activity.getString(R.string.assistant_sheet_attach_coming_soon)); } catch (Throwable ignore) {}
            });
        }

        ImageButton mic = root.findViewById(R.id.assistant_sheet_mic);
        if (mic != null) {
            mic.setOnClickListener(v -> {
                try {
                    AssistantContextSnapshot snap = buildVoiceContextSnapshot(activity, repo, docView, currentScope(scopeGroup));
                    AssistantContextStore.set(snap);
                    activity.startActivity(new Intent(activity, AssistantActivity.class));
                } catch (Throwable t) {
                    try { activity.showInfo(t.getMessage()); } catch (Throwable ignore) {}
                }
            });
        }

        EditText prompt = root.findViewById(R.id.assistant_sheet_prompt);
        Button send = root.findViewById(R.id.assistant_sheet_send);
        if (send != null && prompt != null) {
            send.setOnClickListener(v -> {
                String qRaw = prompt.getText() != null ? prompt.getText().toString() : "";
                if (qRaw == null) qRaw = "";
                final String question = qRaw.trim();
                if (question.isEmpty()) return;

                if (!isAssistantEnabled(prefs)) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_disabled_info)); } catch (Throwable ignore) {}
                    return;
                }
                AssistantLlmProviderConfig currentProvider = AssistantLlmProvidersStore.defaultProviderOrNull(activity);
                if (currentProvider == null) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_no_provider)); } catch (Throwable ignore) {}
                    return;
                }
                String apiKey = AssistantSecrets.getLlmApiKeyOrNull(activity, currentProvider.id());
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    try { activity.showInfo(activity.getString(R.string.assistant_provider_key_unset)); } catch (Throwable ignore) {}
                    return;
                }
                if (isWifiOnly(prefs) && !isOnWifi(activity)) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_wifi_only_blocked)); } catch (Throwable ignore) {}
                    return;
                }

                Scope scope = currentScope(scopeGroup);
                int pageIndex = safeSelectedPageIndex(docView);

                if (scope == Scope.DOCUMENT) {
                    showDocumentPreviewAndAskAsync(activity, prefs, repo, docView, documentKey, question, currentProvider, apiKey, chatContainer, chatScroll, clearChat, showSources.get(), behaviorHolder);
                    return;
                }

                final String ctxText;
                final boolean truncated;
                if (scope == Scope.SELECTION) {
                    String sel = AssistantContextTextExtractor.selectionTextOrNull(activity.getSelectedPageView());
                    if (sel == null || sel.trim().isEmpty()) {
                        try { activity.showInfo(activity.getString(R.string.assistant_sheet_no_selection)); } catch (Throwable ignore) {}
                        return;
                    }
                    ctxText = "Page " + (pageIndex + 1) + ":\n" + sel;
                    truncated = false;
                } else {
                    AssistantContextTextExtractor.TextResult page = AssistantContextTextExtractor.pageText(repo, pageIndex, MAX_PREVIEW_CHARS);
                    ctxText = "Page " + (pageIndex + 1) + ":\n" + (page.text != null ? page.text : "");
                    truncated = page.truncated;
                }

                String previewSummary = describeScope(activity, scope, pageIndex, ctxText.length(), truncated) + " • Ask";
                final List<AssistantLlmClient.ChatMessage> chatHistory = boundedAskChatHistory(documentKey);
                String outgoing = formatAskOutgoingPreview(question, ctxText, chatHistory);
                runWithPrivacyGate(activity, prefs, currentProvider, previewSummary, outgoing, R.string.assistant_sheet_send, () -> {
                    prompt.setText("");
                    if (chatContainer == null) return;
                    final long transcriptVersion = AssistantAskTranscriptStore.appendUser(documentKey, question);
                    updateClearChatEnabled(clearChat, documentKey);

                    chatContainer.addView(buildChatBubble(activity, question, true, false, null, null, showSources.get(), behaviorHolder, docView));
                    View pending = buildChatBubble(activity, activity.getString(R.string.assistant_sheet_generating), false, false, null, null, showSources.get(), behaviorHolder, docView);
                    chatContainer.addView(pending);
                    if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));

                    executor.execute(() -> {
                        AssistantLlmClient.AskResult result;
                        try {
                            result = AssistantLlmClient.askBlocking(http, currentProvider, apiKey, question, ctxText, chatHistory);
                        } catch (Throwable t) {
                            String msg = t.getMessage();
                            if (msg == null || msg.trim().isEmpty()) msg = t.getClass().getSimpleName();
                            result = AssistantLlmClient.AskResult.plainText(msg);
                        }
                        final AssistantLlmClient.AskResult resultFinal = result;
                        if (AssistantAskTranscriptStore.isVersion(documentKey, transcriptVersion)) {
                            AssistantAskTranscriptStore.appendAssistant(documentKey, resultFinal.answerText, resultFinal.citationNumbers, resultFinal.citationPages1Based);
                        }
                        activity.runOnUiThread(() -> {
                            if (!AssistantAskTranscriptStore.isVersion(documentKey, transcriptVersion)) return;
                            if (!chatContainer.isAttachedToWindow()) return;
                            if (chatContainer.indexOfChild(pending) >= 0) chatContainer.removeView(pending);
                            chatContainer.addView(buildChatBubble(activity,
                                    resultFinal.answerText,
                                    false,
                                    true,
                                    resultFinal.citationNumbers,
                                    resultFinal.citationPages1Based,
                                    showSources.get(),
                                    behaviorHolder,
                                    docView));
                            updateClearChatEnabled(clearChat, documentKey);
                            if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                        });
                    });
                });
            });
        }

        // Summary mode (Selection only for now).
        RadioGroup summaryStyleGroup = root.findViewById(R.id.assistant_sheet_summary_style_group);
        Button summaryGenerate = root.findViewById(R.id.assistant_sheet_summary_generate);
        TextView summaryStatus = root.findViewById(R.id.assistant_sheet_summary_status);
        TextView summaryOutput = root.findViewById(R.id.assistant_sheet_summary_output);
        Button summaryCopy = root.findViewById(R.id.assistant_sheet_summary_copy);
        Button summaryExport = root.findViewById(R.id.assistant_sheet_summary_export);
        Button summarySaveNote = root.findViewById(R.id.assistant_sheet_summary_save_note);
        Button summaryInsert = root.findViewById(R.id.assistant_sheet_summary_insert_into_document);
        final AtomicBoolean noteSaveInFlight = new AtomicBoolean(false);
        final AtomicBoolean exportInFlight = new AtomicBoolean(false);
        if (summaryCopy != null && summaryOutput != null) {
            summaryCopy.setOnClickListener(v -> {
                String text = summaryOutput.getText() != null ? summaryOutput.getText().toString() : "";
                if (text == null) text = "";
                text = text.trim();
                if (text.isEmpty()) return;
                copyToClipboard(activity, "assistant_summary", text);
                try { activity.showInfo(activity.getString(R.string.assistant_sheet_copied)); } catch (Throwable ignore) {}
            });
        }
        if (summaryExport != null && summaryOutput != null) {
            summaryExport.setEnabled(false);
            summaryExport.setOnClickListener(v -> {
                String text = summaryOutput.getText() != null ? summaryOutput.getText().toString() : "";
                if (text == null) text = "";
                text = text.trim();
                if (text.isEmpty()) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_summary_empty)); } catch (Throwable ignore) {}
                    return;
                }
                if (noteSaveInFlight.get()) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_saving_note)); } catch (Throwable ignore) {}
                    return;
                }
                if (exportInFlight.getAndSet(true)) return;

                if (summaryStatus != null) summaryStatus.setText(R.string.assistant_sheet_preparing_export);
                if (summaryGenerate != null) summaryGenerate.setEnabled(false);
                if (summaryCopy != null) summaryCopy.setEnabled(false);
                if (summaryInsert != null) summaryInsert.setEnabled(false);
                if (summarySaveNote != null) summarySaveNote.setEnabled(false);
                summaryExport.setEnabled(false);

                final String sourceTitle = safeCurrentDocumentTitle(activity);
                final String styleLabel = summaryStyleLabel(activity, summaryStyleGroup);
                final String textFinal = text;
                executor.execute(() -> {
                    File outFile = null;
                    String error = null;
                    try {
                        outFile = AssistantNoteDocumentCreator.createSummaryExportPdf(activity, sourceTitle, textFinal, styleLabel);
                    } catch (Throwable t) {
                        error = t.getMessage();
                        if (error == null || error.trim().isEmpty()) error = t.getClass().getSimpleName();
                    }
                    final File outFinal = outFile;
                    final String errFinal = error;
                    activity.runOnUiThread(() -> {
                        exportInFlight.set(false);
                        if (isActivityInvalid(activity)) return;
                        if (summaryStatus != null) summaryStatus.setText("");

                        boolean hasText;
                        try {
                            hasText = summaryOutput.getText() != null && !summaryOutput.getText().toString().trim().isEmpty();
                        } catch (Throwable ignore) {
                            hasText = false;
                        }

                        if (summaryGenerate != null) summaryGenerate.setEnabled(true);
                        if (summaryCopy != null) summaryCopy.setEnabled(hasText);
                        if (summaryInsert != null) summaryInsert.setEnabled(hasText);
                        if (summarySaveNote != null) summarySaveNote.setEnabled(hasText && !noteSaveInFlight.get() && !exportInFlight.get());
                        summaryExport.setEnabled(hasText && !noteSaveInFlight.get());

                        if (errFinal != null) {
                            try { activity.showInfo(activity.getString(R.string.assistant_sheet_export_failed, errFinal)); } catch (Throwable ignore) {}
                            return;
                        }
                        if (outFinal == null) {
                            try { activity.showInfo(activity.getString(R.string.assistant_sheet_export_failed, activity.getString(R.string.assistant_sheet_unknown_error))); } catch (Throwable ignore) {}
                            return;
                        }

                        try {
                            Uri uri = Uri.fromFile(outFinal);
                            Intent intent = DocumentViewerIntents.viewInAppAndOpenExportSheet(activity, uri, outFinal.getName());
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            activity.startActivity(intent);
                        } catch (Throwable t) {
                            try { activity.showInfo(t.getMessage()); } catch (Throwable ignore) {}
                            return;
                        }

                        try { dialog.dismiss(); } catch (Throwable ignore) {}
                    });
                });
            });
        }
        if (summaryInsert != null && summaryOutput != null) {
            summaryInsert.setEnabled(false);
            summaryInsert.setOnClickListener(v -> {
                String text = summaryOutput.getText() != null ? summaryOutput.getText().toString() : "";
                if (text == null) text = "";
                text = text.trim();
                if (text.isEmpty()) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_summary_empty)); } catch (Throwable ignore) {}
                    return;
                }
                promptInsertTextIntoDocument(activity, docView, behaviorHolder, text, null);
            });
        }
        if (summarySaveNote != null && summaryOutput != null) {
            summarySaveNote.setEnabled(false);
            summarySaveNote.setOnClickListener(v -> {
                String text = summaryOutput.getText() != null ? summaryOutput.getText().toString() : "";
                if (text == null) text = "";
                text = text.trim();
                if (text.isEmpty()) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_summary_empty)); } catch (Throwable ignore) {}
                    return;
                }
                if (exportInFlight.get()) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_preparing_export)); } catch (Throwable ignore) {}
                    return;
                }
                if (noteSaveInFlight.getAndSet(true)) return;

                if (summaryStatus != null) summaryStatus.setText(R.string.assistant_sheet_saving_note);
                summarySaveNote.setEnabled(false);
                if (summaryExport != null) summaryExport.setEnabled(false);

                final String sourceTitle = safeCurrentDocumentTitle(activity);
                final String styleLabel = summaryStyleLabel(activity, summaryStyleGroup);

                final String textFinal = text;
                executor.execute(() -> {
                    File outFile = null;
                    String error = null;
                    try {
                        outFile = AssistantNoteDocumentCreator.createSummaryNotePdf(activity, sourceTitle, textFinal, styleLabel);
                    } catch (Throwable t) {
                        error = t.getMessage();
                        if (error == null || error.trim().isEmpty()) error = t.getClass().getSimpleName();
                    }
                    final File outFinal = outFile;
                    final String errFinal = error;
                    activity.runOnUiThread(() -> {
                        noteSaveInFlight.set(false);
                        if (isActivityInvalid(activity)) return;
                        if (summaryStatus != null) summaryStatus.setText("");

                        boolean hasText;
                        try {
                            hasText = summaryOutput.getText() != null && !summaryOutput.getText().toString().trim().isEmpty();
                        } catch (Throwable ignore) {
                            hasText = false;
                        }
                        summarySaveNote.setEnabled(hasText && !exportInFlight.get());
                        if (summaryExport != null) summaryExport.setEnabled(hasText && !exportInFlight.get());

                        if (errFinal != null) {
                            try { activity.showInfo(activity.getString(R.string.assistant_sheet_save_note_failed, errFinal)); } catch (Throwable ignore) {}
                            return;
                        }
                        if (outFinal == null) {
                            try { activity.showInfo(activity.getString(R.string.assistant_sheet_save_note_failed, activity.getString(R.string.assistant_sheet_unknown_error))); } catch (Throwable ignore) {}
                            return;
                        }

                        try { activity.showInfo(activity.getString(R.string.assistant_sheet_saved_note, outFinal.getName())); } catch (Throwable ignore) {}
                        try {
                            Uri uri = Uri.fromFile(outFinal);
                            Intent intent = DocumentViewerIntents.viewInApp(activity, uri, outFinal.getName());
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            activity.startActivity(intent);
                        } catch (Throwable t) {
                            try { activity.showInfo(t.getMessage()); } catch (Throwable ignore) {}
                        }
                    });
                });
            });
        }
        final AtomicBoolean summaryInFlight = new AtomicBoolean(false);
        if (summaryGenerate != null && summaryOutput != null) {
            summaryGenerate.setOnClickListener(v -> {
                if (!isAssistantEnabled(prefs)) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_disabled_info)); } catch (Throwable ignore) {}
                    return;
                }
                AssistantLlmProviderConfig currentProvider = AssistantLlmProvidersStore.defaultProviderOrNull(activity);
                if (currentProvider == null) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_no_provider)); } catch (Throwable ignore) {}
                    return;
                }
                String apiKey = AssistantSecrets.getLlmApiKeyOrNull(activity, currentProvider.id());
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    try { activity.showInfo(activity.getString(R.string.assistant_provider_key_unset)); } catch (Throwable ignore) {}
                    return;
                }
                if (isWifiOnly(prefs) && !isOnWifi(activity)) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_wifi_only_blocked)); } catch (Throwable ignore) {}
                    return;
                }

                Scope scope = currentScope(scopeGroup);
                if (scope != Scope.SELECTION) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_no_selection)); } catch (Throwable ignore) {}
                    return;
                }
                String sel = AssistantContextTextExtractor.selectionTextOrNull(activity.getSelectedPageView());
                if (sel == null || sel.trim().isEmpty()) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_no_selection)); } catch (Throwable ignore) {}
                    return;
                }

                AssistantLlmClient.SummaryStyle styleTmp = AssistantLlmClient.SummaryStyle.MEDIUM;
                if (summaryStyleGroup != null) {
                    int checked = summaryStyleGroup.getCheckedRadioButtonId();
                    if (checked == R.id.assistant_sheet_summary_style_short) styleTmp = AssistantLlmClient.SummaryStyle.SHORT;
                    else if (checked == R.id.assistant_sheet_summary_style_detailed) styleTmp = AssistantLlmClient.SummaryStyle.DETAILED;
                }
                final AssistantLlmClient.SummaryStyle styleFinal = styleTmp;

                int pageIndex = safeSelectedPageIndex(docView);
                String previewSummary = describeScope(activity, Scope.SELECTION, pageIndex, sel.length(), false) + " • Summary";
                runWithPrivacyGate(activity, prefs, currentProvider, previewSummary, sel, R.string.assistant_sheet_generate, () -> {
                    if (summaryInFlight.getAndSet(true)) return;
                    if (summaryStatus != null) summaryStatus.setText(R.string.assistant_sheet_generating);
                    summaryOutput.setText("");
                    summaryGenerate.setEnabled(false);
                    if (summaryCopy != null) summaryCopy.setEnabled(false);
                    if (summaryInsert != null) summaryInsert.setEnabled(false);
                    if (summarySaveNote != null) summarySaveNote.setEnabled(false);
                    if (summaryExport != null) summaryExport.setEnabled(false);

                    executor.execute(() -> {
                        String out;
                        try {
                            out = AssistantLlmClient.summarizeBlocking(http, currentProvider, apiKey, sel, styleFinal);
                        } catch (Throwable t) {
                            out = t.getMessage();
                        }
                        final String outFinal = out != null ? out : "";
                        activity.runOnUiThread(() -> {
                            summaryInFlight.set(false);
                            if (summaryStatus != null) summaryStatus.setText("");
                            summaryGenerate.setEnabled(true);
                            summaryOutput.setText(outFinal);
                            boolean hasOut = !outFinal.trim().isEmpty();
                            if (summaryCopy != null) summaryCopy.setEnabled(hasOut);
                            if (summaryInsert != null) summaryInsert.setEnabled(hasOut);
                            if (summarySaveNote != null) summarySaveNote.setEnabled(hasOut && !noteSaveInFlight.get() && !exportInFlight.get());
                            if (summaryExport != null) summaryExport.setEnabled(hasOut && !exportInFlight.get() && !noteSaveInFlight.get());
                        });
                    });
                });
            });
        }

        Button readAloudStart = root.findViewById(R.id.assistant_sheet_read_aloud_start);
        if (readAloudStart != null) {
            readAloudStart.setOnClickListener(v -> {
                try {
                    activity.requestReadAloud();
                } catch (Throwable ignore) {}
                try {
                    BottomSheetBehavior<?> behavior = behaviorHolder[0];
                    if (behavior != null) behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                } catch (Throwable ignore) {}
            });
        }

        dialog.show();
    }

    private static void dismissIfOpen(@NonNull OpenDroidPDFActivity activity) {
        BottomSheetDialog existing = openDialogs.remove(activity);
        if (existing != null) {
            try { existing.dismiss(); } catch (Throwable ignore) {}
        }
    }

    private static Scope currentScope(@Nullable RadioGroup scopeGroup) {
        if (scopeGroup == null) return Scope.PAGE;
        int checked = scopeGroup.getCheckedRadioButtonId();
        if (checked == R.id.assistant_sheet_scope_selection) return Scope.SELECTION;
        if (checked == R.id.assistant_sheet_scope_document) return Scope.DOCUMENT;
        return Scope.PAGE;
    }

    @NonNull
    private static String safeCurrentDocumentTitle(@NonNull OpenDroidPDFActivity activity) {
        try {
            String title = activity.currentDocumentNameOrAppName();
            if (title != null) {
                title = title.trim();
                if (!title.isEmpty()) return title;
            }
        } catch (Throwable ignore) {}
        return "Document";
    }

    @Nullable
    private static String summaryStyleLabel(@NonNull Context context, @Nullable RadioGroup summaryStyleGroup) {
        if (summaryStyleGroup == null) return null;
        int checked = summaryStyleGroup.getCheckedRadioButtonId();
        int labelResId = R.string.assistant_sheet_summary_medium;
        if (checked == R.id.assistant_sheet_summary_style_short) labelResId = R.string.assistant_sheet_summary_short;
        else if (checked == R.id.assistant_sheet_summary_style_detailed) labelResId = R.string.assistant_sheet_summary_detailed;
        try {
            return context.getString(labelResId);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static void updateExpandIcon(@NonNull ImageButton toggle, int state) {
        try {
            if (state == BottomSheetBehavior.STATE_EXPANDED) {
                toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
            } else {
                toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
            }
        } catch (Throwable ignore) {}
    }

    private static int safeSelectedPageIndex(@Nullable MuPDFReaderView docView) {
        try {
            if (docView != null) return Math.max(0, docView.getSelectedItemPosition());
        } catch (Throwable ignore) {}
        return 0;
    }

    private static AssistantContextSnapshot buildVoiceContextSnapshot(@NonNull OpenDroidPDFActivity activity,
                                                                      @Nullable MuPdfRepository repo,
                                                                      @NonNull MuPDFReaderView docView,
                                                                      @NonNull Scope scope) {
        int pageIndex = safeSelectedPageIndex(docView);
        String title;
        try { title = activity.currentDocumentNameOrAppName(); } catch (Throwable t) { title = "Document"; }

        if (scope == Scope.SELECTION) {
            String selection = AssistantContextTextExtractor.selectionTextOrNull(activity.getSelectedPageView());
            if (selection == null || selection.trim().isEmpty()) {
                throw new IllegalStateException(activity.getString(R.string.assistant_sheet_no_selection));
            }
            return new AssistantContextSnapshot(AssistantContextSnapshot.Kind.SELECTION, title, pageIndex, selection, false);
        }
        if (scope == Scope.DOCUMENT) {
            AssistantContextTextExtractor.TextResult res = AssistantContextTextExtractor.documentText(repo, MAX_PREVIEW_CHARS, null);
            return new AssistantContextSnapshot(AssistantContextSnapshot.Kind.DOCUMENT, title, pageIndex, res.text, res.truncated);
        }
        AssistantContextTextExtractor.TextResult page = AssistantContextTextExtractor.pageText(repo, pageIndex, MAX_PREVIEW_CHARS);
        return new AssistantContextSnapshot(AssistantContextSnapshot.Kind.PAGE, title, pageIndex, page.text, page.truncated);
    }

    private static void showPreviewDialog(@NonNull OpenDroidPDFActivity activity,
                                         @Nullable MuPdfRepository repo,
                                         @NonNull MuPDFReaderView docView,
                                         @NonNull Scope scope) {
        if (scope == Scope.DOCUMENT) {
            showDocumentPreviewDialogAsync(activity, repo, docView);
            return;
        }

        int pageIndex = safeSelectedPageIndex(docView);
        String title;
        try { title = activity.currentDocumentNameOrAppName(); } catch (Throwable t) { title = "Document"; }

        String text = "";
        boolean truncated = false;
        if (scope == Scope.SELECTION) {
            text = AssistantContextTextExtractor.selectionTextOrNull(activity.getSelectedPageView());
            if (text == null) text = "";
        } else {
            AssistantContextTextExtractor.TextResult page = AssistantContextTextExtractor.pageText(repo, pageIndex, MAX_PREVIEW_CHARS);
            text = page.text;
            truncated = page.truncated;
        }

        showPreviewDialogWithText(activity, title, scope, pageIndex, text, truncated);
    }

    private static void showDocumentPreviewDialogAsync(@NonNull OpenDroidPDFActivity activity,
                                                      @Nullable MuPdfRepository repo,
                                                      @NonNull MuPDFReaderView docView) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_sheet_preview_title)
                .setMessage(R.string.assistant_context_loading)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> cancelled.set(true))
                .create();
        progress.show();

        executor.execute(() -> {
            AssistantContextTextExtractor.TextResult result = AssistantContextTextExtractor.documentText(repo, MAX_PREVIEW_CHARS, cancelled);

            activity.runOnUiThread(() -> {
                try { progress.dismiss(); } catch (Throwable ignore) {}
                if (cancelled.get()) return;
                if (isActivityInvalid(activity)) return;
                int pageIndex = safeSelectedPageIndex(docView);
                String title;
                try { title = activity.currentDocumentNameOrAppName(); } catch (Throwable t) { title = "Document"; }
                showPreviewDialogWithText(activity, title, Scope.DOCUMENT, pageIndex, result.text, result.truncated);
            });
        });
    }

    private static boolean isActivityInvalid(OpenDroidPDFActivity activity) {
        try {
            if (activity.isFinishing()) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                return activity.isDestroyed();
            }
        } catch (Throwable ignore) {}
        return false;
    }

    private static void showPreviewDialogWithText(@NonNull OpenDroidPDFActivity activity,
                                                 @NonNull String documentTitle,
                                                 @NonNull Scope scope,
                                                 int pageIndex,
                                                 @Nullable String text,
                                                 boolean truncated) {
        View body = LayoutInflater.from(activity).inflate(R.layout.dialog_assistant_preview, null);
        TextView summary = body.findViewById(R.id.assistant_preview_summary);
        TextView content = body.findViewById(R.id.assistant_preview_text);

        String scopeSummary = describeScope(activity, scope, pageIndex, text != null ? text.length() : 0, truncated);
        if (summary != null) summary.setText(scopeSummary);
        if (content != null) content.setText(text != null ? text : "");

        AlertDialog d = new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_sheet_preview_title)
                .setView(body)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        d.show();
    }

    private static void runWithPrivacyGate(@NonNull OpenDroidPDFActivity activity,
                                          @NonNull SharedPreferences prefs,
                                          @NonNull AssistantLlmProviderConfig provider,
                                          @NonNull String summaryLine,
                                          @NonNull String outgoingText,
                                          int actionLabelResId,
                                          @NonNull Runnable onConfirm) {
        if (isActivityInvalid(activity)) return;

        boolean requirePreview = safeGetBoolean(prefs, SettingsActivity.PREF_ASSISTANT_REQUIRE_PREVIEW, true);
        if (!requirePreview || isSessionAllowed(activity, provider)) {
            try { onConfirm.run(); } catch (Throwable ignore) {}
            return;
        }

        showPreviewConfirmDialog(activity, provider, summaryLine, outgoingText, actionLabelResId, onConfirm);
    }

    private static void showPreviewConfirmDialog(@NonNull OpenDroidPDFActivity activity,
                                                @NonNull AssistantLlmProviderConfig provider,
                                                @NonNull String summaryLine,
                                                @NonNull String outgoingText,
                                                int actionLabelResId,
                                                @NonNull Runnable onConfirm) {
        if (isActivityInvalid(activity)) return;

        View body = LayoutInflater.from(activity).inflate(R.layout.dialog_assistant_preview, null);
        TextView summary = body.findViewById(R.id.assistant_preview_summary);
        TextView content = body.findViewById(R.id.assistant_preview_text);
        CheckBox allowSession = body.findViewById(R.id.assistant_preview_allow_session);

        if (summary != null) summary.setText(summaryLine);
        if (content != null) content.setText(outgoingText != null ? outgoingText : "");
        if (allowSession != null) {
            allowSession.setChecked(false);
            allowSession.setVisibility(View.VISIBLE);
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_sheet_preview_title)
                .setView(body)
                .setPositiveButton(actionLabelResId, (dialog, which) -> {
                    if (allowSession != null && allowSession.isChecked()) {
                        setSessionAllowed(activity, provider);
                        try { activity.showInfo(activity.getString(R.string.assistant_sheet_allowed_for_session)); } catch (Throwable ignore) {}
                    }
                    try { onConfirm.run(); } catch (Throwable ignore) {}
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static boolean isSessionAllowed(@NonNull OpenDroidPDFActivity activity,
                                           @NonNull AssistantLlmProviderConfig provider) {
        SessionApproval approval = sessionApprovals.get(activity);
        if (approval == null) return false;
        if (!provider.id().equals(approval.providerId)) return false;
        return currentDocumentSessionKey(activity).equals(approval.documentKey);
    }

    private static void setSessionAllowed(@NonNull OpenDroidPDFActivity activity,
                                          @NonNull AssistantLlmProviderConfig provider) {
        sessionApprovals.put(activity, new SessionApproval(currentDocumentSessionKey(activity), provider.id()));
    }

    private static void clearSessionApproval(@NonNull OpenDroidPDFActivity activity) {
        sessionApprovals.remove(activity);
    }

    @NonNull
    private static String currentDocumentSessionKey(@NonNull OpenDroidPDFActivity activity) {
        try {
            Uri uri = activity.currentUserFacingUriOrNull();
            if (uri != null) return uri.toString();
        } catch (Throwable ignore) {}

        try {
            String displayName = activity.currentUserFacingDisplayNameOrNull();
            if (displayName != null) {
                displayName = displayName.trim();
                if (!displayName.isEmpty()) return "name:" + displayName;
            }
        } catch (Throwable ignore) {}

        try {
            String title = activity.currentDocumentNameOrAppName();
            if (title != null) {
                title = title.trim();
                if (!title.isEmpty()) return "title:" + title;
            }
        } catch (Throwable ignore) {}

        return "unknown";
    }

    private static String describeScope(@NonNull Context ctx,
                                       @NonNull Scope scope,
                                       int pageIndex,
                                       int chars,
                                       boolean truncated) {
        String kind;
        if (scope == Scope.SELECTION) {
            kind = ctx.getString(R.string.assistant_sheet_scope_selection);
        } else if (scope == Scope.DOCUMENT) {
            kind = ctx.getString(R.string.assistant_sheet_scope_whole_document);
        } else {
            kind = ctx.getString(R.string.assistant_sheet_scope_this_page);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(kind);
        if (scope != Scope.DOCUMENT) sb.append(" • p. ").append(pageIndex + 1);
        sb.append(" • ").append(chars).append(" chars");
        if (truncated) sb.append(" (truncated)");
        return sb.toString();
    }

    private static View buildChatBubble(@NonNull OpenDroidPDFActivity activity,
                                        @NonNull String text,
                                        boolean isUser,
                                        boolean showActions,
                                        @Nullable int[] citationNumbers,
                                        @Nullable int[] citationPages1Based,
                                        boolean showSources,
                                        @NonNull BottomSheetBehavior<?>[] behaviorHolder,
                                        @NonNull MuPDFReaderView docView) {
        LinearLayout bubble = new LinearLayout(activity);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int pad = dpToPx(activity, 10);
        bubble.setPadding(pad, pad, pad, pad);

        TextView tv = new TextView(activity);
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setText(text);
        tv.setTextIsSelectable(!isUser);
        bubble.addView(tv);

        if (!isUser && citationNumbers != null && citationPages1Based != null && citationNumbers.length == citationPages1Based.length) {
            LinearLayout sourcesRow = new LinearLayout(activity);
            sourcesRow.setOrientation(LinearLayout.HORIZONTAL);
            sourcesRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            sourcesRow.setTag("assistant_sources_row");
            sourcesRow.setVisibility(showSources ? View.VISIBLE : View.GONE);

            TextView label = new TextView(activity);
            label.setText(R.string.assistant_sheet_sources_label);
            sourcesRow.addView(label);

            for (int i = 0; i < citationNumbers.length; i++) {
                final int number = citationNumbers[i];
                final int page1 = citationPages1Based[i];
                TextView badge = new TextView(activity);
                badge.setText(String.valueOf(number));
                badge.setContentDescription(activity.getString(R.string.assistant_sheet_sources_badge_content_description, number, page1));
                badge.setEllipsize(TextUtils.TruncateAt.END);
                badge.setSingleLine(true);
                badge.setBackgroundResource(R.drawable.bg_assistant_source_badge);
                int hPad = dpToPx(activity, 8);
                int vPad = dpToPx(activity, 4);
                badge.setPadding(hPad, vPad, hPad, vPad);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = dpToPx(activity, 6);
                badge.setLayoutParams(lp);
                badge.setOnClickListener(v -> {
                    int target = Math.max(0, page1 - 1);
                    try { docView.setDisplayedViewIndex(target, true); } catch (Throwable ignore) {}
                    try {
                        BottomSheetBehavior<?> behavior = behaviorHolder[0];
                        if (behavior != null && behavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                            behavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
                        }
                    } catch (Throwable ignore) {}
                });
                sourcesRow.addView(badge);
            }
            bubble.addView(sourcesRow);
        }

        if (!isUser && showActions) {
            View actions = buildAssistantAnswerActions(activity, docView, behaviorHolder, text, citationPages1Based);
            if (actions != null) bubble.addView(actions);
        }

        return bubble;
    }

    @Nullable
    private static View buildAssistantAnswerActions(@NonNull OpenDroidPDFActivity activity,
                                                   @NonNull MuPDFReaderView docView,
                                                   @NonNull BottomSheetBehavior<?>[] behaviorHolder,
                                                   @NonNull String answerText,
                                                   @Nullable int[] citationPages1Based) {
        String text = answerText != null ? answerText.trim() : "";
        if (text.isEmpty()) return null;

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        containerLp.topMargin = dpToPx(activity, 10);
        container.setLayoutParams(containerLp);

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView copy = buildActionChip(activity, R.string.assistant_sheet_copy);
        copy.setOnClickListener(v -> {
            copyToClipboard(activity, "assistant_answer", text);
            try { activity.showInfo(activity.getString(R.string.assistant_sheet_copied)); } catch (Throwable ignore) {}
        });
        row1.addView(copy);

        TextView save = buildActionChip(activity, R.string.assistant_sheet_save_note);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.leftMargin = dpToPx(activity, 8);
        save.setLayoutParams(saveLp);
        save.setOnClickListener(v -> saveAssistantAnswerNoteAsync(activity, text, citationPages1Based, save));
        row1.addView(save);

        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = dpToPx(activity, 6);
        row2.setLayoutParams(row2Lp);

        TextView insert = buildActionChip(activity, R.string.assistant_sheet_insert_into_document);
        insert.setOnClickListener(v -> promptInsertTextIntoDocument(activity, docView, behaviorHolder, text, citationPages1Based));
        row2.addView(insert);

        TextView export = buildActionChip(activity, R.string.assistant_sheet_export);
        LinearLayout.LayoutParams exportLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        exportLp.leftMargin = dpToPx(activity, 8);
        export.setLayoutParams(exportLp);
        export.setOnClickListener(v -> shareAssistantAnswer(activity, text, citationPages1Based));
        row2.addView(export);

        container.addView(row1);
        container.addView(row2);
        return container;
    }

    @NonNull
    private static TextView buildActionChip(@NonNull Context ctx, int labelRes) {
        TextView chip = new TextView(ctx);
        chip.setText(labelRes);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setTextSize(12);
        chip.setBackgroundResource(R.drawable.bg_assistant_action_chip);
        int hPad = dpToPx(ctx, 10);
        int vPad = dpToPx(ctx, 6);
        chip.setPadding(hPad, vPad, hPad, vPad);
        chip.setClickable(true);
        chip.setFocusable(true);
        return chip;
    }

    private static void promptInsertTextIntoDocument(@NonNull OpenDroidPDFActivity activity,
                                                     @NonNull MuPDFReaderView docView,
                                                     @NonNull BottomSheetBehavior<?>[] behaviorHolder,
                                                     @NonNull String rawText,
                                                     @Nullable int[] citationPages1Based) {
        String text = rawText != null ? rawText.trim() : "";
        if (text.isEmpty()) return;

        if (citationPages1Based != null && citationPages1Based.length > 0) {
            String cites = formatCitationPagesInline(citationPages1Based);
            if (!cites.isEmpty()) text = text + "\n\nSources: " + cites;
        }

        int pageCount = safePageCount(docView);
        int current = safeSelectedPageIndex(docView);
        int defaultPageIndex = current;
        if (pageCount > 0) defaultPageIndex = Math.max(0, Math.min(pageCount - 1, defaultPageIndex));

        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_text_input, null, false);
        EditText input = content != null ? content.findViewById(R.id.dialog_text_input) : null;
        if (input != null) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setSingleLine();
            input.setBackgroundDrawable(null);
            input.setHint(activity.getString(R.string.assistant_sheet_insert_page_hint, Math.max(1, pageCount)));
            input.setText(String.valueOf(defaultPageIndex + 1));
            try { input.setSelectAllOnFocus(true); } catch (Throwable ignore) {}
        }

        final String insertTextFinal = text;
        final int pageCountFinal = pageCount;
        final int defaultPageIndexFinal = defaultPageIndex;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_sheet_insert_into_document)
                .setMessage(activity.getString(R.string.assistant_sheet_insert_instructions, Math.max(1, pageCount)))
                .setView(content)
                .setPositiveButton(R.string.assistant_sheet_insert_place, (d, which) -> {
                    int pageIndex = defaultPageIndexFinal;
                    try {
                        String s = input != null && input.getText() != null ? input.getText().toString() : "";
                        if (s != null) s = s.trim();
                        if (s != null && !s.isEmpty()) pageIndex = Integer.parseInt(s) - 1;
                    } catch (Throwable ignore) {
                    }
                    if (pageCountFinal > 0) pageIndex = Math.max(0, Math.min(pageCountFinal - 1, pageIndex));
                    else pageIndex = Math.max(0, pageIndex);

                    try { activity.setPendingTextAnnotationInsertText(insertTextFinal); } catch (Throwable ignore) {}

                    try { docView.setDisplayedViewIndex(pageIndex, true); } catch (Throwable ignore) {
                        try { docView.setDisplayedViewIndex(pageIndex); } catch (Throwable ignore2) {}
                    }

                    try {
                        BottomSheetBehavior<?> behavior = behaviorHolder[0];
                        if (behavior != null) behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    } catch (Throwable ignore) {
                    }

                    try { docView.requestMode(ReaderMode.ADDING_TEXT_ANNOT); } catch (Throwable ignore) {
                        try { docView.setMode(ReaderMode.ADDING_TEXT_ANNOT); } catch (Throwable ignore2) {}
                    }

                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_insert_tap_to_place)); } catch (Throwable ignore) {}
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        try { dialog.show(); } catch (Throwable ignore) {}
        try { if (input != null) input.requestFocus(); } catch (Throwable ignore) {}
    }

    private static int safePageCount(@NonNull MuPDFReaderView docView) {
        if (docView == null) return 0;
        try {
            Adapter adapter = docView.getAdapter();
            return adapter != null ? adapter.getCount() : 0;
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private static void saveAssistantAnswerNoteAsync(@NonNull OpenDroidPDFActivity activity,
                                                    @NonNull String answerText,
                                                    @Nullable int[] citationPages1Based,
                                                    @NonNull TextView chip) {
        String body = answerText != null ? answerText.trim() : "";
        if (body.isEmpty()) return;

        if (citationPages1Based != null && citationPages1Based.length > 0) {
            String cites = formatCitationPagesInline(citationPages1Based);
            if (!cites.isEmpty()) body = body + "\n\nSources: " + cites;
        }

        if (!chip.isEnabled()) return;
        chip.setEnabled(false);
        chip.setAlpha(0.5f);
        try { activity.showInfo(activity.getString(R.string.assistant_sheet_saving_note)); } catch (Throwable ignore) {}

        final String sourceTitle = safeCurrentDocumentTitle(activity);
        final String bodyFinal = body;
        executor.execute(() -> {
            File outFile = null;
            String error = null;
            try {
                outFile = AssistantNoteDocumentCreator.createAnswerNotePdf(activity, sourceTitle, bodyFinal);
            } catch (Throwable t) {
                error = t.getMessage();
                if (error == null || error.trim().isEmpty()) error = t.getClass().getSimpleName();
            }
            final File outFinal = outFile;
            final String errFinal = error;
            activity.runOnUiThread(() -> {
                chip.setEnabled(true);
                chip.setAlpha(1f);
                if (isActivityInvalid(activity)) return;

                if (errFinal != null) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_save_note_failed, errFinal)); } catch (Throwable ignore) {}
                    return;
                }
                if (outFinal == null) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_save_note_failed, activity.getString(R.string.assistant_sheet_unknown_error))); } catch (Throwable ignore) {}
                    return;
                }

                try { activity.showInfo(activity.getString(R.string.assistant_sheet_saved_note, outFinal.getName())); } catch (Throwable ignore) {}
                try {
                    Uri uri = Uri.fromFile(outFinal);
                    Intent intent = DocumentViewerIntents.viewInApp(activity, uri, outFinal.getName());
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    activity.startActivity(intent);
                } catch (Throwable t) {
                    try { activity.showInfo(t.getMessage()); } catch (Throwable ignore) {}
                }
            });
        });
    }

    private static void shareAssistantAnswer(@NonNull OpenDroidPDFActivity activity,
                                            @NonNull String answerText,
                                            @Nullable int[] citationPages1Based) {
        String text = answerText != null ? answerText.trim() : "";
        if (text.isEmpty()) return;
        if (citationPages1Based != null && citationPages1Based.length > 0) {
            String cites = formatCitationPagesInline(citationPages1Based);
            if (!cites.isEmpty()) text = text + "\n\nSources: " + cites;
        }
        String subject = "Assistant answer";
        String title = safeCurrentDocumentTitle(activity);
        if (title != null && !title.trim().isEmpty()) subject = subject + " — " + title.trim();

        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
            intent.putExtra(Intent.EXTRA_TEXT, text);
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_with)));
        } catch (Throwable t) {
            try { activity.showInfo(t.getMessage()); } catch (Throwable ignore) {}
        }
    }

    private static void restoreAskTranscript(@NonNull OpenDroidPDFActivity activity,
                                            @NonNull String documentKey,
                                            @Nullable LinearLayout chatContainer,
                                            @Nullable ScrollView chatScroll,
                                            boolean showSources,
                                            @NonNull BottomSheetBehavior<?>[] behaviorHolder,
                                            @NonNull MuPDFReaderView docView) {
        if (chatContainer == null) return;
        List<AssistantAskTranscriptStore.Message> messages = AssistantAskTranscriptStore.snapshot(documentKey);
        if (messages.isEmpty()) return;
        for (AssistantAskTranscriptStore.Message m : messages) {
            chatContainer.addView(buildChatBubble(activity, m.text, m.isUser, !m.isUser, m.citationNumbers, m.citationPages1Based, showSources, behaviorHolder, docView));
        }
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private static void clearAskChat(@NonNull String documentKey,
                                    @Nullable LinearLayout chatContainer,
                                    @Nullable View clearChatButton) {
        AssistantAskTranscriptStore.clear(documentKey);
        if (chatContainer != null) chatContainer.removeAllViews();
        updateClearChatEnabled(clearChatButton, documentKey);
    }

    private static void updateClearChatEnabled(@Nullable View clearChatButton, @NonNull String documentKey) {
        if (clearChatButton == null) return;
        try { clearChatButton.setEnabled(AssistantAskTranscriptStore.hasMessages(documentKey)); } catch (Throwable ignore) {}
    }

    @NonNull
    private static List<AssistantLlmClient.ChatMessage> boundedAskChatHistory(@NonNull String documentKey) {
        List<AssistantAskTranscriptStore.Message> messages = AssistantAskTranscriptStore.snapshot(documentKey);
        if (messages.isEmpty()) return Collections.emptyList();

        ArrayList<AssistantLlmClient.ChatMessage> reversed = new ArrayList<>();
        int chars = 0;
        for (int i = messages.size() - 1; i >= 0 && reversed.size() < MAX_ASK_HISTORY_MESSAGES; i--) {
            AssistantAskTranscriptStore.Message m = messages.get(i);
            String base = m != null && m.text != null ? m.text.trim() : "";
            if (base.isEmpty()) continue;

            String content = base;
            if (m != null && !m.isUser && m.citationPages1Based != null && m.citationPages1Based.length > 0) {
                String cites = formatCitationPagesInline(m.citationPages1Based);
                if (!cites.isEmpty()) content = content + "\n\nSources: " + cites;
            }

            if (content.length() > MAX_ASK_HISTORY_CHARS && reversed.isEmpty()) {
                content = content.substring(0, Math.max(0, MAX_ASK_HISTORY_CHARS));
            }
            if (!reversed.isEmpty() && (chars + content.length()) > MAX_ASK_HISTORY_CHARS) break;
            if (content.trim().isEmpty()) continue;

            chars += content.length();
            reversed.add(m != null && m.isUser
                    ? AssistantLlmClient.ChatMessage.user(content)
                    : AssistantLlmClient.ChatMessage.assistant(content));
        }

        if (reversed.isEmpty()) return Collections.emptyList();
        Collections.reverse(reversed);
        return reversed;
    }

    @NonNull
    private static String formatAskOutgoingPreview(@NonNull String question,
                                                   @NonNull String contextText,
                                                   @NonNull List<AssistantLlmClient.ChatMessage> chatHistory) {
        StringBuilder sb = new StringBuilder();
        if (chatHistory != null && !chatHistory.isEmpty()) {
            sb.append("CHAT HISTORY:\n");
            for (int i = 0; i < chatHistory.size(); i++) {
                AssistantLlmClient.ChatMessage m = chatHistory.get(i);
                if (m == null) continue;
                String role = m.role != null ? m.role.trim() : "";
                String content = m.content != null ? m.content.trim() : "";
                if (content.isEmpty()) continue;
                sb.append("assistant".equals(role) ? "ASSISTANT" : "USER")
                        .append(":\n")
                        .append(content)
                        .append("\n\n");
            }
            sb.append("\n");
        }
        sb.append("QUESTION:\n").append(question).append("\n\nCONTEXT:\n").append(contextText);
        return sb.toString();
    }

    @NonNull
    private static String formatCitationPagesInline(@NonNull int[] pages1Based) {
        if (pages1Based.length <= 0) return "";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(pages1Based.length, 12);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append("p. ").append(pages1Based[i]);
        }
        if (pages1Based.length > n) sb.append(", …");
        return sb.toString();
    }

    private static void applySourcesVisibility(@NonNull View root, boolean visible) {
        Object tag = root.getTag();
        if ("assistant_sources_row".equals(tag)) {
            root.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applySourcesVisibility(vg.getChildAt(i), visible);
            }
        }
    }

    private static void showDocumentPreviewAndAskAsync(@NonNull OpenDroidPDFActivity activity,
                                                       @NonNull SharedPreferences prefs,
                                                       @Nullable MuPdfRepository repo,
                                                       @NonNull MuPDFReaderView docView,
                                                       @NonNull String documentKey,
                                                       @NonNull String question,
                                                       @NonNull AssistantLlmProviderConfig provider,
                                                       @NonNull String apiKey,
                                                       @Nullable LinearLayout chatContainer,
                                                       @Nullable ScrollView chatScroll,
                                                       @Nullable View clearChatButton,
                                                       boolean showSources,
                                                       @NonNull BottomSheetBehavior<?>[] behaviorHolder) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_sheet_preview_title)
                .setMessage(R.string.assistant_context_loading)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> cancelled.set(true))
                .create();
        progress.show();

        executor.execute(() -> {
            AssistantContextTextExtractor.TextResult result =
                    AssistantContextTextExtractor.documentText(repo, MAX_PREVIEW_CHARS, cancelled);

            activity.runOnUiThread(() -> {
                try { progress.dismiss(); } catch (Throwable ignore) {}
                if (cancelled.get()) return;
                if (isActivityInvalid(activity)) return;

                int pageIndex = safeSelectedPageIndex(docView);
                String ctx = result.text != null ? result.text : "";
                String previewSummary = describeScope(activity, Scope.DOCUMENT, pageIndex, ctx.length(), result.truncated) + " • Ask";
                final List<AssistantLlmClient.ChatMessage> chatHistory = boundedAskChatHistory(documentKey);
                String outgoing = formatAskOutgoingPreview(question, ctx, chatHistory);
                runWithPrivacyGate(activity, prefs, provider, previewSummary, outgoing, R.string.assistant_sheet_send, () -> {
                    if (chatContainer == null) return;
                    final long transcriptVersion = AssistantAskTranscriptStore.appendUser(documentKey, question);
                    updateClearChatEnabled(clearChatButton, documentKey);

                    chatContainer.addView(buildChatBubble(activity, question, true, false, null, null, showSources, behaviorHolder, docView));
                    View pending = buildChatBubble(activity, activity.getString(R.string.assistant_sheet_generating), false, false, null, null, showSources, behaviorHolder, docView);
                    chatContainer.addView(pending);
                    if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));

                    final String ctxFinal = ctx;
                    executor.execute(() -> {
                        AssistantLlmClient.AskResult askResult;
                        try {
                            askResult = AssistantLlmClient.askBlocking(http, provider, apiKey, question, ctxFinal, chatHistory);
                        } catch (Throwable t) {
                            String msg = t.getMessage();
                            if (msg == null || msg.trim().isEmpty()) msg = t.getClass().getSimpleName();
                            askResult = AssistantLlmClient.AskResult.plainText(msg);
                        }
                        final AssistantLlmClient.AskResult resultFinal = askResult;
                        if (AssistantAskTranscriptStore.isVersion(documentKey, transcriptVersion)) {
                            AssistantAskTranscriptStore.appendAssistant(documentKey, resultFinal.answerText, resultFinal.citationNumbers, resultFinal.citationPages1Based);
                        }
                        activity.runOnUiThread(() -> {
                            if (!AssistantAskTranscriptStore.isVersion(documentKey, transcriptVersion)) return;
                            if (chatContainer == null || !chatContainer.isAttachedToWindow()) return;
                            if (chatContainer.indexOfChild(pending) >= 0) chatContainer.removeView(pending);
                            chatContainer.addView(buildChatBubble(activity,
                                    resultFinal.answerText,
                                    false,
                                    true,
                                    resultFinal.citationNumbers,
                                    resultFinal.citationPages1Based,
                                    showSources,
                                    behaviorHolder,
                                    docView));
                            updateClearChatEnabled(clearChatButton, documentKey);
                            if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                        });
                    });
                });
            });
        });
    }

    private static boolean isAssistantEnabled(@NonNull SharedPreferences prefs) {
        return safeGetBoolean(prefs, SettingsActivity.PREF_ASSISTANT_ENABLED, false);
    }

    private static boolean isWifiOnly(@NonNull SharedPreferences prefs) {
        return safeGetBoolean(prefs, SettingsActivity.PREF_ASSISTANT_WIFI_ONLY, false);
    }

    private static boolean safeGetBoolean(@NonNull SharedPreferences prefs, @NonNull String key, boolean fallback) {
        try {
            return prefs.getBoolean(key, fallback);
        } catch (ClassCastException e) {
            try { prefs.edit().remove(key).apply(); } catch (Throwable ignore) {}
            return fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static boolean isOnWifi(@NonNull Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (Throwable ignore) {
            return true;
        }
    }

    private static void copyToClipboard(@NonNull Context context, @NonNull String label, @NonNull String text) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
        } catch (Throwable ignore) {
        }
    }

    private static int dpToPx(@NonNull Context ctx, int dp) {
        float density = 1f;
        try { density = ctx.getResources().getDisplayMetrics().density; } catch (Throwable ignore) { density = 1f; }
        return Math.max(1, Math.round(dp * density));
    }
}
