package az.millers.hcm.selfservice.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.selfservice.domain.HrAgentQueue;
import az.millers.hcm.selfservice.domain.HrServiceRequest;
import az.millers.hcm.selfservice.domain.ServiceRequestCategory;
import az.millers.hcm.selfservice.domain.ServiceRequestPriority;
import az.millers.hcm.selfservice.domain.ServiceRequestStatus;
import az.millers.hcm.selfservice.repo.HrAgentQueueRepository;
import az.millers.hcm.selfservice.repo.HrServiceRequestRepository;

/**
 * M429 — HR helpdesk service request system.
 * Employees submit service requests (salary cert, policy question, grievance).
 * HR receives in a queue, assigns, resolves. SLA by priority.
 */
@Service
public class HrServiceRequestService {

    private static final String TENANT = "default";
    private static final String MODULE = "self-service";
    private static final String ENTITY = "HrServiceRequest";

    private final HrServiceRequestRepository repo;
    private final HrAgentQueueRepository queueRepo;
    private final EmployeeRepository employees;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final NotificationService notifications;

    public HrServiceRequestService(HrServiceRequestRepository repo,
                                    HrAgentQueueRepository queueRepo,
                                    EmployeeRepository employees,
                                    NamedParameterJdbcTemplate jdbc,
                                    AuditService audit,
                                    CurrentRequest currentRequest,
                                    NotificationService notifications) {
        this.repo = repo;
        this.queueRepo = queueRepo;
        this.employees = employees;
        this.jdbc = jdbc;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.notifications = notifications;
    }

