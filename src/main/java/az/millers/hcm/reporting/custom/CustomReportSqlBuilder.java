package az.millers.hcm.reporting.custom;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import az.millers.hcm.common.BadRequestException;

/**
 * M119 — pure-static SQL assembler for the custom report builder. Given a
 * {@link CustomReportSpec} (already validated), produces a
 * parameterised SELECT statement and the parameter map to bind.
 *
 * <p>Security model: every column the SPA can pass — field key, filter key,
 * sort key — is looked up against
 * {@link CustomReportSource#field(String)} which returns the hard-coded
 * {@link FieldSpec#sqlExpr()}. User input never lands in SELECT / FROM /
 * WHERE / ORDER BY directly. Filter VALUES are bound as named parameters,
 * never inlined.
 *
 * <p>ABAC: when {@code scopeIds != null}, appends an
 * {@code AND <empCol> IN (:scopeIds)} clause iff the source declares a
 * {@link CustomReportSource#scopeEmployeeIdExpr()}. Sources without one
 * (none today, but reserved for HR-master data like leave-types) bypass
 * the filter — those rows are organisation-wide by design.
 */
public final class CustomReportSqlBuilder {

    /** Hard cap to keep a malicious or accidental spec from melting the DB. */
    public static final int MAX_ROW_LIMIT = 5_000;

    /** Default page size when the spec doesn't pin one. */
    public static final int DEFAULT_ROW_LIMIT = 1_000;

    public record Built(String sql, MapSqlParameterSource params, List<FieldSpec> columns) {}

    private CustomReportSqlBuilder() {}

    /**
     * Validate {@code spec} structurally — every key resolves, every op
     * matches the field type, value-count matches the op. Throws
     * {@link BadRequestException} on the first problem; the caller is
     * expected to surface the message to the user.
     */
    public static void validate(CustomReportSpec spec) {
        if (spec == null || spec.source() == null) {
            throw new BadRequestException("Source is required");
        }
        if (spec.fieldKeys().isEmpty()) {
            throw new BadRequestException("Select at least one field");
        }
        for (String k : spec.fieldKeys()) {
            spec.source().field(k).orElseThrow(
                    () -> new BadRequestException("Unknown field: " + k));
        }
        for (CustomReportSpec.Filter f : spec.filters()) {
            FieldSpec fs = spec.source().field(f.fieldKey()).orElseThrow(
                    () -> new BadRequestException("Unknown filter field: " + f.fieldKey()));
            if (!fs.filterable()) {
                throw new BadRequestException("Field is not filterable: " + f.fieldKey());
            }
            if (!f.op().isCompatible(fs.type())) {
                throw new BadRequestException(
                        "Operator " + f.op() + " is not valid on " + fs.type() + " field "
                                + f.fieldKey());
            }
            int got = f.values().size();
            switch (f.op()) {
                case IS_NULL, IS_NOT_NULL -> {
                    if (got != 0) {
                        throw new BadRequestException(
                                f.op() + " expects no values, got " + got);
                    }
                }
                case BETWEEN -> {
                    if (got != 2) {
                        throw new BadRequestException(
                                "BETWEEN requires exactly 2 values, got " + got);
                    }
                }
                case IN, NOT_IN -> {
                    if (got < 1) {
                        throw new BadRequestException(
                                f.op() + " requires at least 1 value");
                    }
                }
                default -> {
                    if (got != 1) {
                        throw new BadRequestException(
                                f.op() + " expects 1 value, got " + got);
                    }
                }
            }
            // Probe-parse every value so a bad literal fails on save, not on run.
            for (String v : f.values()) parseValue(fs.type(), v);
        }
        for (CustomReportSpec.Sort s : spec.sorts()) {
            FieldSpec fs = spec.source().field(s.fieldKey()).orElseThrow(
                    () -> new BadRequestException("Unknown sort field: " + s.fieldKey()));
            if (!fs.sortable()) {
                throw new BadRequestException("Field is not sortable: " + s.fieldKey());
            }
        }
    }

