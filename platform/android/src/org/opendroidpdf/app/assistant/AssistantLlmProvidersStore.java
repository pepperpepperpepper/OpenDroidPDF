package org.opendroidpdf.app.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.preferences.PreferencesNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AssistantLlmProvidersStore {
    private static final String KEY_PROVIDERS_JSON = "pref_assistant_llm_providers_json";

    private AssistantLlmProvidersStore() {}

    @NonNull
    public static List<AssistantLlmProviderConfig> load(@NonNull Context context) {
        if (context == null) return Collections.emptyList();
        SharedPreferences prefs = prefs(context);
        String raw = null;
        try { raw = prefs.getString(KEY_PROVIDERS_JSON, null); } catch (Throwable ignore) {}
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();

        try {
            JSONArray arr = new JSONArray(raw);
            Map<String, AssistantLlmProviderConfig> out = new LinkedHashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                Object entry = arr.opt(i);
                if (!(entry instanceof JSONObject)) continue;
                AssistantLlmProviderConfig cfg = AssistantLlmProviderConfig.fromJson((JSONObject) entry);
                if (cfg == null) continue;
                out.put(cfg.id(), cfg);
            }
            return new ArrayList<>(out.values());
        } catch (Throwable t) {
            // Corrupted JSON: clear so Settings and the sheet can recover.
            try { prefs.edit().remove(KEY_PROVIDERS_JSON).apply(); } catch (Throwable ignore) {}
            return Collections.emptyList();
        }
    }

    public static void save(@NonNull Context context, @NonNull List<AssistantLlmProviderConfig> providers) {
        if (context == null) return;
        if (providers == null) providers = Collections.emptyList();

        JSONArray arr = new JSONArray();
        for (AssistantLlmProviderConfig cfg : providers) {
            if (cfg == null) continue;
            try { arr.put(cfg.toJson()); } catch (Throwable ignore) {}
        }

        try {
            prefs(context).edit().putString(KEY_PROVIDERS_JSON, arr.toString()).apply();
        } catch (Throwable ignore) {
        }

        // Keep the default provider id sane.
        ensureDefaultProviderIsValid(context);
    }

    @Nullable
    public static AssistantLlmProviderConfig defaultProviderOrNull(@NonNull Context context) {
        if (context == null) return null;
        String id = null;
        try { id = prefs(context).getString(SettingsActivity.PREF_ASSISTANT_PROVIDER, null); } catch (Throwable ignore) {}
        List<AssistantLlmProviderConfig> providers = load(context);
        if (providers.isEmpty()) return null;
        if (id != null) {
            for (AssistantLlmProviderConfig cfg : providers) {
                if (cfg != null && id.equals(cfg.id())) return cfg;
            }
        }
        // Fallback to first provider.
        return providers.get(0);
    }

    public static void setDefaultProviderId(@NonNull Context context, @Nullable String providerId) {
        if (context == null) return;
        try { prefs(context).edit().putString(SettingsActivity.PREF_ASSISTANT_PROVIDER, providerId).apply(); } catch (Throwable ignore) {}
    }

    @NonNull
    public static AssistantLlmProviderConfig addProvider(
            @NonNull Context context,
            @NonNull String name,
            @NonNull String baseUrl,
            @NonNull String model) {
        String id = UUID.randomUUID().toString();
        AssistantLlmProviderConfig cfg = new AssistantLlmProviderConfig(id, name, baseUrl, model);
        List<AssistantLlmProviderConfig> providers = new ArrayList<>(load(context));
        providers.add(cfg);
        save(context, providers);
        return cfg;
    }

    public static void updateProvider(@NonNull Context context, @NonNull AssistantLlmProviderConfig updated) {
        if (context == null || updated == null) return;
        List<AssistantLlmProviderConfig> providers = new ArrayList<>(load(context));
        boolean replaced = false;
        for (int i = 0; i < providers.size(); i++) {
            AssistantLlmProviderConfig cfg = providers.get(i);
            if (cfg != null && updated.id().equals(cfg.id())) {
                providers.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) providers.add(updated);
        save(context, providers);
    }

    public static void deleteProvider(@NonNull Context context, @NonNull String providerId) {
        if (context == null || providerId == null) return;
        List<AssistantLlmProviderConfig> providers = new ArrayList<>(load(context));
        boolean changed = false;
        for (int i = providers.size() - 1; i >= 0; i--) {
            AssistantLlmProviderConfig cfg = providers.get(i);
            if (cfg != null && providerId.equals(cfg.id())) {
                providers.remove(i);
                changed = true;
            }
        }
        if (changed) {
            save(context, providers);
            try { AssistantSecrets.clearLlmApiKey(context, providerId); } catch (Throwable ignore) {}
        }
        ensureDefaultProviderIsValid(context);
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
    }

    private static void ensureDefaultProviderIsValid(@NonNull Context context) {
        List<AssistantLlmProviderConfig> providers = load(context);
        String current = null;
        try { current = prefs(context).getString(SettingsActivity.PREF_ASSISTANT_PROVIDER, null); } catch (Throwable ignore) {}
        if (providers.isEmpty()) {
            try { prefs(context).edit().remove(SettingsActivity.PREF_ASSISTANT_PROVIDER).apply(); } catch (Throwable ignore) {}
            return;
        }
        if (current != null) {
            for (AssistantLlmProviderConfig cfg : providers) {
                if (cfg != null && current.equals(cfg.id())) return;
            }
        }
        // Point to first provider.
        try { prefs(context).edit().putString(SettingsActivity.PREF_ASSISTANT_PROVIDER, providers.get(0).id()).apply(); } catch (Throwable ignore) {}
    }
}
