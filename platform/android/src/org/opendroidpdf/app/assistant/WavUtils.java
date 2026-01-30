package org.opendroidpdf.app.assistant;

public final class WavUtils {
    private WavUtils() {}

    public static byte[] pcm16leToWav(byte[] pcmData, int sampleRateHz, int channels) {
        if (pcmData == null) pcmData = new byte[0];
        if (channels <= 0) channels = 1;
        int bitsPerSample = 16;
        int byteRate = sampleRateHz * channels * (bitsPerSample / 8);
        int blockAlign = channels * (bitsPerSample / 8);

        int dataLen = pcmData.length;
        int riffChunkSize = 36 + dataLen;

        byte[] header = new byte[44];
        // RIFF chunk descriptor
        writeAscii(header, 0, "RIFF");
        writeIntLE(header, 4, riffChunkSize);
        writeAscii(header, 8, "WAVE");
        // fmt subchunk
        writeAscii(header, 12, "fmt ");
        writeIntLE(header, 16, 16); // Subchunk1Size for PCM
        writeShortLE(header, 20, (short) 1); // AudioFormat PCM
        writeShortLE(header, 22, (short) channels);
        writeIntLE(header, 24, sampleRateHz);
        writeIntLE(header, 28, byteRate);
        writeShortLE(header, 32, (short) blockAlign);
        writeShortLE(header, 34, (short) bitsPerSample);
        // data subchunk
        writeAscii(header, 36, "data");
        writeIntLE(header, 40, dataLen);

        byte[] out = new byte[header.length + pcmData.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(pcmData, 0, out, header.length, pcmData.length);
        return out;
    }

    private static void writeAscii(byte[] buf, int offset, String value) {
        for (int i = 0; i < value.length(); i++) {
            buf[offset + i] = (byte) value.charAt(i);
        }
    }

    private static void writeIntLE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xff);
        buf[offset + 1] = (byte) ((value >> 8) & 0xff);
        buf[offset + 2] = (byte) ((value >> 16) & 0xff);
        buf[offset + 3] = (byte) ((value >> 24) & 0xff);
    }

    private static void writeShortLE(byte[] buf, int offset, short value) {
        buf[offset] = (byte) (value & 0xff);
        buf[offset + 1] = (byte) ((value >> 8) & 0xff);
    }
}

