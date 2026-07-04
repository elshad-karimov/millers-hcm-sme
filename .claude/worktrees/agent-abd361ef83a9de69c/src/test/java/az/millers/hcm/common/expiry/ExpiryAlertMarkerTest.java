package az.millers.hcm.common.expiry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Verifies the regex-based dedup marker detection that {@code ExpiryAlertScheduler.alreadyAlerted}
 * uses. The full scheduler walker is exercised by the production runtime; this
 * test pins down the one piece of non-obvious logic — the marker pattern — so
 * a future change to the audit JSON shape can't silently break idempotency.
 *
 * <p>If you change the marker pattern in {@code ExpiryAlertScheduler}, mirror
 * the change here.
 */
class ExpiryAlertMarkerTest {

    private static Pattern markerFor(LocalDate today, int delta) {
        return Pattern.compile(
                "\"day\"\\s*:\\s*\"" + Pattern.quote(today.toString()) + "\""
                        + ".*?\"delta\"\\s*:\\s*" + delta + "\\b",
                Pattern.DOTALL);
    }

    @Test
    void matchesCompactJsonProducedByObjectMapper() {
        LocalDate today = LocalDate.of(2026, 6, 1);
        String json = "{\"day\":\"2026-06-01\",\"delta\":7,\"label\":\"Passport\"}";
        assertThat(markerFor(today, 7).matcher(json).find()).isTrue();
    }

    @Test
    void matchesPrettyPrintedJsonRoundTrippedThroughJsonb() {
        // PostgreSQL JSONB normalises whitespace on read — the original
        // Jackson-compact JSON ends up with " : " after keys.
        LocalDate today = LocalDate.of(2026, 6, 1);
        String json = "{\"day\" : \"2026-06-01\", \"delta\" : 7, \"label\" : \"Passport\"}";
        assertThat(markerFor(today, 7).matcher(json).find()).isTrue();
    }

    @Test
    void rejectsMismatchedDate() {
        LocalDate today = LocalDate.of(2026, 6, 1);
        String json = "{\"day\":\"2026-05-31\",\"delta\":7}";
        assertThat(markerFor(today, 7).matcher(json).find()).isFalse();
    }

    @Test
    void rejectsMismatchedDelta() {
        // Critical correctness test: the regex uses \b after delta so it
        // doesn't match "delta:7" inside "delta:70".
        LocalDate today = LocalDate.of(2026, 6, 1);
        String json = "{\"day\":\"2026-06-01\",\"delta\":70}";
        assertThat(markerFor(today, 7).matcher(json).find()).isFalse();
    }

    @Test
    void rejectsMissingDelta() {
        LocalDate today = LocalDate.of(2026, 6, 1);
        String json = "{\"day\":\"2026-06-01\",\"label\":\"Passport\"}";
        assertThat(markerFor(today, 7).matcher(json).find()).isFalse();
    }

    @Test
    void handlesDayBeforeDeltaInJsonOrder() {
        // The DOTALL flag and .*? lazy quantifier let the matcher span the
        // gap between "day" and "delta" regardless of intervening keys.
        LocalDate today = LocalDate.of(2026, 6, 1);
        String json = "{\"day\":\"2026-06-01\",\"label\":\"Passport\",\"recipient\":\"alice\",\"delta\":7}";
        assertThat(markerFor(today, 7).matcher(json).find()).isTrue();
    }
}
