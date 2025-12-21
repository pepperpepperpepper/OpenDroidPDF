package org.opendroidpdf.app.epub;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Best-effort EPUB encryption/DRM detection.
 *
 * <p>Many DRM-protected EPUBs include {@code META-INF/encryption.xml} with non-font-obfuscation
 * algorithms. We treat those as unsupported and present a specific error instead of a generic
 * open failure.</p>
 */
public final class EpubEncryptionDetector {

    public enum EncryptionKind {
        NONE,
        FONT_OBFUSCATION_ONLY,
        DRM_OR_UNKNOWN,
        ERROR,
    }

    private static final String ENCRYPTION_ENTRY = "META-INF/encryption.xml";
    private static final int MAX_ENCRYPTION_XML_BYTES = 256 * 1024;

    private EpubEncryptionDetector() {}

    public static boolean isProbablyDrmOrEncryptedEpub(@NonNull Context context, @NonNull Uri uri) {
        EncryptionKind kind = detectFromEpubUri(context, uri);
        return kind == EncryptionKind.DRM_OR_UNKNOWN;
    }

    @NonNull
    public static EncryptionKind detectFromEpubUri(@NonNull Context context, @NonNull Uri uri) {
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) return EncryptionKind.ERROR;
            return detectFromEpubZipStream(raw);
        } catch (SecurityException se) {
            // Permission prompts are handled by the document open flow.
            return EncryptionKind.ERROR;
        } catch (Throwable t) {
            return EncryptionKind.ERROR;
        }
    }

    @NonNull
    public static EncryptionKind detectFromEpubZipStream(@NonNull InputStream inputStream) {
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new BufferedInputStream(inputStream));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null) continue;
                if (!ENCRYPTION_ENTRY.equalsIgnoreCase(name)) continue;

                String xml = readUtf8Bounded(zis, MAX_ENCRYPTION_XML_BYTES);
                return classifyEncryptionXml(xml);
            }
            return EncryptionKind.NONE;
        } catch (Throwable t) {
            return EncryptionKind.ERROR;
        } finally {
            if (zis != null) {
                try { zis.close(); } catch (Throwable ignore) {}
            }
        }
    }

    @NonNull
    static EncryptionKind classifyEncryptionXml(@NonNull String xml) {
        // Minimal, namespace-agnostic scanning. We treat unknown algorithms as DRM/unsupported.
        //
        // Expected structure includes entries like:
        //   <EncryptionMethod Algorithm="..."/>
        //   <CipherReference URI="..."/>
        //
        // We allow font obfuscation algorithms ONLY when they apply to font resources.
        final String lower = xml.toLowerCase(Locale.US);
        int idx = 0;
        boolean sawAnyEncryptedData = false;
        boolean sawAnyAlgorithm = false;

        while (true) {
            int encData = lower.indexOf("<encrypteddata", idx);
            if (encData < 0) break;
            sawAnyEncryptedData = true;
            int next = lower.indexOf("</encrypteddata", encData);
            if (next < 0) next = Math.min(lower.length(), encData + 32_768);
            String block = lower.substring(encData, next);
            idx = next;

            String algorithm = firstAttributeValue(block, "algorithm");
            String uri = firstAttributeValue(block, "uri");
            if (algorithm != null) sawAnyAlgorithm = true;

            if (algorithm == null || uri == null) {
                return EncryptionKind.DRM_OR_UNKNOWN;
            }

            if (isFontObfuscationAlgorithm(algorithm) && isFontResource(uri)) {
                continue;
            }

            // Unknown/strong encryption, or encryption applied to non-font resources.
            return EncryptionKind.DRM_OR_UNKNOWN;
        }

        // If there's an encryption.xml but we couldn't find any EncryptedData or algorithms, treat as unknown.
        if (!sawAnyEncryptedData || !sawAnyAlgorithm) {
            return EncryptionKind.DRM_OR_UNKNOWN;
        }

        return EncryptionKind.FONT_OBFUSCATION_ONLY;
    }

    private static boolean isFontObfuscationAlgorithm(@NonNull String algorithmLower) {
        // Common EPUB font obfuscation algorithms:
        // - IDPF:  http://www.idpf.org/2008/embedding
        // - Adobe: http://ns.adobe.com/pdf/enc#RC
        return algorithmLower.contains("idpf.org/2008/embedding")
                || algorithmLower.contains("ns.adobe.com/pdf/enc#rc");
    }

    private static boolean isFontResource(@NonNull String uriLower) {
        return uriLower.endsWith(".otf")
                || uriLower.endsWith(".ttf")
                || uriLower.endsWith(".woff")
                || uriLower.endsWith(".woff2");
    }

    @NonNull
    private static String readUtf8Bounded(@NonNull InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(16_384, maxBytes));
        byte[] buf = new byte[8192];
        int total = 0;
        while (true) {
            int read = in.read(buf);
            if (read <= 0) break;
            int remaining = maxBytes - total;
            if (remaining <= 0) break;
            int toWrite = Math.min(read, remaining);
            out.write(buf, 0, toWrite);
            total += toWrite;
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static String firstAttributeValue(@NonNull String xmlLower, @NonNull String attributeLower) {
        // Finds the first occurrence of attributeLower="value" and returns value, or null.
        int i = xmlLower.indexOf(attributeLower + "=\"");
        if (i < 0) return null;
        i += attributeLower.length() + 2;
        int end = xmlLower.indexOf('\"', i);
        if (end < 0) return null;
        return xmlLower.substring(i, end);
    }
}

