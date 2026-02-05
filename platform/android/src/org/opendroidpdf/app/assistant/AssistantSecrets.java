package org.opendroidpdf.app.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.security.auth.x500.X500Principal;

/**
 * Stores user-provided API keys locally using Android Keystore-backed encryption.
 *
 * This supports API 21+:
 * - API 23+: AES key in AndroidKeyStore + AES/GCM encryption
 * - API 21-22: RSA keypair in AndroidKeyStore + RSA encryption (sufficient for short secrets)
 */
public final class AssistantSecrets {
    private static final String PREFS_FILE = "OpenDroidPDF_AssistantSecrets";

    private static final String KEY_CARTESIA_MODE = "cartesia_key_mode";
    private static final String KEY_CARTESIA_CIPHERTEXT = "cartesia_key_ciphertext";
    private static final String KEY_CARTESIA_IV = "cartesia_key_iv";

    private static final String KEY_LLM_MODE_PREFIX = "llm_key_mode_";
    private static final String KEY_LLM_CIPHERTEXT_PREFIX = "llm_key_ciphertext_";
    private static final String KEY_LLM_IV_PREFIX = "llm_key_iv_";

    private static final String MODE_AES_GCM = "aesgcm";
    private static final String MODE_RSA = "rsa";

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String AES_ALIAS = "org.opendroidpdf.assistant.aes";
    private static final String RSA_ALIAS = "org.opendroidpdf.assistant.rsa";

    private AssistantSecrets() {}

    @Nullable
    public static String getCartesiaApiKeyOrNull(Context context) {
        return getSecretOrNull(context, KEY_CARTESIA_MODE, KEY_CARTESIA_CIPHERTEXT, KEY_CARTESIA_IV);
    }

    public static void setCartesiaApiKey(Context context, @Nullable String apiKey) {
        setSecret(context, KEY_CARTESIA_MODE, KEY_CARTESIA_CIPHERTEXT, KEY_CARTESIA_IV, apiKey);
    }

    public static void clearCartesiaApiKey(Context context) {
        clearSecret(context, KEY_CARTESIA_MODE, KEY_CARTESIA_CIPHERTEXT, KEY_CARTESIA_IV);
    }

    public static boolean hasCartesiaApiKey(Context context) {
        return getCartesiaApiKeyOrNull(context) != null;
    }

    @Nullable
    public static String cartesiaApiKeyLast4OrNull(Context context) {
        String key = getCartesiaApiKeyOrNull(context);
        if (key == null) return null;
        if (key.length() <= 4) return key;
        return key.substring(key.length() - 4);
    }

    @Nullable
    public static String getLlmApiKeyOrNull(Context context, @Nullable String providerId) {
        if (providerId == null || providerId.trim().isEmpty()) return null;
        String safe = safeProviderId(providerId);
        return getSecretOrNull(
                context,
                KEY_LLM_MODE_PREFIX + safe,
                KEY_LLM_CIPHERTEXT_PREFIX + safe,
                KEY_LLM_IV_PREFIX + safe
        );
    }

    public static void setLlmApiKey(Context context, @NonNull String providerId, @Nullable String apiKey) {
        if (providerId == null || providerId.trim().isEmpty()) return;
        String safe = safeProviderId(providerId);
        setSecret(
                context,
                KEY_LLM_MODE_PREFIX + safe,
                KEY_LLM_CIPHERTEXT_PREFIX + safe,
                KEY_LLM_IV_PREFIX + safe,
                apiKey
        );
    }

    public static void clearLlmApiKey(Context context, @NonNull String providerId) {
        if (providerId == null || providerId.trim().isEmpty()) return;
        String safe = safeProviderId(providerId);
        clearSecret(
                context,
                KEY_LLM_MODE_PREFIX + safe,
                KEY_LLM_CIPHERTEXT_PREFIX + safe,
                KEY_LLM_IV_PREFIX + safe
        );
    }

