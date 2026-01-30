package org.opendroidpdf.app.assistant;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class Pcm16Recorder {
    private final int sampleRateHz;
    private final int channelConfig;
    private final int audioFormat;

    private volatile boolean recording = false;
    private AudioRecord audioRecord;
    private Thread thread;
    private ByteArrayOutputStream out;

    public Pcm16Recorder(int sampleRateHz) {
        this.sampleRateHz = sampleRateHz;
        this.channelConfig = AudioFormat.CHANNEL_IN_MONO;
        this.audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    }

    public void start() throws IOException {
        if (recording) return;

        int minBuffer = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, audioFormat);
        if (minBuffer <= 0) throw new IOException("AudioRecord buffer init failed");

        int bufferSize = Math.max(minBuffer * 2, 4096);
        AudioRecord r = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateHz,
                channelConfig,
                audioFormat,
                bufferSize
        );
        if (r.getState() != AudioRecord.STATE_INITIALIZED) {
            r.release();
            throw new IOException("AudioRecord initialization failed");
        }

        out = new ByteArrayOutputStream();
        audioRecord = r;
        recording = true;

        audioRecord.startRecording();
        thread = new Thread(() -> recordLoop(bufferSize), "AssistantAudioRecorder");
        thread.start();
    }

    public byte[] stop() {
        recording = false;

        if (thread != null) {
            try {
                thread.join(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }

        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Throwable ignored) {}
            audioRecord.release();
            audioRecord = null;
        }

        byte[] result = out != null ? out.toByteArray() : new byte[0];
        out = null;
        return result;
    }

    public boolean isRecording() {
        return recording;
    }

    private void recordLoop(int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        while (recording && audioRecord != null) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read > 0 && out != null) {
                out.write(buffer, 0, read);
            }
        }
    }
}

