package org.opendroidpdf.app.document;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Resolves a canonical, content-derived document id for a Uri.
 *
 * <p>The id is intended to survive rename/move and is used to key sidecar annotations,
 * per-document reflow prefs, and viewport/recents state. If hashing fails, we fall back
 * to the legacy URI string.</p>
 */
public final class DocumentIdentityResolver {
    private static final int SAMPLE_BYTES = 64 * 1024;
    private static final String PREFIX = "sha256:";

    private DocumentIdentityResolver() {}

    @NonNull
    public static DocumentIdentity resolve(@NonNull Context context, @NonNull Uri uri) {
        final String legacy = uri.toString();
        String stable = tryComputeContentId(context, uri);
        if (stable == null || stable.isEmpty()) stable = legacy;
        return new DocumentIdentity(stable, legacy);
    }

    @Nullable
    private static String tryComputeContentId(@NonNull Context context, @NonNull Uri uri) {
        try {
            String scheme = uri.getScheme();
            if ("file".equalsIgnoreCase(scheme)) {
                String path = uri.getPath();
                if (path == null || path.isEmpty()) return null;
                File f = new File(path);
                if (!f.exists() || !f.isFile()) return null;
                return PREFIX + hashFile(f);
            }
        } catch (Throwable ignore) {
        }

        try {
            return PREFIX + hashContentUri(context.getContentResolver(), uri);
        } catch (Throwable t) {
            return null;
        }
    }

    @NonNull
    private static String hashFile(@NonNull File f) throws Exception {
        try (FileInputStream fis = new FileInputStream(f)) {
            FileChannel ch = fis.getChannel();
            long size = -1L;
            try {
                size = ch.size();
            } catch (Throwable ignore) {
            }
            if (size >= 0) {
                return hashSeekableChannel(ch, size);
            }
        }
        // Fallback: stream the whole file.
        try (FileInputStream fis = new FileInputStream(f)) {
            return hashStream(fis);
        }
    }

    @NonNull
    private static String hashContentUri(@NonNull ContentResolver resolver, @NonNull Uri uri) throws Exception {
        // Prefer a seekable FD so we can sample head/tail/middle cheaply.
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            if (pfd != null) {
                long size = pfd.getStatSize();
                try (FileInputStream fis = new FileInputStream(pfd.getFileDescriptor())) {
                    FileChannel ch = fis.getChannel();
                    if (size < 0) {
                        try { size = ch.size(); } catch (Throwable ignore) {}
                    }
                    if (size >= 0) {
                        return hashSeekableChannel(ch, size);
                    }
                }
            }
        }

        // Last resort: stream full content (may be slower for large docs).
        try (InputStream is = resolver.openInputStream(uri)) {
            if (is == null) throw new IllegalStateException("openInputStream returned null");
            return hashStream(is);
        }
    }

    @NonNull
    private static String hashSeekableChannel(@NonNull FileChannel ch, long size) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update("odpdf-docid-v1".getBytes(StandardCharsets.UTF_8));
        md.update(longToBytes(size));

        if (size <= SAMPLE_BYTES * 3L) {
            // Small enough: hash full content.
            ch.position(0L);
            streamIntoDigest(ch, md, size);
        } else {
            // Sample head/middle/tail. Include offsets to make collisions less likely.
            sample(ch, md, 0L, SAMPLE_BYTES);
            long mid = Math.max(0L, (size / 2L) - (SAMPLE_BYTES / 2L));
            sample(ch, md, mid, SAMPLE_BYTES);
            long tail = Math.max(0L, size - SAMPLE_BYTES);
            sample(ch, md, tail, SAMPLE_BYTES);
        }
        return toHex(md.digest());
    }

    private static void sample(@NonNull FileChannel ch, @NonNull MessageDigest md, long offset, int len) throws Exception {
        md.update(longToBytes(offset));
        ch.position(offset);
        streamIntoDigest(ch, md, len);
    }

    private static void streamIntoDigest(@NonNull FileChannel ch, @NonNull MessageDigest md, long bytes) throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(16 * 1024);
        long remaining = bytes;
        while (remaining > 0) {
            buf.clear();
            int want = (int) Math.min((long) buf.capacity(), remaining);
            buf.limit(want);
            int r = ch.read(buf);
            if (r <= 0) break;
            md.update(buf.array(), 0, r);
            remaining -= r;
        }
    }

    @NonNull
    private static String hashStream(@NonNull InputStream is) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update("odpdf-docid-v1".getBytes(StandardCharsets.UTF_8));
        byte[] buf = new byte[32 * 1024];
        int r;
        while ((r = is.read(buf)) > 0) {
            md.update(buf, 0, r);
        }
        return toHex(md.digest());
    }

    private static byte[] longToBytes(long v) {
        return new byte[]{
                (byte) (v >>> 56),
                (byte) (v >>> 48),
                (byte) (v >>> 40),
                (byte) (v >>> 32),
                (byte) (v >>> 24),
                (byte) (v >>> 16),
                (byte) (v >>> 8),
                (byte) (v)
        };
    }

    @NonNull
    private static String toHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        int i = 0;
        for (byte b : bytes) {
            hex[i++] = digits[(b >>> 4) & 0xF];
            hex[i++] = digits[b & 0xF];
        }
        return new String(hex);
    }
}

