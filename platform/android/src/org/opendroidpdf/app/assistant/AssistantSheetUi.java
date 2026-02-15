package org.opendroidpdf.app.assistant;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
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
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.OutlineItem;
import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.document.DocumentViewerIntents;
import org.opendroidpdf.app.document.DocumentAccessIntents;
import org.opendroidpdf.app.document.DocumentType;
import org.opendroidpdf.app.epub.EpubTocParser;
import org.opendroidpdf.app.helpers.RequestCodes;
import org.opendroidpdf.app.preferences.PreferencesNames;
import org.opendroidpdf.app.readaloud.ReadAloudController;
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
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.OkHttpClient;

public final class AssistantSheetUi {
    private static final float PEEK_RATIO = 0.20f;
    private static final float HALF_RATIO = 0.60f;
    private static final float EXPANDED_OFFSET_RATIO = 0.20f; // 80% height.

    private static final int MAX_PREVIEW_CHARS = 25_000;
    private static final int MAX_ATTACHMENTS_CONTEXT_CHARS = 8_000;
    private static final int MAX_ASK_HISTORY_MESSAGES = 12;
    private static final int MAX_ASK_HISTORY_CHARS = 4_000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final OkHttpClient http = new OkHttpClient();
    private static final WeakHashMap<OpenDroidPDFActivity, BottomSheetDialog> openDialogs = new WeakHashMap<>();
    private static final WeakHashMap<OpenDroidPDFActivity, SessionApproval> sessionApprovals = new WeakHashMap<>();
    private static final WeakHashMap<OpenDroidPDFActivity, AttachmentsUiHandle> attachmentsUiHandles = new WeakHashMap<>();
    private static final WeakHashMap<OpenDroidPDFActivity, ReadAloudUiHandle> readAloudUiHandles = new WeakHashMap<>();

    private enum Scope { SELECTION, PAGE, TOC_SECTION, DOCUMENT }

    private static final class AttachmentsUiHandle {
        @NonNull final String documentKey;
        @Nullable final View scroll;
        @Nullable final LinearLayout container;

        AttachmentsUiHandle(@NonNull String documentKey, @Nullable View scroll, @Nullable LinearLayout container) {
            this.documentKey = documentKey;
            this.scroll = scroll;
            this.container = container;
        }
    }

    private static final class ReadAloudUiHandle {
        @NonNull final String documentKey;
        @Nullable final TextView status;
        @Nullable final TextView nowReading;
        @Nullable final TextView excerpt;
        @Nullable final Button playPause;
        @Nullable final Button stop;

        ReadAloudUiHandle(@NonNull String documentKey,
                          @Nullable TextView status,
                          @Nullable TextView nowReading,
                          @Nullable TextView excerpt,
                          @Nullable Button playPause,
                          @Nullable Button stop) {
            this.documentKey = documentKey;
            this.status = status;
            this.nowReading = nowReading;
            this.excerpt = excerpt;
            this.playPause = playPause;
            this.stop = stop;
        }
    }

    private static final class TocSectionScope {
        @Nullable final String title;
        final int startPageIndex;
        final int endPageIndex;

        TocSectionScope(@Nullable String title, int startPageIndex, int endPageIndex) {
            this.title = title != null ? title.trim() : null;
            this.startPageIndex = Math.max(0, startPageIndex);
            this.endPageIndex = Math.max(this.startPageIndex, endPageIndex);
        }
    }

    private static final class SheetPreset {
        final int initialModeCheckedId;
        @NonNull final Scope initialScope;
        @Nullable final TocSectionScope tocScope;

