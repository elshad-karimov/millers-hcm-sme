package az.millers.hcm.reporting.custom;

/**
 * M119 — one column the user can select / filter / sort by, within a
 * {@link CustomReportSource}.
 *
 * @param key        stable JSON-safe identifier (e.g. {@code "first_name"}).
 *                   This is what the SPA stores in fieldsJson/filtersJson.
 * @param label      human-friendly label shown in the builder + result columns.
 * @param sqlExpr    raw SQL fragment — usually {@code "alias.column"}, but can
 *                   be a CASE expression. ⚠ MUST be hard-coded — never derived
 *                   from user input. The builder splices this verbatim into
 *                   SELECT / WHERE / ORDER BY.
 * @param type       drives filter-op compatibility and value parsing.
 * @param filterable true if this field can be used in a filter row.
 * @param sortable   true if this field can be used as a sort column.
 */
public record FieldSpec(
        String key,
        String label,
        String sqlExpr,
        FieldType type,
        boolean filterable,
        boolean sortable) {

    /** Convenience for fields that are both filterable and sortable. */
    public static FieldSpec of(String key, String label, String sqlExpr, FieldType type) {
        return new FieldSpec(key, label, sqlExpr, type, true, true);
    }
}
