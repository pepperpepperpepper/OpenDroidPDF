package org.opendroidpdf.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class AssistantLlmClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public enum SummaryStyle { SHORT, MEDIUM, DETAILED }

    private AssistantLlmClient() {}

    @NonNull
    public static String summarizeBlocking(
            @NonNull OkHttpClient http,
            @NonNull AssistantLlmProviderConfig provider,
            @NonNull String apiKey,
            @NonNull String text,
            @NonNull SummaryStyle style) throws IOException {
        String instruction = summaryInstruction(style);

        JSONObject body = new JSONObject();
        try {
            body.put("model", provider.model());
            body.put("temperature", 0.2);
            body.put("max_tokens", 700);

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

        try (Response resp = http.newCall(req).execute()) {
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
    public static String askBlocking(
            @NonNull OkHttpClient http,
            @NonNull AssistantLlmProviderConfig provider,
            @NonNull String apiKey,
            @NonNull String question,
            @NonNull String contextText) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", provider.model());
            body.put("temperature", 0.2);
            body.put("max_tokens", 900);

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", "You answer questions about a document using the provided context. "
                            + "If the context does not contain the answer, say so plainly."));
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

        try (Response resp = http.newCall(req).execute()) {
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
}