    @Nullable
    public static String llmApiKeyLast4OrNull(Context context, @NonNull String providerId) {
        String key = getLlmApiKeyOrNull(context, providerId);
        if (key == null) return null;
        if (key.length() <= 4) return key;
        return key.substring(key.length() - 4);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    @Nullable
    private static String getSecretOrNull(Context context,
                                          @NonNull String modeKey,
                                          @NonNull String ciphertextKey,
                                          @NonNull String ivKey) {
        if (context == null) return null;
        SharedPreferences prefs = prefs(context);
        String mode = prefs.getString(modeKey, null);
        String b64Ciphertext = prefs.getString(ciphertextKey, null);
        if (mode == null || b64Ciphertext == null || b64Ciphertext.trim().isEmpty()) return null;

        try {
            byte[] ciphertext = Base64.decode(b64Ciphertext, Base64.NO_WRAP);
            if (MODE_AES_GCM.equals(mode) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String b64Iv = prefs.getString(ivKey, null);
                if (b64Iv == null || b64Iv.trim().isEmpty()) return null;
                byte[] iv = Base64.decode(b64Iv, Base64.NO_WRAP);
                SecretKey key = getOrCreateAesKey();
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
                byte[] plain = cipher.doFinal(ciphertext);
                String out = new String(plain, StandardCharsets.UTF_8).trim();
                return out.isEmpty() ? null : out;
            }
            if (MODE_RSA.equals(mode)) {
                PrivateKey key = getOrCreateRsaPrivateKey(context);
                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.DECRYPT_MODE, key);
                byte[] plain = cipher.doFinal(ciphertext);
                String out = new String(plain, StandardCharsets.UTF_8).trim();
                return out.isEmpty() ? null : out;
            }
        } catch (Throwable t) {
            // Corrupted key or keystore reset: clear and fall back to unset.
            clearSecret(context, modeKey, ciphertextKey, ivKey);
        }
        return null;
    }

    private static void setSecret(Context context,
                                  @NonNull String modeKey,
                                  @NonNull String ciphertextKey,
                                  @NonNull String ivKey,
                                  @Nullable String apiKey) {
        if (context == null) return;
        String value = apiKey != null ? apiKey.trim() : "";
        if (value.isEmpty()) {
            clearSecret(context, modeKey, ciphertextKey, ivKey);
            return;
        }

        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor edit = prefs.edit();

        try {
            byte[] plain = value.getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SecretKey key = getOrCreateAesKey();
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                byte[] iv = cipher.getIV();
                byte[] ciphertext = cipher.doFinal(plain);
                edit.putString(modeKey, MODE_AES_GCM);
                edit.putString(ivKey, Base64.encodeToString(iv, Base64.NO_WRAP));
                edit.putString(ciphertextKey, Base64.encodeToString(ciphertext, Base64.NO_WRAP));
            } else {
                PublicKey key = getOrCreateRsaPublicKey(context);
                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                byte[] ciphertext = cipher.doFinal(plain);
                edit.putString(modeKey, MODE_RSA);
                edit.remove(ivKey);
                edit.putString(ciphertextKey, Base64.encodeToString(ciphertext, Base64.NO_WRAP));
            }
            edit.apply();
        } catch (Throwable t) {
            // Do not persist plaintext on failure.
            clearSecret(context, modeKey, ciphertextKey, ivKey);
            throw new RuntimeException("Failed to store assistant secret", t);
        }
    }

    private static void clearSecret(Context context,
                                    @NonNull String modeKey,
                                    @NonNull String ciphertextKey,
                                    @NonNull String ivKey) {
        if (context == null) return;
        prefs(context).edit()
                .remove(modeKey)
                .remove(ciphertextKey)
                .remove(ivKey)
                .apply();
    }

    @NonNull
    private static String safeProviderId(@NonNull String providerId) {
        // Keep SharedPreferences keys stable and safe.
        String in = providerId.trim();
        StringBuilder sb = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private static SecretKey getOrCreateAesKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        Key key = ks.getKey(AES_ALIAS, null);
        if (key instanceof SecretKey) return (SecretKey) key;

        KeyGenerator gen = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE);
        android.security.keystore.KeyGenParameterSpec spec =
                new android.security.keystore.KeyGenParameterSpec.Builder(
                        AES_ALIAS,
                        android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                                | android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build();
        gen.init(spec);
        return gen.generateKey();
    }

    private static PublicKey getOrCreateRsaPublicKey(Context context) throws Exception {
        KeyPair kp = getOrCreateRsaKeyPair(context);
        return kp.getPublic();
    }

    private static PrivateKey getOrCreateRsaPrivateKey(Context context) throws Exception {
        KeyPair kp = getOrCreateRsaKeyPair(context);
        return kp.getPrivate();
    }

    private static KeyPair getOrCreateRsaKeyPair(Context context) throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(RSA_ALIAS)) {
            java.security.cert.Certificate cert = ks.getCertificate(RSA_ALIAS);
            PublicKey pub = cert != null ? cert.getPublicKey() : null;
            Key priv = ks.getKey(RSA_ALIAS, null);
            if (pub != null && priv instanceof PrivateKey) {
                return new KeyPair(pub, (PrivateKey) priv);
            }
        }

        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 25);

        KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                .setAlias(RSA_ALIAS)
                .setSubject(new X500Principal("CN=OpenDroidPDF Assistant"))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(start.getTime())
                .setEndDate(end.getTime())
                .build();

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE);
        gen.initialize(spec);
        return gen.generateKeyPair();
    }
}
