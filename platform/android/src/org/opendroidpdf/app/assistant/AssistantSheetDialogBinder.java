package org.opendroidpdf.app.assistant;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.document.DocumentAccessIntents;
import org.opendroidpdf.app.helpers.RequestCodes;
import org.opendroidpdf.app.preferences.PreferencesNames;

import java.util.concurrent.atomic.AtomicBoolean;

final class AssistantSheetDialogBinder {
    private static final float PEEK_RATIO = 0.20f;
    private static final float HALF_RATIO = 0.60f;
    private static final float EXPANDED_OFFSET_RATIO = 0.20f; // 80% height.

    static final class Views {
        @NonNull final View root;

        @Nullable final ImageButton close;
        @Nullable final ImageButton options;
        @Nullable final ImageButton clearChat;
        @Nullable final ImageButton expandToggle;

        @Nullable final RadioGroup modeGroup;
        @Nullable final RadioGroup scopeGroup;

        @Nullable final TextView providerLine;
        @Nullable final Button preview;
        @Nullable final Button setupProvider;

        @Nullable final android.widget.ViewFlipper contentFlipper;

        @Nullable final LinearLayout chatContainer;
        @Nullable final ScrollView chatScroll;

        @Nullable final View attachmentsScroll;
        @Nullable final LinearLayout attachmentsContainer;

        @Nullable final ImageButton attach;
        @Nullable final ImageButton mic;
        @Nullable final EditText prompt;
        @Nullable final Button send;
        @Nullable final ImageButton stopAsk;

        @Nullable final RadioGroup summaryStyleGroup;
        @Nullable final Button summaryGenerate;
        @Nullable final ImageButton summaryStop;
        @Nullable final TextView summaryStatus;
        @Nullable final ScrollView summaryScroll;
        @Nullable final TextView summaryOutput;
        @Nullable final Button summaryInsert;
        @Nullable final Button summaryExport;
        @Nullable final Button summarySaveNote;
        @Nullable final Button summaryCopy;

        @Nullable final TextView readStatus;
        @Nullable final TextView readNowReading;
        @Nullable final TextView readExcerpt;
        @Nullable final Button readPlayPause;
        @Nullable final Button readStop;

        private Views(@NonNull View root) {
            this.root = root;

            close = root.findViewById(R.id.assistant_sheet_close);
            options = root.findViewById(R.id.assistant_sheet_options);
            clearChat = root.findViewById(R.id.assistant_sheet_clear_chat);
            expandToggle = root.findViewById(R.id.assistant_sheet_expand_toggle);

            modeGroup = root.findViewById(R.id.assistant_sheet_mode_group);
            scopeGroup = root.findViewById(R.id.assistant_sheet_scope_group);

            providerLine = root.findViewById(R.id.assistant_sheet_provider_line);
            preview = root.findViewById(R.id.assistant_sheet_preview);
            setupProvider = root.findViewById(R.id.assistant_sheet_setup_provider);

            View flipper = root.findViewById(R.id.assistant_sheet_content_flipper);
            contentFlipper = flipper instanceof android.widget.ViewFlipper ? (android.widget.ViewFlipper) flipper : null;

            chatContainer = root.findViewById(R.id.assistant_sheet_chat_container);
            chatScroll = root.findViewById(R.id.assistant_sheet_chat_scroll);

            attachmentsScroll = root.findViewById(R.id.assistant_sheet_attachments_scroll);
            attachmentsContainer = root.findViewById(R.id.assistant_sheet_attachments_container);

            attach = root.findViewById(R.id.assistant_sheet_attach);
            mic = root.findViewById(R.id.assistant_sheet_mic);
            prompt = root.findViewById(R.id.assistant_sheet_prompt);
            send = root.findViewById(R.id.assistant_sheet_send);
            stopAsk = root.findViewById(R.id.assistant_sheet_stop);

            summaryStyleGroup = root.findViewById(R.id.assistant_sheet_summary_style_group);
            summaryGenerate = root.findViewById(R.id.assistant_sheet_summary_generate);
            summaryStop = root.findViewById(R.id.assistant_sheet_summary_stop);
            summaryStatus = root.findViewById(R.id.assistant_sheet_summary_status);
            summaryScroll = root.findViewById(R.id.assistant_sheet_summary_scroll);
            summaryOutput = root.findViewById(R.id.assistant_sheet_summary_output);
            summaryInsert = root.findViewById(R.id.assistant_sheet_summary_insert_into_document);
            summaryExport = root.findViewById(R.id.assistant_sheet_summary_export);
            summarySaveNote = root.findViewById(R.id.assistant_sheet_summary_save_note);
            summaryCopy = root.findViewById(R.id.assistant_sheet_summary_copy);

            readStatus = root.findViewById(R.id.assistant_sheet_read_aloud_status);
            readNowReading = root.findViewById(R.id.assistant_sheet_read_aloud_now_reading);
            readExcerpt = root.findViewById(R.id.assistant_sheet_read_aloud_excerpt);
            readPlayPause = root.findViewById(R.id.assistant_sheet_read_aloud_play_pause);
            readStop = root.findViewById(R.id.assistant_sheet_read_aloud_stop);
        }
    }

