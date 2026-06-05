package az.millers.hcm.preboarding.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * M122 — pure-static plaintext generation + SHA-256 hashing for
 * pre-boarding magic-link tokens. Mirrors the M120 ApiKeyCrypto pattern
 * (separate class so a future refactor can DRY them through one
 * {@code MagicLinkToken} util without churning callers).
 *
 * <p>Format: {@code prebd_<43-char base64url>} — 256 bits of entropy, well
 * past any practical brute-force.
 */
public final class PreboardingTokens {

    public static final String PREFIX = "prebd_";

    /** prefix + 43 base64url chars (32 bytes, no padding). */
    public static final int PLAINTEXT_LENGTH = PREFIX.length() + 43;

    private static final SecureRandom RNG = new SecureRandom();

    private PreboardingTokens() {}

    public static String generatePlaintext() {
        byte[] raw = new byte[32];
        RNG.nextBytes(raw);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

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

    /** Cheap shape gate — rejects obvious junk before hashing. */
    public static boolean looksValid(String maybeToken) {
        return maybeToken != null
                && maybeToken.length() == PLAINTEXT_LENGTH
                && maybeToken.startsWith(PREFIX);
    }
}
