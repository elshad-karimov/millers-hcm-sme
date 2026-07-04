package az.millers.hcm.reporting.custom;

import java.util.EnumSet;
import java.util.Set;

/**
 * M119 — comparison operators the builder offers. Each op carries its
 * SQL fragment, the number of bind placeholders it consumes, and a
 * whitelist of compatible {@link FieldType}s.
 *
 * <p>{@code isCompatible} is the gate that stops a user from saving a
 * silly spec like "hire_date LIKE 'foo%'": the controller rejects on
 * the server side before any SQL is built.
 */
public enum FilterOp {
    EQ("="                , 1, EnumSet.allOf(FieldType.class)),
    NEQ("<>"              , 1, EnumSet.allOf(FieldType.class)),
    GT(">"                , 1, EnumSet.of(FieldType.INTEGER, FieldType.DECIMAL,
                                          FieldType.DATE, FieldType.DATETIME)),
    GTE(">="              , 1, EnumSet.of(FieldType.INTEGER, FieldType.DECIMAL,
                                          FieldType.DATE, FieldType.DATETIME)),
    LT("<"                , 1, EnumSet.of(FieldType.INTEGER, FieldType.DECIMAL,
                                          FieldType.DATE, FieldType.DATETIME)),
    LTE("<="              , 1, EnumSet.of(FieldType.INTEGER, FieldType.DECIMAL,
                                          FieldType.DATE, FieldType.DATETIME)),
    LIKE("ILIKE"          , 1, EnumSet.of(FieldType.STRING)),
    NOT_LIKE("NOT ILIKE"  , 1, EnumSet.of(FieldType.STRING)),
    IN("IN"               , 1, EnumSet.of(FieldType.STRING, FieldType.UUID,
                                          FieldType.INTEGER)),
    NOT_IN("NOT IN"       , 1, EnumSet.of(FieldType.STRING, FieldType.UUID,
                                          FieldType.INTEGER)),
    IS_NULL("IS NULL"     , 0, EnumSet.allOf(FieldType.class)),
    IS_NOT_NULL("IS NOT NULL", 0, EnumSet.allOf(FieldType.class)),
    BETWEEN("BETWEEN"     , 2, EnumSet.of(FieldType.INTEGER, FieldType.DECIMAL,
                                          FieldType.DATE, FieldType.DATETIME));

    private final String sql;
    private final int valueCount;
    private final Set<FieldType> compatible;

    FilterOp(String sql, int valueCount, Set<FieldType> compatible) {
        this.sql = sql;
        this.valueCount = valueCount;
        this.compatible = compatible;
    }

    public String sql() { return sql; }

    /** Number of bind placeholders this op consumes (0 for null-checks, 2 for BETWEEN). */
    public int valueCount() { return valueCount; }

    public boolean isCompatible(FieldType type) {
        return compatible.contains(type);
    }
}