    /**
     * Assemble the SELECT statement and bind parameters. Caller passes
     * {@code scopeIds} as-is from
     * {@link az.millers.hcm.security.scope.AccessScopeService#scopeOrNullForCurrentUser()}
     * — {@code null} means "unrestricted", a set means "narrow to these".
     */
    public static Built build(CustomReportSpec spec, Set<UUID> scopeIds) {
        validate(spec);
        CustomReportSource src = spec.source();

        StringBuilder select = new StringBuilder("SELECT ");
        List<FieldSpec> columns = new ArrayList<>(spec.fieldKeys().size());
        for (int i = 0; i < spec.fieldKeys().size(); i++) {
            String key = spec.fieldKeys().get(i);
            FieldSpec fs = src.field(key).orElseThrow();
            columns.add(fs);
            if (i > 0) select.append(", ");
            // The alias is the field key — never user-typed, always whitelisted.
            select.append(fs.sqlExpr()).append(" AS \"").append(fs.key()).append('"');
        }

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        for (int i = 0; i < spec.filters().size(); i++) {
            CustomReportSpec.Filter f = spec.filters().get(i);
            FieldSpec fs = src.field(f.fieldKey()).orElseThrow();
            String expr = fs.sqlExpr();
            switch (f.op()) {
                case IS_NULL ->
                    where.append(" AND ").append(expr).append(" IS NULL");
                case IS_NOT_NULL ->
                    where.append(" AND ").append(expr).append(" IS NOT NULL");
                case BETWEEN -> {
                    String p1 = "f" + i + "a";
                    String p2 = "f" + i + "b";
                    where.append(" AND ").append(expr)
                            .append(" BETWEEN :").append(p1).append(" AND :").append(p2);
                    params.addValue(p1, parseValue(fs.type(), f.values().get(0)));
                    params.addValue(p2, parseValue(fs.type(), f.values().get(1)));
                }
                case IN, NOT_IN -> {
                    String p = "f" + i;
                    where.append(" AND ").append(expr)
                            .append(' ').append(f.op().sql()).append(" (:").append(p).append(')');
                    List<Object> parsed = new ArrayList<>(f.values().size());
                    for (String v : f.values()) parsed.add(parseValue(fs.type(), v));
                    params.addValue(p, parsed);
                }
                case LIKE, NOT_LIKE -> {
                    String p = "f" + i;
                    where.append(" AND ").append(expr)
                            .append(' ').append(f.op().sql()).append(" :").append(p);
                    // wildcard convention: user is expected to include %; if absent,
                    // we make it a "contains" search to match expectations.
                    String raw = f.values().get(0);
                    String wrapped = raw.contains("%") ? raw : "%" + raw + "%";
                    params.addValue(p, wrapped);
                }
                default -> {
                    String p = "f" + i;
                    where.append(" AND ").append(expr)
                            .append(' ').append(f.op().sql()).append(" :").append(p);
                    params.addValue(p, parseValue(fs.type(), f.values().get(0)));
                }
            }
        }

        // ABAC scope clause
        if (scopeIds != null && src.scopeEmployeeIdExpr() != null) {
            if (scopeIds.isEmpty()) {
                // Caller has no scope — guarantee no rows without rejecting the request.
                where.append(" AND 1=0");
            } else {
                where.append(" AND ").append(src.scopeEmployeeIdExpr())
                        .append(" IN (:scopeIds)");
                params.addValue("scopeIds", scopeIds);
            }
        }

        StringBuilder order = new StringBuilder();
        if (!spec.sorts().isEmpty()) {
            order.append(" ORDER BY ");
            for (int i = 0; i < spec.sorts().size(); i++) {
                CustomReportSpec.Sort s = spec.sorts().get(i);
                FieldSpec fs = src.field(s.fieldKey()).orElseThrow();
                if (i > 0) order.append(", ");
                order.append(fs.sqlExpr()).append(' ').append(s.direction().name());
            }
        }

        int limit = clampLimit(spec.rowLimit());
        String sql = select.toString()
                + " FROM " + src.fromClause()
                + where
                + order
                + " LIMIT " + limit;

        return new Built(sql, params, columns);
    }

    public static int clampLimit(int requested) {
        if (requested <= 0) return DEFAULT_ROW_LIMIT;
        return Math.min(requested, MAX_ROW_LIMIT);
    }

    /**
     * Parse a raw string from the JSON spec into the column's native Java
     * type so {@link MapSqlParameterSource} hands the driver the right
     * binding. Throws {@link BadRequestException} on malformed input so
     * the user sees a useful message rather than a JDBC stack trace.
     */
    static Object parseValue(FieldType type, String raw) {
        if (raw == null) {
            throw new BadRequestException("Filter value cannot be null");
        }
        try {
            return switch (type) {
                case STRING   -> raw;
                case INTEGER  -> Integer.parseInt(raw.trim());
                case DECIMAL  -> new BigDecimal(raw.trim());
                case DATE     -> LocalDate.parse(raw.trim());
                case DATETIME -> OffsetDateTime.parse(raw.trim());
                case BOOLEAN  -> Boolean.parseBoolean(raw.trim());
                case UUID     -> UUID.fromString(raw.trim());
            };
        } catch (RuntimeException e) {
            throw new BadRequestException(
                    "Invalid " + type + " value: '" + raw + "'");
        }
    }
}
