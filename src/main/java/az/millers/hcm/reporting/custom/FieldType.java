package az.millers.hcm.reporting.custom;

/**
 * M119 — type of a custom-report field. Drives both filter-op compatibility
 * (see {@link FilterOp#isCompatible}) and the value-parsing branch in
 * {@link CustomReportSqlBuilder#parseValue}.
 *
 * <p>Kept deliberately small — every source's columns map onto one of these
 * seven. New types should be added only when a real source needs one
 * (TIME, INTERVAL, JSON would be candidates).
 */
public enum FieldType {
    STRING,
    INTEGER,
    DECIMAL,
    DATE,
    DATETIME,
    BOOLEAN,
    UUID
}
