package org.opendroidpdf.app.assistant;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.preferences.PreferencesNames;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class AssistantActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO = 1042;

    public static final String EXTRA_RETURN_TRANSCRIPT = "assistant_return_transcript";
    public static final String EXTRA_AUTO_START_RECORDING = "assistant_auto_start_recording";
    public static final String EXTRA_TRANSCRIPT = "assistant_transcript";

    private static final int STT_SAMPLE_RATE_HZ = 16_000;
    private static final String STT_MODEL = "ink-whisper";
    private static final String STT_LANGUAGE = "en";

    private static final int TTS_SAMPLE_RATE_HZ = 24_000;
    private static final String TTS_MODEL_ID = "sonic-3";
    // Public Cartesia voice id from docs ("Katie").
    private static final String TTS_VOICE_ID = "f786b574-daa5-4673-aa0c-cbe3e8534c02";
    private static final String TTS_LANGUAGE = "en";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient();

    private boolean returnTranscript = false;
    private boolean autoStartRecording = false;

    private @Nullable Pcm16Recorder recorder;
    private boolean recording = false;

    private TextView statusView;
    private TextView contextSummaryView;
    private TextView contextTextView;
    private TextView transcriptView;
    private Button recordButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.assistant_activity);

        Intent i = getIntent();
        if (i != null) {
            returnTranscript = i.getBooleanExtra(EXTRA_RETURN_TRANSCRIPT, false);
            autoStartRecording = i.getBooleanExtra(EXTRA_AUTO_START_RECORDING, false);
        }

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(returnTranscript ? R.string.assistant_sheet_voice_prompt : R.string.assistant_voice_assistant_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        statusView = (TextView) findViewById(R.id.assistant_status);
        contextSummaryView = (TextView) findViewById(R.id.assistant_context_summary);
        contextTextView = (TextView) findViewById(R.id.assistant_context_text);
        transcriptView = (TextView) findViewById(R.id.assistant_transcript);
        recordButton = (Button) findViewById(R.id.assistant_record_button);
        recordButton.setOnClickListener(v -> onRecordButtonClicked());

        bindContextFromStore();

        if (autoStartRecording && recordButton != null) {
            try { recordButton.post(this::onRecordButtonClicked); } catch (Throwable ignore) {}
        }
    }

    @Override
    protected void onDestroy() {
        stopRecordingIfNeeded();
        executor.shutdownNow();
        super.onDestroy();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            setStatus(getString(R.string.assistant_voice_status_idle));
        }
    }

    private void onRecordButtonClicked() {
        if (recording) {
            stopAndRunPipeline();
            return;
        }

        if (!hasRecordAudioPermission()) {
            requestRecordAudioPermission();
            return;
        }
        startRecording();
    }

    private boolean hasRecordAudioPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO
        );
    }

    private void startRecording() {
        if (recording) return;

        if (isWifiOnlyEnabled() && !isOnWifi(this)) {
            setStatus(getString(R.string.assistant_sheet_wifi_only_blocked));
            return;
        }

        if (!AssistantSecrets.hasCartesiaApiKey(this)) {
            setStatus(getString(R.string.assistant_cartesia_api_key_required));
            return;
        }

        transcriptView.setText("");
        setStatus(getString(R.string.assistant_voice_status_recording));
        recordButton.setText(R.string.assistant_voice_stop);

        try {
            recorder = new Pcm16Recorder(STT_SAMPLE_RATE_HZ);
            recorder.start();
            recording = true;
        } catch (IOException e) {
            recorder = null;
            recording = false;
            setStatus(errorFor(e));
            recordButton.setText(R.string.assistant_voice_record);
        }
    }

    private void stopAndRunPipeline() {
        final Pcm16Recorder r = recorder;
        recording = false;
        recorder = null;

        recordButton.setEnabled(false);
        recordButton.setText(R.string.assistant_voice_record);
        setStatus(getString(R.string.assistant_voice_status_transcribing));

        final byte[] pcm = r != null ? r.stop() : new byte[0];
        final byte[] wav = WavUtils.pcm16leToWav(pcm, STT_SAMPLE_RATE_HZ, 1);

        executor.execute(() -> {
            try {
                String apiKey = AssistantSecrets.getCartesiaApiKeyOrNull(this);
                if (apiKey == null || apiKey.trim().isEmpty()) throw new IOException("Cartesia API key is not set");
                CartesiaClient cartesia = new CartesiaClient(httpClient, CartesiaClient.DEFAULT_BASE_URL, apiKey, CartesiaClient.DEFAULT_API_VERSION);

                String text = cartesia.transcribeWav(wav, STT_MODEL, STT_LANGUAGE);
                runOnUiThread(() -> transcriptView.setText(text));

                if (returnTranscript) {
                    String transcript = text != null ? text.trim() : "";
                    if (transcript.isEmpty()) throw new IOException("Transcription returned empty text");
                    Intent out = new Intent();
                    out.putExtra(EXTRA_TRANSCRIPT, transcript);
                    runOnUiThread(() -> {
                        setResult(RESULT_OK, out);
                        finish();
                        overridePendingTransition(R.anim.fade_in, R.anim.exit_to_left);
                    });
                    return;
                }

                String ttsText = "You said: " + text;
                runOnUiThread(() -> setStatus(getString(R.string.assistant_voice_status_speaking)));

                try (Response response = cartesia.openTtsBytesResponse(
                        ttsText,
                        TTS_MODEL_ID,
                        TTS_VOICE_ID,
                        TTS_LANGUAGE,
                        TTS_SAMPLE_RATE_HZ
                )) {
                    if (!response.isSuccessful()) {
                        String err = response.body() != null ? response.body().string() : "";
                        throw new IOException("Cartesia TTS failed (" + response.code() + "): " + truncate(err, 500));
                    }
                    ResponseBody body = response.body();
                    if (body == null) throw new IOException("Cartesia TTS returned empty body");
                    try (InputStream in = body.byteStream()) {
                        playPcm16leMonoBlocking(in, TTS_SAMPLE_RATE_HZ);
                    }
                }

                runOnUiThread(() -> setStatus(getString(R.string.assistant_voice_status_done)));
            } catch (Throwable t) {
                runOnUiThread(() -> setStatus(errorFor(t)));
            } finally {
                runOnUiThread(() -> recordButton.setEnabled(true));
            }
        });
    }

    private void stopRecordingIfNeeded() {
        recording = false;
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (Throwable ignored) {}
            recorder = null;
        }
    }

    private void setStatus(String status) {
        statusView.setText(status != null ? status : "");
    }

    private void bindContextFromStore() {
        if (contextSummaryView == null || contextTextView == null) return;

        AssistantContextSnapshot ctx = AssistantContextStore.get();
        if (ctx == null || ctx.text() == null || ctx.text().trim().isEmpty()) {
            contextSummaryView.setText(R.string.assistant_voice_context_none);
            contextTextView.setText("");
            return;
        }

        String kind;
        switch (ctx.kind()) {
            case SELECTION:
                kind = getString(R.string.assistant_context_kind_selection);
                break;
            case DOCUMENT:
                kind = getString(R.string.assistant_context_kind_document);
                break;
            case PAGE:
            default:
                kind = getString(R.string.assistant_context_kind_page);
                break;
        }

        int page = ctx.pageIndex();
        StringBuilder summary = new StringBuilder();
        summary.append(kind);
        if (page >= 0) summary.append(" • p. ").append(page + 1);
        summary.append(" • ").append(ctx.text().length()).append(" chars");
        if (ctx.truncated()) summary.append(" (truncated)");

        contextSummaryView.setText(summary.toString());

        final int maxPreviewChars = 2_000;
        String preview = ctx.text();
        if (preview.length() > maxPreviewChars) {
            preview = preview.substring(0, maxPreviewChars) + "…";
        }
        contextTextView.setText(preview);
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) return "";
        if (value.length() <= maxChars) return value;
        return value.substring(0, maxChars) + "…";
    }

    private String errorFor(Throwable t) {
        String msg = t != null ? t.getMessage() : null;
        if (msg == null || msg.trim().isEmpty()) msg = t != null ? t.toString() : "Unknown error";
        return "Error: " + msg;
    }

    private static void playPcm16leMonoBlocking(InputStream in, int sampleRateHz) throws IOException {
        if (in == null) return;
        int minBuf = AudioTrack.getMinBufferSize(
                sampleRateHz,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        int bufSize = Math.max(minBuf, 8192);

        AudioTrack track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRateHz,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
                AudioTrack.MODE_STREAM
        );
        try {
            track.play();
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                track.write(buf, 0, read);
            }
            track.stop();
        } finally {
            track.release();
        }
    }

    private boolean isWifiOnlyEnabled() {
        SharedPreferences prefs = getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
        try {
            return prefs.getBoolean(SettingsActivity.PREF_ASSISTANT_WIFI_ONLY, false);
        } catch (ClassCastException e) {
            try { prefs.edit().remove(SettingsActivity.PREF_ASSISTANT_WIFI_ONLY).apply(); } catch (Throwable ignore) {}
            return false;
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static boolean isOnWifi(Context context) {
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
}
