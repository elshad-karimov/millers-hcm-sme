package az.millers.hcm.config.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import az.millers.hcm.common.tenant.TenantContext;
import az.millers.hcm.corehr.repo.EmployeeRepository;

/** Plan headcount ceiling — wired and covered while every tier is unlimited. */
class PlanLimitGateTest {

    private EmployeeRepository employees;
    private StubPlans plans;
    private PlanLimitGate gate;

    @BeforeEach
    void setUp() {
        employees = mock(EmployeeRepository.class);
        plans = new StubPlans();
        gate = new PlanLimitGate(plans, employees);
        TenantContext.set("acme");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("an unlimited plan never even counts — no query on the hot hire path")
    void unlimitedPlanShortCircuits() {
        plans.uniform = Plan.LITE;

        assertThatCode(() -> gate.assertCanAddEmployee()).doesNotThrowAnyException();

        verify(employees, never()).countActiveEmployees();
    }

    @Test
    @DisplayName("every shipped tier is unlimited today, so no hire is blocked")
    void noTierBlocksToday() {
        for (Plan plan : Plan.values()) {
            plans.uniform = plan;
            assertThatCode(() -> gate.assertCanAddEmployee())
                    .as("%s must not block a hire while unlimited", plan)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("below the ceiling passes")
    void belowLimitPasses() {
        when(employees.countActiveEmployees()).thenReturn(49L);

        assertThatCode(() -> gate.assertCanAddEmployee(Plan.LITE, new PlanLimits(50)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("at the ceiling blocks with an upgrade message, not a validation error")
    void atLimitBlocks() {
        when(employees.countActiveEmployees()).thenReturn(50L);

        assertThatThrownBy(() -> gate.assertCanAddEmployee(Plan.LITE, new PlanLimits(50)))
                .isInstanceOf(PlanLimitGate.PlanLimitExceededException.class)
                .hasMessageContaining("LITE")
                .hasMessageContaining("up to 50 active employees")
                .hasMessageContaining("Upgrade");
    }

    @Test
    @DisplayName("the exception carries the numbers the UI needs to explain itself")
    void exceptionCarriesContext() {
        when(employees.countActiveEmployees()).thenReturn(51L);

        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                PlanLimitGate.PlanLimitExceededException.class,
                () -> gate.assertCanAddEmployee(Plan.LITE, new PlanLimits(50)));

        assertThat(thrown.plan()).isEqualTo(Plan.LITE);
        assertThat(thrown.limit()).isEqualTo(50);
        assertThat(thrown.current()).isEqualTo(51L);
    }
}
