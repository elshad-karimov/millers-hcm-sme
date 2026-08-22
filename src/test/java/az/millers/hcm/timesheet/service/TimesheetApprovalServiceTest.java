package az.millers.hcm.timesheet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.junit.jupiter.api.Test;

import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.ApproveRequest;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.RejectRequest;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.ReturnRequest;
import az.millers.hcm.timesheet.domain.DayApprovalState;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetDay;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.domain.WorkType;
import az.millers.hcm.timesheet.repo.DayQuantityRepository;
import az.millers.hcm.timesheet.repo.TimeCategoryRepository;
import az.millers.hcm.timesheet.repo.TimesheetDayRepository;
import az.millers.hcm.timesheet.repo.TimesheetMonthTotalRepository;
import az.millers.hcm.timesheet.repo.TimesheetRepository;

/**
 * Pins the approval controls.
 *
 * <p>These are the checks that stand between "an employee typed some hours" and
 * "payroll paid for them", so each one is asserted rather than assumed: nobody
 * approves themselves, a month with a returned day cannot be approved, a
 * partial return leaves the other days alone, and a locked period refuses
 * decisions.
 */
class TimesheetApprovalServiceTest {

    private static final UUID TIMESHEET = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final UUID MANAGER = UUID.randomUUID();
    private static final LocalDate D1 = LocalDate.of(2026, 1, 5);
    private static final LocalDate D2 = LocalDate.of(2026, 1, 6);
    private static final LocalDate D3 = LocalDate.of(2026, 1, 7);

    private TimesheetRepository timesheets;
    private TimesheetDayRepository days;
    private EmployeeRepository employees;
    private TimesheetApprovalService service;

    private boolean periodLocked;
    /** null = unrestricted, otherwise the ids the caller may see. */
    private java.util.Set<UUID> scope;
    private java.util.Set<UUID> inaccessible;
    private List<TimesheetDay> storedDays;
    private Timesheet timesheet;

    /** Concrete collaborators are stubbed by subclass — this JDK cannot mock classes. */
    private TimesheetPeriodService periodService() {
        return new TimesheetPeriodService(null, null, null, null, null, null) {
            @Override
            public boolean isLocked(int year, int month) {
                return periodLocked;
            }
        };
    }

    private AuditService noopAudit() {
        return new AuditService(null, null, null) {
            @Override
            public void record(String module, String entityName, String entityId,
                               String action, Object before, Object after) {
                /* assertions here are about state transitions, not the audit sink */
            }
        };
    }

    /**
     * Stubbed by subclass for the same reason as the others: AccessScopeService
     * is a concrete class and this JDK cannot instrument classes for mocking.
     */
    private AccessScopeService scopeService() {
        return new AccessScopeService(null, null, null) {
            @Override
            public boolean isAccessible(UUID targetEmployeeId) {
                return !inaccessible.contains(targetEmployeeId);
            }

            @Override
            public java.util.Set<UUID> scopeOrNullForCurrentUser() {
                return scope;
            }
        };
    }

    private TimesheetCorrectionService noCorrections() {
        return new TimesheetCorrectionService(null, null, null, null, null, null, null, null) {
            @Override
            public java.util.List<az.millers.hcm.timesheet.api.dto.ApprovalDtos.CorrectionView>
                    forTimesheet(UUID timesheetId) {
                return List.of();
            }
        };
    }

    private CurrentRequest asManager() {
        return new CurrentRequest() {
            @Override
            public String username() {
                return "manager1";
            }
        };
    }

    private static final java.util.UUID INSTANCE = java.util.UUID.randomUUID();
    private StubWorkflow workflow;

    /**
     * Stands in for the approval engine.
     *
     * <p>The route itself (manager, then HR) is the TIMESHEET_APPROVAL
     * definition's business, not this service's — so the double records that a
     * decision was handed over and controls only whether the instance is still
     * running or has finished.
     */
    private static final class StubWorkflow
            extends az.millers.hcm.workflow.service.WorkflowService {
        private final az.millers.hcm.workflow.domain.WorkflowInstance instance =
                new az.millers.hcm.workflow.domain.WorkflowInstance();
        private int actions;
        /** Emulates what the completion event does while act() is running. */
        private Runnable onAct = () -> { };

        StubWorkflow() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            instance.setId(INSTANCE);
            instance.setStatus(az.millers.hcm.workflow.domain.WorkflowStatus.PENDING);
            instance.setCurrentStepIndex(1);
        }

