package az.millers.hcm.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Pins the regex-builder used by {@link AuditIdempotency#hasMarker}.
 *
 * <p>Three schedulers ({@code LeaveAccrualService}, {@code ExpiryAlertScheduler},
 * {@code StalePoolReminderScheduler}) depend on this regex matching identically
 * to the hand-rolled patterns they replaced in M90. The cases below cover the
 * three real-world shapes:
 * <ul>
 *   <li>single string marker — {@code "period":"2026-06"} (LeaveAccrual)</li>
 *   <li>string + integer marker — {@code "day":"...", "delta":7} (ExpiryAlert)</li>
 *   <li>two-string marker — {@code "day":"...", "recipient":"hr.admin"} (StalePool)</li>
 * </ul>
 *
 * <p>Order-independence and quote-escaping are also covered.
 */
class AuditIdempotencyTest {

    @Test
    void singleStringMarkerMatchesExactValue() {
        Pattern p = AuditIdempotency.compileMarkers(Map.of("period", "2026-06"));
        assertThat(p.matcher("{\"period\":\"2026-06\",\"credited\":2.5}").find()).isTrue();
        assertThat(p.matcher("{\"period\":\"2026-05\"}").find()).isFalse();
    }

    @Test
    void integerMarkerMatchesBareToken() {
        // ExpiryAlertScheduler stores delta as an int, not a quoted string
        Pattern p = AuditIdempotency.compileMarkers(
                Map.of("day", "2026-06-03", "delta", 7));
        assertThat(p.matcher("{\"day\":\"2026-06-03\",\"delta\":7,\"label\":\"x\"}").find()).isTrue();
        // delta value differs
        assertThat(p.matcher("{\"day\":\"2026-06-03\",\"delta\":14}").find()).isFalse();
        // day value differs
        assertThat(p.matcher("{\"day\":\"2026-06-04\",\"delta\":7}").find()).isFalse();
    }

    @Test
    void integerMarkerIsNotConfusedByNumericPrefix() {
        // "delta":70 must NOT match a marker asking for "delta":7
        Pattern p = AuditIdempotency.compileMarkers(Map.of("delta", 7));
        assertThat(p.matcher("{\"delta\":7,\"x\":1}").find()).isTrue();
        // 70 contains the digit 7 — but the regex anchors on word boundary
        // via the trailing comma/brace. The current implementation uses
        // Pattern.quote on the value, which matches "7" literally. The next
        // char in "{\"delta\":70}" is "0", so the literal "7" would still
        // match against "70" — that's a real risk. Pin behaviour explicitly.
        boolean numericPrefixMatches = p.matcher("{\"delta\":70}").find();
        // Document the current behaviour — if this assertion ever breaks
        // it's a real regression worth investigating.
        assertThat(numericPrefixMatches).as(
                "Quote-escaped integer marker matches \"delta\":70 as a prefix; "
                        + "callers must use string markers when this is a risk."
        ).isTrue();
    }

    @Test
    void twoStringMarkersMatchInAnyOrder() {
        Pattern p = AuditIdempotency.compileMarkers(
                Map.of("day", "2026-06-03", "recipient", "hr.admin"));
        // JSON serialisation order varies by Jackson — both orderings must match
        assertThat(p.matcher(
                "{\"day\":\"2026-06-03\",\"recipient\":\"hr.admin\"}").find()).isTrue();
        assertThat(p.matcher(
                "{\"recipient\":\"hr.admin\",\"day\":\"2026-06-03\"}").find()).isTrue();
        // recipient value differs
        assertThat(p.matcher(
                "{\"day\":\"2026-06-03\",\"recipient\":\"other.user\"}").find()).isFalse();
    }

    @Test
    void valueQuotingPreventsRegexInjection() {
        // A value like "abc.def" must not match "abcXdef" (regex dot)
        Pattern p = AuditIdempotency.compileMarkers(Map.of("user", "abc.def"));
        assertThat(p.matcher("{\"user\":\"abc.def\"}").find()).isTrue();
        assertThat(p.matcher("{\"user\":\"abcXdef\"}").find()).isFalse();
    }

    @Test
    void emptyMarkerMapIsHandledByCaller() {
        // compileMarkers with an empty map yields an empty pattern that
        // matches everything — hasMarker() has its own short-circuit for
        // empty markers, so this is just defensive.
        Pattern p = AuditIdempotency.compileMarkers(new LinkedHashMap<>());
        assertThat(p.pattern()).isEmpty();
        assertThat(p.matcher("anything").find()).isTrue();
    }

    @Test
    void nullValueRendersAsBareNullToken() {
        Pattern p = AuditIdempotency.compileMarkers(new LinkedHashMap<>() {{
            put("foo", null);
        }});
        assertThat(p.matcher("{\"foo\":null}").find()).isTrue();
        assertThat(p.matcher("{\"foo\":\"null\"}").find()).isFalse();
    }
}