    static final class Binding {
        @NonNull final BottomSheetDialog dialog;
        @NonNull final Views views;
        @NonNull final BottomSheetBehavior<?>[] behaviorHolder;

        private Binding(@NonNull BottomSheetDialog dialog,
                        @NonNull Views views,
                        @NonNull BottomSheetBehavior<?>[] behaviorHolder) {
            this.dialog = dialog;
            this.views = views;
            this.behaviorHolder = behaviorHolder;
        }
    }

    @NonNull
    static Binding bind(@NonNull OpenDroidPDFActivity activity, int initialModeCheckedId) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_assistant_sheet, null);
        dialog.setContentView(root);

        Views views = new Views(root);
        final BottomSheetBehavior<?>[] behaviorHolder = new BottomSheetBehavior<?>[1];

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
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

                int screenHeight;
                try {
                    screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
                } catch (Throwable ignore) {
                    screenHeight = 0;
                }
                if (screenHeight > 0) {
                    behavior.setPeekHeight(Math.max(1, Math.round(screenHeight * PEEK_RATIO)));
                    behavior.setHalfExpandedRatio(HALF_RATIO);
                    behavior.setExpandedOffset(Math.max(0, Math.round(screenHeight * EXPANDED_OFFSET_RATIO)));
                }

                behavior.setFitToContents(false);
                behavior.setHideable(true);
                behavior.setSkipCollapsed(false);
                behavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);

                final ImageButton expandToggle = views.expandToggle;
                if (expandToggle != null) updateExpandIcon(expandToggle, behavior.getState());
                behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                    @Override public void onStateChanged(@NonNull View bs, int newState) {
                        if (expandToggle != null) updateExpandIcon(expandToggle, newState);
                    }
                    @Override public void onSlide(@NonNull View bs, float slideOffset) {}
                });
            } catch (Throwable ignore) {}
        });

        if (views.close != null) views.close.setOnClickListener(v -> dialog.dismiss());

        if (views.expandToggle != null) {
            views.expandToggle.setOnClickListener(v -> {
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

        // Mode switching.
        if (views.modeGroup != null && views.contentFlipper != null) {
            RadioGroup modeGroup = views.modeGroup;
            modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                boolean isAsk = checkedId == R.id.assistant_sheet_mode_ask;
                if (checkedId == R.id.assistant_sheet_mode_summary) {
                    views.contentFlipper.setDisplayedChild(1);
                } else if (checkedId == R.id.assistant_sheet_mode_read_aloud) {
                    views.contentFlipper.setDisplayedChild(2);
                } else {
                    views.contentFlipper.setDisplayedChild(0);
                }
                if (views.clearChat != null) views.clearChat.setVisibility(isAsk ? View.VISIBLE : View.GONE);
            });
            if (initialModeCheckedId != 0) {
                try { modeGroup.check(initialModeCheckedId); } catch (Throwable ignore) {}
            }
            try {
                if (views.clearChat != null) {
                    views.clearChat.setVisibility(modeGroup.getCheckedRadioButtonId() == R.id.assistant_sheet_mode_ask ? View.VISIBLE : View.GONE);
                }
            } catch (Throwable ignore) {}
        }

        wireAttachButton(activity, views.attach);

        return new Binding(dialog, views, behaviorHolder);
    }

    static void bindProviderRow(@NonNull OpenDroidPDFActivity activity, @NonNull Views views) {
        SharedPreferences prefs = activity.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
        boolean enabled = AssistantSheetUi.safeGetBoolean(prefs, SettingsActivity.PREF_ASSISTANT_ENABLED, false);
        AssistantLlmProviderConfig provider = AssistantLlmProvidersStore.defaultProviderOrNull(activity);
        boolean sessionAllowed = provider != null && AssistantSheetUi.isSessionAllowed(activity, provider);
        updateProviderLine(activity, views.providerLine, enabled, provider, sessionAllowed);

        if (views.setupProvider != null) {
            views.setupProvider.setOnClickListener(v -> {
                try {
                    if (!AssistantSheetUi.safeGetBoolean(prefs, SettingsActivity.PREF_ASSISTANT_ENABLED, false)) {
                        activity.startActivity(new Intent(activity, SettingsActivity.class));
                    } else {
                        activity.startActivity(new Intent(activity, AssistantProvidersActivity.class));
                    }
                } catch (Throwable ignore) {}
            });
        }
    }

    static void wireOptionsMenu(@NonNull OpenDroidPDFActivity activity,
                               @NonNull Views views,
                               @NonNull String documentKey,
                               @NonNull AtomicBoolean showSources,
                               @Nullable LinearLayout chatContainer,
                               @Nullable View attachmentsScroll,
                               @Nullable LinearLayout attachmentsContainer,
                               @NonNull Runnable onVoiceAssistant) {
        if (views.options == null) return;
        views.options.setOnClickListener(v -> {
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
                    boolean allowed = currentProvider != null && AssistantSheetUi.isSessionAllowed(activity, currentProvider);
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
                    AssistantSheetUi.clearAskChat(documentKey, chatContainer, views.clearChat);
                    return true;
                }
                if (id == R.id.assistant_sheet_action_require_preview_again) {
                    AssistantSheetUi.clearSessionApproval(activity);
                    try {
                        SharedPreferences prefs = activity.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
                        boolean enabled = AssistantSheetUi.safeGetBoolean(prefs, SettingsActivity.PREF_ASSISTANT_ENABLED, false);
                        AssistantLlmProviderConfig currentProvider = AssistantLlmProvidersStore.defaultProviderOrNull(activity);
                        boolean sessionAllowed = currentProvider != null && AssistantSheetUi.isSessionAllowed(activity, currentProvider);
                        updateProviderLine(activity, views.providerLine, enabled, currentProvider, sessionAllowed);
                    } catch (Throwable ignore) {}
                    return true;
                }
                if (id == R.id.assistant_sheet_action_toggle_sources) {
                    boolean next = !showSources.get();
                    showSources.set(next);
                    if (chatContainer != null) {
                        for (int i = 0; i < chatContainer.getChildCount(); i++) {
                            AssistantSheetUi.applySourcesVisibility(chatContainer.getChildAt(i), next);
                        }
                    }
                    return true;
                }
                if (id == R.id.assistant_sheet_action_clear_attachments) {
                    AssistantAttachmentsStore.clear(documentKey);
                    if (attachmentsScroll != null && attachmentsContainer != null) {
                        AssistantSheetUi.renderAttachmentsRow(activity, documentKey, attachmentsScroll, attachmentsContainer);
                    }
                    try { activity.showInfo(activity.getString(R.string.assistant_sheet_attachments_cleared)); } catch (Throwable ignore) {}
                    return true;
                }
                if (id == R.id.assistant_sheet_action_voice_assistant) {
                    try { onVoiceAssistant.run(); } catch (Throwable ignore) {}
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private static void updateProviderLine(@NonNull OpenDroidPDFActivity activity,
                                          @Nullable TextView providerLine,
                                          boolean enabled,
                                          @Nullable AssistantLlmProviderConfig provider,
                                          boolean sessionAllowed) {
        if (providerLine == null) return;
        if (!enabled) {
            providerLine.setText(R.string.assistant_sheet_provider_disabled);
            return;
        }
        if (provider == null) {
            providerLine.setText(R.string.assistant_sheet_provider_unconfigured);
            return;
        }
        if (sessionAllowed) {
            providerLine.setText(activity.getString(R.string.assistant_sheet_provider_configured_allowed, provider.name()));
            return;
        }
        providerLine.setText(activity.getString(R.string.assistant_sheet_provider_configured, provider.name()));
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

    private static void wireAttachButton(@NonNull OpenDroidPDFActivity activity, @Nullable ImageButton attach) {
        if (attach == null) return;
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
}
