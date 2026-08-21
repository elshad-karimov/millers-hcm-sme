package az.millers.hcm.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.organization.repo.OrgUnitRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.security.scope.WorkflowSubjectResolver;
import az.millers.hcm.workflow.api.dto.ActionRequest;
import az.millers.hcm.workflow.domain.ActionType;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.domain.WorkflowStatus;
import az.millers.hcm.workflow.domain.WorkflowStep;
import az.millers.hcm.workflow.repo.ApprovalGroupMemberRepository;
import az.millers.hcm.workflow.repo.WorkflowActionRepository;
import az.millers.hcm.workflow.repo.WorkflowDefinitionRepository;
import az.millers.hcm.workflow.repo.WorkflowInstanceRepository;
import az.millers.hcm.workflow.repo.WorkflowParallelVoteRepository;
import az.millers.hcm.workflow.repo.WorkflowStepRepository;
import az.millers.hcm.workflow.repo.SubstituteApproverRepository;

/**
 * M330 — pins the timesheet chain this edition actually runs: the employee's
 * direct manager, then the person named on their record as timesheet approver.
 *
 * <p>The interesting cases are the ones the old pooled ROLE_HR_ADMIN step got
 * wrong. A nominated approver is a named individual: they may hold no manager
 * or HR role, and they often sit outside the subject's reporting line, so both
 * the role gate and the ABAC scope would have hidden their own queue from them.
 * Equally, nobody ELSE holding the role may act, and an employee with no
 * approver named must not have their month stranded on a step no one owns.
 */
class WorkflowNamedApproverTest {

    private static final UUID DEF = UUID.randomUUID();
    private static final UUID INSTANCE = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final UUID MANAGER = UUID.randomUUID();
    private static final UUID APPROVER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    private WorkflowInstanceRepository instances;
    private WorkflowStepRepository steps;
    private WorkflowActionRepository actions;
    private EmployeeRepository employees;

    private WorkflowService service;

    /** The caller, as both a username and an employee id. */
    private String username = "approver";
    /** false = the caller is outside the subject's scope (a stranger to ABAC). */
    private boolean subjectAccessible;
    /** The subject employee's nominated timesheet approver; null = none. */
    private UUID namedApprover = APPROVER;

    private WorkflowInstance instance;

