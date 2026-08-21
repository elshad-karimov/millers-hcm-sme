package az.millers.hcm.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Converts arbitrary values into the {@link JsonNode} form that {@code jsonb}
 * columns are mapped with.
 *
 * <p>Entities used to declare those columns as a bare {@code Object}:
 *
 * <pre>{@code @JdbcTypeCode(SqlTypes.JSON) private Object afterValue;}</pre>
 *
 * <p>Hibernate cannot bind that. {@code Object} carries no type descriptor, so
 * the JSON binder falls back to expecting a String, and assigning anything else
 * blows up at flush time with a ClassCastException — "OrgUnitResponse cannot be
 * cast to java.lang.String" — inside the commit, so the whole transaction rolls
 * back and the user sees a 500 for an operation whose real work had already
 * succeeded. Adding an org unit failed exactly this way, on the history row
 * written after the unit itself had inserted cleanly.
 *
 * <p>{@link JsonNode} is a type Hibernate can bind and Jackson can round-trip,
 * so the column works and the REST shape is unchanged — a node serialises as
 * the object it holds, not as a quoted string. Serialisation goes through the
 * application's {@link ObjectMapper}, which is the one with JSR-310 registered,
 * so dates inside these payloads survive.
 */
public final class JsonColumns {

    private JsonColumns() {}

    /**
     * Value as a {@link JsonNode}, or null for null input.
     *
     * <p>Never throws. These payloads are audit trails and diagnostic detail
     * hanging off an operation that has already succeeded; failing the caller's
     * transaction because a snapshot would not serialise gets the trade-off
     * backwards. An unserialisable value is recorded as an object saying so,
     * which is greppable and honest, rather than silently dropped.
     */
    public static JsonNode toNode(ObjectMapper mapper, Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.valueToTree(value);
        } catch (RuntimeException ex) {
            return mapper.createObjectNode()
                    .put("_serializationError", String.valueOf(ex.getMessage()))
                    .put("_type", value.getClass().getName());
        }
    }
}
