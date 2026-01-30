package org.opendroidpdf.app.assistant;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class CartesiaClient {
    public static final String DEFAULT_BASE_URL = "https://api.cartesia.ai";
    // Keep in sync with Cartesia API docs as needed.
    public static final String DEFAULT_API_VERSION = "2025-04-16";

    private static final MediaType MEDIA_TYPE_WAV = MediaType.get("audio/wav");
    private static final MediaType MEDIA_TYPE_JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String baseUrl;
    private final String apiKey;
    private final @Nullable String apiVersion;

    public CartesiaClient(OkHttpClient client, String baseUrl, String apiKey, @Nullable String apiVersion) {
        this.client = client != null ? client : new OkHttpClient();
        this.baseUrl = (baseUrl == null || baseUrl.trim().isEmpty()) ? DEFAULT_BASE_URL : baseUrl.trim();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.apiVersion = (apiVersion != null && !apiVersion.trim().isEmpty()) ? apiVersion.trim() : null;
    }

    public CartesiaClient(String apiKey) {
        this(new OkHttpClient(), DEFAULT_BASE_URL, apiKey, DEFAULT_API_VERSION);
    }

    public String transcribeWav(byte[] wavBytes, String model, String language) throws IOException {
        if (apiKey.isEmpty()) throw new IOException("Cartesia API key is not set");
        if (wavBytes == null || wavBytes.length == 0) throw new IOException("No audio provided");

        MultipartBody.Builder multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav", RequestBody.create(wavBytes, MEDIA_TYPE_WAV))
                .addFormDataPart("model", model != null ? model : "")
                .addFormDataPart("language", language != null ? language : "");

        Request request = baseRequestBuilder(baseUrl + "/stt")
                .post(multipart.build())
                .build();

        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            String bodyStr = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Cartesia STT failed (" + response.code() + "): " + truncate(bodyStr, 500));
            }
            try {
                JSONObject json = new JSONObject(bodyStr);
                String text = json.optString("text", "").trim();
                if (text.isEmpty()) throw new IOException("Cartesia STT returned empty text");
                return text;
            } catch (Exception e) {
                throw new IOException("Failed to parse Cartesia STT response", e);
            }
        }
    }

    /**
     * Returns a live HTTP response whose body is an audio byte stream. Caller must close it.
     */
    public Response openTtsBytesResponse(
            String transcript,
            String modelId,
            String voiceId,
            String language,
            int sampleRate
    ) throws IOException {
        if (apiKey.isEmpty()) throw new IOException("Cartesia API key is not set");
        if (transcript == null || transcript.trim().isEmpty()) throw new IOException("No transcript provided");

        try {
            JSONObject voice = new JSONObject()
                    .put("mode", "id")
                    .put("id", voiceId != null ? voiceId : "");

            JSONObject outputFormat = new JSONObject()
                    .put("container", "raw")
                    .put("encoding", "pcm_s16le")
                    .put("sample_rate", sampleRate);

            JSONObject payload = new JSONObject()
                    .put("model_id", modelId != null ? modelId : "")
                    .put("transcript", transcript)
                    .put("voice", voice)
                    .put("output_format", outputFormat)
                    .put("language", language != null ? language : "");

            RequestBody body = RequestBody.create(
                    payload.toString().getBytes(StandardCharsets.UTF_8),
                    MEDIA_TYPE_JSON
            );
            Request request = baseRequestBuilder(baseUrl + "/tts/bytes")
                    .post(body)
                    .build();
            return client.newCall(request).execute();
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            throw new IOException("Failed to build Cartesia TTS request", e);
        }
    }

    private Request.Builder baseRequestBuilder(String url) {
        Request.Builder b = new Request.Builder()
                .url(url)
                .addHeader("X-API-Key", apiKey);
        if (apiVersion != null) b.addHeader("Cartesia-Version", apiVersion);
        return b;
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) return "";
        if (value.length() <= maxChars) return value;
        return value.substring(0, maxChars) + "…";
    }
}

