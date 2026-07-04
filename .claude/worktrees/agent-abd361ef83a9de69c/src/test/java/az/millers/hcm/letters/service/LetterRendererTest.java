package az.millers.hcm.letters.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import az.millers.hcm.corehr.domain.Employee;

/**
 * Unit tests for the M77 placeholder substitution. Mirrors the EffectiveDated
 * test pattern — POJO + assertions, no Spring context, no mocks.
 */
class LetterRendererTest {

    private final LetterRenderer renderer = new LetterRenderer();

    @Test
    void substitutesEmployeePlaceholders() {
        Employee e = new Employee();
        e.setFirstName("Aytan");
        e.setLastName("Mammadova");
        e.setEmployeeNo("EMP-00042");
        e.setPositionTitle("Senior accountant");
        e.setDepartmentName("Finance");
        e.setHireDate(LocalDate.of(2022, 3, 15));

        String rendered = renderer.render(
                "Hello {{employee.firstName}} {{employee.lastName}} ({{employee.employeeNo}}) — "
                        + "{{employee.positionTitle}} in {{employee.departmentName}}, hired {{employee.hireDate}}.",
                e,
                Map.of());

        assertThat(rendered).isEqualTo(
                "Hello Aytan Mammadova (EMP-00042) — Senior accountant in Finance, hired 2022-03-15.");
    }

    @Test
    void substitutesCustomFields() {
        Employee e = new Employee();
        e.setFirstName("A");
        e.setLastName("B");

        String rendered = renderer.render(
                "Purpose: {{custom.purpose}} | Activity: {{custom.activity}}",
                e,
                Map.of("purpose", "Visa application", "activity", "Remote work in Georgia"));

        assertThat(rendered).contains("Purpose: Visa application");
        assertThat(rendered).contains("Activity: Remote work in Georgia");
    }

    @Test
    void marksMissingPlaceholdersInline() {
        String rendered = renderer.render(
                "{{employee.firstName}} {{custom.missing}}",
                null,
                Map.of());

        // employee.firstName resolves but employee=null path leaves it absent
        assertThat(rendered).contains("[missing: employee.firstName]");
        assertThat(rendered).contains("[missing: custom.missing]");
    }

    @Test
    void todayUsesIsoDate() {
        String rendered = renderer.render("Issued {{today}}", null, Map.of());
        assertThat(rendered)
                .startsWith("Issued ")
                .endsWith(LocalDate.now().toString());
    }

    @Test
    void ignoresMalformedMarkers() {
        // No double-braces → no substitution attempt.
        String rendered = renderer.render(
                "{employee.firstName} { not.a.marker }",
                new Employee(), Map.of());
        assertThat(rendered).isEqualTo("{employee.firstName} { not.a.marker }");
    }
}
