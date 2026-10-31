package hu.laskiss.remoteforadguardhome;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;

/**
 * A mentett szerverjelszavak titkosítása az Android Keystore egy saját AES-GCM kulcsával.
 * A kulcs a telefon védett kulcstárában keletkezik és marad, az appból sem olvasható ki.
 */
final class Crypto {

    private static final String STORE = "AndroidKeyStore";
    private static final String ALIAS = "server_passwords";
    private static final int IV_LEN = 12, TAG_BITS = 128;

    /** Csak tesztekhez: Keystore helyett ez a kulcs (normál futásnál mindig null). */
    static Key testKey;

    private Crypto() { }

    private static synchronized Key key() throws Exception {
        if (testKey != null) return testKey;
        KeyStore ks = KeyStore.getInstance(STORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ks.getKey(ALIAS, null);
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
        g.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return g.generateKey();
    }

    /** Titkosítás; az eredmény Base64(IV + titkos szöveg + hitelesítő címke). */
    static String encrypt(String plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    /** Visszafejtés; ha nem sikerül (pl. a kulcs elveszett), null. */
    static String decrypt(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            byte[] in = Base64.decode(data, Base64.NO_WRAP);
            if (in.length <= IV_LEN) return null;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, in, 0, IV_LEN));
            return new String(c.doFinal(in, IV_LEN, in.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
