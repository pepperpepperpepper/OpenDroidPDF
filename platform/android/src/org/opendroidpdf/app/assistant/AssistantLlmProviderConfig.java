package org.opendroidpdf.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public final class AssistantLlmProviderConfig {
    private static final String JSON_ID = "id";
    private static final String JSON_NAME = "name";
    private static final String JSON_BASE_URL = "baseUrl";
    private static final String JSON_MODEL = "model";

    @NonNull private final String id;
    @NonNull private final String name;
    @NonNull private final String baseUrl;
    @NonNull private final String model;

    public AssistantLlmProviderConfig(
            @NonNull String id,
            @NonNull String name,
            @NonNull String baseUrl,
            @NonNull String model) {
        this.id = safeTrim(id);
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String trimmedName = safeTrim(name);
        if (trimmedName.isEmpty()) trimmedName = normalizedBaseUrl;
        this.name = trimmedName;
        this.baseUrl = normalizedBaseUrl;
        this.model = safeTrim(model);
    }

    @NonNull public String id() { return id; }
    @NonNull public String name() { return name; }
    @NonNull public String baseUrl() { return baseUrl; }
    @NonNull public String model() { return model; }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put(JSON_ID, id);
        o.put(JSON_NAME, name);
        o.put(JSON_BASE_URL, baseUrl);
        o.put(JSON_MODEL, model);
        return o;
    }

    @Nullable
    public static AssistantLlmProviderConfig fromJson(@Nullable JSONObject o) {
        if (o == null) return null;
        String id = safeTrim(o.optString(JSON_ID, ""));
        String name = safeTrim(o.optString(JSON_NAME, ""));
        String baseUrl = safeTrim(o.optString(JSON_BASE_URL, ""));
        String model = safeTrim(o.optString(JSON_MODEL, ""));
        if (id.isEmpty() || baseUrl.isEmpty() || model.isEmpty()) return null;
        if (name.isEmpty()) name = baseUrl;
        return new AssistantLlmProviderConfig(id, name, baseUrl, model);
    }

    @NonNull
    public static String normalizeBaseUrl(@Nullable String baseUrl) {
        String v = safeTrim(baseUrl);
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        // Defensive: normalize common "api.openai.com/v1" pastes.
        String lower = v.toLowerCase(Locale.US);
        if (lower.endsWith("/v1")) v = v.substring(0, v.length() - 3);
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    @NonNull
    private static String safeTrim(@Nullable String s) {
        return s != null ? s.trim() : "";
    }
}