        void completesWith(Runnable listenerEffect) {
            this.onAct = () -> {
                instance.setStatus(az.millers.hcm.workflow.domain.WorkflowStatus.APPROVED);
                listenerEffect.run();
            };
        }

        @Override
        public az.millers.hcm.workflow.domain.WorkflowInstance act(
                java.util.UUID id, az.millers.hcm.workflow.api.dto.ActionRequest req) {
            actions++;
            onAct.run();
            return instance;
        }

        @Override
        public az.millers.hcm.workflow.domain.WorkflowInstance get(java.util.UUID id) {
            return instance;
        }
    }

    @BeforeEach
    void setUp() {
        periodLocked = false;
        scope = null;
        inaccessible = new java.util.HashSet<>();

        timesheet = new Timesheet();
        timesheet.setId(TIMESHEET);
        timesheet.setEmployeeId(EMPLOYEE);
        timesheet.setPeriodYear(2026);
        timesheet.setPeriodMonth(1);
        timesheet.setStatus(TimesheetStatus.SUBMITTED);
        timesheet.setTotalWorkedHours(new BigDecimal("24.00"));
        timesheet.setTotalOvertimeHours(BigDecimal.ZERO);
        // Submitted months carry the approval workflow that routes them.
        timesheet.setWorkflowInstanceId(INSTANCE);
        workflow = new StubWorkflow();

        storedDays = new ArrayList<>(List.of(day(D1), day(D2), day(D3)));

        timesheets = mock(TimesheetRepository.class);
        days = mock(TimesheetDayRepository.class);
        employees = mock(EmployeeRepository.class);
        DayQuantityRepository quantities = mock(DayQuantityRepository.class);
        TimesheetMonthTotalRepository totals = mock(TimesheetMonthTotalRepository.class);
        TimeCategoryRepository categories = mock(TimeCategoryRepository.class);
        DailySummaryRepository summaries = mock(DailySummaryRepository.class);
        AccessScopeService accessScope = scopeService();
        TimesheetCorrectionService corrections = noCorrections();

        lenient().when(timesheets.findById(TIMESHEET)).thenReturn(Optional.of(timesheet));
        lenient().when(timesheets.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(days.findByTimesheetIdOrderByWorkDateAsc(TIMESHEET)).thenReturn(storedDays);
        lenient().when(days.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(employees.findById(any())).thenReturn(Optional.empty());
        lenient().when(employees.findAllById(any())).thenReturn(List.of());
        lenient().when(quantities.findByTimesheetDayIdIn(any())).thenReturn(List.of());
        lenient().when(totals.findByTimesheetIdOrderByCategoryCodeAsc(any())).thenReturn(List.of());
        lenient().when(categories.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of());
        lenient().when(summaries.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.empty());
        // Default: the caller is a manager who is not the subject.
        lenient().when(employees.findByUsername("manager1")).thenReturn(Optional.of(managerEmployee()));

        service = new TimesheetApprovalService(workflow, timesheets, days, quantities, totals,
                categories, employees, summaries,
                // Projects only resolve a display name on the review screen; an
                // empty master is fine for these tests.
                mock(az.millers.hcm.timesheet.repo.TimesheetProjectRepository.class),
                periodService(), corrections, accessScope,
                noopAudit(), asManager(), directTransactionManager());
    }

    /**
     * A transaction manager that does nothing.
     *
     * <p>These are unit tests with mocked repositories — there is no database
     * and nothing to commit. The service wraps each bulk-approve item in a
     * TransactionTemplate so one failure cannot poison the rest; with this
     * manager the template simply runs the callback, which is what the tests
     * want to exercise.
     */
    private static PlatformTransactionManager directTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) { /* nothing to commit */ }

            @Override
            public void rollback(TransactionStatus status) { /* nothing to roll back */ }
        };
    }

    private TimesheetDay day(LocalDate date) {
        TimesheetDay d = new TimesheetDay();
        d.setId(UUID.randomUUID());
        d.setTimesheetId(TIMESHEET);
        d.setWorkDate(date);
        d.setWorkType(WorkType.OFFSHORE);
        d.setWorkedHours(new BigDecimal("8.00"));
        d.setApprovalState(DayApprovalState.PENDING);
        return d;
    }

    private Employee managerEmployee() {
        Employee e = new Employee();
        e.setId(MANAGER);
        e.setFirstName("Man");
        e.setLastName("Ager");
        return e;
    }

    private TimesheetDay stored(LocalDate date) {
        return storedDays.stream().filter(d -> d.getWorkDate().equals(date)).findFirst().orElseThrow();
    }

    // ---- self-approval ----

    @Test
    void aManagerCannotApproveTheirOwnTimesheet() {
        timesheet.setEmployeeId(MANAGER);   // the subject IS the caller

        assertThatThrownBy(() -> service.approve(TIMESHEET, new ApproveRequest("looks fine")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot approve your own");
    }

    @Test
    void aManagersOwnMonthIsNotEvenListedInTheirQueue() {
        Timesheet own = new Timesheet();
        own.setId(UUID.randomUUID());
        own.setEmployeeId(MANAGER);
        own.setPeriodYear(2026);
        own.setPeriodMonth(1);
        own.setStatus(TimesheetStatus.SUBMITTED);
        own.setTotalWorkedHours(BigDecimal.ZERO);
        own.setTotalOvertimeHours(BigDecimal.ZERO);

        scope = null;   // unrestricted caller
        when(timesheets.findByPeriodYearAndPeriodMonthAndStatusIn(anyInt(), anyInt(), any()))
                .thenReturn(List.of(timesheet, own));
        when(days.findByTimesheetIdOrderByWorkDateAsc(own.getId())).thenReturn(List.of());

        assertThat(service.queue(2026, 1, null))
                .extracting("employeeId")
                .containsExactly(EMPLOYEE);
    }

    // ---- hierarchy ----

    @Test
    void aTimesheetOutsideTheHierarchyIsNotFoundRatherThanForbidden() {
        inaccessible.add(EMPLOYEE);

        assertThatThrownBy(() -> service.review(TIMESHEET))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anEmptyScopeYieldsAnEmptyQueueRatherThanEverything() {
        scope = java.util.Set.of();

        assertThat(service.queue(2026, 1, null)).isEmpty();
    }

    // ---- period lock ----

    @Test
    void aLockedPeriodRefusesApproval() {
        periodLocked = true;

        assertThatThrownBy(() -> service.approve(TIMESHEET, new ApproveRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("locked");
    }

    // ---- return ----

    @Test
    void returningNamedDaysLeavesTheRestApproved() {
        service.returnForCorrection(TIMESHEET,
                new ReturnRequest(List.of(D2), "13 Jan overtime should be 2h not 4h"));

        assertThat(stored(D2).getApprovalState()).isEqualTo(DayApprovalState.RETURNED);
        assertThat(stored(D2).getReturnReason()).contains("overtime");
        assertThat(stored(D1).getApprovalState()).isEqualTo(DayApprovalState.APPROVED);
        assertThat(stored(D3).getApprovalState()).isEqualTo(DayApprovalState.APPROVED);
        assertThat(timesheet.getStatus()).isEqualTo(TimesheetStatus.RETURNED);
    }

    @Test
    void returningRequiresAReason() {
        assertThatThrownBy(() -> service.returnForCorrection(TIMESHEET,
                new ReturnRequest(List.of(D2), "  ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    void returningRequiresAtLeastOneDay() {
        assertThatThrownBy(() -> service.returnForCorrection(TIMESHEET,
                new ReturnRequest(List.of(), "fix it")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least one day");
    }

    @Test
    void returningADayThatIsNotInTheMonthIsRefused() {
        assertThatThrownBy(() -> service.returnForCorrection(TIMESHEET,
                new ReturnRequest(List.of(LocalDate.of(2026, 2, 2)), "fix it")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No day");
    }

    // ---- approve ----

    @Test
    void aMonthWithAReturnedDayCannotBeApproved() {
        stored(D2).setApprovalState(DayApprovalState.RETURNED);
        timesheet.setStatus(TimesheetStatus.RETURNED);

        assertThatThrownBy(() -> service.approve(TIMESHEET, new ApproveRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("still returned");
    }

    @Test
    void theManagersApprovalHandsTheMonthToTheNextStepRatherThanFinishingIt() {
        service.approve(TIMESHEET, new ApproveRequest("fine"));

        // Stage 1 of the configured chain: the engine is still running, so the
        // month is with HR — not approved, and crucially the days are NOT yet
        // marked approved. A day flagged APPROVED before the final sign-off
        // would tell payroll the month is settled when it is not.
        assertThat(workflow.actions).isEqualTo(1);
        assertThat(timesheet.getStatus()).isEqualTo(TimesheetStatus.PENDING_HR);
        assertThat(timesheet.getManagerApprovedBy()).isEqualTo("manager1");
        assertThat(timesheet.getApprovedBy()).isNull();
        assertThat(storedDays).allSatisfy(d ->
                assertThat(d.getApprovalState()).isEqualTo(DayApprovalState.PENDING));
    }

    @Test
    void whenTheEngineFinishesTheServiceDoesNotOverrideTheOutcome() {
        // Final step: the engine completes inside act(), and its event drives
        // the month to APPROVED. This service must not then stamp PENDING_HR
        // back over the finished outcome.
        workflow.completesWith(() -> timesheet.setStatus(TimesheetStatus.APPROVED));

        service.approve(TIMESHEET, new ApproveRequest("final"));

        assertThat(timesheet.getStatus()).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    void aMonthSubmittedBeforeWorkflowRoutingCannotBeApprovedBlindly() {
        timesheet.setWorkflowInstanceId(null);

        assertThatThrownBy(() -> service.approve(TIMESHEET, new ApproveRequest("fine")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("recall");
    }

    @Test
    void anAlreadyApprovedMonthHasNothingLeftToDecide() {
        timesheet.setStatus(TimesheetStatus.APPROVED);

        assertThatThrownBy(() -> service.approve(TIMESHEET, new ApproveRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nothing to decide");
    }

    // ---- reject ----

    @Test
    void rejectingClearsEveryDayVerdictAndReturnsTheMonthToDraft() {
        stored(D1).setApprovalState(DayApprovalState.APPROVED);

        service.reject(TIMESHEET, new RejectRequest("Wrong period submitted"));

        assertThat(storedDays).allSatisfy(d ->
                assertThat(d.getApprovalState()).isEqualTo(DayApprovalState.PENDING));
        assertThat(timesheet.getStatus()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(timesheet.getRejectionReason()).isEqualTo("Wrong period submitted");
        assertThat(timesheet.getSubmittedAt()).isNull();
    }

    @Test
    void rejectingRequiresAReason() {
        assertThatThrownBy(() -> service.reject(TIMESHEET, new RejectRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reason is required");
    }

    // ---- bulk ----

    @Test
    void bulkApproveSkipsWhatItCannotApproveAndSaysWhy() {
        UUID other = UUID.randomUUID();
        when(timesheets.findById(other)).thenReturn(Optional.empty());

        var result = service.bulkApprove(List.of(TIMESHEET, other), "monthly run");

        assertThat(result.approved()).containsExactly(TIMESHEET);
        assertThat(result.skipped()).containsKey(other.toString());
    }

    @Test
    void bulkApproveSkipsAMonthWithReturnedDays() {
        stored(D2).setApprovalState(DayApprovalState.RETURNED);

        var result = service.bulkApprove(List.of(TIMESHEET), null);

        assertThat(result.approved()).isEmpty();
        assertThat(result.skipped().get(TIMESHEET.toString()))
                .contains("returned for correction");
    }
}
