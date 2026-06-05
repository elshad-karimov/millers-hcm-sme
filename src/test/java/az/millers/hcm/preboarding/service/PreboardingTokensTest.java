package az.millers.hcm.preboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * M122 — pre-boarding magic-link token invariants the public REST handler
 * leans on:
 * <ul>
 *   <li>plaintext has the prefix and is exactly the expected length,</li>
 *   <li>no two minted plaintexts collide,</li>
 *   <li>SHA-256 hashing is deterministic and matches a known vector,</li>
 *   <li>{@code looksValid} accepts only the minted shape.</li>
 * </ul>
 */
class PreboardingTokensTest {

    @Test
    void plaintextStartsWithPrefixAndIsExpectedLength() {
        String pt = PreboardingTokens.generatePlaintext();
        assertThat(pt).startsWith("prebd_").hasSize(PreboardingTokens.PLAINTEXT_LENGTH);
        assertThat(pt.substring(6)).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void mintedPlaintextsAreDistinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String t = PreboardingTokens.generatePlaintext();
            assertThat(seen.add(t)).as("collision at iteration " + i).isTrue();
        }
    }

    @Test
    void hashIsDeterministic() {
        String pt = "prebd_abc_def_GHI_jkl_MNO_pqr_STU_vwx_YZ_0123";
        assertThat(PreboardingTokens.hash(pt)).isEqualTo(PreboardingTokens.hash(pt));
    }

    @Test
    void hashMatchesKnownSha256Vector() {
        // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        assertThat(PreboardingTokens.hash("hello"))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void hashRejectsNull() {
        assertThatThrownBy(() -> PreboardingTokens.hash(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void looksValidAcceptsMintedShape() {
        assertThat(PreboardingTokens.looksValid(PreboardingTokens.generatePlaintext())).isTrue();
    }

    @Test
    void looksValidRejectsFakes() {
        assertThat(PreboardingTokens.looksValid(null)).isFalse();
        assertThat(PreboardingTokens.looksValid("")).isFalse();
        assertThat(PreboardingTokens.looksValid("prebd_short")).isFalse();
        assertThat(PreboardingTokens.looksValid("hcm_abcdefghijklmnopqrstuvwxyz0123456789abcdefg")).isFalse();
    }

    @Test
    void looksValidRejectsCorrectLengthWithWrongPrefix() {
        StringBuilder b = new StringBuilder("xxxxx_");
        while (b.length() < PreboardingTokens.PLAINTEXT_LENGTH) b.append('A');
        assertThat(PreboardingTokens.looksValid(b.toString())).isFalse();
    }
}
