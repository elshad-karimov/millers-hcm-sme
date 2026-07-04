package az.millers.hcm.reporting.custom;

import java.util.List;

/**
 * M119 — value-typed spec the SQL builder works from. This is what the SPA
 * sends on save and on preview; what's persisted in
 * {@code custom_report.fields_json/filters_json/sort_json} is the same shape.
 *
 * <p>All fields immutable; defensive copies of the lists are taken on
 * construction so callers can't mutate after the fact.
 */
public record CustomReportSpec(
        CustomReportSource source,
        List<String> fieldKeys,
        List<Filter> filters,
        List<Sort> sorts,
        int rowLimit) {

    public CustomReportSpec {
        fieldKeys = List.copyOf(fieldKeys);
        filters   = List.copyOf(filters);
        sorts     = List.copyOf(sorts);
    }

    /**
     * A single WHERE clause.
     *
     * @param values raw string values from the JSON; parsed to the field's
     *               native type by the SQL builder. For unary ops
     *               ({@link FilterOp#IS_NULL}, {@link FilterOp#IS_NOT_NULL})
     *               this is empty. For {@link FilterOp#IN} / NOT_IN each
     *               element is a separate item.
     */
    public record Filter(String fieldKey, FilterOp op, List<String> values) {
        public Filter {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    /** A single ORDER BY clause. */
    public record Sort(String fieldKey, Direction direction) {
        public enum Direction { ASC, DESC }
    }
}
