package com.javaagent.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** API keys are encrypted with an app-only Android Keystore key; no key is shipped in the APK. */
public final class SecureSettings {
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String PREFS = "agent_settings";
    private static final String KEY_ALIAS = "java_agent_api_key_v1";
    private static final String BASE_URL = "base_url";
    private static final String MODEL = "model";
    private static final String OFFLINE = "offline";
    private static final String CIPHERTEXT = "api_ciphertext";
    private static final String IV = "api_iv";

    private final SharedPreferences prefs;

    public SecureSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getBaseUrl() {
        return prefs.getString(BASE_URL, DEFAULT_BASE_URL);
    }

    public String getModel() {
        return prefs.getString(MODEL, DEFAULT_MODEL);
    }

    public boolean isOffline() {
        return prefs.getBoolean(OFFLINE, false);
    }

    public boolean hasApiKey() {
        return !getApiKey().isEmpty();
    }

    public String getApiKey() {
        String encoded = prefs.getString(CIPHERTEXT, "");
        String encodedIv = prefs.getString(IV, "");
        if (encoded == null || encoded.isEmpty() || encodedIv == null || encodedIv.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception error) {
            // A restored/invalidated Keystore entry cannot be used. Remove only the unreadable secret.
            prefs.edit().remove(CIPHERTEXT).remove(IV).apply();
            return "";
        }
    }

    public void save(String baseUrl, String model, String apiKey, boolean offline) throws Exception {
        String normalizedBase = baseUrl == null ? "" : baseUrl.trim();
        String normalizedModel = model == null ? "" : model.trim();
        if (normalizedBase.isEmpty()) normalizedBase = DEFAULT_BASE_URL;
        if (normalizedModel.isEmpty()) normalizedModel = DEFAULT_MODEL;
        SharedPreferences.Editor editor = prefs.edit()
                .putString(BASE_URL, normalizedBase)
                .putString(MODEL, normalizedModel)
                .putBoolean(OFFLINE, offline);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            editor.remove(CIPHERTEXT).remove(IV);
        } else {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] ciphertext = cipher.doFinal(apiKey.trim().getBytes(StandardCharsets.UTF_8));
            editor.putString(CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                    .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        }
        if (!editor.commit()) throw new IllegalStateException("Settings could not be saved");
    }

    public void clearApiKey() {
        prefs.edit().remove(CIPHERTEXT).remove(IV).apply();
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return (SecretKey) store.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
