package org.opendroidpdf.app.document;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.InputStream;

/**
 * Best-effort detector for XFA (XML Forms Architecture) PDFs.
 *
 * <p>MuPDF does not support XFA forms. Most XFA PDFs contain an /XFA entry in the AcroForm
 * dictionary, which is typically stored in clear text near the top of the file. We scan a bounded
 * prefix to avoid heavy I/O for large documents.</p>
 */
public final class XfaFormDetector {
    private static final byte[] XFA_TOKEN = new byte[] {'/', 'X', 'F', 'A'};
    private static final int DEFAULT_SCAN_LIMIT_BYTES = 1024 * 1024; // 1 MiB
    private static final int BUFFER_SIZE_BYTES = 16 * 1024;

    private XfaFormDetector() {}

    public static boolean hasXfaForms(@NonNull Context context, @NonNull Uri uri) {
        ContentResolver cr = context.getContentResolver();
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return false;
            return streamContainsToken(in, XFA_TOKEN, DEFAULT_SCAN_LIMIT_BYTES);
        } catch (Exception ignore) {
            return false;
        }
    }

    static boolean streamContainsToken(@NonNull InputStream in, @NonNull byte[] token, int maxBytes) throws Exception {
        if (token.length == 0) return false;
        if (maxBytes <= 0) return false;

        byte[] buf = new byte[BUFFER_SIZE_BYTES];
        byte[] carry = new byte[token.length - 1];
        int carryLen = 0;

        int remaining = maxBytes;
        while (remaining > 0) {
            int toRead = Math.min(buf.length, remaining);
            int n = in.read(buf, 0, toRead);
            if (n <= 0) break;
            remaining -= n;

            if (carryLen > 0) {
                if (indexOf(carry, carryLen, buf, n, token) >= 0) return true;
            }
            if (indexOf(buf, n, token) >= 0) return true;

            carryLen = Math.min(carry.length, n);
            if (carryLen > 0) {
                System.arraycopy(buf, n - carryLen, carry, 0, carryLen);
            }
        }
        return false;
    }

    private static int indexOf(byte[] buf, int len, byte[] token) {
        if (len < token.length) return -1;
        for (int i = 0; i <= len - token.length; i++) {
            boolean ok = true;
            for (int j = 0; j < token.length; j++) {
                if (buf[i + j] != token[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) return i;
        }
        return -1;
    }

    /**
     * Searches for {@code token} in {@code carry[0:carryLen] + buf[0:bufLen]}.
     */
    private static int indexOf(byte[] carry, int carryLen, byte[] buf, int bufLen, byte[] token) {
        int totalLen = carryLen + bufLen;
        if (totalLen < token.length) return -1;

        for (int i = 0; i <= totalLen - token.length; i++) {
            boolean ok = true;
            for (int j = 0; j < token.length; j++) {
                int idx = i + j;
                byte b = (idx < carryLen) ? carry[idx] : buf[idx - carryLen];
                if (b != token[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) return i;
        }
        return -1;
    }
}

