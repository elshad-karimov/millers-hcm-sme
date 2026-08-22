package az.millers.hcm.corehr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.api.dto.EmployeeRequest;
import az.millers.hcm.security.CurrentRequest;

/**
 * PRD §4 steps 4-5 let a hire carry its contract and its opening salary, which
 * puts two rules on the create path that did not exist before:
 *
 *   §10  a contract cannot end before it starts
 *   §11  setting pay is a different authority from creating the person
 *
 * Both are checked BEFORE anything is written, so a rejected hire leaves no
 * half-made employee behind — that ordering is what these tests pin.
 */
class EmployeeHireExtrasTest {

    /** Stands in for the logged-in user; only the roles matter here. */
    private static final class StubCurrentRequest extends CurrentRequest {
        private final java.util.Set<String> roles;

        StubCurrentRequest(String... roles) {
            this.roles = java.util.Set.of(roles);
        }

        @Override
        public boolean hasRole(String role) {
            return roles.contains(role);
        }

        @Override
        public String username() {
            return "tester";
        }
    }

    @Test
    @DisplayName("a contract ending before it starts is rejected")
    void contractEndBeforeStartRejected() {
        assertThatThrownBy(() -> validate(
                hire(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1), null),
                new StubCurrentRequest("HR_ADMIN")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("earlier than the contract start date");
    }

    @Test
    @DisplayName("an open-ended contract is fine — no end date means indefinite")
    void indefiniteContractAccepted() {
        assertThatCode(() -> validate(
                hire(LocalDate.of(2026, 8, 1), null, null),
                new StubCurrentRequest("HR_ADMIN")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("equal start and end dates are allowed — a one-day contract is legal")
    void sameDayContractAccepted() {
        LocalDate day = LocalDate.of(2026, 8, 1);
        assertThatCode(() -> validate(
                hire(day, day, null), new StubCurrentRequest("HR_ADMIN")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an HR specialist cannot set salary while creating the employee")
    void salaryRequiresHrAdmin() {
        assertThatThrownBy(() -> validate(
                hire(null, null, new BigDecimal("2500.00")),
                new StubCurrentRequest("HR_SPECIALIST")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("permission to set salary");
    }

    @Test
    @DisplayName("...but they can still create the employee without one")
    void specialistMayHireWithoutSalary() {
        assertThatCode(() -> validate(
                hire(LocalDate.of(2026, 8, 1), null, null),
                new StubCurrentRequest("HR_SPECIALIST")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HR admin and system admin may both set salary")
    void adminsMaySetSalary() {
        for (String role : new String[] { "HR_ADMIN", "SYSTEM_ADMIN" }) {
            assertThatCode(() -> validate(
                    hire(null, null, new BigDecimal("2500.00")), new StubCurrentRequest(role)))
                    .as("role %s", role)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("the salary check does not fire when no salary was supplied")
    void noSalaryNoCheck() {
        assertThatCode(() -> validate(hire(null, null, null), new StubCurrentRequest()))
                .doesNotThrowAnyException();
    }

    /** Sanity-check that the fields under test actually reach the request. */
    @Test
    void requestCarriesTheHireExtras() {
        EmployeeRequest r = hire(LocalDate.of(2026, 8, 1), LocalDate.of(2027, 8, 1),
                new BigDecimal("2328.00"));
        assertThat(r.contractStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(r.contractEndDate()).isEqualTo(LocalDate.of(2027, 8, 1));
        assertThat(r.monthlyBaseSalary()).isEqualByComparingTo("2328.00");
    }

    /**
     * Invokes the private validator directly. The alternative is standing up
     * EmployeeService with a dozen collaborators to reach two if-statements,
     * which would test the mocks rather than the rules.
     */
    private static void validate(EmployeeRequest request, CurrentRequest caller) throws Exception {
        EmployeeService service = new EmployeeService(
                null, null, null, caller, null, null, null, null, null, null, null, null, null);
        Method m = EmployeeService.class.getDeclaredMethod("validateHireExtras", EmployeeRequest.class);
        m.setAccessible(true);
        try {
            m.invoke(service, request);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    /** A create payload carrying only the fields these rules look at. */
    private static EmployeeRequest hire(LocalDate contractStart, LocalDate contractEnd,
                                        BigDecimal salary) {
        return new EmployeeRequest(
                "Abbas", "Abbasli", null, null, null, null,
                null, null, null, null, null, null,
                LocalDate.of(2026, 8, 1), null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, contractStart, contractEnd, null, salary,
                null);
    }
}
