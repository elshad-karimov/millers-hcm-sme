package az.millers.hcm.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM column-level encryption for PII fields (PRD 14.3).
 *
 * <p>Wire format: base64( IV || ciphertext || authTag ), where IV is 12
 * random bytes and the GCM auth tag is 16 bytes — both standard. Decryption
 * fails closed (any tampering or wrong key → exception).
 *
 * <p>The key is loaded from {@code hcm.security.encryption.data-key}
 * (base64-encoded 32 bytes). Key rotation is a later milestone — when it
 * lands, this class will prepend a key-id byte before the IV.
 */
@Component
public class AesGcmEncryptor {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String ALGO = "AES/GCM/NoPadding";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmEncryptor(@Value("${hcm.security.encryption.data-key}") String base64Key) {
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "hcm.security.encryption.data-key must be base64-encoded 32 bytes (AES-256), got "
                            + decoded.length + " bytes");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    /** Returns null on null input — JPA conventions. */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return MARKER + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("AES encryption failed", e);
        }
    }

    /**
     * Decrypts a wire-format string. If the value doesn't carry the marker
     * prefix, returns it as-is (plaintext fallback — exists so legacy rows
     * loaded before the converter was applied keep working).
     */
    public String decrypt(String wireValue) {
        if (wireValue == null) return null;
        if (!wireValue.startsWith(MARKER)) {
            // Legacy plaintext — pass through.
            return wireValue;
        }
        String b64 = wireValue.substring(MARKER.length());
        try {
            byte[] all = Base64.getDecoder().decode(b64);
            if (all.length < IV_BYTES + 16) {
                throw new IllegalStateException("ciphertext shorter than IV + tag");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            byte[] ct = new byte[all.length - IV_BYTES];
            System.arraycopy(all, IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES decryption failed — wrong key or tampered ciphertext", e);
        }
    }

    /**
     * Prefix used to mark wire-format ciphertext so we can distinguish from
     * legacy plaintext in the same column.
     */
    public static final String MARKER = "enc:v1:";

    public boolean looksEncrypted(String value) {
        return value != null && value.startsWith(MARKER);
    }
}