    @BeforeEach
    void setUp() {
        instances = mock(WorkflowInstanceRepository.class);
        steps = mock(WorkflowStepRepository.class);
        actions = mock(WorkflowActionRepository.class);
        employees = mock(EmployeeRepository.class);

        subjectAccessible = false;
        namedApprover = APPROVER;

        instance = new WorkflowInstance();
        instance.setId(INSTANCE);
        instance.setDefinitionId(DEF);
        instance.setDefinitionCode("TIMESHEET_APPROVAL");
        instance.setSubjectModule("TIMESHEET");
        instance.setSubjectEntity("Timesheet");
        instance.setSubjectId(UUID.randomUUID().toString());
        instance.setTitle("Timesheet 2026-01");
        instance.setStatus(WorkflowStatus.PENDING);
        instance.setInitiatedBy("employee");
        instance.setCurrentStepIndex(2);
        instance.setCurrentStepRole("ROLE_DEPARTMENT_MANAGER");

        lenient().when(steps.findByDefinitionIdOrderByStepOrderAsc(DEF))
                .thenReturn(List.of(managerStep(), namedApproverStep()));
        lenient().when(steps.findByResolvesToTimesheetApproverTrue())
                .thenReturn(List.of(namedApproverStep()));
        lenient().when(instances.findById(INSTANCE)).thenReturn(Optional.of(instance));
        lenient().when(instances.save(any(WorkflowInstance.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(instances.findByStatusAndDefinitionIdInOrderByInitiatedAtDesc(
                any(), any())).thenReturn(List.of(instance));
        lenient().when(instances.findByStatusAndCurrentStepRoleInOrderByInitiatedAtDesc(any(), anyList()))
                .thenReturn(List.of());
        lenient().when(instances.findByStatusAndDelegatedToOrderByInitiatedAtDesc(any(), anyString()))
                .thenReturn(List.of());
        lenient().when(instances.findPendingParallelForRoles(any())).thenReturn(List.of());
        lenient().when(instances.findAll()).thenReturn(List.of());

        lenient().when(employees.findByUsername(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(byUsername(inv.getArgument(0))));
        lenient().when(employees.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(byId(inv.getArgument(0))));

        service = new WorkflowService(
                mock(WorkflowDefinitionRepository.class),
                steps,
                instances,
                actions,
                mock(WorkflowParallelVoteRepository.class),
                substitutes(),
                mock(ApprovalGroupMemberRepository.class),
                mock(ApplicationEventPublisher.class),
                new ObjectMapper(),
                currentRequest(),
                accessScope(),
                subjectResolver(),
                employees,
                mock(OrgUnitRepository.class));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Inbox ----------

    @Test
    void namedApproverSeesTheirOwnQueueWithoutRoleOrHierarchy() {
        username = "approver"; // holds only ROLE_EMPLOYEE, not in the subject's line

        List<WorkflowInstance> inbox = service.inboxFor(List.of("ROLE_EMPLOYEE"));

        assertThat(inbox).extracting(WorkflowInstance::getId).containsExactly(INSTANCE);
    }

    @Test
    void otherManagersDoNotSeeSomebodyElsesNamedStep() {
        username = "stranger";
        // The role query WOULD hand this manager the row — identity is what drops it.
        when(instances.findByStatusAndCurrentStepRoleInOrderByInitiatedAtDesc(any(), anyList()))
                .thenReturn(List.of(instance));

        List<WorkflowInstance> inbox = service.inboxFor(List.of("ROLE_DEPARTMENT_MANAGER"));

        assertThat(inbox).isEmpty();
    }

    // ---------- Visibility of the one instance ----------

    @Test
    void namedApproverCanOpenTheInstanceAddressedToThem() {
        username = "approver";

        assertThat(service.get(INSTANCE).getId()).isEqualTo(INSTANCE);
    }

    @Test
    void strangerStillGets404OnTheSameInstance() {
        username = "stranger";

        assertThatThrownBy(() -> service.get(INSTANCE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- Acting ----------

    @Test
    void namedApproverMayApproveWithoutAManagerOrHrRole() {
        username = "approver";
        authenticate("approver", "ROLE_EMPLOYEE");

        WorkflowInstance after = service.act(INSTANCE,
                new ActionRequest(ActionType.APPROVE, "checked", null, null));

        assertThat(after.getStatus()).isEqualTo(WorkflowStatus.APPROVED);
    }

    @Test
    void anHrAdminWhoIsNotTheNamedApproverIsRefused() {
        username = "stranger";
        subjectAccessible = true; // HR-wide visibility is not the question here
        authenticate("stranger", "ROLE_HR_ADMIN");

        assertThatThrownBy(() -> service.act(INSTANCE,
                new ActionRequest(ActionType.APPROVE, "rubber stamp", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("named timesheet approver");
    }

    // ---------- The skip rule ----------

    @Test
    void withNoApproverNamedTheManagerAloneCompletesTheChain() {
        namedApprover = null;
        username = "manager";
        subjectAccessible = true;
        instance.setCurrentStepIndex(1);
        instance.setCurrentStepRole("ROLE_DEPARTMENT_MANAGER");
        authenticate("manager", "ROLE_DEPARTMENT_MANAGER");

        WorkflowInstance after = service.act(INSTANCE,
                new ActionRequest(ActionType.APPROVE, "ok", null, null));

        assertThat(after.getStatus()).isEqualTo(WorkflowStatus.APPROVED);
    }

    @Test
    void aManagerWhoIsAlsoTheNamedApproverDoesNotSignTwice() {
        namedApprover = MANAGER;
        username = "manager";
        subjectAccessible = true;
        instance.setCurrentStepIndex(1);
        authenticate("manager", "ROLE_DEPARTMENT_MANAGER");

        WorkflowInstance after = service.act(INSTANCE,
                new ActionRequest(ActionType.APPROVE, "ok", null, null));

        assertThat(after.getStatus()).isEqualTo(WorkflowStatus.APPROVED);
    }

    @Test
    void aSeparateApproverStillGetsTheSecondStep() {
        username = "manager";
        subjectAccessible = true;
        instance.setCurrentStepIndex(1);
        authenticate("manager", "ROLE_DEPARTMENT_MANAGER");

        WorkflowInstance after = service.act(INSTANCE,
                new ActionRequest(ActionType.APPROVE, "ok", null, null));

        assertThat(after.getStatus()).isEqualTo(WorkflowStatus.PENDING);
        assertThat(after.getCurrentStepIndex()).isEqualTo(2);
    }

    // ---------- Fixtures ----------

    private WorkflowStep managerStep() {
        WorkflowStep s = new WorkflowStep();
        s.setId(UUID.nameUUIDFromBytes("step1".getBytes()));
        s.setDefinitionId(DEF);
        s.setStepOrder(1);
        s.setName("Manager review");
        s.setApproverRole("ROLE_DEPARTMENT_MANAGER");
        s.setResolvesToManager(true);
        return s;
    }

    private WorkflowStep namedApproverStep() {
        WorkflowStep s = new WorkflowStep();
        s.setId(UUID.nameUUIDFromBytes("step2".getBytes()));
        s.setDefinitionId(DEF);
        s.setStepOrder(2);
        s.setName("Timesheet approver");
        s.setApproverRole("ROLE_DEPARTMENT_MANAGER");
        s.setResolvesToTimesheetApprover(true);
        return s;
    }

    private Employee employee(UUID id, String user) {
        Employee e = new Employee();
        e.setId(id);
        e.setUsername(user);
        return e;
    }

    private Employee byId(UUID id) {
        if (EMPLOYEE.equals(id)) {
            Employee e = employee(EMPLOYEE, "employee");
            e.setManagerId(MANAGER);
            e.setTimesheetApproverId(namedApprover);
            return e;
        }
        if (MANAGER.equals(id)) return employee(MANAGER, "manager");
        if (APPROVER.equals(id)) return employee(APPROVER, "approver");
        if (STRANGER.equals(id)) return employee(STRANGER, "stranger");
        return null;
    }

    private Employee byUsername(String user) {
        return switch (user) {
            case "employee" -> byId(EMPLOYEE);
            case "manager" -> byId(MANAGER);
            case "approver" -> byId(APPROVER);
            case "stranger" -> byId(STRANGER);
            default -> null;
        };
    }

    private void authenticate(String user, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "n/a",
                        java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }

    private SubstituteApproverRepository substitutes() {
        SubstituteApproverRepository repo = mock(SubstituteApproverRepository.class);
        lenient().when(repo.findActiveBySubstituteRole(anyString(), any())).thenReturn(List.of());
        return repo;
    }

    /** Concrete collaborators are subclassed, not mocked — this JDK cannot mock classes. */
    private CurrentRequest currentRequest() {
        return new CurrentRequest() {
            @Override
            public String username() {
                return username;
            }

            @Override
            public String ipAddress() {
                return "127.0.0.1";
            }
        };
    }

    private AccessScopeService accessScope() {
        return new AccessScopeService(null, null, null) {
            @Override
            public boolean isUnrestricted() {
                return false;
            }

            @Override
            public boolean isWorkflowSubjectAccessible(String entity, String subjectIdStr) {
                return subjectAccessible;
            }
        };
    }

    private WorkflowSubjectResolver subjectResolver() {
        return new WorkflowSubjectResolver(null, null, null, null, null, null, null, null, null, null) {
            @Override
            public Optional<UUID> resolveEmployeeId(String entity, String subjectIdStr) {
                return Optional.of(EMPLOYEE);
            }

            @Override
            public boolean isEmployeeScoped(String entity) {
                return true;
            }
        };
    }
}
