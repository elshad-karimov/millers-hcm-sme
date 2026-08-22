package az.millers.hcm.workflow.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.workflow.domain.WorkflowStep;
import az.millers.hcm.workflow.repo.SubstituteApproverRepository;

/**
 * Who may act on a step is answered twice: by role, and by identity. Where a
 * step names an individual the identity answer is the real one, and the role
 * answer contradicted it — a line manager holding only EMPLOYEE was routed a
 * timesheet by name and then refused permission to approve it, so the month
 * stopped with nobody able to see why.
 *
 * <p>These pin which steps ask which question. The identity check itself lives
 * in requireResolvedApprover and is unchanged: for a named step the caller
 * must still BE that person.
 */
class StepRoleGateTest {

    private static final List<String> PLAIN_EMPLOYEE = List.of("ROLE_EMPLOYEE");

    @Test
    @DisplayName("the line manager step no longer demands a manager role")
    void managerStepSkipsTheRoleCheck() {
        assertThatCode(() -> check(step("ROLE_DEPARTMENT_MANAGER", true, false, false)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the named timesheet approver step never did")
    void timesheetApproverStepSkipsTheRoleCheck() {
        assertThatCode(() -> check(step("ROLE_DEPARTMENT_MANAGER", false, true, false)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the HRBP step resolves to a person too")
    void hrbpStepSkipsTheRoleCheck() {
        assertThatCode(() -> check(step("ROLE_HR_ADMIN", false, false, true)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a step that resolves to a role pool still requires the role")
    void rolePoolStepStillChecksTheRole() {
        // HR verification names nobody — there the role IS the answer, so an
        // ordinary employee must not slip through.
        assertThatThrownBy(() -> check(step("ROLE_HR_ADMIN", false, false, false)))
                .isInstanceOf(BadRequestException.class);
    }

    private static WorkflowStep step(String role, boolean manager, boolean timesheetApprover,
                                     boolean hrbp) {
        WorkflowStep s = new WorkflowStep();
        s.setApproverRole(role);
        s.setResolvesToManager(manager);
        s.setResolvesToTimesheetApprover(timesheetApprover);
        s.setResolvesToHrbp(hrbp);
        return s;
    }

    /** Calls the private gate directly — it is the whole decision under test. */
    private void check(WorkflowStep step) {
        // Only the substitute-approver lookup is reached by this gate; nobody
        // is covering for anybody, so the role answer stands on its own.
        SubstituteApproverRepository substitutes = mock(SubstituteApproverRepository.class);
        when(substitutes.findActiveBySubstituteRole(any(), any())).thenReturn(List.of());
        WorkflowService service = new WorkflowService(
                null, null, null, null, null, substitutes,
                null, null, null, null, null, null, null, null);
        try {
            Method m = WorkflowService.class.getDeclaredMethod(
                    "requireStepRole", List.class, WorkflowStep.class, String.class);
            m.setAccessible(true);
            m.invoke(service, PLAIN_EMPLOYEE, step, "refused");
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
