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
 * <p>Wire format: {@code enc:v<N>:base64( IV || ciphertext || authTag )},
 * where IV is 12 random bytes and the GCM auth tag is 16 bytes.  Decryption
 * fails closed (any tampering or wrong key → exception).
 *
 * <h3>Key rotation (M48)</h3>
 * <p>Two key slots are supported:
 * <ul>
 *   <li><b>v1</b> — {@code hcm.security.encryption.data-key} (always required).
 *   <li><b>v2</b> — {@code hcm.security.encryption.data-key-v2} (optional).
 *       When present, {@link #encrypt} writes {@code enc:v2:…} and
 *       {@link #decrypt} can read both prefixes (dual-read window).
 * </ul>
 * <p>Run {@code POST /api/admin/key-rotation/run} after deploying a build that
 * carries the v2 key to re-encrypt all {@code enc:v1:} rows.  Once rotation
 * completes, promote v2 → v1 and remove the v2 property in the next deploy.
 */
@Component
public class AesGcmEncryptor {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String ALGO = "AES/GCM/NoPadding";

    /** Prefix for ciphertext produced with the v1 (original) data key. */
    public static final String MARKER    = "enc:v1:";
    /** Prefix for ciphertext produced with the v2 (rotated) data key. */
    public static final String MARKER_V2 = "enc:v2:";

    private final SecretKeySpec keyV1;
    /** Null when {@code hcm.security.encryption.data-key-v2} is not configured. */
    private final SecretKeySpec keyV2;

    private final SecureRandom random = new SecureRandom();

    public AesGcmEncryptor(
            @Value("${hcm.security.encryption.data-key}") String base64KeyV1,
            @Value("${hcm.security.encryption.data-key-v2:}") String base64KeyV2) {

        byte[] v1Bytes = Base64.getDecoder().decode(base64KeyV1);
        if (v1Bytes.length != 32) {
            throw new IllegalStateException(
                    "hcm.security.encryption.data-key must be base64-encoded 32 bytes (AES-256), got "
                            + v1Bytes.length + " bytes");
        }
        this.keyV1 = new SecretKeySpec(v1Bytes, "AES");

        if (base64KeyV2 == null || base64KeyV2.isBlank()) {
            this.keyV2 = null;
        } else {
            byte[] v2Bytes = Base64.getDecoder().decode(base64KeyV2);
            if (v2Bytes.length != 32) {
                throw new IllegalStateException(
                        "hcm.security.encryption.data-key-v2 must be base64-encoded 32 bytes (AES-256), got "
                                + v2Bytes.length + " bytes");
            }
            this.keyV2 = new SecretKeySpec(v2Bytes, "AES");
        }
    }

    /**
     * Encrypts {@code plaintext} using the active key.
     * Writes {@code enc:v2:…} when the v2 key is configured, otherwise
     * {@code enc:v1:…}.  Returns {@code null} on {@code null} input.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        SecretKeySpec activeKey   = keyV2 != null ? keyV2 : keyV1;
        String         activeMarker = keyV2 != null ? MARKER_V2 : MARKER;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, activeKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct  = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv,  0, out, 0,         iv.length);
            System.arraycopy(ct,  0, out, iv.length, ct.length);
            return activeMarker + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("AES encryption failed", e);
        }
    }

    /**
     * Decrypts a wire-format string.
     * <ul>
     *   <li>{@code enc:v1:…} — decrypted with the v1 key.</li>
     *   <li>{@code enc:v2:…} — decrypted with the v2 key (throws if v2 not configured).</li>
     *   <li>Anything else — returned as-is (legacy plaintext fallback).</li>
     * </ul>
     */
    public String decrypt(String wireValue) {
        if (wireValue == null) return null;
        if (wireValue.startsWith(MARKER_V2)) {
            if (keyV2 == null) {
                throw new IllegalStateException(
                        "Encountered enc:v2: ciphertext but hcm.security.encryption.data-key-v2 is not configured");
            }
            return doDecrypt(wireValue.substring(MARKER_V2.length()), keyV2);
        }
        if (wireValue.startsWith(MARKER)) {
            return doDecrypt(wireValue.substring(MARKER.length()), keyV1);
        }
        // Legacy plaintext row — pass through unchanged.
        return wireValue;
    }

    private String doDecrypt(String b64, SecretKeySpec k) {
        try {
            byte[] all = Base64.getDecoder().decode(b64);
            if (all.length < IV_BYTES + 16) {
                throw new IllegalStateException("ciphertext shorter than IV + tag");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0,        iv, 0,        IV_BYTES);
            byte[] ct = new byte[all.length - IV_BYTES];
            System.arraycopy(all, IV_BYTES, ct, 0,        ct.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES decryption failed — wrong key or tampered ciphertext", e);
        }
    }

    /** True if the value carries any recognised encryption prefix. */
    public boolean looksEncrypted(String value) {
        return value != null
                && (value.startsWith(MARKER) || value.startsWith(MARKER_V2));
    }

    /** True if the value carries the v1 prefix and therefore needs rotation. */
    public boolean isV1Encrypted(String value) {
        return value != null && value.startsWith(MARKER);
    }

    /** True when the v2 key is present — key rotation can proceed. */
    public boolean hasV2Key() {
        return keyV2 != null;
    }
}
