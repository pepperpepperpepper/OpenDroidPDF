package org.opendroidpdf.app.assistant;

import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.opendroidpdf.R;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public final class AssistantProvidersActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient http = new OkHttpClient();

    @Nullable private LinearLayout listContainer;
    @Nullable private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.assistant_providers_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.assistant_providers_screen_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        listContainer = findViewById(R.id.assistant_providers_list);
        emptyView = findViewById(R.id.assistant_providers_empty);

        Button add = findViewById(R.id.assistant_providers_add);
        if (add != null) {
            add.setOnClickListener(v -> showEditDialog(null));
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.exit_to_left);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshList() {
        LinearLayout container = listContainer;
        if (container == null) return;

        container.removeAllViews();
        List<AssistantLlmProviderConfig> providers = AssistantLlmProvidersStore.load(this);
        AssistantLlmProviderConfig def = AssistantLlmProvidersStore.defaultProviderOrNull(this);
        String defaultId = def != null ? def.id() : null;

        TextView empty = emptyView;
        if (empty != null) empty.setVisibility(providers.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AssistantLlmProviderConfig cfg : providers) {
            if (cfg == null) continue;
            View row = inflater.inflate(R.layout.item_assistant_provider, container, false);

            RadioButton radio = row.findViewById(R.id.assistant_provider_default);
            TextView name = row.findViewById(R.id.assistant_provider_name);
            TextView summary = row.findViewById(R.id.assistant_provider_summary);
            ImageButton edit = row.findViewById(R.id.assistant_provider_edit);
            ImageButton del = row.findViewById(R.id.assistant_provider_delete);

            if (name != null) name.setText(cfg.name());

            String last4 = AssistantSecrets.llmApiKeyLast4OrNull(this, cfg.id());
            String keySummary = last4 != null
                    ? getString(R.string.assistant_provider_key_set, last4)
                    : getString(R.string.assistant_provider_key_unset);
            String s = cfg.baseUrl() + " • " + cfg.model() + " • " + keySummary;
            if (summary != null) summary.setText(s);

            boolean isDefault = defaultId != null && defaultId.equals(cfg.id());
            if (radio != null) radio.setChecked(isDefault);

            View.OnClickListener setDefault = v -> {
                AssistantLlmProvidersStore.setDefaultProviderId(this, cfg.id());
                refreshList();
            };
            if (radio != null) radio.setOnClickListener(setDefault);

            row.setOnClickListener(v -> showEditDialog(cfg));

            if (edit != null) edit.setOnClickListener(v -> showEditDialog(cfg));
            if (del != null) {
                del.setOnClickListener(v -> confirmDelete(cfg));
            }

            container.addView(row);
        }
    }

    private void confirmDelete(@NonNull AssistantLlmProviderConfig cfg) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.assistant_provider_delete_confirm_title)
                .setMessage(R.string.assistant_provider_delete_confirm_message)
                .setPositiveButton(R.string.assistant_provider_delete, (d, w) -> {
                    AssistantLlmProvidersStore.deleteProvider(this, cfg.id());
                    refreshList();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showEditDialog(@Nullable AssistantLlmProviderConfig existing) {
        View body = LayoutInflater.from(this).inflate(R.layout.dialog_assistant_provider_edit, null);
        EditText name = body.findViewById(R.id.assistant_provider_edit_name);
        EditText baseUrl = body.findViewById(R.id.assistant_provider_edit_base_url);
        EditText model = body.findViewById(R.id.assistant_provider_edit_model);
        EditText apiKey = body.findViewById(R.id.assistant_provider_edit_api_key);

        if (existing != null) {
            if (name != null) name.setText(existing.name());
            if (baseUrl != null) baseUrl.setText(existing.baseUrl());
            if (model != null) model.setText(existing.model());
            if (apiKey != null) apiKey.setHint(R.string.assistant_provider_api_key_hint_edit);
        }
        if (apiKey != null) {
            apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            apiKey.setTransformationMethod(PasswordTransformationMethod.getInstance());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing != null ? R.string.assistant_provider_edit : R.string.assistant_provider_add)
                .setView(body)
                .setPositiveButton(R.string.save, null)
                .setNeutralButton(R.string.assistant_provider_test, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button test = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);

            if (save != null) {
                save.setOnClickListener(v -> {
                    String n = textOrEmpty(name);
                    String b = AssistantLlmProviderConfig.normalizeBaseUrl(textOrEmpty(baseUrl));
                    String m = textOrEmpty(model);
                    String k = textOrEmpty(apiKey);

                    if (b.isEmpty() || m.isEmpty()) {
                        Toast.makeText(this, R.string.assistant_provider_validation_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!b.startsWith("http://") && !b.startsWith("https://")) {
                        Toast.makeText(this, R.string.assistant_provider_validation_scheme, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AssistantLlmProviderConfig cfg;
                    if (existing == null) {
                        cfg = AssistantLlmProvidersStore.addProvider(this, n, b, m);
                        AssistantLlmProvidersStore.setDefaultProviderId(this, cfg.id());
                    } else {
                        cfg = new AssistantLlmProviderConfig(existing.id(), n, b, m);
                        AssistantLlmProvidersStore.updateProvider(this, cfg);
                    }

                    if (!k.isEmpty()) {
                        try { AssistantSecrets.setLlmApiKey(this, cfg.id(), k); } catch (Throwable ignore) {}
                    }
                    dialog.dismiss();
                    refreshList();
                });
            }

            if (test != null) {
                test.setOnClickListener(v -> runTest(existing, name, baseUrl, model, apiKey, test));
            }
        });

        dialog.show();
    }

    private void runTest(@Nullable AssistantLlmProviderConfig existing,
                         @Nullable EditText name,
                         @Nullable EditText baseUrl,
                         @Nullable EditText model,
                         @Nullable EditText apiKey,
                         @NonNull Button testButton) {
        String b = AssistantLlmProviderConfig.normalizeBaseUrl(textOrEmpty(baseUrl));
        String m = textOrEmpty(model);
        String k = textOrEmpty(apiKey);

        if (b.isEmpty() || m.isEmpty()) {
            Toast.makeText(this, R.string.assistant_provider_validation_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!b.startsWith("http://") && !b.startsWith("https://")) {
            Toast.makeText(this, R.string.assistant_provider_validation_scheme, Toast.LENGTH_SHORT).show();
            return;
        }

        String id = existing != null ? existing.id() : "test";
        AssistantLlmProviderConfig cfg = new AssistantLlmProviderConfig(id, textOrEmpty(name), b, m);
        if (k.isEmpty() && existing != null) {
            String stored = AssistantSecrets.getLlmApiKeyOrNull(this, existing.id());
            if (stored != null) k = stored;
        }
        if (k.isEmpty()) {
            Toast.makeText(this, R.string.assistant_provider_key_unset, Toast.LENGTH_SHORT).show();
            return;
        }

        testButton.setEnabled(false);
        testButton.setText(R.string.assistant_provider_test_running);

        final String apiKeyFinal = k;
        executor.execute(() -> {
            String resultTmp;
            boolean okTmp;
            try {
                resultTmp = AssistantLlmClient.testChatCompletionBlocking(http, cfg, apiKeyFinal);
                okTmp = true;
            } catch (Throwable t) {
                resultTmp = t.getMessage();
                okTmp = false;
            }
            final String result = resultTmp;
            final boolean ok = okTmp;
            runOnUiThread(() -> {
                testButton.setEnabled(true);
                testButton.setText(R.string.assistant_provider_test);
                if (ok) {
                    Toast.makeText(this, getString(R.string.assistant_provider_test_ok, result), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, getString(R.string.assistant_provider_test_failed, String.valueOf(result)), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @NonNull
    private static String textOrEmpty(@Nullable EditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }
}
