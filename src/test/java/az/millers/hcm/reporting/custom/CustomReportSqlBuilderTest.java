package az.millers.hcm.reporting.custom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;

/**
 * M119 — security and correctness tests for the SQL builder. The contract
 * we lean on hardest in production is exercised here:
 * <ul>
 *   <li>only whitelisted field keys ever land in SELECT / WHERE / ORDER BY,</li>
 *   <li>filter values are bound, never inlined,</li>
 *   <li>op/type compatibility is enforced before we ever assemble SQL,</li>
 *   <li>ABAC scope filter is honoured and an empty scope short-circuits to no rows.</li>
 * </ul>
 */
class CustomReportSqlBuilderTest {

    private static CustomReportSpec spec(List<String> fields,
                                         List<CustomReportSpec.Filter> filters,
                                         List<CustomReportSpec.Sort> sorts,
                                         int limit) {
        return new CustomReportSpec(
                CustomReportSource.EMPLOYEES, fields, filters, sorts, limit);
    }

    // ── happy path ─────────────────────────────────────────────────────────

    @Test
    void buildsSelectFromAndOrderBy() {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("first_name", "last_name", "hire_date"),
                     List.of(),
                     List.of(new CustomReportSpec.Sort(
                             "last_name", CustomReportSpec.Sort.Direction.ASC)),
                     100),
                null);

        assertThat(built.sql()).contains("SELECT e.first_name");
        assertThat(built.sql()).contains("e.last_name AS \"last_name\"");
        assertThat(built.sql()).contains("FROM core_hr.employee e");
        assertThat(built.sql()).contains("ORDER BY e.last_name ASC");
        assertThat(built.sql()).contains("LIMIT 100");
        assertThat(built.columns()).extracting(FieldSpec::key)
                .containsExactly("first_name", "last_name", "hire_date");
    }

    @Test
    void clampsRowLimitToMax() {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("employee_no"), List.of(), List.of(), 99_999),
                null);
        assertThat(built.sql()).endsWith("LIMIT " + CustomReportSqlBuilder.MAX_ROW_LIMIT);
    }

    @Test
    void defaultsRowLimitWhenZero() {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("employee_no"), List.of(), List.of(), 0),
                null);
        assertThat(built.sql()).endsWith("LIMIT " + CustomReportSqlBuilder.DEFAULT_ROW_LIMIT);
    }

    // ── filters ────────────────────────────────────────────────────────────

    @Test
    void buildsEqualityFilter() {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("employee_no"),
                     List.of(new CustomReportSpec.Filter(
                             "employment_status", FilterOp.EQ, List.of("ACTIVE"))),
                     List.of(), 100),
                null);
        assertThat(built.sql()).contains("WHERE 1=1 AND e.employment_status = :f0");
        assertThat(built.params().getValues()).containsEntry("f0", "ACTIVE");
    }

    @Test
    void buildsBetweenFilter() {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("employee_no"),
                     List.of(new CustomReportSpec.Filter(
                             "hire_date", FilterOp.BETWEEN,
                             List.of("2025-01-01", "2025-12-31"))),
                     List.of(), 100),
                null);
        assertThat(built.sql()).contains("BETWEEN :f0a AND :f0b");
        assertThat(built.params().getValues()).containsEntry("f0a", LocalDate.of(2025, 1, 1));
        assertThat(built.params().getValues()).containsEntry("f0b", LocalDate.of(2025, 12, 31));
    }

    @Test
    void buildsInFilter() {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("employee_no"),
                     List.of(new CustomReportSpec.Filter(
                             "department_name", FilterOp.IN,
                             List.of("Engineering", "Finance"))),
                     List.of(), 100),
                null);
        assertThat(built.sql()).contains("e.department_name IN (:f0)");
        @SuppressWarnings("unchecked")
        List<Object> inValues = (List<Object>) built.params().getValues().get("f0");
        assertThat(inValues).containsExactly("Engineering", "Finance");
    }

    @Test
    void buildsNullChecks() {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("employee_no"),
                     List.of(
                         new CustomReportSpec.Filter("manager_id", FilterOp.IS_NULL, List.of()),
                         new CustomReportSpec.Filter("email", FilterOp.IS_NOT_NULL, List.of())),
                     List.of(), 100),
                null);
        assertThat(built.sql()).contains("e.manager_id IS NULL");
        assertThat(built.sql()).contains("e.email IS NOT NULL");
        assertThat(built.params().getValues()).isEmpty();
    }

    @Test
    void wrapsLikeWithPercentsWhenAbsent() {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("employee_no"),
                     List.of(new CustomReportSpec.Filter(
                             "first_name", FilterOp.LIKE, List.of("Sara"))),
                     List.of(), 100),
                null);
        assertThat(built.params().getValues()).containsEntry("f0", "%Sara%");
    }

    @Test
    void preservesLikeWildcardsWhenSupplied() {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("employee_no"),
                     List.of(new CustomReportSpec.Filter(
                             "first_name", FilterOp.LIKE, List.of("Sar%"))),
                     List.of(), 100),
                null);
        assertThat(built.params().getValues()).containsEntry("f0", "Sar%");
    }

    @Test
    void filtersChainAndIndependently() {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("employee_no"),
                     List.of(
                         new CustomReportSpec.Filter("employment_status", FilterOp.EQ, List.of("ACTIVE")),
                         new CustomReportSpec.Filter("hire_date", FilterOp.GTE, List.of("2024-01-01"))),
                     List.of(), 100),
                null);
        // Each filter must use a unique parameter name so reusing the same field
        // twice (e.g. range filters) doesn't clobber.
        assertThat(built.params().getValues()).containsKeys("f0", "f1");
    }

    // ── ABAC scope ─────────────────────────────────────────────────────────

    @Test
    void appendsScopeClauseWhenRestricted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("employee_no"), List.of(), List.of(), 100),
                Set.of(a, b));
        assertThat(built.sql()).contains("AND e.id IN (:scopeIds)");
        @SuppressWarnings("unchecked")
        Set<Object> scopeBind = (Set<Object>) built.params().getValues().get("scopeIds");
        assertThat(scopeBind).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void emptyScopeShortCircuitsToNoRows() {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("employee_no"), List.of(), List.of(), 100),
                Set.of());
        assertThat(built.sql()).contains("AND 1=0");
        assertThat(built.params().getValues()).doesNotContainKey("scopeIds");
    }

    @Test
    void nullScopeMeansUnrestricted() {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec(List.of("employee_no"), List.of(), List.of(), 100),
                null);
        assertThat(built.sql()).doesNotContain(":scopeIds");
        assertThat(built.sql()).doesNotContain("AND 1=0");
    }

    @Test
    void scopeRespectsSourceJoinAlias() {
        UUID emp = UUID.randomUUID();
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                new CustomReportSpec(
                        CustomReportSource.LEAVE_REQUESTS,
                        List.of("request_no"),
                        List.of(), List.of(), 50),
                Set.of(emp));
        assertThat(built.sql()).contains("AND lr.employee_id IN (:scopeIds)");
    }

    // ── validation: type/op compat ─────────────────────────────────────────

    @Test
    void rejectsLikeOnNonString() {
        CustomReportSpec bad = spec(List.of("employee_no"),
                List.of(new CustomReportSpec.Filter(
                        "hire_date", FilterOp.LIKE, List.of("2025"))),
                List.of(), 100);
        assertThatThrownBy(() -> CustomReportSqlBuilder.validate(bad))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not valid on DATE");
    }

    @Test
    void rejectsUnknownField() {
        CustomReportSpec bad = spec(List.of("does_not_exist"), List.of(), List.of(), 100);
        assertThatThrownBy(() -> CustomReportSqlBuilder.validate(bad))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown field: does_not_exist");
    }

    @Test
    void rejectsBetweenWithWrongValueCount() {
        CustomReportSpec bad = spec(List.of("employee_no"),
                List.of(new CustomReportSpec.Filter(
                        "hire_date", FilterOp.BETWEEN, List.of("2025-01-01"))),
                List.of(), 100);
        assertThatThrownBy(() -> CustomReportSqlBuilder.validate(bad))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BETWEEN requires exactly 2");
    }

    @Test
    void rejectsNullCheckWithValues() {
        CustomReportSpec bad = spec(List.of("employee_no"),
                List.of(new CustomReportSpec.Filter(
                        "manager_id", FilterOp.IS_NULL, List.of("foo"))),
                List.of(), 100);
        assertThatThrownBy(() -> CustomReportSqlBuilder.validate(bad))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expects no values");
    }

    @Test
    void rejectsEmptyFieldList() {
        CustomReportSpec bad = spec(List.of(), List.of(), List.of(), 100);
        assertThatThrownBy(() -> CustomReportSqlBuilder.validate(bad))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least one field");
    }

    @Test
    void rejectsMalformedDateLiteral() {
        CustomReportSpec bad = spec(List.of("employee_no"),
                List.of(new CustomReportSpec.Filter(
                        "hire_date", FilterOp.EQ, List.of("not-a-date"))),
                List.of(), 100);
        assertThatThrownBy(() -> CustomReportSqlBuilder.validate(bad))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid DATE value");
    }

    @Test
    void rejectsMalformedDecimalLiteral() {
        CustomReportSpec bad = new CustomReportSpec(
                CustomReportSource.PAYROLL_RESULTS,
                List.of("net_amount"),
                List.of(new CustomReportSpec.Filter(
                        "net_amount", FilterOp.GTE, List.of("not-a-number"))),
                List.of(), 100);
        assertThatThrownBy(() -> CustomReportSqlBuilder.validate(bad))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid DECIMAL value");
    }

    // ── value parsing ──────────────────────────────────────────────────────

    @Test
    void parsesDecimal() {
        assertThat(CustomReportSqlBuilder.parseValue(FieldType.DECIMAL, "1234.56"))
                .isEqualTo(new BigDecimal("1234.56"));
    }

    @Test
    void parsesUuid() {
        UUID u = UUID.randomUUID();
        assertThat(CustomReportSqlBuilder.parseValue(FieldType.UUID, u.toString()))
                .isEqualTo(u);
    }

    @Test
    void parsesBooleanLiteral() {
        assertThat(CustomReportSqlBuilder.parseValue(FieldType.BOOLEAN, "true"))
                .isEqualTo(Boolean.TRUE);
    }

    // ── injection attempt: an attacker tries to smuggle SQL through a key ─

    @Test
    void rejectsFieldKeyContainingSqlEvenIfShapeMatches() {
        // We don't accept "first_name; DROP TABLE" as a field key. The
        // whitelist check looks up by EXACT key, so this is a 'Unknown field'.
        CustomReportSpec bad = spec(
                List.of("first_name; DROP TABLE core_hr.employee --"),
                List.of(), List.of(), 100);
        assertThatThrownBy(() -> CustomReportSqlBuilder.validate(bad))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown field");
    }
}
