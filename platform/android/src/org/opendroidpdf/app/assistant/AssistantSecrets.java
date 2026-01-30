package org.opendroidpdf.app.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.util.Base64;

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

    private static final String MODE_AES_GCM = "aesgcm";
    private static final String MODE_RSA = "rsa";

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String AES_ALIAS = "org.opendroidpdf.assistant.aes";
    private static final String RSA_ALIAS = "org.opendroidpdf.assistant.rsa";

    private AssistantSecrets() {}

    @Nullable
    public static String getCartesiaApiKeyOrNull(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = prefs(context);
        String mode = prefs.getString(KEY_CARTESIA_MODE, null);
        String b64Ciphertext = prefs.getString(KEY_CARTESIA_CIPHERTEXT, null);
        if (mode == null || b64Ciphertext == null || b64Ciphertext.trim().isEmpty()) return null;

        try {
            byte[] ciphertext = Base64.decode(b64Ciphertext, Base64.NO_WRAP);
            if (MODE_AES_GCM.equals(mode) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String b64Iv = prefs.getString(KEY_CARTESIA_IV, null);
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
            clearCartesiaApiKey(context);
        }
        return null;
    }

    public static void setCartesiaApiKey(Context context, @Nullable String apiKey) {
        if (context == null) return;
        String value = apiKey != null ? apiKey.trim() : "";
        if (value.isEmpty()) {
            clearCartesiaApiKey(context);
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
                edit.putString(KEY_CARTESIA_MODE, MODE_AES_GCM);
                edit.putString(KEY_CARTESIA_IV, Base64.encodeToString(iv, Base64.NO_WRAP));
                edit.putString(KEY_CARTESIA_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP));
            } else {
                PublicKey key = getOrCreateRsaPublicKey(context);
                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                byte[] ciphertext = cipher.doFinal(plain);
                edit.putString(KEY_CARTESIA_MODE, MODE_RSA);
                edit.remove(KEY_CARTESIA_IV);
                edit.putString(KEY_CARTESIA_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP));
            }
            edit.apply();
        } catch (Throwable t) {
            // Do not persist plaintext on failure.
            clearCartesiaApiKey(context);
            throw new RuntimeException("Failed to store Cartesia API key", t);
        }
    }

    public static void clearCartesiaApiKey(Context context) {
        if (context == null) return;
        prefs(context).edit()
                .remove(KEY_CARTESIA_MODE)
                .remove(KEY_CARTESIA_CIPHERTEXT)
                .remove(KEY_CARTESIA_IV)
                .apply();
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

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
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

