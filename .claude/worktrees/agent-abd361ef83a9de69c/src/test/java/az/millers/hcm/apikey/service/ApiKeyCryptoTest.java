package az.millers.hcm.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * M120 — invariants we lean on so hard they need pinning by tests:
 * <ul>
 *   <li>plaintext is the prefix + 43 base64url chars (256 bits of entropy),</li>
 *   <li>no two generated plaintexts collide,</li>
 *   <li>SHA-256 is deterministic and matches a known vector,</li>
 *   <li>{@code looksValid} rejects every shape that {@code generatePlaintext}
 *       could never produce.</li>
 * </ul>
 */
class ApiKeyCryptoTest {

    @Test
    void plaintextHasExpectedShape() {
        String pt = ApiKeyCrypto.generatePlaintext();
        assertThat(pt).startsWith("hcm_");
        assertThat(pt).hasSize(ApiKeyCrypto.PLAINTEXT_LENGTH);
        assertThat(pt.substring(4)).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void generatedPlaintextsAreDistinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String k = ApiKeyCrypto.generatePlaintext();
            assertThat(seen.add(k)).as("plaintext collision on iteration " + i).isTrue();
        }
    }

    @Test
    void hashIsDeterministic() {
        String pt = "hcm_abcdefgABCDEFG12345678901234567890123456789";
        assertThat(ApiKeyCrypto.hash(pt)).isEqualTo(ApiKeyCrypto.hash(pt));
    }

    @Test
    void hashMatchesKnownSha256() {
        // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        assertThat(ApiKeyCrypto.hash("hello"))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void last4IsLast4Chars() {
        assertThat(ApiKeyCrypto.last4("hcm_aaaaaaaa1234")).isEqualTo("1234");
    }

    @Test
    void last4RejectsShortStrings() {
        assertThatThrownBy(() -> ApiKeyCrypto.last4("abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void looksValidAcceptsOnlyMintedShape() {
        assertThat(ApiKeyCrypto.looksValid(ApiKeyCrypto.generatePlaintext())).isTrue();
    }

    @Test
    void looksValidRejectsObviousFakes() {
        assertThat(ApiKeyCrypto.looksValid(null)).isFalse();
        assertThat(ApiKeyCrypto.looksValid("")).isFalse();
        assertThat(ApiKeyCrypto.looksValid("hcm_too_short")).isFalse();
        assertThat(ApiKeyCrypto.looksValid("Bearer eyJhbGciOiJSUzI1NiJ9.foo")).isFalse();
        // Right length but wrong prefix — must still fail before hashing.
        StringBuilder sameLen = new StringBuilder("xyz_");
        while (sameLen.length() < ApiKeyCrypto.PLAINTEXT_LENGTH) sameLen.append('A');
        assertThat(ApiKeyCrypto.looksValid(sameLen.toString())).isFalse();
    }
}