    @Transactional
    public HrServiceRequest submit(ServiceRequestCategory category,
                                     ServiceRequestPriority priority,
                                     String subject,
                                     String description,
                                     UUID employeeId) {
        // GRIEVANCE visible to HR_ADMIN only (exclude from specialist queue)
        // — enforced at display time; submission allowed

        HrServiceRequest req = new HrServiceRequest();
        req.setTenantId(TENANT);
        req.setEmployeeId(employeeId);
        req.setCategory(category);
        req.setPriority(priority != null ? priority : ServiceRequestPriority.NORMAL);
        req.setSubject(subject);
        req.setDescription(description);
        req.setStatus(ServiceRequestStatus.OPEN);
        req.setCreatedBy(currentRequest.username());
        req.setUpdatedBy(currentRequest.username());

        // Auto-route to queue by category (M438)
        queueRepo.findByTenantIdAndRoutingCategory(TENANT, category).ifPresent(queue -> {
            req.setQueueId(queue.getId());
        });

        // Calculate SLA due (category SLA takes precedence over priority SLA per M438)
        req.setSlaDue(calculateSlaDue(category, priority != null ? priority : ServiceRequestPriority.NORMAL));

        HrServiceRequest saved = repo.save(req);

        // Generate request number
        String requestNo = String.format("SR-%05d", jdbc.queryForObject(
                "SELECT nextval('selfservice.hr_service_request_no_seq')",
                java.util.Map.of(), Long.class));
        saved.setRequestNo(requestNo);
        saved = repo.save(saved);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "SUBMITTED", null, null);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<HrServiceRequest> myRequests(UUID employeeId) {
        return repo.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TENANT, employeeId);
    }

    @Transactional(readOnly = true)
    public List<HrServiceRequest> queue(ServiceRequestCategory categoryFilter,
                                         ServiceRequestStatus statusFilter,
                                         boolean overdueOnly) {
        // HR queue: OPEN + IN_PROGRESS
        List<ServiceRequestStatus> statuses = statusFilter != null
                ? List.of(statusFilter)
                : List.of(ServiceRequestStatus.OPEN, ServiceRequestStatus.IN_PROGRESS);

        List<HrServiceRequest> results = repo.findByTenantIdAndStatusInOrderBySlaDueAsc(TENANT, statuses);

        // Grievances are confidential: HR_ADMIN only, regardless of requested filters
        if (!currentRequest.hasRole("HR_ADMIN")) {
            results = results.stream()
                    .filter(r -> r.getCategory() != ServiceRequestCategory.GRIEVANCE)
                    .toList();
        }

        // Category filter
        if (categoryFilter != null) {
            results = results.stream()
                    .filter(r -> r.getCategory() == categoryFilter)
                    .toList();
        }

        // Overdue filter
        if (overdueOnly) {
            OffsetDateTime now = OffsetDateTime.now();
            results = results.stream()
                    .filter(r -> r.getSlaDue() != null && r.getSlaDue().isBefore(now))
                    .toList();
        }

        return results;
    }

    @Transactional
    public HrServiceRequest assign(UUID id, String assignToUsername) {
        HrServiceRequest req = get(id);
        req.setAssignedToUsername(assignToUsername);
        if (req.getStatus() == ServiceRequestStatus.OPEN) {
            req.setStatus(ServiceRequestStatus.IN_PROGRESS);
        }
        req.setUpdatedBy(currentRequest.username());
        req.setUpdatedAt(OffsetDateTime.now());
        HrServiceRequest saved = repo.save(req);
        audit.record(MODULE, ENTITY, id.toString(), "ASSIGNED", null, assignToUsername);
        return saved;
    }

    @Transactional
    public HrServiceRequest start(UUID id) {
        HrServiceRequest req = get(id);
        if (req.getStatus() != ServiceRequestStatus.OPEN) {
            throw new BadRequestException("Only OPEN requests can be started");
        }
        req.setStatus(ServiceRequestStatus.IN_PROGRESS);
        req.setAssignedToUsername(currentRequest.username());
        req.setUpdatedBy(currentRequest.username());
        req.setUpdatedAt(OffsetDateTime.now());
        HrServiceRequest saved = repo.save(req);
        audit.record(MODULE, ENTITY, id.toString(), "STARTED", null, null);
        return saved;
    }

    @Transactional
    public HrServiceRequest resolve(UUID id, String resolutionNotes) {
        HrServiceRequest req = get(id);
        req.setStatus(ServiceRequestStatus.RESOLVED);
        req.setResolvedAt(OffsetDateTime.now());
        req.setResolutionNotes(resolutionNotes);
        req.setUpdatedBy(currentRequest.username());
        req.setUpdatedAt(OffsetDateTime.now());
        HrServiceRequest saved = repo.save(req);
        audit.record(MODULE, ENTITY, id.toString(), "RESOLVED", null, null);

        // Notify the employee
        Employee emp = employees.findById(saved.getEmployeeId()).orElse(null);
        if (emp != null && emp.getUsername() != null) {
            notifications.notifyAll(
                    emp.getUsername(),
                    "HR Request Resolved",
                    "Your request '" + saved.getSubject() + "' has been resolved.",
                    MODULE,
                    ENTITY,
                    saved.getId().toString()
            );
        }

        return saved;
    }

    @Transactional
    public HrServiceRequest close(UUID id) {
        HrServiceRequest req = get(id);
        if (req.getStatus() != ServiceRequestStatus.RESOLVED) {
            throw new BadRequestException("Only RESOLVED requests can be closed");
        }
        req.setStatus(ServiceRequestStatus.CLOSED);
        req.setUpdatedBy(currentRequest.username());
        req.setUpdatedAt(OffsetDateTime.now());
        HrServiceRequest saved = repo.save(req);
        audit.record(MODULE, ENTITY, id.toString(), "CLOSED", null, null);
        return saved;
    }

    @Transactional
    public HrServiceRequest reopen(UUID id) {
        HrServiceRequest req = get(id);
        if (req.getStatus() != ServiceRequestStatus.RESOLVED && req.getStatus() != ServiceRequestStatus.CLOSED) {
            throw new BadRequestException("Only RESOLVED or CLOSED requests can be reopened");
        }
        req.setStatus(ServiceRequestStatus.OPEN);
        req.setResolvedAt(null);
        req.setUpdatedBy(currentRequest.username());
        req.setUpdatedAt(OffsetDateTime.now());
        HrServiceRequest saved = repo.save(req);
        audit.record(MODULE, ENTITY, id.toString(), "REOPENED", null, null);
        return saved;
    }

    @Transactional(readOnly = true)
    public HrServiceRequest get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HR service request not found: " + id));
    }

    /**
     * Calculate SLA due date by adding business days (skip Sat/Sun; holidays optional).
     * M438: Category SLA takes precedence over priority SLA.
     * Category defaults: SALARY_CERT 2, EMPLOYMENT_LETTER 2, PAYROLL_INQUIRY 3,
     * POLICY_QUESTION 5, GRIEVANCE 1, OTHER 3.
     */
    private OffsetDateTime calculateSlaDue(ServiceRequestCategory category, ServiceRequestPriority priority) {
        int businessDays = getCategorySla(category);
        if (businessDays == 0) {
            // Fallback to priority SLA if no category SLA
            businessDays = priority.slaBusinessDays();
        }

        LocalDate date = LocalDate.now();
        int added = 0;
        while (added < businessDays) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private int getCategorySla(ServiceRequestCategory category) {
        return switch (category) {
            case SALARY_CERT -> 2;
            case EMPLOYMENT_LETTER -> 2;
            case PAYROLL_INQUIRY -> 3;
            case POLICY_QUESTION -> 5;
            case GRIEVANCE -> 1;
            case DOCUMENT_RENEWAL -> 5; // M441
            case OTHER -> 3;
        };
    }

    @Transactional
    public HrServiceRequest reassign(UUID id, UUID queueId) {
        HrServiceRequest req = get(id);
        req.setQueueId(queueId);
        req.setUpdatedBy(currentRequest.username());
        req.setUpdatedAt(OffsetDateTime.now());
        HrServiceRequest saved = repo.save(req);
        audit.record(MODULE, ENTITY, id.toString(), "REASSIGN_QUEUE", null, queueId.toString());
        return saved;
    }
}
