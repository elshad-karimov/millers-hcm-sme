package az.millers.hcm.apikey.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * M120 — pure-static helpers for API-key plaintext generation and
 * hashing. Kept Spring-free so the contract can be exercised with
 * plain JUnit (Mockito doesn't work on Java 25 class-file v69).
 *
 * <p>Format: {@code hcm_<base64url-32-bytes-of-entropy>}. The {@code hcm_}
 * prefix makes leaked keys searchable in logs / Git history; the 32
 * bytes are 256 bits of entropy, well past any practical brute-force.
 */
public final class ApiKeyCrypto {

    public static final String PREFIX = "hcm_";

    /** Length of a generated plaintext key including the prefix. */
    public static final int PLAINTEXT_LENGTH = PREFIX.length() + 43; // 32 bytes base64url, no padding = 43

    private static final SecureRandom RNG = new SecureRandom();

    private ApiKeyCrypto() {}

    /** Mint a fresh plaintext key. The caller must persist only the hash. */
    public static String generatePlaintext() {
        byte[] raw = new byte[32];
        RNG.nextBytes(raw);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** SHA-256 hex digest of the plaintext key. */
    public static String hash(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext is required");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Last-4 UI fingerprint. Always exactly 4 chars on valid input. */
    public static String last4(String plaintext) {
        if (plaintext == null || plaintext.length() < 4) {
            throw new IllegalArgumentException("plaintext too short");
        }
        return plaintext.substring(plaintext.length() - 4);
    }

    /** Cheap shape gate — rejects obviously-malformed headers before hashing. */
    public static boolean looksValid(String maybeKey) {
        return maybeKey != null
            && maybeKey.length() == PLAINTEXT_LENGTH
            && maybeKey.startsWith(PREFIX);
    }
}
