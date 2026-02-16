package org.opendroidpdf.app.assistant;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;

import java.util.ArrayList;
import java.util.List;

final class AssistantSheetChatUi {
    private AssistantSheetChatUi() {}

    static void restoreAskTranscript(@NonNull OpenDroidPDFActivity activity,
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

    static void clearAskChat(@NonNull String documentKey,
                             @Nullable LinearLayout chatContainer,
                             @Nullable View clearChatButton) {
        AssistantAskTranscriptStore.clear(documentKey);
        if (chatContainer != null) chatContainer.removeAllViews();
        updateClearChatEnabled(clearChatButton, documentKey);
    }

    static void updateClearChatEnabled(@Nullable View clearChatButton, @NonNull String documentKey) {
        if (clearChatButton == null) return;
        try { clearChatButton.setEnabled(AssistantAskTranscriptStore.hasMessages(documentKey)); } catch (Throwable ignore) {}
    }

    static void setChatBubbleText(@Nullable View bubble, @NonNull CharSequence text) {
        if (bubble == null || text == null) return;
        if (!(bubble instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) bubble;
        if (vg.getChildCount() <= 0) return;
        View first = vg.getChildAt(0);
        if (first instanceof TextView) {
            ((TextView) first).setText(text);
        }
    }

    static void applySourcesVisibility(@NonNull View root, boolean visible) {
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

    static View buildChatBubble(@NonNull OpenDroidPDFActivity activity,
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
        int pad = AssistantSheetUi.dpToPx(activity, 10);
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
                int hPad = AssistantSheetUi.dpToPx(activity, 8);
                int vPad = AssistantSheetUi.dpToPx(activity, 4);
                badge.setPadding(hPad, vPad, hPad, vPad);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = AssistantSheetUi.dpToPx(activity, 6);
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
        containerLp.topMargin = AssistantSheetUi.dpToPx(activity, 10);
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
        scrollLp.topMargin = AssistantSheetUi.dpToPx(activity, 6);
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
            if (i > 0) lp.leftMargin = AssistantSheetUi.dpToPx(activity, 8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> AssistantSheetUi.prefillAndSendAskQuestion(activity, qFinal));
            row.addView(chip);
        }

        scroll.addView(row);
        container.addView(scroll);
        return container;
    }

    @NonNull
    private static TextView buildTextChip(@NonNull OpenDroidPDFActivity ctx, @NonNull String text) {
        TextView chip = new TextView(ctx);
        chip.setText(text);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setTextSize(12);
        chip.setBackgroundResource(R.drawable.bg_assistant_action_chip);
        int hPad = AssistantSheetUi.dpToPx(ctx, 10);
        int vPad = AssistantSheetUi.dpToPx(ctx, 6);
        chip.setPadding(hPad, vPad, hPad, vPad);
        chip.setClickable(true);
        chip.setFocusable(true);
        return chip;
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
        containerLp.topMargin = AssistantSheetUi.dpToPx(activity, 10);
        container.setLayoutParams(containerLp);

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView copy = buildActionChip(activity, R.string.assistant_sheet_copy);
        copy.setOnClickListener(v -> {
            AssistantSheetUi.copyToClipboard(activity, "assistant_answer", text);
            try { activity.showInfo(activity.getString(R.string.assistant_sheet_copied)); } catch (Throwable ignore) {}
        });
        row1.addView(copy);

        TextView save = buildActionChip(activity, R.string.assistant_sheet_save_note);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.leftMargin = AssistantSheetUi.dpToPx(activity, 8);
        save.setLayoutParams(saveLp);
        save.setOnClickListener(v -> AssistantSheetUi.saveAssistantAnswerNoteAsync(activity, text, citationPages1Based, save));
        row1.addView(save);

        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = AssistantSheetUi.dpToPx(activity, 6);
        row2.setLayoutParams(row2Lp);

        TextView insert = buildActionChip(activity, R.string.assistant_sheet_insert_into_document);
        insert.setOnClickListener(v -> AssistantSheetUi.promptInsertTextIntoDocument(activity, docView, behaviorHolder, text, citationPages1Based));
        row2.addView(insert);

        TextView export = buildActionChip(activity, R.string.assistant_sheet_export);
        LinearLayout.LayoutParams exportLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        exportLp.leftMargin = AssistantSheetUi.dpToPx(activity, 8);
        export.setLayoutParams(exportLp);
        export.setOnClickListener(v -> AssistantSheetUi.shareAssistantAnswer(activity, text, citationPages1Based));
        row2.addView(export);

        container.addView(row1);
        container.addView(row2);
        return container;
    }

    @NonNull
    private static TextView buildActionChip(@NonNull OpenDroidPDFActivity ctx, int labelRes) {
        TextView chip = new TextView(ctx);
        chip.setText(labelRes);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setTextSize(12);
        chip.setBackgroundResource(R.drawable.bg_assistant_action_chip);
        int hPad = AssistantSheetUi.dpToPx(ctx, 10);
        int vPad = AssistantSheetUi.dpToPx(ctx, 6);
        chip.setPadding(hPad, vPad, hPad, vPad);
        chip.setClickable(true);
        chip.setFocusable(true);
        return chip;
    }
}
