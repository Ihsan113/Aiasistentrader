package id.danzku.mt5analyzer;

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

/** User-owned BYOK Groq key encrypted at rest using Android Keystore AES-GCM. */
public final class KeyVault {
    private static final String ALIAS = "mt5_groq_key_v1";
    private static final String PREF = "vault";
    private static final String IV = "iv";
    private static final String CT = "ct";
    private KeyVault() {}

    private static SecretKey secret() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry)store.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    public static void save(Context context, String key) throws Exception {
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("Groq API key kosong");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret());
        byte[] encrypted = cipher.doFinal(key.trim().getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(CT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply();
    }

    public static String load(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            if (!prefs.contains(CT)) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secret(),
                    new GCMParameterSpec(128, Base64.decode(prefs.getString(IV, ""), Base64.DEFAULT)));
            byte[] plain = cipher.doFinal(Base64.decode(prefs.getString(CT, ""), Base64.DEFAULT));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ignored) { return ""; }
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS);
        } catch (Exception ignored) { }
    }
}
