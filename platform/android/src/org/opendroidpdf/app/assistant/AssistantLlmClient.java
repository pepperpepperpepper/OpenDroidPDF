package org.opendroidpdf.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class AssistantLlmClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public enum SummaryStyle { SHORT, MEDIUM, DETAILED }

    public static final class AskResult {
        @NonNull public final String answerText;
        @Nullable public final int[] citationNumbers;
        @Nullable public final int[] citationPages1Based;
        @Nullable public final String[] relatedQuestions;

        private AskResult(@NonNull String answerText,
                          @Nullable int[] citationNumbers,
                          @Nullable int[] citationPages1Based,
                          @Nullable String[] relatedQuestions) {
            this.answerText = answerText != null ? answerText : "";
            this.citationNumbers = citationNumbers;
            this.citationPages1Based = citationPages1Based;
            this.relatedQuestions = relatedQuestions;
        }

        @NonNull
        public static AskResult plainText(@Nullable String text) {
            String out = text != null ? text.trim() : "";
            return new AskResult(out, null, null, null);
        }
    }

    public static final class ChatMessage {
        @NonNull public final String role;
        @NonNull public final String content;

        public ChatMessage(@NonNull String role, @NonNull String content) {
            this.role = role != null ? role : "user";
            this.content = content != null ? content : "";
        }

        @NonNull
        public static ChatMessage user(@NonNull String content) {
            return new ChatMessage("user", content);
        }

        @NonNull
        public static ChatMessage assistant(@NonNull String content) {
            return new ChatMessage("assistant", content);
        }
    }

    private AssistantLlmClient() {}

    @NonNull
    public static String summarizeBlocking(
            @NonNull OkHttpClient http,
            @NonNull AssistantLlmProviderConfig provider,
            @NonNull String apiKey,
            @NonNull String text,
            @NonNull SummaryStyle style) throws IOException {
        return summarizeBlocking(http, provider, apiKey, text, style, 700, null);
    }

    @NonNull
    public static String summarizeBlocking(
            @NonNull OkHttpClient http,
            @NonNull AssistantLlmProviderConfig provider,
            @NonNull String apiKey,
            @NonNull String text,
            @NonNull SummaryStyle style,
            int maxTokens) throws IOException {
        return summarizeBlocking(http, provider, apiKey, text, style, maxTokens, null);
    }

    @NonNull
    public static String summarizeBlocking(
            @NonNull OkHttpClient http,
            @NonNull AssistantLlmProviderConfig provider,
            @NonNull String apiKey,
            @NonNull String text,
            @NonNull SummaryStyle style,
            int maxTokens,
            @Nullable AtomicReference<Call> activeCallOut) throws IOException {
        String instruction = summaryInstruction(style);
        int tokens = Math.max(16, Math.min(maxTokens, 4096));
        return summarizeWithInstructionBlocking(http, provider, apiKey, instruction, text, tokens, activeCallOut);
    }

    @NonNull
    private static String summarizeWithInstructionBlocking(
            @NonNull OkHttpClient http,
            @NonNull AssistantLlmProviderConfig provider,
            @NonNull String apiKey,
            @NonNull String instruction,
            @NonNull String text,
            int maxTokens,
            @Nullable AtomicReference<Call> activeCallOut) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", provider.model());
            body.put("temperature", 0.2);
            body.put("max_tokens", maxTokens);

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", "You are a helpful assistant. Return only the requested summary text."));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", instruction + "\n\nTEXT:\n" + text));
            body.put("messages", messages);
        } catch (Throwable t) {
            throw new IOException("Failed to build request JSON", t);
        }

        String url = chatCompletionsUrl(provider.baseUrl());
        Request req = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        Call call = http.newCall(req);
        if (activeCallOut != null) activeCallOut.set(call);
        try (Response resp = call.execute()) {
            String payload = readBody(resp);
            if (!resp.isSuccessful()) {
                throw new IOException("LLM request failed (" + resp.code() + "): " + truncate(payload, 800));
            }
            try {
                JSONObject json = new JSONObject(payload);
                String out = extractAssistantText(json);
                if (out == null || out.trim().isEmpty()) throw new IOException("Provider returned empty content");
                return out.trim();
            } catch (IOException e) {
                throw e;
            } catch (Throwable t) {
                throw new IOException("Failed to parse provider response: " + truncate(payload, 800), t);
            }
        } finally {
            if (activeCallOut != null) activeCallOut.compareAndSet(call, null);
        }
    }

    @NonNull
    public static String testChatCompletionBlocking(
            @NonNull OkHttpClient http,
            @NonNull AssistantLlmProviderConfig provider,
            @NonNull String apiKey) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", provider.model());
            body.put("temperature", 0);
            body.put("max_tokens", 16);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "user").put("content", "Reply with OK."));
            body.put("messages", messages);
        } catch (Throwable t) {
            throw new IOException("Failed to build request JSON", t);
        }

        Request req = new Request.Builder()
                .url(chatCompletionsUrl(provider.baseUrl()))
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String payload = readBody(resp);
            if (!resp.isSuccessful()) {
                throw new IOException("Test failed (" + resp.code() + "): " + truncate(payload, 800));
            }
            try {
                JSONObject json = new JSONObject(payload);
                String out = extractAssistantText(json);
                if (out == null || out.trim().isEmpty()) throw new IOException("Provider returned empty content");
                return out.trim();
            } catch (IOException e) {
                throw e;
            } catch (Throwable t) {
                throw new IOException("Failed to parse provider response: " + truncate(payload, 800), t);
            }
        }
    }

    @NonNull
    public static AskResult askBlocking(
            @NonNull OkHttpClient http,
            @NonNull AssistantLlmProviderConfig provider,
            @NonNull String apiKey,
            @NonNull String question,
            @NonNull String contextText) throws IOException {
        return askBlocking(http, provider, apiKey, question, contextText, null);
    }

    @NonNull
    public static AskResult askBlocking(
            @NonNull OkHttpClient http,
            @NonNull AssistantLlmProviderConfig provider,
            @NonNull String apiKey,
            @NonNull String question,
            @NonNull String contextText,
            @Nullable List<ChatMessage> chatHistory) throws IOException {
        return askBlocking(http, provider, apiKey, question, contextText, chatHistory, null);
    }

    @NonNull
    public static AskResult askBlocking(
            @NonNull OkHttpClient http,
            @NonNull AssistantLlmProviderConfig provider,
            @NonNull String apiKey,
            @NonNull String question,
            @NonNull String contextText,
            @Nullable List<ChatMessage> chatHistory,
            @Nullable AtomicReference<Call> activeCallOut) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", provider.model());
            body.put("temperature", 0.2);
            body.put("max_tokens", 900);

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", "You answer questions about a document using the provided context.\n"
                            + "You may also be given prior conversation messages to help with follow-up questions.\n"
                            + "Use prior conversation only for continuity; base factual claims and citations only on the provided context.\n"
                            + "The context contains blocks like:\n"
                            + "Page 12:\n"
                            + "<text>\n\n"
                            + "The context may also include an Attachments section with additional background text.\n"
                            + "Do not cite attachments; citations must be page numbers from the main document blocks only.\n\n"
                            + "Return a single JSON object with exactly these keys:\n"
                            + "- answerText: string\n"
                            + "- citations: array of 1-based page numbers (integers)\n"
                            + "- relatedQuestions: array of 2–4 short follow-up questions (strings)\n\n"
                            + "Rules:\n"
                            + "- Use only page numbers that appear in the context.\n"
                            + "- If you cannot support an answer with the context, say so in answerText and return citations: [].\n"
                            + "- If there are no good follow-ups, return relatedQuestions: [].\n"
                            + "- Do not wrap JSON in code fences and do not include any other text."));
            if (chatHistory != null && !chatHistory.isEmpty()) {
                for (int i = 0; i < chatHistory.size(); i++) {
                    ChatMessage m = chatHistory.get(i);
                    if (m == null) continue;
                    String role = m.role != null ? m.role.trim() : "";
                    if (!"user".equals(role) && !"assistant".equals(role)) continue;
                    String content = m.content != null ? m.content.trim() : "";
                    if (content.isEmpty()) continue;
                    messages.put(new JSONObject()
                            .put("role", role)
                            .put("content", content));
                }
            }
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", "QUESTION:\n" + question + "\n\nCONTEXT:\n" + contextText));
            body.put("messages", messages);
        } catch (Throwable t) {
            throw new IOException("Failed to build request JSON", t);
        }

        Request req = new Request.Builder()
                .url(chatCompletionsUrl(provider.baseUrl()))
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        Call call = http.newCall(req);
        if (activeCallOut != null) activeCallOut.set(call);
        try (Response resp = call.execute()) {
            String payload = readBody(resp);
            if (!resp.isSuccessful()) {
                throw new IOException("LLM request failed (" + resp.code() + "): " + truncate(payload, 800));
            }
            try {
                JSONObject json = new JSONObject(payload);
                String out = extractAssistantText(json);
                if (out == null || out.trim().isEmpty()) throw new IOException("Provider returned empty content");
                return parseAskResult(out);
            } catch (IOException e) {
                throw e;
            } catch (Throwable t) {
                throw new IOException("Failed to parse provider response: " + truncate(payload, 800), t);
            }
        } finally {
            if (activeCallOut != null) activeCallOut.compareAndSet(call, null);
        }
    }

    @NonNull
    private static String summaryInstruction(@NonNull SummaryStyle style) {
        switch (style) {
            case SHORT:
                return "Write a short summary in 3–5 bullet points.";
            case DETAILED:
                return "Write a detailed summary with headings and bullet points when helpful.";
            case MEDIUM:
            default:
                return "Write a medium-length summary in bullet points.";
        }
    }

    @NonNull
    public static String chatCompletionsUrl(@NonNull String baseUrl) {
        String b = AssistantLlmProviderConfig.normalizeBaseUrl(baseUrl);
        return b + "/v1/chat/completions";
    }

    @Nullable
    private static String extractAssistantText(@NonNull JSONObject json) {
        // OpenAI-compatible Chat Completions:
        // { choices: [ { message: { content: "..." } } ] }
        try {
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject first = choices.optJSONObject(0);
                if (first != null) {
                    JSONObject msg = first.optJSONObject("message");
                    if (msg != null) {
                        String content = msg.optString("content", null);
                        if (content != null) return content;
                    }
                    String text = first.optString("text", null);
                    if (text != null) return text;
                }
            }
        } catch (Throwable ignore) {}

        // Some OpenAI-like responses return `output_text` (Responses API).
        try {
            String outputText = json.optString("output_text", null);
            if (outputText != null && !outputText.trim().isEmpty()) return outputText;
        } catch (Throwable ignore) {}

        return null;
    }

    @NonNull
    private static String readBody(@NonNull Response resp) throws IOException {
        ResponseBody body = resp.body();
        if (body == null) return "";
        return body.string();
    }

    @NonNull
    private static String truncate(@Nullable String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max)) + "…";
    }

    @NonNull
    private static AskResult parseAskResult(@NonNull String content) {
        String raw = stripCodeFences(content);
        String trimmed = raw != null ? raw.trim() : "";
        if (trimmed.isEmpty()) return AskResult.plainText("");

        JSONObject obj = tryParseJsonObject(trimmed);
        if (obj == null) {
            AskResult fallback = tryParseAskResultFallback(trimmed);
            if (fallback != null) return fallback;
            return AskResult.plainText(trimmed);
        }

        String answerText = null;
        try { answerText = obj.optString("answerText", null); } catch (Throwable ignore) {}
        if (answerText == null || answerText.trim().isEmpty()) {
            try { answerText = obj.optString("answer", null); } catch (Throwable ignore) {}
        }
        if (answerText == null || answerText.trim().isEmpty()) {
            try { answerText = obj.optString("text", null); } catch (Throwable ignore) {}
        }
        if (answerText == null) answerText = "";
        answerText = answerText.trim();

        JSONArray citations = null;
        try { citations = obj.optJSONArray("citations"); } catch (Throwable ignore) { citations = null; }

        JSONArray related = null;
        try { related = obj.optJSONArray("relatedQuestions"); } catch (Throwable ignore) { related = null; }
        if (related == null) {
            try { related = obj.optJSONArray("followUpQuestions"); } catch (Throwable ignore) { related = null; }
        }
        String[] relatedQuestions = parseRelatedQuestions(related, 4, 160);

        int[] pages1Based = parseCitationPages1Based(citations, 12);
        int[] numbers = null;
        if (pages1Based != null) {
            numbers = new int[pages1Based.length];
            for (int i = 0; i < numbers.length; i++) numbers[i] = i + 1;
        }
        boolean extractedAny = !answerText.isEmpty() || pages1Based != null || relatedQuestions != null;
        if (!extractedAny) {
            AskResult fallback = tryParseAskResultFallback(trimmed);
            if (fallback != null) return fallback;
            return AskResult.plainText(trimmed);
        }
        if (answerText.isEmpty()) answerText = trimmed;
        return new AskResult(answerText, numbers, pages1Based, relatedQuestions);
    }

    @Nullable
    private static AskResult tryParseAskResultFallback(@NonNull String text) {
        String candidate = extractJsonObjectSubstring(text);
        if (candidate == null) return null;

        String answerText = extractJsonStringValue(candidate, "answerText");
        if (answerText == null || answerText.trim().isEmpty()) {
            answerText = extractJsonStringValue(candidate, "answer");
        }
        if (answerText == null || answerText.trim().isEmpty()) {
            answerText = extractJsonStringValue(candidate, "text");
        }

        int[] pages1Based = extractJsonIntArray(candidate, "citations", 12);
        int[] numbers = null;
        if (pages1Based != null) {
            numbers = new int[pages1Based.length];
            for (int i = 0; i < numbers.length; i++) numbers[i] = i + 1;
        }

        String[] related = extractJsonStringArray(candidate, "relatedQuestions", 4);
        if (related == null) related = extractJsonStringArray(candidate, "followUpQuestions", 4);
        related = sanitizeRelatedQuestions(related, 4, 160);

        boolean hasAny = (answerText != null && !answerText.trim().isEmpty()) || pages1Based != null || related != null;
        if (!hasAny) return null;

        String outText = answerText != null ? answerText.trim() : "";
        if (outText.isEmpty()) outText = text.trim();
        return new AskResult(outText, numbers, pages1Based, related);
    }

    @Nullable
    private static String extractJsonObjectSubstring(@NonNull String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        String sub = text.substring(start, end + 1);
        if (sub.indexOf("\"answerText\"") < 0 && sub.indexOf("\"citations\"") < 0) return null;
        return sub;
    }

    @Nullable
    private static String extractJsonStringValue(@NonNull String json, @NonNull String key) {
        int[] idx = new int[]{indexAfterKeyValueSeparator(json, key)};
        if (idx[0] < 0) return null;
        if (idx[0] >= json.length() || json.charAt(idx[0]) != '"') return null;
        return parseJsonStringLiteral(json, idx);
    }

    @Nullable
    private static int[] extractJsonIntArray(@NonNull String json, @NonNull String key, int max) {
        int pos = indexAfterKeyValueSeparator(json, key);
        if (pos < 0) return null;
        int i = pos;
        if (i >= json.length() || json.charAt(i) != '[') return null;
        i++;

        int[] tmp = new int[Math.max(1, max)];
        int outCount = 0;
        while (i < json.length() && outCount < tmp.length) {
            i = skipWsAndCommas(json, i);
            if (i >= json.length()) break;
            char c = json.charAt(i);
            if (c == ']') break;

            int value = -1;
            if (c == '"') {
                int[] idx = new int[]{i};
                String s = parseJsonStringLiteral(json, idx);
                i = idx[0];
                if (s != null) {
                    try { value = Integer.parseInt(s.trim()); } catch (Throwable ignore) { value = -1; }
                }
            } else {
                int start = i;
                boolean neg = false;
                if (c == '-') {
                    neg = true;
                    i++;
                }
                while (i < json.length() && Character.isDigit(json.charAt(i))) i++;
                if (i > start + (neg ? 1 : 0)) {
                    try { value = Integer.parseInt(json.substring(start, i).trim()); } catch (Throwable ignore) { value = -1; }
                } else {
                    i = start + 1;
                }
            }

            if (value > 0) {
                boolean dup = false;
                for (int j = 0; j < outCount; j++) {
                    if (tmp[j] == value) { dup = true; break; }
                }
                if (!dup) tmp[outCount++] = value;
            }

            i = skipToNextArrayValue(json, i);
        }

        if (outCount <= 0) return null;
        int[] out = new int[outCount];
        System.arraycopy(tmp, 0, out, 0, outCount);
        return out;
    }

    @Nullable
    private static String[] extractJsonStringArray(@NonNull String json, @NonNull String key, int max) {
        int pos = indexAfterKeyValueSeparator(json, key);
        if (pos < 0) return null;
        int i = pos;
        if (i >= json.length() || json.charAt(i) != '[') return null;
        i++;

        ArrayList<String> out = new ArrayList<>();
        while (i < json.length() && out.size() < Math.max(1, max)) {
            i = skipWsAndCommas(json, i);
            if (i >= json.length()) break;
            char c = json.charAt(i);
            if (c == ']') break;
            if (c != '"') {
                i = skipToNextArrayValue(json, i + 1);
                continue;
            }
            int[] idx = new int[]{i};
            String v = parseJsonStringLiteral(json, idx);
            i = idx[0];
            if (v != null) out.add(v);
            i = skipToNextArrayValue(json, i);
        }

        if (out.isEmpty()) return null;
        return out.toArray(new String[0]);
    }

    private static int indexAfterKeyValueSeparator(@NonNull String json, @NonNull String key) {
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) return -1;
        int colon = json.indexOf(':', k + needle.length());
        if (colon < 0) return -1;
        return skipWhitespace(json, colon + 1);
    }

    private static int skipWhitespace(@NonNull String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static int skipWsAndCommas(@NonNull String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == ',') { i++; continue; }
            return i;
        }
        return i;
    }

    private static int skipToNextArrayValue(@NonNull String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ',') return i + 1;
            if (c == ']') return i;
            i++;
        }
        return i;
    }

    @Nullable
    private static String parseJsonStringLiteral(@NonNull String s, @NonNull int[] indexHolder) {
        int i = indexHolder[0];
        if (i < 0 || i >= s.length() || s.charAt(i) != '"') return null;
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') {
                indexHolder[0] = i;
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i >= s.length()) break;
            char esc = s.charAt(i++);
            switch (esc) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (i + 3 < s.length()) {
                        try {
                            int code = Integer.parseInt(s.substring(i, i + 4), 16);
                            sb.append((char) code);
                            i += 4;
                        } catch (Throwable ignore) {
                        }
                    }
                    break;
                default:
                    sb.append(esc);
                    break;
            }
        }
        return null;
    }

    @Nullable
    private static String[] sanitizeRelatedQuestions(@Nullable String[] relatedQuestions, int max, int maxChars) {
        if (relatedQuestions == null || relatedQuestions.length == 0) return null;
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < relatedQuestions.length && out.size() < Math.max(1, max); i++) {
            String q = relatedQuestions[i];
            if (q == null) continue;
            q = q.trim();
            if (q.isEmpty()) continue;
            if (q.length() > maxChars) q = q.substring(0, Math.max(0, maxChars)).trim();
            if (q.isEmpty()) continue;

            boolean dup = false;
            for (int j = 0; j < out.size(); j++) {
                if (q.equalsIgnoreCase(out.get(j))) { dup = true; break; }
            }
            if (!dup) out.add(q);
        }
        if (out.isEmpty()) return null;
        return out.toArray(new String[0]);
    }

    @Nullable
    private static String[] parseRelatedQuestions(@Nullable JSONArray arr, int max, int maxChars) {
        if (arr == null) return null;
        int n = arr.length();
        if (n <= 0) return null;

        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < n && out.size() < Math.max(1, max); i++) {
            String q = null;
            try { q = arr.optString(i, null); } catch (Throwable ignore) { q = null; }
            if (q == null) continue;
            q = q.trim();
            if (q.isEmpty()) continue;
            if (q.length() > maxChars) q = q.substring(0, Math.max(0, maxChars)).trim();
            if (q.isEmpty()) continue;

            boolean dup = false;
            for (int j = 0; j < out.size(); j++) {
                if (q.equalsIgnoreCase(out.get(j))) {
                    dup = true;
                    break;
                }
            }
            if (!dup) out.add(q);
        }

        if (out.isEmpty()) return null;
        return sanitizeRelatedQuestions(out.toArray(new String[0]), max, maxChars);
    }

    @Nullable
    private static JSONObject tryParseJsonObject(@NonNull String text) {
        try {
            return new JSONObject(text);
        } catch (Throwable ignore) {}

        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String sub = text.substring(start, end + 1);
                return new JSONObject(sub);
            }
        } catch (Throwable ignore) {}
        return null;
    }

    @Nullable
    private static int[] parseCitationPages1Based(@Nullable JSONArray citations, int max) {
        if (citations == null) return null;
        int n = citations.length();
        if (n <= 0) return null;

        int[] tmp = new int[Math.min(n, Math.max(1, max))];
        int outCount = 0;
        for (int i = 0; i < n && outCount < tmp.length; i++) {
            int page = -1;
            Object v = null;
            try { v = citations.opt(i); } catch (Throwable ignore) { v = null; }
            if (v instanceof Number) {
                page = ((Number) v).intValue();
            } else if (v instanceof String) {
                try { page = Integer.parseInt(((String) v).trim()); } catch (Throwable ignore) { page = -1; }
            } else if (v instanceof JSONObject) {
                try { page = ((JSONObject) v).optInt("page", -1); } catch (Throwable ignore) { page = -1; }
            }

            if (page <= 0) continue;
            boolean dup = false;
            for (int j = 0; j < outCount; j++) {
                if (tmp[j] == page) { dup = true; break; }
            }
            if (dup) continue;
            tmp[outCount++] = page;
        }

        if (outCount <= 0) return null;
        int[] out = new int[outCount];
        System.arraycopy(tmp, 0, out, 0, outCount);
        return out;
    }

    @NonNull
    private static String stripCodeFences(@Nullable String s) {
        if (s == null) return "";
        String t = s.trim();
        if (!t.startsWith("```")) return s;

        int firstNl = t.indexOf('\n');
        if (firstNl >= 0) t = t.substring(firstNl + 1);

        int lastFence = t.lastIndexOf("```");
        if (lastFence >= 0) t = t.substring(0, lastFence);

        return t.trim();
    }
}