        SheetPreset(int initialModeCheckedId, @NonNull Scope initialScope, @Nullable TocSectionScope tocScope) {
            this.initialModeCheckedId = initialModeCheckedId;
            this.initialScope = initialScope != null ? initialScope : Scope.PAGE;
            this.tocScope = tocScope;
        }
    }

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
        show(activity, null);
    }

    public static void showAskForSelection(@NonNull OpenDroidPDFActivity activity) {
        show(activity, new SheetPreset(
                R.id.assistant_sheet_mode_ask,
                Scope.SELECTION,
                null));
    }

    public static void showSummaryForSelection(@NonNull OpenDroidPDFActivity activity) {
        show(activity, new SheetPreset(
                R.id.assistant_sheet_mode_summary,
                Scope.SELECTION,
                null));
    }

    public static void showSummaryForTocSection(@NonNull OpenDroidPDFActivity activity,
                                                @Nullable String sectionTitle,
                                                int startPageIndex,
                                                int endPageIndex) {
        show(activity, new SheetPreset(
                R.id.assistant_sheet_mode_summary,
                Scope.TOC_SECTION,
                new TocSectionScope(sectionTitle, startPageIndex, endPageIndex)));
    }

    private static void show(@NonNull OpenDroidPDFActivity activity, @Nullable SheetPreset preset) {
        if (activity == null) return;

        dismissIfOpen(activity);

        final MuPDFReaderView docView = activity.getDocView();
        final MuPdfRepository repo = activity.getRepository();
        if (docView == null) {
            try { activity.showInfo("Open a document first."); } catch (Throwable ignore) {}
            return;
        }
        final String documentKey = currentDocumentSessionKey(activity);
        final AtomicReference<Call> askActiveCall = new AtomicReference<>(null);
        final AtomicBoolean askStopRequested = new AtomicBoolean(false);
        final AtomicReference<View> askPendingBubble = new AtomicReference<>(null);
        final AtomicReference<Call> summaryActiveCall = new AtomicReference<>(null);
        final AtomicBoolean summaryStopRequested = new AtomicBoolean(false);

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        final View root = LayoutInflater.from(activity).inflate(R.layout.dialog_assistant_sheet, null);
        dialog.setContentView(root);
        openDialogs.put(activity, dialog);
        dialog.setOnDismissListener(d -> {
            try {
                Call c = askActiveCall.get();
                if (c != null) c.cancel();
            } catch (Throwable ignore) {}
            try {
                Call c = summaryActiveCall.get();
                if (c != null) c.cancel();
            } catch (Throwable ignore) {}
            openDialogs.remove(activity);
            attachmentsUiHandles.remove(activity);
            readAloudUiHandles.remove(activity);
        });

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
            if (preset != null && preset.initialModeCheckedId != 0) {
                try { modeGroup.check(preset.initialModeCheckedId); } catch (Throwable ignore) {}
            }
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

        final View attachmentsScroll = root.findViewById(R.id.assistant_sheet_attachments_scroll);
        final LinearLayout attachmentsContainer = root.findViewById(R.id.assistant_sheet_attachments_container);
        if (attachmentsScroll != null && attachmentsContainer != null) {
            attachmentsUiHandles.put(activity, new AttachmentsUiHandle(documentKey, attachmentsScroll, attachmentsContainer));
            renderAttachmentsRow(activity, documentKey, attachmentsScroll, attachmentsContainer);
        }

        final TextView readStatus = root.findViewById(R.id.assistant_sheet_read_aloud_status);
        final TextView readNowReading = root.findViewById(R.id.assistant_sheet_read_aloud_now_reading);
        final TextView readExcerpt = root.findViewById(R.id.assistant_sheet_read_aloud_excerpt);
        final Button readPlayPause = root.findViewById(R.id.assistant_sheet_read_aloud_play_pause);
        final Button readStop = root.findViewById(R.id.assistant_sheet_read_aloud_stop);
        readAloudUiHandles.put(activity, new ReadAloudUiHandle(documentKey, readStatus, readNowReading, readExcerpt, readPlayPause, readStop));

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
                try {
                    android.view.MenuItem clearAttachments = popup.getMenu().findItem(R.id.assistant_sheet_action_clear_attachments);
                    if (clearAttachments != null) {
                        clearAttachments.setVisible(AssistantAttachmentsStore.count(documentKey) > 0);
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
                    if (id == R.id.assistant_sheet_action_clear_attachments) {
                        AssistantAttachmentsStore.clear(documentKey);
                        if (attachmentsScroll != null && attachmentsContainer != null) {
                            renderAttachmentsRow(activity, documentKey, attachmentsScroll, attachmentsContainer);
                        }
                        try { activity.showInfo(activity.getString(R.string.assistant_sheet_attachments_cleared)); } catch (Throwable ignore) {}
                        return true;
                    }
                    if (id == R.id.assistant_sheet_action_voice_assistant) {
                        try {
                            RadioGroup sg = root.findViewById(R.id.assistant_sheet_scope_group);
                            Scope scope = currentScope(sg);
                            AssistantContextSnapshot snap = buildVoiceContextSnapshot(activity, repo, docView, scope, preset != null ? preset.tocScope : null);
                            AssistantContextStore.set(snap);
                            Intent voice = new Intent(activity, AssistantActivity.class);
                            voice.putExtra(AssistantActivity.EXTRA_RETURN_TRANSCRIPT, false);
                            voice.putExtra(AssistantActivity.EXTRA_AUTO_START_RECORDING, true);
                            activity.startActivity(voice);
                            try { dialog.dismiss(); } catch (Throwable ignore) {}
                        } catch (Throwable t) {
                            try { activity.showInfo(t.getMessage()); } catch (Throwable ignore) {}
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
        final RadioButton scopeToc = root.findViewById(R.id.assistant_sheet_scope_toc_section);
        final RadioButton scopeDoc = root.findViewById(R.id.assistant_sheet_scope_document);

        final String selectionText = AssistantContextTextExtractor.selectionTextOrNull(docView, repo, activity.getSelectedPageView());
        final boolean hasSelection = selectionText != null && !selectionText.trim().isEmpty();
        if (scopeSelection != null) {
            scopeSelection.setEnabled(hasSelection);
        }

        final TocSectionScope tocScopeForUi = resolveTocSectionScopeOrNull(activity, docView, preset != null ? preset.tocScope : null);
        if (scopeToc != null) {
            scopeToc.setVisibility(tocScopeForUi != null ? View.VISIBLE : View.GONE);
            scopeToc.setEnabled(tocScopeForUi != null);
        }

        final SharedPreferences prefs =
                activity.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
        final boolean allowWhole = safeGetBoolean(prefs, SettingsActivity.PREF_ASSISTANT_ALLOW_WHOLE_DOCUMENT, false);
        if (scopeDoc != null) {
            scopeDoc.setEnabled(allowWhole);
        }

        // Apply initial mode/scope (with sensible fallbacks).
        Scope desired = preset != null ? preset.initialScope : null;
        if (desired == Scope.SELECTION && !hasSelection) desired = null;
        if (desired == Scope.DOCUMENT && !allowWhole) desired = null;
        if (desired == Scope.TOC_SECTION && tocScopeForUi == null) desired = null;

        if (desired == Scope.SELECTION && scopeSelection != null) {
            scopeSelection.setChecked(true);
        } else if (desired == Scope.TOC_SECTION && scopeToc != null) {
            scopeToc.setChecked(true);
        } else if (desired == Scope.DOCUMENT && scopeDoc != null) {
            scopeDoc.setChecked(true);
        } else if (hasSelection && scopeSelection != null) {
            scopeSelection.setChecked(true);
        } else if (scopePage != null) {
            scopePage.setChecked(true);
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
            preview.setOnClickListener(v -> showPreviewDialog(activity, repo, docView, currentScope(scopeGroup), preset != null ? preset.tocScope : null));
        }

        // Prompt row actions.
        ImageButton attach = root.findViewById(R.id.assistant_sheet_attach);
        if (attach != null) {
            attach.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                            DocumentAccessIntents.MIME_PDF,
                            DocumentAccessIntents.MIME_EPUB,
                    });
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    activity.startActivityForResult(intent, RequestCodes.ASSISTANT_ATTACH_DOCUMENTS);
                } catch (Throwable t) {
                    try { activity.showInfo(t.getMessage()); } catch (Throwable ignore) {}
                }
            });
        }

        ImageButton mic = root.findViewById(R.id.assistant_sheet_mic);
        if (mic != null) {
            mic.setOnClickListener(v -> {
                try {
                    AssistantContextSnapshot snap = buildVoiceContextSnapshot(activity, repo, docView, currentScope(scopeGroup), preset != null ? preset.tocScope : null);
                    AssistantContextStore.set(snap);
                    Intent voice = new Intent(activity, AssistantActivity.class);
                    voice.putExtra(AssistantActivity.EXTRA_RETURN_TRANSCRIPT, true);
                    voice.putExtra(AssistantActivity.EXTRA_AUTO_START_RECORDING, true);
                    activity.startActivityForResult(voice, RequestCodes.ASSISTANT_VOICE_PROMPT);
                } catch (Throwable t) {
                    try { activity.showInfo(t.getMessage()); } catch (Throwable ignore) {}
                }
            });
        }

        EditText prompt = root.findViewById(R.id.assistant_sheet_prompt);
        Button send = root.findViewById(R.id.assistant_sheet_send);
        ImageButton stopAsk = root.findViewById(R.id.assistant_sheet_stop);
        if (stopAsk != null) {
            stopAsk.setVisibility(View.GONE);
            stopAsk.setEnabled(false);
            stopAsk.setOnClickListener(v -> requestAskStop(activity, askActiveCall, askStopRequested, askPendingBubble, stopAsk));
        }

        if (send != null && prompt != null) {
            send.setOnClickListener(v -> {
                if (askActiveCall.get() != null) {
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_generating)); } catch (Throwable ignore) {}
                    return;
                }

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
                    showDocumentPreviewAndAskAsync(activity, prefs, repo, docView, documentKey, question, currentProvider, apiKey, chatContainer, chatScroll, clearChat, showSources.get(), behaviorHolder, prompt, send, stopAsk, askActiveCall, askStopRequested, askPendingBubble);
                    return;
                }
                if (scope == Scope.TOC_SECTION) {
                    TocSectionScope tocScope = resolveTocSectionScopeOrNull(activity, docView, preset != null ? preset.tocScope : null);
                    if (tocScope == null) {
                        try { activity.showInfo(activity.getString(R.string.assistant_sheet_no_toc)); } catch (Throwable ignore) {}
                        return;
                    }
                    showTocSectionPreviewAndAskAsync(activity, prefs, repo, docView, documentKey, question, currentProvider, apiKey, chatContainer, chatScroll, clearChat, showSources.get(), behaviorHolder, tocScope, prompt, send, stopAsk, askActiveCall, askStopRequested, askPendingBubble);
                    return;
                }

                int attachmentsBudget = askAttachmentsBudgetChars(documentKey);
                if (attachmentsBudget > 0) {
                    int mainBudget = Math.max(1, MAX_PREVIEW_CHARS - attachmentsBudget);
                    if (scope == Scope.SELECTION) {
                        String sel = AssistantContextTextExtractor.selectionTextOrNull(docView, repo, activity.getSelectedPageView());
                        if (sel == null || sel.trim().isEmpty()) {
                            try { activity.showInfo(activity.getString(R.string.assistant_sheet_no_selection)); } catch (Throwable ignore) {}
                            return;
                        }
                        String main = "Page " + (pageIndex + 1) + ":\n" + sel;
                        boolean mainTruncated = false;
                        if (main.length() > mainBudget) {
                            main = main.substring(0, mainBudget);
                            mainTruncated = true;
                        }
                        showPreviewAndAskWithAttachmentsAsync(activity, prefs, docView, documentKey, question, currentProvider, apiKey, chatContainer, chatScroll, clearChat, showSources.get(), behaviorHolder, prompt, scope, pageIndex, main, mainTruncated, attachmentsBudget, send, stopAsk, askActiveCall, askStopRequested, askPendingBubble);
                        return;
                    } else {
                        String header = "Page " + (pageIndex + 1) + ":\n";
                        int pageBudget = Math.max(1, mainBudget - header.length());
                        AssistantContextTextExtractor.TextResult page = AssistantContextTextExtractor.pageText(repo, pageIndex, pageBudget);
                        String main = header + (page.text != null ? page.text : "");
                        boolean mainTruncated = page.truncated;
                        showPreviewAndAskWithAttachmentsAsync(activity, prefs, docView, documentKey, question, currentProvider, apiKey, chatContainer, chatScroll, clearChat, showSources.get(), behaviorHolder, prompt, scope, pageIndex, main, mainTruncated, attachmentsBudget, send, stopAsk, askActiveCall, askStopRequested, askPendingBubble);
                        return;
                    }
                }

                final String ctxText;
                final boolean truncated;
                if (scope == Scope.SELECTION) {
                    String sel = AssistantContextTextExtractor.selectionTextOrNull(docView, repo, activity.getSelectedPageView());
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

                String previewSummary = describeScope(activity, scope, pageIndex, ctxText.length(), truncated, null) + " • Ask";
                final List<AssistantLlmClient.ChatMessage> chatHistory = boundedAskChatHistory(documentKey);
                String outgoing = formatAskOutgoingPreview(question, ctxText, chatHistory);
                runWithPrivacyGate(activity, prefs, currentProvider, previewSummary, outgoing, R.string.assistant_sheet_send, () -> {
                    prompt.setText("");
                    if (chatContainer == null) return;
                    runAskRequestAsync(activity,
                            documentKey,
                            question,
                            currentProvider,
                            apiKey,
                            ctxText,
                            chatHistory,
                            chatContainer,
                            chatScroll,
                            clearChat,
                            showSources.get(),
                            behaviorHolder,
                            docView,
                            send,
                            stopAsk,
                            askActiveCall,
                            askStopRequested,
                            askPendingBubble);
                });
            });
        }

        // Summary mode (Selection / Page / TOC section).
        RadioGroup summaryStyleGroup = root.findViewById(R.id.assistant_sheet_summary_style_group);
        Button summaryGenerate = root.findViewById(R.id.assistant_sheet_summary_generate);
        ImageButton summaryStop = root.findViewById(R.id.assistant_sheet_summary_stop);
        TextView summaryStatus = root.findViewById(R.id.assistant_sheet_summary_status);
        TextView summaryOutput = root.findViewById(R.id.assistant_sheet_summary_output);
        Button summaryCopy = root.findViewById(R.id.assistant_sheet_summary_copy);
        Button summaryExport = root.findViewById(R.id.assistant_sheet_summary_export);
        Button summarySaveNote = root.findViewById(R.id.assistant_sheet_summary_save_note);
        Button summaryInsert = root.findViewById(R.id.assistant_sheet_summary_insert_into_document);
        final AtomicBoolean noteSaveInFlight = new AtomicBoolean(false);
        final AtomicBoolean exportInFlight = new AtomicBoolean(false);
        if (summaryStop != null) {
            summaryStop.setVisibility(View.GONE);
            summaryStop.setEnabled(false);
            summaryStop.setOnClickListener(v -> requestSummaryStop(activity, summaryActiveCall, summaryStopRequested, summaryStatus, summaryStop));
        }
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
                AssistantLlmClient.SummaryStyle styleTmp = AssistantLlmClient.SummaryStyle.MEDIUM;
                if (summaryStyleGroup != null) {
                    int checked = summaryStyleGroup.getCheckedRadioButtonId();
                    if (checked == R.id.assistant_sheet_summary_style_short) styleTmp = AssistantLlmClient.SummaryStyle.SHORT;
                    else if (checked == R.id.assistant_sheet_summary_style_detailed) styleTmp = AssistantLlmClient.SummaryStyle.DETAILED;
                }
                final AssistantLlmClient.SummaryStyle styleFinal = styleTmp;

                int pageIndex = safeSelectedPageIndex(docView);
                if (scope == Scope.DOCUMENT) {
                    showWholeDocumentSummarySafetyThenPreviewAndSummarizeAsync(
                            activity,
                            prefs,
                            repo,
                            docView,
                            currentProvider,
                            apiKey,
                            styleFinal,
                            summaryInFlight,
                            summaryStatus,
                            summaryOutput,
                            summaryGenerate,
                            summaryStop,
                            summaryActiveCall,
                            summaryStopRequested,
                            summaryCopy,
                            summaryInsert,
                            summarySaveNote,
                            summaryExport,
                            noteSaveInFlight,
                            exportInFlight);
                    return;
                }
                if (scope == Scope.TOC_SECTION) {
                    TocSectionScope tocScope = resolveTocSectionScopeOrNull(activity, docView, preset != null ? preset.tocScope : null);
                    if (tocScope == null) {
                        try { activity.showInfo(activity.getString(R.string.assistant_sheet_no_toc)); } catch (Throwable ignore) {}
                        return;
                    }
                    showTocSectionPreviewAndSummarizeAsync(activity, prefs, repo, docView, currentProvider, apiKey, tocScope, styleFinal, summaryInFlight, summaryStatus, summaryOutput, summaryGenerate, summaryStop, summaryActiveCall, summaryStopRequested, summaryCopy, summaryInsert, summarySaveNote, summaryExport, noteSaveInFlight, exportInFlight);
                    return;
                }

                String scopeText;
                boolean truncated;
                if (scope == Scope.SELECTION) {
                    String sel = AssistantContextTextExtractor.selectionTextOrNull(docView, repo, activity.getSelectedPageView());
                    if (sel == null || sel.trim().isEmpty()) {
                        try { activity.showInfo(activity.getString(R.string.assistant_sheet_no_selection)); } catch (Throwable ignore) {}
                        return;
                    }
                    scopeText = sel;
                    truncated = false;
                } else {
                    AssistantContextTextExtractor.TextResult page = AssistantContextTextExtractor.pageText(repo, pageIndex, MAX_PREVIEW_CHARS);
                    scopeText = page.text != null ? page.text : "";
                    truncated = page.truncated;
                }

                final String outgoingText = scopeText;
                String previewSummary = describeScope(activity, scope, pageIndex, outgoingText.length(), truncated, null) + " • Summary";
                runWithPrivacyGate(activity, prefs, currentProvider, previewSummary, outgoingText, R.string.assistant_sheet_generate, () ->
                        runSummaryRequestAsync(activity, currentProvider, apiKey, outgoingText, styleFinal, summaryInFlight, summaryStatus, summaryOutput, summaryGenerate, summaryStop, summaryActiveCall, summaryStopRequested, summaryCopy, summaryInsert, summarySaveNote, summaryExport, noteSaveInFlight, exportInFlight));
            });
        }

        if (readPlayPause != null) {
            readPlayPause.setOnClickListener(v -> {
                try {
                    if (activity.isReadAloudActive()) {
                        activity.toggleReadAloudPlayPause();
                    } else {
                        startReadAloudForScope(activity, docView, currentScope(scopeGroup), preset != null ? preset.tocScope : null);
                    }
                } catch (Throwable ignore) {}
            });
        }
        if (readStop != null) {
            readStop.setOnClickListener(v -> {
                try { activity.stopReadAloudIfActive(); } catch (Throwable ignore) {}
            });
        }

        updateReadAloudUi(activity, activity.readAloudCursorOrNull());

        dialog.show();
    }

    public static void updateReadAloudUi(@NonNull OpenDroidPDFActivity activity,
                                         @Nullable ReadAloudController.Cursor cursor) {
        if (activity == null) return;
        ReadAloudUiHandle h = readAloudUiHandles.get(activity);
        if (h == null) return;
        if (!currentDocumentSessionKey(activity).equals(h.documentKey)) return;

        boolean active = cursor != null && cursor.active;
        boolean playing = cursor != null && cursor.playing;
        int pageIndex = cursor != null ? cursor.pageIndex : -1;
        String text = cursor != null ? cursor.text : null;

        if (h.playPause != null) {
            int label;
            if (!active) label = R.string.read_aloud_play;
            else if (playing) label = R.string.read_aloud_pause;
            else label = R.string.read_aloud_play;
            h.playPause.setText(label);
        }
        if (h.stop != null) {
            h.stop.setEnabled(active);
            h.stop.setAlpha(active ? 1f : 0.6f);
        }

        if (h.status != null) {
            if (!active) {
                h.status.setText(R.string.assistant_sheet_read_aloud_status_ready);
            } else if (pageIndex >= 0) {
                h.status.setText(activity.getString(playing ? R.string.assistant_sheet_read_aloud_status_playing : R.string.assistant_sheet_read_aloud_status_paused, pageIndex + 1));
            } else {
                h.status.setText(R.string.assistant_sheet_read_aloud_status_ready);
            }
        }

        if (h.nowReading != null) {
            if (active && pageIndex >= 0) {
                h.nowReading.setText(activity.getString(R.string.assistant_sheet_read_aloud_now_reading_page, pageIndex + 1));
            } else {
                h.nowReading.setText(R.string.assistant_sheet_read_aloud_now_reading_unknown);
            }
        }

        if (h.excerpt != null) {
            String trimmed = text != null ? text.trim() : "";
            if (active && !trimmed.isEmpty()) h.excerpt.setText(trimmed);
            else h.excerpt.setText(R.string.assistant_sheet_read_aloud_excerpt_empty);
        }
    }

    private static void startReadAloudForScope(@NonNull OpenDroidPDFActivity activity,
                                              @NonNull MuPDFReaderView docView,
                                              @NonNull Scope scope,
                                              @Nullable TocSectionScope presetTocScope) {
        if (activity == null || docView == null) return;
        if (scope == Scope.SELECTION) {
            String sel = AssistantContextTextExtractor.selectionTextOrNull(docView, activity.getRepository(), activity.getSelectedPageView());
            if (sel == null || sel.trim().isEmpty()) {
                try { activity.showInfo(activity.getString(R.string.assistant_sheet_no_selection)); } catch (Throwable ignore) {}
                return;
            }
            activity.startReadAloudFromSelection();
            return;
        }
        if (scope == Scope.TOC_SECTION) {
            TocSectionScope tocScope = resolveTocSectionScopeOrNull(activity, docView, presetTocScope);
            if (tocScope == null) {
                try { activity.showInfo(activity.getString(R.string.assistant_sheet_no_toc)); } catch (Throwable ignore) {}
                return;
            }
            activity.startReadAloudFromPageRange(tocScope.startPageIndex, tocScope.endPageIndex);
            return;
        }
        if (scope == Scope.DOCUMENT) {
            Adapter adapter = null;
            try { adapter = docView.getAdapter(); } catch (Throwable ignore) { adapter = null; }
            int count = adapter != null ? adapter.getCount() : 0;
            int end = Math.max(0, count - 1);
            activity.startReadAloudFromPageRange(0, end);
            return;
        }

        int pageIndex = safeSelectedPageIndex(docView);
        activity.startReadAloudFromPage(pageIndex);
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
        if (checked == R.id.assistant_sheet_scope_toc_section) return Scope.TOC_SECTION;
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

    private static final class TocEntry {
        final int level;
        @NonNull final String title;
        final int pageIndex;

        TocEntry(int level, @Nullable String title, int pageIndex) {
            this.level = Math.max(0, level);
            this.title = title != null ? title : "";
            this.pageIndex = Math.max(0, pageIndex);
        }
    }

    @NonNull
    private static List<TocEntry> buildTocEntries(@NonNull OpenDroidPDFCore core) {
        DocumentType docType = DocumentType.OTHER;
        try { docType = DocumentType.fromFileFormat(core.fileFormat()); } catch (Throwable ignore) {}

        if (docType == DocumentType.EPUB) {
            String path = null;
            try { path = core.getPath(); } catch (Throwable ignore) {}
            if (path == null || path.trim().isEmpty()) return new ArrayList<>();

            List<EpubTocParser.TocEntry> toc = EpubTocParser.parseFromEpubPath(path);
            ArrayList<TocEntry> out = new ArrayList<>();
            for (EpubTocParser.TocEntry e : toc) {
                if (e == null) continue;
                int page = -1;
                try { page = core.resolveLinkPage(e.href); } catch (Throwable ignore) { page = -1; }
                if (page < 0) continue;
                out.add(new TocEntry(e.level, e.title, page));
            }
            return out;
        }

        try {
            OutlineItem[] outline = core.getOutline();
            if (outline == null || outline.length == 0) return new ArrayList<>();
            ArrayList<TocEntry> out = new ArrayList<>();
            for (OutlineItem it : outline) {
                if (it == null) continue;
                int page = it.page;
                if (page < 0) continue;
                out.add(new TocEntry(it.level, it.title, page));
            }
            return out;
        } catch (Throwable ignore) {
            return new ArrayList<>();
        }
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static int findBestTocIndexForPage(@NonNull List<TocEntry> toc, int pageIndex) {
        int best = -1;
        int bestPage = -1;
        for (int i = 0; i < toc.size(); i++) {
            TocEntry e = toc.get(i);
            if (e == null) continue;
            int p = e.pageIndex;
            if (p <= pageIndex && p >= bestPage) {
                bestPage = p;
                best = i;
            }
        }
        return best >= 0 ? best : 0;
    }

    private static int sectionEndPageIndexForTocEntry(@NonNull List<TocEntry> toc,
                                                      int index,
                                                      int startPageIndex,
                                                      int maxPageIndex) {
        if (toc.isEmpty()) return maxPageIndex;
        int level = 0;
        try { level = toc.get(index).level; } catch (Throwable ignore) { level = 0; }
        int end = maxPageIndex;
        for (int i = index + 1; i < toc.size(); i++) {
            TocEntry next = toc.get(i);
            if (next == null) continue;
            if (next.level <= level) {
                int candidate = next.pageIndex - 1;
                if (candidate < startPageIndex) candidate = startPageIndex;
                end = clamp(candidate, startPageIndex, maxPageIndex);
                break;
            }
        }
        if (end < startPageIndex) end = startPageIndex;
        return end;
    }

    @Nullable
    private static TocSectionScope resolveTocSectionScopeOrNull(@NonNull OpenDroidPDFActivity activity,
                                                               @NonNull MuPDFReaderView docView,
                                                               @Nullable TocSectionScope presetTocScope) {
        int pageCount = safePageCount(docView);
        if (pageCount <= 0) return null;
        int maxPageIndex = Math.max(0, pageCount - 1);

        if (presetTocScope != null) {
            int start = clamp(presetTocScope.startPageIndex, 0, maxPageIndex);
            int end = clamp(presetTocScope.endPageIndex, start, maxPageIndex);
            return new TocSectionScope(presetTocScope.title, start, end);
        }

        OpenDroidPDFCore core = null;
        try { core = activity.getCore(); } catch (Throwable ignore) { core = null; }
        if (core == null) return null;

        List<TocEntry> toc = buildTocEntries(core);
        if (toc.isEmpty()) return null;

        int currentPageIndex = safeSelectedPageIndex(docView);
        int idx = findBestTocIndexForPage(toc, currentPageIndex);
        TocEntry entry = toc.get(Math.max(0, Math.min(idx, toc.size() - 1)));
        int start = clamp(entry.pageIndex, 0, maxPageIndex);
        int end = sectionEndPageIndexForTocEntry(toc, idx, start, maxPageIndex);
        return new TocSectionScope(entry.title, start, end);
    }

    private static AssistantContextSnapshot buildVoiceContextSnapshot(@NonNull OpenDroidPDFActivity activity,
                                                                      @Nullable MuPdfRepository repo,
                                                                      @NonNull MuPDFReaderView docView,
                                                                      @NonNull Scope scope,
                                                                      @Nullable TocSectionScope presetTocScope) {
        int pageIndex = safeSelectedPageIndex(docView);
        String title;
        try { title = activity.currentDocumentNameOrAppName(); } catch (Throwable t) { title = "Document"; }

        if (scope == Scope.SELECTION) {
            String selection = AssistantContextTextExtractor.selectionTextOrNull(docView, repo, activity.getSelectedPageView());
            if (selection == null || selection.trim().isEmpty()) {
                throw new IllegalStateException(activity.getString(R.string.assistant_sheet_no_selection));
            }
            String header = null;
            try {
                org.opendroidpdf.app.selection.DocumentTextSelection sel = docView.getDocumentTextSelectionOrNull();
                if (sel != null) {
                    if (sel.startPage == sel.endPage) {
                        header = "Page " + (sel.startPage + 1) + ":\n";
                    } else {
                        header = "Pages " + (sel.startPage + 1) + "-" + (sel.endPage + 1) + ":\n";
                    }
                }
            } catch (Throwable ignore) {
            }
            if (header == null) header = "Page " + (pageIndex + 1) + ":\n";
            String text = header + selection;
            boolean truncated = false;
            if (text.length() > MAX_PREVIEW_CHARS) {
                text = text.substring(0, MAX_PREVIEW_CHARS);
                truncated = true;
            }
            return new AssistantContextSnapshot(AssistantContextSnapshot.Kind.SELECTION, title, pageIndex, text, truncated);
        }
        if (scope == Scope.TOC_SECTION) {
            TocSectionScope tocScope = resolveTocSectionScopeOrNull(activity, docView, presetTocScope);
            if (tocScope == null) throw new IllegalStateException(activity.getString(R.string.assistant_sheet_no_toc));
            AssistantContextTextExtractor.TextResult res = AssistantContextTextExtractor.pageRangeText(
                    repo,
                    tocScope.startPageIndex,
                    tocScope.endPageIndex,
                    MAX_PREVIEW_CHARS,
                    null,
                    true
            );
            return new AssistantContextSnapshot(AssistantContextSnapshot.Kind.DOCUMENT, title, tocScope.startPageIndex, res.text, res.truncated);
        }
        if (scope == Scope.DOCUMENT) {
            AssistantContextTextExtractor.TextResult res = AssistantContextTextExtractor.documentText(repo, MAX_PREVIEW_CHARS, null);
            return new AssistantContextSnapshot(AssistantContextSnapshot.Kind.DOCUMENT, title, pageIndex, res.text, res.truncated);
        }
        String header = "Page " + (pageIndex + 1) + ":\n";
        int pageBudget = Math.max(1, MAX_PREVIEW_CHARS - header.length());
        AssistantContextTextExtractor.TextResult page = AssistantContextTextExtractor.pageText(repo, pageIndex, pageBudget);
        String text = header + (page.text != null ? page.text : "");
        boolean truncated = page.truncated;
        if (text.length() > MAX_PREVIEW_CHARS) {
            text = text.substring(0, MAX_PREVIEW_CHARS);
            truncated = true;
        }
        return new AssistantContextSnapshot(AssistantContextSnapshot.Kind.PAGE, title, pageIndex, text, truncated);
    }

    private static void showPreviewDialog(@NonNull OpenDroidPDFActivity activity,
                                         @Nullable MuPdfRepository repo,
                                         @NonNull MuPDFReaderView docView,
                                         @NonNull Scope scope,
                                         @Nullable TocSectionScope presetTocScope) {
        if (scope == Scope.DOCUMENT) {
            showDocumentPreviewDialogAsync(activity, repo, docView);
            return;
        }
        if (scope == Scope.TOC_SECTION) {
            TocSectionScope tocScope = resolveTocSectionScopeOrNull(activity, docView, presetTocScope);
            if (tocScope == null) {
                try { activity.showInfo(activity.getString(R.string.assistant_sheet_no_toc)); } catch (Throwable ignore) {}
                return;
            }
            showTocSectionPreviewDialogAsync(activity, repo, docView, tocScope);
            return;
        }

        int pageIndex = safeSelectedPageIndex(docView);
        String title;
        try { title = activity.currentDocumentNameOrAppName(); } catch (Throwable t) { title = "Document"; }

        String text = "";
        boolean truncated = false;
        if (scope == Scope.SELECTION) {
            text = AssistantContextTextExtractor.selectionTextOrNull(docView, repo, activity.getSelectedPageView());
            if (text == null) text = "";
        } else {
            AssistantContextTextExtractor.TextResult page = AssistantContextTextExtractor.pageText(repo, pageIndex, MAX_PREVIEW_CHARS);
            text = page.text;
            truncated = page.truncated;
        }

        showPreviewDialogWithText(activity, title, scope, pageIndex, text, truncated, null);
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
                showPreviewDialogWithText(activity, title, Scope.DOCUMENT, pageIndex, result.text, result.truncated, null);
            });
        });
    }

    private static void showTocSectionPreviewDialogAsync(@NonNull OpenDroidPDFActivity activity,
                                                        @Nullable MuPdfRepository repo,
                                                        @NonNull MuPDFReaderView docView,
                                                        @NonNull TocSectionScope tocScope) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_sheet_preview_title)
                .setMessage(R.string.assistant_context_loading)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> cancelled.set(true))
                .create();
        progress.show();

        executor.execute(() -> {
            AssistantContextTextExtractor.TextResult result = AssistantContextTextExtractor.pageRangeText(
                    repo,
                    tocScope.startPageIndex,
                    tocScope.endPageIndex,
                    MAX_PREVIEW_CHARS,
                    cancelled,
                    false
            );

            activity.runOnUiThread(() -> {
                try { progress.dismiss(); } catch (Throwable ignore) {}
                if (cancelled.get()) return;
                if (isActivityInvalid(activity)) return;
                String title;
                try { title = activity.currentDocumentNameOrAppName(); } catch (Throwable t) { title = "Document"; }
                showPreviewDialogWithText(activity, title, Scope.TOC_SECTION, tocScope.startPageIndex, result.text, result.truncated, tocScope);
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
                                                 boolean truncated,
                                                 @Nullable TocSectionScope tocScope) {
        View body = LayoutInflater.from(activity).inflate(R.layout.dialog_assistant_preview, null);
        TextView summary = body.findViewById(R.id.assistant_preview_summary);
        TextView content = body.findViewById(R.id.assistant_preview_text);

        String scopeSummary = describeScope(activity, scope, pageIndex, text != null ? text.length() : 0, truncated, tocScope);
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

    public static void onActivityResultAttachDocuments(@NonNull OpenDroidPDFActivity activity,
                                                       int resultCode,
                                                       @Nullable Intent intent) {
        if (activity == null) return;
        if (resultCode != Activity.RESULT_OK) return;
        if (intent == null) return;

        String documentKey = currentDocumentSessionKey(activity);

        String currentDocUriString = null;
        try {
            Uri uri = activity.currentUserFacingUriOrNull();
            if (uri != null) currentDocUriString = uri.toString();
        } catch (Throwable ignore) {}

        List<Uri> uris = collectAttachmentUris(intent);
        int added = 0;
        for (int i = 0; i < uris.size(); i++) {
            Uri uri = uris.get(i);
            if (uri == null) continue;
            String uriString = uri.toString();
            if (uriString == null || uriString.trim().isEmpty()) continue;
            if (currentDocUriString != null && currentDocUriString.equals(uriString)) continue;

            String name = resolveDisplayNameOrNull(activity, uri);
            if (AssistantAttachmentsStore.add(documentKey, uri, name)) {
                added++;
            }
            maybeTakePersistablePermission(activity, intent, uri);
        }

        if (added > 0) {
            try { activity.showInfo(activity.getString(R.string.assistant_sheet_attachments_added_count, added)); } catch (Throwable ignore) {}
        }
        updateAttachmentsUiIfOpen(activity, documentKey);
    }

    public static void onActivityResultVoicePrompt(@NonNull OpenDroidPDFActivity activity,
                                                   int resultCode,
                                                   @Nullable Intent intent) {
        if (activity == null) return;
        if (resultCode != Activity.RESULT_OK) return;
        if (intent == null) return;

        String transcript = null;
        try { transcript = intent.getStringExtra(AssistantActivity.EXTRA_TRANSCRIPT); } catch (Throwable ignore) { transcript = null; }
        if (transcript == null) return;
        transcript = transcript.trim();
        if (transcript.isEmpty()) return;

        BottomSheetDialog dialog = openDialogs.get(activity);
        if (dialog == null) return;
        EditText prompt = dialog.findViewById(R.id.assistant_sheet_prompt);
        if (prompt == null) return;
        prompt.setText(transcript);
        try { prompt.setSelection(prompt.getText() != null ? prompt.getText().length() : 0); } catch (Throwable ignore) {}
        try { prompt.requestFocus(); } catch (Throwable ignore) {}
        try {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(prompt, InputMethodManager.SHOW_IMPLICIT);
        } catch (Throwable ignore) {}
    }

    private static void updateAttachmentsUiIfOpen(@NonNull OpenDroidPDFActivity activity, @NonNull String documentKey) {
        AttachmentsUiHandle handle = attachmentsUiHandles.get(activity);
        if (handle == null) return;
        if (!documentKey.equals(handle.documentKey)) return;
        if (handle.scroll != null && handle.container != null) {
            renderAttachmentsRow(activity, documentKey, handle.scroll, handle.container);
        }
    }

    @NonNull
    private static List<Uri> collectAttachmentUris(@NonNull Intent intent) {
        ArrayList<Uri> out = new ArrayList<>();
        try {
            Uri single = intent.getData();
            if (single != null) out.add(single);
        } catch (Throwable ignore) {}

        try {
            ClipData clip = intent.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    ClipData.Item item = clip.getItemAt(i);
                    Uri uri = item != null ? item.getUri() : null;
                    if (uri != null) out.add(uri);
                }
            }
        } catch (Throwable ignore) {}

        if (out.size() <= 1) return out;

        ArrayList<Uri> deduped = new ArrayList<>(out.size());
        for (int i = 0; i < out.size(); i++) {
            Uri uri = out.get(i);
            if (uri == null) continue;
            String s = uri.toString();
            if (s == null || s.trim().isEmpty()) continue;
            boolean seen = false;
            for (int j = 0; j < deduped.size(); j++) {
                Uri prev = deduped.get(j);
                if (prev != null && s.equals(prev.toString())) {
                    seen = true;
                    break;
                }
            }
            if (!seen) deduped.add(uri);
        }
        return deduped;
    }

    @Nullable
    private static String resolveDisplayNameOrNull(@NonNull Context context, @NonNull Uri uri) {
        if (context == null || uri == null) return null;
        try {
            Cursor c = context.getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        String name = c.getString(0);
                        if (name != null && !name.trim().isEmpty()) return name;
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Throwable ignore) {
        }
        try {
            String path = uri.getPath();
            if (path != null) {
                int slash = path.lastIndexOf('/');
                String name = slash >= 0 ? path.substring(slash + 1) : path;
                name = name != null ? name.trim() : "";
                if (!name.isEmpty()) return name;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static void maybeTakePersistablePermission(@NonNull Context context,
                                                       @NonNull Intent resultIntent,
                                                       @NonNull Uri uri) {
        try {
            int flags = resultIntent.getFlags();
            int takeFlags = 0;
            if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                takeFlags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
            }
            if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                takeFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            }
            if (takeFlags == 0) return;
            ContentResolver cr = context.getContentResolver();
            if (cr != null) {
                cr.takePersistableUriPermission(uri, takeFlags);
            }
        } catch (Throwable ignore) {
        }
    }

    private static void renderAttachmentsRow(@NonNull OpenDroidPDFActivity activity,
                                            @NonNull String documentKey,
                                            @NonNull View scroll,
                                            @NonNull LinearLayout container) {
        List<AssistantAttachmentsStore.Attachment> atts = AssistantAttachmentsStore.snapshot(documentKey);
        container.removeAllViews();
        if (atts.isEmpty()) {
            scroll.setVisibility(View.GONE);
            return;
        }

        scroll.setVisibility(View.VISIBLE);
        int textColor = 0;
        try { textColor = ContextCompat.getColor(activity, R.color.primary_text); } catch (Throwable ignore) { textColor = 0; }

        for (int i = 0; i < atts.size(); i++) {
            AssistantAttachmentsStore.Attachment att = atts.get(i);
            if (att == null) continue;

            LinearLayout chip = new LinearLayout(activity);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            try { chip.setBackgroundResource(R.drawable.bg_assistant_action_chip); } catch (Throwable ignore) {}

            int padStart = dpToPx(activity, 12);
            int padTop = dpToPx(activity, 6);
            int padEnd = dpToPx(activity, 6);
            int padBottom = dpToPx(activity, 6);
            chip.setPadding(padStart, padTop, padEnd, padBottom);

            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            chipLp.rightMargin = dpToPx(activity, 8);
            chip.setLayoutParams(chipLp);

            TextView name = new TextView(activity);
            name.setText(att.displayName);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setMaxEms(18);
            if (textColor != 0) name.setTextColor(textColor);
            chip.addView(name, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            ImageButton remove = new ImageButton(activity);
            remove.setImageResource(R.drawable.ic_close_white_24dp);
            remove.setScaleType(ImageButton.ScaleType.CENTER);
            remove.setPadding(dpToPx(activity, 4), dpToPx(activity, 4), dpToPx(activity, 4), dpToPx(activity, 4));
            if (textColor != 0) remove.setColorFilter(textColor);
            remove.setBackground(null);
            try { remove.setContentDescription(activity.getString(R.string.assistant_sheet_remove_attachment, att.displayName)); } catch (Throwable ignore) {}
            remove.setOnClickListener(v -> {
                AssistantAttachmentsStore.remove(documentKey, att.uri());
                renderAttachmentsRow(activity, documentKey, scroll, container);
            });
            LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dpToPx(activity, 32), dpToPx(activity, 32));
            removeLp.leftMargin = dpToPx(activity, 4);
            chip.addView(remove, removeLp);

            container.addView(chip);
        }
    }

    private static int askAttachmentsBudgetChars(@NonNull String documentKey) {
        if (AssistantAttachmentsStore.count(documentKey) <= 0) return 0;
        int max = Math.max(0, MAX_PREVIEW_CHARS - 5_000);
        return Math.min(MAX_ATTACHMENTS_CONTEXT_CHARS, max);
    }

    private static final class AttachmentsContextResult {
        @NonNull final String text;
        final boolean truncated;

        AttachmentsContextResult(@NonNull String text, boolean truncated) {
            this.text = text != null ? text : "";
            this.truncated = truncated;
        }
    }

    @NonNull
    private static AttachmentsContextResult buildAttachmentsContextText(@NonNull OpenDroidPDFActivity activity,
                                                                       @NonNull String documentKey,
                                                                       int maxChars,
                                                                       @Nullable AtomicBoolean cancelled) {
        if (maxChars <= 0) return new AttachmentsContextResult("", false);
        List<AssistantAttachmentsStore.Attachment> atts = AssistantAttachmentsStore.snapshot(documentKey);
        if (atts.isEmpty()) return new AttachmentsContextResult("", false);

        StringBuilder sb = new StringBuilder(Math.min(maxChars, 8192));
        boolean truncated = false;
        int included = 0;

        for (int i = 0; i < atts.size(); i++) {
            if (cancelled != null && cancelled.get()) break;
            if (sb.length() >= maxChars) {
                truncated = true;
                break;
            }

            AssistantAttachmentsStore.Attachment att = atts.get(i);
            if (att == null) continue;

            if (included > 0) sb.append("\n\n");
            included++;
            sb.append("Attachment: ").append(att.displayName).append("\n");

            int remaining = maxChars - sb.length();
            if (remaining <= 0) {
                truncated = true;
                break;
            }

            OpenDroidPDFCore core = null;
            try {
                core = new OpenDroidPDFCore(activity, att.uri());
                if (core.needsPassword()) {
                    sb.append("(Password required)");
                    continue;
                }
                MuPdfRepository attachmentRepo = new MuPdfRepository(core);
                AssistantContextTextExtractor.TextResult res = AssistantContextTextExtractor.pageRangeText(
                        attachmentRepo,
                        0,
                        Integer.MAX_VALUE,
                        remaining,
                        cancelled,
                        false);
                String text = res.text != null ? res.text : "";
                if (text.trim().isEmpty()) {
                    sb.append("(No extractable text)");
                } else {
                    sb.append(text);
                }
                if (res.truncated) truncated = true;
            } catch (Throwable t) {
                sb.append("(Couldn’t extract text)");
            } finally {
                if (core != null) {
                    try { core.onDestroy(); } catch (Throwable ignore) {}
                }
            }
        }

        String out = sb.toString().trim();
        if (out.length() > maxChars) {
            out = out.substring(0, maxChars);
            truncated = true;
        }
        return new AttachmentsContextResult(out, truncated);
    }

    private static String describeScope(@NonNull Context ctx,
                                       @NonNull Scope scope,
                                       int pageIndex,
                                       int chars,
                                       boolean truncated,
                                       @Nullable TocSectionScope tocScope) {
        String kind;
        if (scope == Scope.SELECTION) {
            kind = ctx.getString(R.string.assistant_sheet_scope_selection);
        } else if (scope == Scope.TOC_SECTION) {
            kind = ctx.getString(R.string.assistant_sheet_scope_toc_section);
        } else if (scope == Scope.DOCUMENT) {
            kind = ctx.getString(R.string.assistant_sheet_scope_whole_document);
        } else {
            kind = ctx.getString(R.string.assistant_sheet_scope_this_page);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(kind);
        if (scope == Scope.TOC_SECTION && tocScope != null) {
            sb.append(" • p. ").append(tocScope.startPageIndex + 1);
            if (tocScope.endPageIndex != tocScope.startPageIndex) {
                sb.append("–").append(tocScope.endPageIndex + 1);
            }
            if (tocScope.title != null && !tocScope.title.isEmpty()) {
                String title = tocScope.title;
                if (title.length() > 80) title = title.substring(0, 80) + "…";
                sb.append(" • ").append(title);
            }
        } else if (scope != Scope.DOCUMENT) {
            sb.append(" • p. ").append(pageIndex + 1);
        }
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
                                        @Nullable String[] relatedQuestions,
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

        if (!isUser && relatedQuestions != null && relatedQuestions.length > 0) {
            View related = buildRelatedQuestionsRow(activity, relatedQuestions);
            if (related != null) bubble.addView(related);
        }

        if (!isUser && showActions) {
            View actions = buildAssistantAnswerActions(activity, docView, behaviorHolder, text, citationPages1Based);
            if (actions != null) bubble.addView(actions);
        }

        return bubble;
    }

    @Nullable
    private static View buildRelatedQuestionsRow(@NonNull OpenDroidPDFActivity activity,
                                                @NonNull String[] relatedQuestions) {
        if (relatedQuestions == null || relatedQuestions.length == 0) return null;

        ArrayList<String> cleaned = new ArrayList<>();
        for (int i = 0; i < relatedQuestions.length && cleaned.size() < 4; i++) {
            String q = relatedQuestions[i];
            if (q == null) continue;
            q = q.trim();
            if (q.isEmpty()) continue;
            if (q.length() > 160) q = q.substring(0, 160).trim();
            if (q.isEmpty()) continue;

            boolean dup = false;
            for (int j = 0; j < cleaned.size(); j++) {
                if (q.equalsIgnoreCase(cleaned.get(j))) {
                    dup = true;
                    break;
                }
            }
            if (!dup) cleaned.add(q);
        }
        if (cleaned.isEmpty()) return null;

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        containerLp.topMargin = dpToPx(activity, 10);
        container.setLayoutParams(containerLp);

        TextView label = new TextView(activity);
        label.setText(R.string.assistant_sheet_related_questions_label);
        label.setTextAppearance(activity, androidx.appcompat.R.style.TextAppearance_AppCompat_Small);
        container.addView(label);

        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollLp.topMargin = dpToPx(activity, 6);
        scroll.setLayoutParams(scrollLp);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < cleaned.size(); i++) {
            final String qFinal = cleaned.get(i);
            TextView chip = buildTextChip(activity, qFinal);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = dpToPx(activity, 8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> prefillAndSendAskQuestion(activity, qFinal));
            row.addView(chip);
        }

        scroll.addView(row);
        container.addView(scroll);
        return container;
    }

    @NonNull
    private static TextView buildTextChip(@NonNull Context ctx, @NonNull String text) {
        TextView chip = new TextView(ctx);
        chip.setText(text);
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

    private static void prefillAndSendAskQuestion(@NonNull OpenDroidPDFActivity activity, @NonNull String question) {
        if (activity == null) return;
        String q = question != null ? question.trim() : "";
        if (q.isEmpty()) return;
        BottomSheetDialog dialog = openDialogs.get(activity);
        if (dialog == null) return;

        EditText prompt = dialog.findViewById(R.id.assistant_sheet_prompt);
        Button send = dialog.findViewById(R.id.assistant_sheet_send);
        if (prompt == null || send == null) return;

        try {
            prompt.setText(q);
            if (prompt.getText() != null) prompt.setSelection(prompt.getText().length());
        } catch (Throwable ignore) {}

        try { send.performClick(); } catch (Throwable ignore) {}
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
            chatContainer.addView(buildChatBubble(activity,
                    m.text,
                    m.isUser,
                    !m.isUser,
                    m.citationNumbers,
                    m.citationPages1Based,
                    m.relatedQuestions,
                    showSources,
                    behaviorHolder,
                    docView));
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

    private static void setChatBubbleText(@Nullable View bubble, @NonNull CharSequence text) {
        if (bubble == null || text == null) return;
        if (!(bubble instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) bubble;
        if (vg.getChildCount() <= 0) return;
        View first = vg.getChildAt(0);
        if (first instanceof TextView) {
            ((TextView) first).setText(text);
        }
    }

    private static void setAskGeneratingUi(@Nullable Button sendButton,
                                          @Nullable ImageButton stopButton,
                                          boolean generating) {
        if (sendButton != null) {
            sendButton.setEnabled(!generating);
            sendButton.setVisibility(generating ? View.GONE : View.VISIBLE);
        }
        if (stopButton != null) {
            stopButton.setEnabled(generating);
            stopButton.setAlpha(1f);
            stopButton.setVisibility(generating ? View.VISIBLE : View.GONE);
        }
    }

    private static void requestAskStop(@NonNull OpenDroidPDFActivity activity,
                                       @NonNull AtomicReference<Call> activeCall,
                                       @NonNull AtomicBoolean stopRequested,
                                       @NonNull AtomicReference<View> pendingBubble,
                                       @Nullable ImageButton stopButton) {
        stopRequested.set(true);
        try { setChatBubbleText(pendingBubble.get(), activity.getString(R.string.assistant_sheet_stopping)); } catch (Throwable ignore) {}

        Call call = activeCall.get();
        if (call == null) return;
        try { call.cancel(); } catch (Throwable ignore) {}
        if (stopButton != null) {
            stopButton.setEnabled(false);
            stopButton.setAlpha(0.6f);
        }
    }

    private static void runAskRequestAsync(@NonNull OpenDroidPDFActivity activity,
                                           @NonNull String documentKey,
                                           @NonNull String question,
                                           @NonNull AssistantLlmProviderConfig provider,
                                           @NonNull String apiKey,
                                           @NonNull String contextText,
                                           @NonNull List<AssistantLlmClient.ChatMessage> chatHistory,
                                           @NonNull LinearLayout chatContainer,
                                           @Nullable ScrollView chatScroll,
                                           @Nullable View clearChatButton,
                                           boolean showSources,
                                           @NonNull BottomSheetBehavior<?>[] behaviorHolder,
                                           @NonNull MuPDFReaderView docView,
                                           @Nullable Button sendButton,
                                           @Nullable ImageButton stopButton,
                                           @NonNull AtomicReference<Call> activeCallOut,
                                           @NonNull AtomicBoolean stopRequested,
                                           @NonNull AtomicReference<View> pendingBubbleOut) {
        stopRequested.set(false);
        setAskGeneratingUi(sendButton, stopButton, true);
        if (stopButton != null) {
            stopButton.setEnabled(true);
            stopButton.setAlpha(1f);
        }

        final long transcriptVersion = AssistantAskTranscriptStore.appendUser(documentKey, question);
        updateClearChatEnabled(clearChatButton, documentKey);

        chatContainer.addView(buildChatBubble(activity, question, true, false, null, null, null, showSources, behaviorHolder, docView));
        View pending = buildChatBubble(activity, activity.getString(R.string.assistant_sheet_generating), false, false, null, null, null, showSources, behaviorHolder, docView);
        chatContainer.addView(pending);
        pendingBubbleOut.set(pending);
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));

        executor.execute(() -> {
            AssistantLlmClient.AskResult result;
            try {
                if (stopRequested.get()) {
                    result = AssistantLlmClient.AskResult.plainText(activity.getString(R.string.assistant_sheet_generation_stopped));
                } else {
                    result = AssistantLlmClient.askBlocking(http, provider, apiKey, question, contextText, chatHistory, activeCallOut);
                    if (stopRequested.get()) {
                        result = AssistantLlmClient.AskResult.plainText(activity.getString(R.string.assistant_sheet_generation_stopped));
                    }
                }
            } catch (Throwable t) {
                if (stopRequested.get()) {
                    result = AssistantLlmClient.AskResult.plainText(activity.getString(R.string.assistant_sheet_generation_stopped));
                } else {
                    String msg = t.getMessage();
                    if (msg == null || msg.trim().isEmpty()) msg = t.getClass().getSimpleName();
                    result = AssistantLlmClient.AskResult.plainText(msg);
                }
            }

            final AssistantLlmClient.AskResult resultFinal = result;
            if (AssistantAskTranscriptStore.isVersion(documentKey, transcriptVersion)) {
                AssistantAskTranscriptStore.appendAssistant(
                        documentKey,
                        resultFinal.answerText,
                        resultFinal.citationNumbers,
                        resultFinal.citationPages1Based,
                        resultFinal.relatedQuestions);
            }

            activity.runOnUiThread(() -> {
                if (isActivityInvalid(activity)) return;
                setAskGeneratingUi(sendButton, stopButton, false);
                stopRequested.set(false);

                if (!AssistantAskTranscriptStore.isVersion(documentKey, transcriptVersion)) return;
                if (!chatContainer.isAttachedToWindow()) return;
                if (chatContainer.indexOfChild(pending) >= 0) chatContainer.removeView(pending);
                pendingBubbleOut.compareAndSet(pending, null);
                chatContainer.addView(buildChatBubble(activity,
                        resultFinal.answerText,
                        false,
                        true,
                        resultFinal.citationNumbers,
                        resultFinal.citationPages1Based,
                        resultFinal.relatedQuestions,
                        showSources,
                        behaviorHolder,
                        docView));
                updateClearChatEnabled(clearChatButton, documentKey);
                if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
            });
        });
    }

    private static void setSummaryGeneratingUi(@Nullable Button generateButton,
                                              @Nullable ImageButton stopButton,
                                              boolean generating) {
        if (generateButton != null) {
            generateButton.setEnabled(!generating);
            generateButton.setVisibility(generating ? View.GONE : View.VISIBLE);
        }
        if (stopButton != null) {
            stopButton.setEnabled(generating);
            stopButton.setAlpha(1f);
            stopButton.setVisibility(generating ? View.VISIBLE : View.GONE);
        }
    }

    private static void requestSummaryStop(@NonNull OpenDroidPDFActivity activity,
                                          @NonNull AtomicReference<Call> activeCall,
                                          @NonNull AtomicBoolean stopRequested,
                                          @Nullable TextView statusView,
                                          @Nullable ImageButton stopButton) {
        stopRequested.set(true);
        if (statusView != null) {
            try { statusView.setText(R.string.assistant_sheet_stopping); } catch (Throwable ignore) {}
        }

        Call call = activeCall.get();
        if (call == null) return;
        try { call.cancel(); } catch (Throwable ignore) {}
        if (stopButton != null) {
            stopButton.setEnabled(false);
            stopButton.setAlpha(0.6f);
        }
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

    private static void runSummaryRequestAsync(@NonNull OpenDroidPDFActivity activity,
                                              @NonNull AssistantLlmProviderConfig provider,
                                              @NonNull String apiKey,
                                              @NonNull String text,
                                              @NonNull AssistantLlmClient.SummaryStyle style,
                                              @NonNull AtomicBoolean summaryInFlight,
                                              @Nullable TextView summaryStatus,
                                              @NonNull TextView summaryOutput,
                                              @NonNull Button summaryGenerate,
                                              @Nullable ImageButton summaryStop,
                                              @NonNull AtomicReference<Call> activeCallOut,
                                              @NonNull AtomicBoolean stopRequested,
                                              @Nullable Button summaryCopy,
                                              @Nullable Button summaryInsert,
                                              @Nullable Button summarySaveNote,
                                              @Nullable Button summaryExport,
                                              @NonNull AtomicBoolean noteSaveInFlight,
                                              @NonNull AtomicBoolean exportInFlight) {
        if (summaryInFlight.getAndSet(true)) return;
        stopRequested.set(false);
        if (summaryStatus != null) summaryStatus.setText(R.string.assistant_sheet_generating);
        summaryOutput.setText("");
        setSummaryGeneratingUi(summaryGenerate, summaryStop, true);
        if (summaryCopy != null) summaryCopy.setEnabled(false);
        if (summaryInsert != null) summaryInsert.setEnabled(false);
        if (summarySaveNote != null) summarySaveNote.setEnabled(false);
        if (summaryExport != null) summaryExport.setEnabled(false);

        executor.execute(() -> {
            String out = "";
            boolean stopped = false;
            try {
                if (stopRequested.get()) {
                    stopped = true;
                } else {
                    out = AssistantLlmClient.summarizeBlocking(http, provider, apiKey, text, style, 700, activeCallOut);
                    if (stopRequested.get()) {
                        out = "";
                        stopped = true;
                    }
                }
            } catch (Throwable t) {
                if (stopRequested.get()) {
                    out = "";
                    stopped = true;
                } else {
                    out = t.getMessage();
                    if (out == null || out.trim().isEmpty()) out = t.getClass().getSimpleName();
                }
            }
            final String outFinal = out != null ? out : "";
            final boolean stoppedFinal = stopped;
            activity.runOnUiThread(() -> {
                summaryInFlight.set(false);
                if (summaryStatus != null) summaryStatus.setText("");
                stopRequested.set(false);
                setSummaryGeneratingUi(summaryGenerate, summaryStop, false);

                if (stoppedFinal) {
                    summaryOutput.setText("");
                    if (summaryCopy != null) summaryCopy.setEnabled(false);
                    if (summaryInsert != null) summaryInsert.setEnabled(false);
                    if (summarySaveNote != null) summarySaveNote.setEnabled(false);
                    if (summaryExport != null) summaryExport.setEnabled(false);
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_generation_stopped)); } catch (Throwable ignore) {}
                } else {
                    summaryOutput.setText(outFinal);
                    boolean hasOut = !outFinal.trim().isEmpty();
                    if (summaryCopy != null) summaryCopy.setEnabled(hasOut);
                    if (summaryInsert != null) summaryInsert.setEnabled(hasOut);
                    if (summarySaveNote != null) summarySaveNote.setEnabled(hasOut && !noteSaveInFlight.get() && !exportInFlight.get());
                    if (summaryExport != null) summaryExport.setEnabled(hasOut && !exportInFlight.get() && !noteSaveInFlight.get());
                }
            });
        });
    }

    private static void showWholeDocumentSummarySafetyThenPreviewAndSummarizeAsync(@NonNull OpenDroidPDFActivity activity,
                                                                                  @NonNull SharedPreferences prefs,
                                                                                  @Nullable MuPdfRepository repo,
                                                                                  @NonNull MuPDFReaderView docView,
                                                                                  @NonNull AssistantLlmProviderConfig provider,
                                                                                  @NonNull String apiKey,
                                                                                  @NonNull AssistantLlmClient.SummaryStyle style,
                                                                                  @NonNull AtomicBoolean summaryInFlight,
                                                                                  @Nullable TextView summaryStatus,
                                                                                  @NonNull TextView summaryOutput,
                                                                                  @NonNull Button summaryGenerate,
                                                                                  @Nullable ImageButton summaryStop,
                                                                                  @NonNull AtomicReference<Call> activeCallOut,
                                                                                  @NonNull AtomicBoolean stopRequested,
                                                                                  @Nullable Button summaryCopy,
                                                                                  @Nullable Button summaryInsert,
                                                                                  @Nullable Button summarySaveNote,
                                                                                  @Nullable Button summaryExport,
                                                                                  @NonNull AtomicBoolean noteSaveInFlight,
                                                                                  @NonNull AtomicBoolean exportInFlight) {
        if (repo == null) {
            try { activity.showInfo(activity.getString(R.string.assistant_sheet_unknown_error)); } catch (Throwable ignore) {}
            return;
        }

        int totalPages = 0;
        try { totalPages = repo.getPageCount(); } catch (Throwable ignore) { totalPages = 0; }
        if (totalPages <= 0) {
            try { activity.showInfo(activity.getString(R.string.assistant_sheet_unknown_error)); } catch (Throwable ignore) {}
            return;
        }

        String providerName = provider.name();
        if (providerName == null) providerName = "";
        providerName = providerName.trim();
        if (providerName.isEmpty()) providerName = provider.baseUrl();

        String msg;
        try {
            msg = activity.getString(R.string.assistant_sheet_whole_document_summary_safety_message, totalPages, providerName);
        } catch (Throwable t) {
            msg = "This will summarize the whole document (" + totalPages + " pages) and send text to " + providerName + ".";
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_sheet_whole_document_summary_safety_title)
                .setMessage(msg)
                .setPositiveButton(R.string.assistant_sheet_continue, (d, w) ->
                        showWholeDocumentPreviewAndSummarizeAsync(
                                activity,
                                prefs,
                                repo,
                                docView,
                                provider,
                                apiKey,
                                style,
                                summaryInFlight,
                                summaryStatus,
                                summaryOutput,
                                summaryGenerate,
                                summaryStop,
                                activeCallOut,
                                stopRequested,
                                summaryCopy,
                                summaryInsert,
                                summarySaveNote,
                                summaryExport,
                                noteSaveInFlight,
                                exportInFlight))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void showWholeDocumentPreviewAndSummarizeAsync(@NonNull OpenDroidPDFActivity activity,
                                                                  @NonNull SharedPreferences prefs,
                                                                  @NonNull MuPdfRepository repo,
                                                                  @NonNull MuPDFReaderView docView,
                                                                  @NonNull AssistantLlmProviderConfig provider,
                                                                  @NonNull String apiKey,
                                                                  @NonNull AssistantLlmClient.SummaryStyle style,
                                                                  @NonNull AtomicBoolean summaryInFlight,
                                                                  @Nullable TextView summaryStatus,
                                                                  @NonNull TextView summaryOutput,
                                                                  @NonNull Button summaryGenerate,
                                                                  @Nullable ImageButton summaryStop,
                                                                  @NonNull AtomicReference<Call> activeCallOut,
                                                                  @NonNull AtomicBoolean stopRequested,
                                                                  @Nullable Button summaryCopy,
                                                                  @Nullable Button summaryInsert,
                                                                  @Nullable Button summarySaveNote,
                                                                  @Nullable Button summaryExport,
                                                                  @NonNull AtomicBoolean noteSaveInFlight,
                                                                  @NonNull AtomicBoolean exportInFlight) {
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
            int totalPages = 0;
            try { totalPages = repo.getPageCount(); } catch (Throwable ignore) { totalPages = 0; }
            final int pagesFinal = totalPages;

            activity.runOnUiThread(() -> {
                try { progress.dismiss(); } catch (Throwable ignore) {}
                if (cancelled.get()) return;
                if (isActivityInvalid(activity)) return;

                String previewText = result.text != null ? result.text : "";
                boolean excerpt = result.truncated;
                String outgoingPreview = previewText;
                if (excerpt) {
                    try {
                        String notice = activity.getString(R.string.assistant_sheet_whole_document_preview_excerpt_notice, MAX_PREVIEW_CHARS);
                        outgoingPreview = notice + "\n\n" + previewText;
                    } catch (Throwable ignore) {
                        outgoingPreview = previewText;
                    }
                }

                String previewSummary;
                try {
                    previewSummary = activity.getString(
                            R.string.assistant_sheet_whole_document_preview_summary,
                            pagesFinal,
                            previewText.length(),
                            excerpt ? activity.getString(R.string.assistant_sheet_whole_document_preview_excerpt_suffix) : "");
                } catch (Throwable t) {
                    previewSummary = describeScope(activity, Scope.DOCUMENT, safeSelectedPageIndex(docView), previewText.length(), excerpt, null);
                }
                previewSummary = previewSummary + " • Summary";

                final String outgoingFinal = outgoingPreview != null ? outgoingPreview : "";
                runWithPrivacyGate(activity, prefs, provider, previewSummary, outgoingFinal, R.string.assistant_sheet_generate, () -> {
                    if (excerpt) {
                        runWholeDocumentProgressiveSummaryRequestAsync(
                                activity,
                                repo,
                                provider,
                                apiKey,
                                style,
                                pagesFinal,
                                summaryInFlight,
                                summaryStatus,
                                summaryOutput,
                                summaryGenerate,
                                summaryStop,
                                activeCallOut,
                                stopRequested,
                                summaryCopy,
                                summaryInsert,
                                summarySaveNote,
                                summaryExport,
                                noteSaveInFlight,
                                exportInFlight);
                    } else {
                        runSummaryRequestAsync(
                                activity,
                                provider,
                                apiKey,
                                previewText,
                                style,
                                summaryInFlight,
                                summaryStatus,
                                summaryOutput,
                                summaryGenerate,
                                summaryStop,
                                activeCallOut,
                                stopRequested,
                                summaryCopy,
                                summaryInsert,
                                summarySaveNote,
                                summaryExport,
                                noteSaveInFlight,
                                exportInFlight);
                    }
                });
            });
        });
    }

    private static void runWholeDocumentProgressiveSummaryRequestAsync(@NonNull OpenDroidPDFActivity activity,
                                                                      @NonNull MuPdfRepository repo,
                                                                      @NonNull AssistantLlmProviderConfig provider,
                                                                      @NonNull String apiKey,
                                                                      @NonNull AssistantLlmClient.SummaryStyle finalStyle,
                                                                      int totalPagesHint,
                                                                      @NonNull AtomicBoolean summaryInFlight,
                                                                      @Nullable TextView summaryStatus,
                                                                      @NonNull TextView summaryOutput,
                                                                      @NonNull Button summaryGenerate,
                                                                      @Nullable ImageButton summaryStop,
                                                                      @NonNull AtomicReference<Call> activeCallOut,
                                                                      @NonNull AtomicBoolean stopRequested,
                                                                      @Nullable Button summaryCopy,
                                                                      @Nullable Button summaryInsert,
                                                                      @Nullable Button summarySaveNote,
                                                                      @Nullable Button summaryExport,
                                                                      @NonNull AtomicBoolean noteSaveInFlight,
                                                                      @NonNull AtomicBoolean exportInFlight) {
        if (summaryInFlight.getAndSet(true)) return;
        stopRequested.set(false);
        if (summaryStatus != null) summaryStatus.setText(R.string.assistant_sheet_generating);
        summaryOutput.setText("");
        setSummaryGeneratingUi(summaryGenerate, summaryStop, true);
        if (summaryCopy != null) summaryCopy.setEnabled(false);
        if (summaryInsert != null) summaryInsert.setEnabled(false);
        if (summarySaveNote != null) summarySaveNote.setEnabled(false);
        if (summaryExport != null) summaryExport.setEnabled(false);

        final int totalPagesFinal = totalPagesHint;
        executor.execute(() -> {
            String out = "";
            boolean stopped = false;
            try {
                if (stopRequested.get()) {
                    stopped = true;
                } else {
                    out = summarizeWholeDocumentProgressivelyBlocking(activity, repo, provider, apiKey, finalStyle, totalPagesFinal, summaryStatus, activeCallOut, stopRequested);
                    if (stopRequested.get()) {
                        out = "";
                        stopped = true;
                    }
                }
            } catch (Throwable t) {
                if (stopRequested.get()) {
                    out = "";
                    stopped = true;
                } else {
                    String msg = t.getMessage();
                    if (msg == null || msg.trim().isEmpty()) msg = t.getClass().getSimpleName();
                    out = msg;
                }
            }
            final String outFinal = out != null ? out : "";
            final boolean stoppedFinal = stopped;
            activity.runOnUiThread(() -> {
                summaryInFlight.set(false);
                if (summaryStatus != null) summaryStatus.setText("");
                stopRequested.set(false);
                setSummaryGeneratingUi(summaryGenerate, summaryStop, false);

                if (stoppedFinal) {
                    summaryOutput.setText("");
                    if (summaryCopy != null) summaryCopy.setEnabled(false);
                    if (summaryInsert != null) summaryInsert.setEnabled(false);
                    if (summarySaveNote != null) summarySaveNote.setEnabled(false);
                    if (summaryExport != null) summaryExport.setEnabled(false);
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_generation_stopped)); } catch (Throwable ignore) {}
                } else {
                    summaryOutput.setText(outFinal);
                    boolean hasOut = !outFinal.trim().isEmpty();
                    if (summaryCopy != null) summaryCopy.setEnabled(hasOut);
                    if (summaryInsert != null) summaryInsert.setEnabled(hasOut);
                    if (summarySaveNote != null) summarySaveNote.setEnabled(hasOut && !noteSaveInFlight.get() && !exportInFlight.get());
                    if (summaryExport != null) summaryExport.setEnabled(hasOut && !exportInFlight.get() && !noteSaveInFlight.get());
                }
            });
        });
    }

    @NonNull
    private static String summarizeWholeDocumentProgressivelyBlocking(@NonNull OpenDroidPDFActivity activity,
                                                                     @NonNull MuPdfRepository repo,
                                                                     @NonNull AssistantLlmProviderConfig provider,
                                                                     @NonNull String apiKey,
                                                                     @NonNull AssistantLlmClient.SummaryStyle finalStyle,
                                                                     int totalPagesHint,
                                                                     @Nullable TextView statusView,
                                                                     @NonNull AtomicReference<Call> activeCallOut,
                                                                     @NonNull AtomicBoolean stopRequested) throws Exception {
        int totalPages = totalPagesHint;
        if (totalPages <= 0) {
            try { totalPages = repo.getPageCount(); } catch (Throwable ignore) { totalPages = 0; }
        }
        if (totalPages <= 0) return "";

        final int chunkMaxChars = 20_000;
        final int maxPageChars = 120_000;
        final int chunkMaxTokens = 300;
        final int combineMaxTokens = 450;

        AssistantLlmClient.SummaryStyle chunkStyle = wholeDocChunkStyle(finalStyle);

        boolean anyPageTruncated = false;
        ArrayList<String> partSummaries = new ArrayList<>();

        StringBuilder chunk = new StringBuilder();
        int chunkStartPage = 0;
        int chunkEndPage = -1;

        for (int page = 0; page < totalPages; page++) {
            throwIfStopRequested(stopRequested);
            AssistantContextTextExtractor.TextResult pageRes = AssistantContextTextExtractor.pageText(repo, page, maxPageChars);
            if (pageRes.truncated) anyPageTruncated = true;
            String pageText = pageRes.text != null ? pageRes.text : "";
            String pageBlock = "Page " + (page + 1) + ":\n" + pageText;

            if (pageBlock.length() > chunkMaxChars) {
                if (chunk.length() > 0) {
                    throwIfStopRequested(stopRequested);
                    String part = summarizeChunkBlockingWithStatus(activity, provider, apiKey, chunkStyle, chunk.toString(), chunkStartPage, chunkEndPage, statusView, chunkMaxTokens, activeCallOut, stopRequested);
                    partSummaries.add(part);
                    chunk.setLength(0);
                }
                throwIfStopRequested(stopRequested);
                String part = summarizeOversizePageBlocking(activity, provider, apiKey, chunkStyle, pageBlock, page, statusView, chunkMaxChars, chunkMaxTokens, activeCallOut, stopRequested);
                partSummaries.add(part);
                chunkStartPage = page + 1;
                chunkEndPage = -1;
                continue;
            }

            if (chunk.length() > 0 && (chunk.length() + 2 + pageBlock.length()) > chunkMaxChars) {
                throwIfStopRequested(stopRequested);
                String part = summarizeChunkBlockingWithStatus(activity, provider, apiKey, chunkStyle, chunk.toString(), chunkStartPage, chunkEndPage, statusView, chunkMaxTokens, activeCallOut, stopRequested);
                partSummaries.add(part);
                chunk.setLength(0);
                chunkStartPage = page;
                chunkEndPage = -1;
            }

            if (chunk.length() > 0) chunk.append("\n\n");
            chunk.append(pageBlock);
            chunkEndPage = page;
        }

        if (chunk.length() > 0) {
            throwIfStopRequested(stopRequested);
            String part = summarizeChunkBlockingWithStatus(activity, provider, apiKey, chunkStyle, chunk.toString(), chunkStartPage, chunkEndPage, statusView, chunkMaxTokens, activeCallOut, stopRequested);
            partSummaries.add(part);
        }

        throwIfStopRequested(stopRequested);
        postStatus(activity, statusView, activity.getString(R.string.assistant_sheet_combining_summaries));
        String combined = combineSummariesToFitBlocking(activity, provider, apiKey, partSummaries, chunkMaxChars, combineMaxTokens, statusView, activeCallOut, stopRequested);
        throwIfStopRequested(stopRequested);
        String finalOut = AssistantLlmClient.summarizeBlocking(http, provider, apiKey, combined, finalStyle, 700, activeCallOut);
        if (finalOut == null) finalOut = "";
        finalOut = finalOut.trim();
        if (anyPageTruncated) {
            try {
                finalOut = finalOut + "\n\n" + activity.getString(R.string.assistant_sheet_whole_document_summary_truncated_pages_note);
            } catch (Throwable ignore) {}
        }
        return finalOut;
    }

    @NonNull
    private static AssistantLlmClient.SummaryStyle wholeDocChunkStyle(@NonNull AssistantLlmClient.SummaryStyle finalStyle) {
        if (finalStyle == AssistantLlmClient.SummaryStyle.DETAILED) return AssistantLlmClient.SummaryStyle.MEDIUM;
        return AssistantLlmClient.SummaryStyle.SHORT;
    }

    private static void throwIfStopRequested(@NonNull AtomicBoolean stopRequested) throws Exception {
        if (stopRequested.get()) throw new Exception("Canceled");
    }

    private static void postStatus(@NonNull OpenDroidPDFActivity activity,
                                   @Nullable TextView statusView,
                                   @NonNull String text) {
        if (statusView == null) return;
        activity.runOnUiThread(() -> {
            try {
                if (!statusView.isAttachedToWindow()) return;
            } catch (Throwable ignore) {}
            try { statusView.setText(text); } catch (Throwable ignore) {}
        });
    }

    @NonNull
    private static String summarizeChunkBlockingWithStatus(@NonNull OpenDroidPDFActivity activity,
                                                          @NonNull AssistantLlmProviderConfig provider,
                                                          @NonNull String apiKey,
                                                          @NonNull AssistantLlmClient.SummaryStyle chunkStyle,
                                                          @NonNull String chunkText,
                                                          int startPageIndex,
                                                          int endPageIndex,
                                                          @Nullable TextView statusView,
                                                          int maxTokens,
                                                          @NonNull AtomicReference<Call> activeCallOut,
                                                          @NonNull AtomicBoolean stopRequested) throws Exception {
        throwIfStopRequested(stopRequested);
        int start1 = Math.max(1, startPageIndex + 1);
        int end1 = Math.max(start1, endPageIndex + 1);
        if (start1 == end1) {
            postStatus(activity, statusView, activity.getString(R.string.assistant_sheet_summarizing_page, start1));
        } else {
            postStatus(activity, statusView, activity.getString(R.string.assistant_sheet_summarizing_pages, start1, end1));
        }
        throwIfStopRequested(stopRequested);
        String summary = AssistantLlmClient.summarizeBlocking(http, provider, apiKey, chunkText, chunkStyle, maxTokens, activeCallOut);
        return formatPartSummary(start1, end1, summary);
    }

    @NonNull
    private static String summarizeOversizePageBlocking(@NonNull OpenDroidPDFActivity activity,
                                                       @NonNull AssistantLlmProviderConfig provider,
                                                       @NonNull String apiKey,
                                                       @NonNull AssistantLlmClient.SummaryStyle chunkStyle,
                                                       @NonNull String pageBlock,
                                                       int pageIndex,
                                                       @Nullable TextView statusView,
                                                       int chunkMaxChars,
                                                       int maxTokens,
                                                       @NonNull AtomicReference<Call> activeCallOut,
                                                       @NonNull AtomicBoolean stopRequested) throws Exception {
        throwIfStopRequested(stopRequested);
        int page1 = Math.max(1, pageIndex + 1);
        postStatus(activity, statusView, activity.getString(R.string.assistant_sheet_summarizing_page, page1));

        int segmentMax = Math.max(1_000, chunkMaxChars - 128);
        ArrayList<String> segmentSummaries = new ArrayList<>();
        int start = 0;
        int seg = 1;
        while (start < pageBlock.length()) {
            throwIfStopRequested(stopRequested);
            int end = Math.min(pageBlock.length(), start + segmentMax);
            String segText = pageBlock.substring(start, end);
            String segSummary = AssistantLlmClient.summarizeBlocking(http, provider, apiKey, segText, AssistantLlmClient.SummaryStyle.SHORT, Math.min(220, Math.max(64, maxTokens)), activeCallOut);
            segmentSummaries.add("Segment " + seg + ":\n" + (segSummary != null ? segSummary.trim() : ""));
            start = end;
            seg++;
        }

        throwIfStopRequested(stopRequested);
        String merged = combineSummariesToFitBlocking(activity, provider, apiKey, segmentSummaries, chunkMaxChars, Math.min(320, Math.max(120, maxTokens)), statusView, activeCallOut, stopRequested);
        throwIfStopRequested(stopRequested);
        String summary = AssistantLlmClient.summarizeBlocking(http, provider, apiKey, merged, chunkStyle, maxTokens, activeCallOut);
        return formatPartSummary(page1, page1, summary);
    }

    @NonNull
    private static String combineSummariesToFitBlocking(@NonNull OpenDroidPDFActivity activity,
                                                       @NonNull AssistantLlmProviderConfig provider,
                                                       @NonNull String apiKey,
                                                       @NonNull List<String> summaries,
                                                       int maxChars,
                                                       int maxTokens,
                                                       @Nullable TextView statusView,
                                                       @NonNull AtomicReference<Call> activeCallOut,
                                                       @NonNull AtomicBoolean stopRequested) throws Exception {
        throwIfStopRequested(stopRequested);
        if (summaries.isEmpty()) return "";
        ArrayList<String> current = new ArrayList<>(summaries);
        int rounds = 0;
        while (rounds < 6) {
            throwIfStopRequested(stopRequested);
            String joined = joinBlocks(current, maxChars);
            if (joined.length() <= maxChars) return joined;
            rounds++;

            ArrayList<String> next = new ArrayList<>();
            List<String> groups = groupByMaxChars(current, maxChars);
            for (int i = 0; i < groups.size(); i++) {
                throwIfStopRequested(stopRequested);
                postStatus(activity, statusView, activity.getString(R.string.assistant_sheet_combining_summaries));
                String reduced = AssistantLlmClient.summarizeBlocking(http, provider, apiKey, groups.get(i), AssistantLlmClient.SummaryStyle.SHORT, maxTokens, activeCallOut);
                next.add(reduced != null ? reduced.trim() : "");
            }
            current = next;
        }
        throwIfStopRequested(stopRequested);
        String joined = joinBlocks(current, maxChars);
        if (joined.length() <= maxChars) return joined;
        return joined.substring(0, Math.min(joined.length(), maxChars));
    }

    @NonNull
    private static List<String> groupByMaxChars(@NonNull List<String> blocks, int maxChars) {
        if (blocks.isEmpty()) return Collections.emptyList();
        ArrayList<String> groups = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            String b = blocks.get(i);
            if (b == null) b = "";
            b = b.trim();
            if (b.isEmpty()) continue;
            if (sb.length() > 0 && (sb.length() + 2 + b.length()) > maxChars) {
                groups.add(sb.toString().trim());
                sb.setLength(0);
            }
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(b);
        }
        if (sb.length() > 0) groups.add(sb.toString().trim());
        return groups;
    }

    @NonNull
    private static String joinBlocks(@NonNull List<String> blocks, int maxCharsHint) {
        StringBuilder sb = new StringBuilder(Math.min(16_384, Math.max(0, maxCharsHint)));
        for (int i = 0; i < blocks.size(); i++) {
            String b = blocks.get(i);
            if (b == null) continue;
            b = b.trim();
            if (b.isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(b);
        }
        return sb.toString().trim();
    }

    @NonNull
    private static String formatPartSummary(int startPage1Based, int endPage1Based, @Nullable String summaryText) {
        String s = summaryText != null ? summaryText.trim() : "";
        StringBuilder sb = new StringBuilder();
        sb.append("Pages ").append(startPage1Based);
        if (endPage1Based != startPage1Based) sb.append("–").append(endPage1Based);
        sb.append(":\n");
        sb.append(s);
        return sb.toString().trim();
    }

    private static void showTocSectionPreviewAndSummarizeAsync(@NonNull OpenDroidPDFActivity activity,
                                                              @NonNull SharedPreferences prefs,
                                                              @Nullable MuPdfRepository repo,
                                                              @NonNull MuPDFReaderView docView,
                                                              @NonNull AssistantLlmProviderConfig provider,
                                                              @NonNull String apiKey,
                                                              @NonNull TocSectionScope tocScope,
                                                              @NonNull AssistantLlmClient.SummaryStyle style,
                                                              @NonNull AtomicBoolean summaryInFlight,
                                                              @Nullable TextView summaryStatus,
                                                              @NonNull TextView summaryOutput,
                                                              @NonNull Button summaryGenerate,
                                                              @Nullable ImageButton summaryStop,
                                                              @NonNull AtomicReference<Call> activeCallOut,
                                                              @NonNull AtomicBoolean stopRequested,
                                                              @Nullable Button summaryCopy,
                                                              @Nullable Button summaryInsert,
                                                              @Nullable Button summarySaveNote,
                                                              @Nullable Button summaryExport,
                                                              @NonNull AtomicBoolean noteSaveInFlight,
                                                              @NonNull AtomicBoolean exportInFlight) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_sheet_preview_title)
                .setMessage(R.string.assistant_context_loading)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> cancelled.set(true))
                .create();
        progress.show();

        executor.execute(() -> {
            AssistantContextTextExtractor.TextResult result = AssistantContextTextExtractor.pageRangeText(
                    repo,
                    tocScope.startPageIndex,
                    tocScope.endPageIndex,
                    MAX_PREVIEW_CHARS,
                    cancelled,
                    false
            );

            activity.runOnUiThread(() -> {
                try { progress.dismiss(); } catch (Throwable ignore) {}
                if (cancelled.get()) return;
                if (isActivityInvalid(activity)) return;

                String text = result.text != null ? result.text : "";
                String previewSummary = describeScope(activity, Scope.TOC_SECTION, tocScope.startPageIndex, text.length(), result.truncated, tocScope) + " • Summary";
                runWithPrivacyGate(activity, prefs, provider, previewSummary, text, R.string.assistant_sheet_generate, () ->
                        runSummaryRequestAsync(activity, provider, apiKey, text, style, summaryInFlight, summaryStatus, summaryOutput, summaryGenerate, summaryStop, activeCallOut, stopRequested, summaryCopy, summaryInsert, summarySaveNote, summaryExport, noteSaveInFlight, exportInFlight));
            });
        });
    }

    private static void showPreviewAndAskWithAttachmentsAsync(@NonNull OpenDroidPDFActivity activity,
                                                             @NonNull SharedPreferences prefs,
                                                             @NonNull MuPDFReaderView docView,
                                                             @NonNull String documentKey,
                                                             @NonNull String question,
                                                             @NonNull AssistantLlmProviderConfig provider,
                                                             @NonNull String apiKey,
                                                             @Nullable LinearLayout chatContainer,
                                                             @Nullable ScrollView chatScroll,
                                                             @Nullable View clearChatButton,
                                                             boolean showSources,
                                                             @NonNull BottomSheetBehavior<?>[] behaviorHolder,
                                                             @NonNull EditText prompt,
                                                             @NonNull Scope scope,
                                                             int pageIndex,
                                                             @NonNull String mainContextText,
                                                             boolean mainTruncated,
                                                             int attachmentsBudgetChars,
                                                             @Nullable Button sendButton,
                                                             @Nullable ImageButton stopButton,
                                                             @NonNull AtomicReference<Call> activeCallOut,
                                                             @NonNull AtomicBoolean stopRequested,
                                                             @NonNull AtomicReference<View> pendingBubbleOut) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_sheet_preview_title)
                .setMessage(R.string.assistant_context_loading)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> cancelled.set(true))
                .create();
        progress.show();

        executor.execute(() -> {
            AttachmentsContextResult atts = buildAttachmentsContextText(activity, documentKey, attachmentsBudgetChars, cancelled);

            String ctx = mainContextText != null ? mainContextText.trim() : "";
            boolean truncated = mainTruncated || atts.truncated;
            if (atts.text != null && !atts.text.trim().isEmpty()) {
                if (!ctx.isEmpty()) ctx = ctx + "\n\n";
                ctx = ctx + "Attachments (background context; do not cite):\n" + atts.text;
            }
            if (ctx.length() > MAX_PREVIEW_CHARS) {
                ctx = ctx.substring(0, MAX_PREVIEW_CHARS);
                truncated = true;
            }
            final String ctxFinal = ctx;
            final boolean truncatedFinal = truncated;

            activity.runOnUiThread(() -> {
                try { progress.dismiss(); } catch (Throwable ignore) {}
                if (cancelled.get()) return;
                if (isActivityInvalid(activity)) return;

                String previewSummary = describeScope(activity, scope, pageIndex, ctxFinal.length(), truncatedFinal, null) + " • Ask";
                final List<AssistantLlmClient.ChatMessage> chatHistory = boundedAskChatHistory(documentKey);
                String outgoing = formatAskOutgoingPreview(question, ctxFinal, chatHistory);
                runWithPrivacyGate(activity, prefs, provider, previewSummary, outgoing, R.string.assistant_sheet_send, () -> {
                    try { prompt.setText(""); } catch (Throwable ignore) {}
                    if (chatContainer == null) return;
                    runAskRequestAsync(activity,
                            documentKey,
                            question,
                            provider,
                            apiKey,
                            ctxFinal,
                            chatHistory,
                            chatContainer,
                            chatScroll,
                            clearChatButton,
                            showSources,
                            behaviorHolder,
                            docView,
                            sendButton,
                            stopButton,
                            activeCallOut,
                            stopRequested,
                            pendingBubbleOut);
                });
            });
        });
    }

    private static void showTocSectionPreviewAndAskAsync(@NonNull OpenDroidPDFActivity activity,
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
                                                        @NonNull BottomSheetBehavior<?>[] behaviorHolder,
                                                        @NonNull TocSectionScope tocScope,
                                                        @Nullable EditText prompt,
                                                        @Nullable Button sendButton,
                                                        @Nullable ImageButton stopButton,
                                                        @NonNull AtomicReference<Call> activeCallOut,
                                                        @NonNull AtomicBoolean stopRequested,
                                                        @NonNull AtomicReference<View> pendingBubbleOut) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        int attachmentsBudget = askAttachmentsBudgetChars(documentKey);
        int mainBudget = Math.max(1, MAX_PREVIEW_CHARS - attachmentsBudget);
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_sheet_preview_title)
                .setMessage(R.string.assistant_context_loading)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> cancelled.set(true))
                .create();
        progress.show();

        executor.execute(() -> {
            AssistantContextTextExtractor.TextResult result = AssistantContextTextExtractor.pageRangeText(
                    repo,
                    tocScope.startPageIndex,
                    tocScope.endPageIndex,
                    mainBudget,
                    cancelled,
                    true
            );

            String ctx = result.text != null ? result.text : "";
            boolean truncated = result.truncated;
            if (attachmentsBudget > 0) {
                AttachmentsContextResult atts = buildAttachmentsContextText(activity, documentKey, attachmentsBudget, cancelled);
                if (atts.text != null && !atts.text.trim().isEmpty()) {
                    ctx = ctx.trim();
                    if (!ctx.isEmpty()) ctx = ctx + "\n\n";
                    ctx = ctx + "Attachments (background context; do not cite):\n" + atts.text;
                }
                truncated = truncated || atts.truncated;
            }
            if (ctx.length() > MAX_PREVIEW_CHARS) {
                ctx = ctx.substring(0, MAX_PREVIEW_CHARS);
                truncated = true;
            }
            final String ctxFinal = ctx;
            final boolean truncatedFinal = truncated;

            activity.runOnUiThread(() -> {
                try { progress.dismiss(); } catch (Throwable ignore) {}
                if (cancelled.get()) return;
                if (isActivityInvalid(activity)) return;

                String previewSummary = describeScope(activity, Scope.TOC_SECTION, tocScope.startPageIndex, ctxFinal.length(), truncatedFinal, tocScope) + " • Ask";
                final List<AssistantLlmClient.ChatMessage> chatHistory = boundedAskChatHistory(documentKey);
                String outgoing = formatAskOutgoingPreview(question, ctxFinal, chatHistory);
                runWithPrivacyGate(activity, prefs, provider, previewSummary, outgoing, R.string.assistant_sheet_send, () -> {
                    if (chatContainer == null) return;
                    try { if (prompt != null) prompt.setText(""); } catch (Throwable ignore) {}
                    runAskRequestAsync(activity,
                            documentKey,
                            question,
                            provider,
                            apiKey,
                            ctxFinal,
                            chatHistory,
                            chatContainer,
                            chatScroll,
                            clearChatButton,
                            showSources,
                            behaviorHolder,
                            docView,
                            sendButton,
                            stopButton,
                            activeCallOut,
                            stopRequested,
                            pendingBubbleOut);
                });
            });
        });
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
                                                       @NonNull BottomSheetBehavior<?>[] behaviorHolder,
                                                       @Nullable EditText prompt,
                                                       @Nullable Button sendButton,
                                                       @Nullable ImageButton stopButton,
                                                       @NonNull AtomicReference<Call> activeCallOut,
                                                       @NonNull AtomicBoolean stopRequested,
                                                       @NonNull AtomicReference<View> pendingBubbleOut) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        int attachmentsBudget = askAttachmentsBudgetChars(documentKey);
        int mainBudget = Math.max(1, MAX_PREVIEW_CHARS - attachmentsBudget);
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_sheet_preview_title)
                .setMessage(R.string.assistant_context_loading)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> cancelled.set(true))
                .create();
        progress.show();

        executor.execute(() -> {
            AssistantContextTextExtractor.TextResult result =
                    AssistantContextTextExtractor.documentText(repo, mainBudget, cancelled);

            String ctx = result.text != null ? result.text : "";
            boolean truncated = result.truncated;
            if (attachmentsBudget > 0) {
                AttachmentsContextResult atts = buildAttachmentsContextText(activity, documentKey, attachmentsBudget, cancelled);
                if (atts.text != null && !atts.text.trim().isEmpty()) {
                    ctx = ctx.trim();
                    if (!ctx.isEmpty()) ctx = ctx + "\n\n";
                    ctx = ctx + "Attachments (background context; do not cite):\n" + atts.text;
                }
                truncated = truncated || atts.truncated;
            }
            if (ctx.length() > MAX_PREVIEW_CHARS) {
                ctx = ctx.substring(0, MAX_PREVIEW_CHARS);
                truncated = true;
            }
            final String ctxFinal = ctx;
            final boolean truncatedFinal = truncated;

            activity.runOnUiThread(() -> {
                try { progress.dismiss(); } catch (Throwable ignore) {}
                if (cancelled.get()) return;
                if (isActivityInvalid(activity)) return;

                int pageIndex = safeSelectedPageIndex(docView);
                String previewSummary = describeScope(activity, Scope.DOCUMENT, pageIndex, ctxFinal.length(), truncatedFinal, null) + " • Ask";
                final List<AssistantLlmClient.ChatMessage> chatHistory = boundedAskChatHistory(documentKey);
                String outgoing = formatAskOutgoingPreview(question, ctxFinal, chatHistory);
                runWithPrivacyGate(activity, prefs, provider, previewSummary, outgoing, R.string.assistant_sheet_send, () -> {
                    if (chatContainer == null) return;
                    try { if (prompt != null) prompt.setText(""); } catch (Throwable ignore) {}
                    runAskRequestAsync(activity,
                            documentKey,
                            question,
                            provider,
                            apiKey,
                            ctxFinal,
                            chatHistory,
                            chatContainer,
                            chatScroll,
                            clearChatButton,
                            showSources,
                            behaviorHolder,
                            docView,
                            sendButton,
                            stopButton,
                            activeCallOut,
                            stopRequested,
                            pendingBubbleOut);
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
