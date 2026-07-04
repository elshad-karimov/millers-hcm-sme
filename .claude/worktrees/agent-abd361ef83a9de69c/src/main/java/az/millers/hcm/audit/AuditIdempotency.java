package az.millers.hcm.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Single source of truth for audit-log-backed idempotency checks (M90).
 *
 * <p>Several schedulers — {@code LeaveAccrualService}, {@code ExpiryAlertScheduler},
 * {@code StalePoolReminderScheduler} — need to answer the same question:
 * "have I already journaled an audit row for this {@code (entityName, entityId,
 * action)} carrying a specific marker?". Each one used to hand-roll the same
 * regex-over-JSON scan, sometimes with subtle differences in escaping. This
 * helper centralises the pattern so a future move to a dedicated idempotency
 * table is a one-place change.
 *
 * <p>Marker matching is keyed on {@code (key, value)} pairs that should all
 * appear in the audit row's {@code newValue} JSON. Order is insignificant —
 * we build one big regex that requires every pair to appear (in any sequence)
 * using a {@code Pattern.DOTALL} match across the row.
 *
 * <p>Both keys and values are {@link Pattern#quote(String)}-escaped so a
 * caller passing e.g. {@code "user.name"} or a UUID string doesn't accidentally
 * inject a regex.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   if (auditIdempotency.hasMarker(MODULE_ENTITY, entityId, action,
 *           Map.of("day", today.toString(), "delta", String.valueOf(delta)))) {
 *       continue;
 *   }
 * }</pre>
 */
@org.springframework.stereotype.Component
public class AuditIdempotency {

    private final AuditService audit;

    public AuditIdempotency(AuditService audit) {
        this.audit = audit;
    }

    /**
     * @return {@code true} iff an audit row exists with the given
     *         {@code (entityName, entityId, action)} whose {@code newValue}
     *         JSON contains every {@code (key, value)} pair in {@code markers}.
     */
    public boolean hasMarker(String entityName, String entityId, String action,
                             Map<String, ?> markers) {
        if (markers == null || markers.isEmpty()) {
            // No markers ⇒ any matching row counts. This is what a caller
            // wants when "any audit row with this action exists" suffices.
            return audit.history(entityName, entityId).stream()
                    .anyMatch(a -> action.equals(a.getAction()));
        }
        Pattern markerPattern = compileMarkers(markers);
        return audit.history(entityName, entityId).stream()
                .filter(a -> action.equals(a.getAction()))
                .map(a -> a.getNewValue() == null ? "" : a.getNewValue())
                .anyMatch(json -> markerPattern.matcher(json).find());
    }

    /**
     * Build a {@code DOTALL} pattern that requires every (key, value) pair to
     * appear in the JSON, in any order. Each pair becomes a lookahead
     * {@code (?=.*"key"\s*:\s*"value")} — strings are matched as quoted JSON
     * values, numbers / booleans / nulls as bare tokens (no surrounding
     * quotes). Both keys and string values are regex-escaped so a value like
     * {@code "abc.def"} doesn't match {@code "abcXdef"}.
     */
    static Pattern compileMarkers(Map<String, ?> markers) {
        // LinkedHashMap preserves caller order — useful for deterministic
        // pattern strings in tests.
        Map<String, ?> ordered = new LinkedHashMap<>(markers);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> e : ordered.entrySet()) {
            String key = Pattern.quote(e.getKey());
            Object raw = e.getValue();
            String valueRegex;
            if (raw == null) {
                valueRegex = "null";
            } else if (raw instanceof Number || raw instanceof Boolean) {
                valueRegex = Pattern.quote(raw.toString());
            } else {
                // Strings (incl. dates, UUIDs) — match the quoted JSON form.
                valueRegex = "\"" + Pattern.quote(raw.toString()) + "\"";
            }
            sb.append("(?=.*\"").append(key).append("\"\\s*:\\s*").append(valueRegex).append(")");
        }
        return Pattern.compile(sb.toString(), Pattern.DOTALL);
    }
}
