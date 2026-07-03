package az.millers.hcm.compbenefits.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.BenefitLifeEventDtos.EventRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitLifeEventDtos.EventResponse;
import az.millers.hcm.compbenefits.domain.BenefitLifeEvent;
import az.millers.hcm.compbenefits.repo.BenefitLifeEventRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * HCM_11 M380 — qualifying life events. Report → HR approve/reject. An APPROVED event
 * opens a special enrollment window ({@code eventDate + windowDays}).
 */
@Service
public class BenefitLifeEventService {

    private static final String TENANT = "default";
    private static final String MODULE = "COMP_BENEFITS";
    private static final String ENTITY = "BenefitLifeEvent";

    private final BenefitLifeEventRepository repo;
    private final EmployeeRepository employees;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public BenefitLifeEventService(BenefitLifeEventRepository repo,
                                   EmployeeRepository employees,
                                   AccessScopeService accessScope,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.repo = repo;
        this.employees = employees;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> list(String status) {
        List<BenefitLifeEvent> rows = (status == null || status.isBlank())
                ? repo.findByTenantIdOrderByEventDateDesc(TENANT)
                : repo.findByTenantIdAndStatusOrderByEventDateDesc(TENANT, status);
        return decorate(rows);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listForEmployee(UUID employeeId) {
        // Hierarchy scope (GLOBAL RULE 7): managers only see their reports; HR/admin unrestricted.
        if (!accessScope.isAccessible(employeeId)) {
            return List.of();
        }
        return decorate(repo.findByTenantIdAndEmployeeIdOrderByEventDateDesc(TENANT, employeeId));
    }

    /** Whether the employee currently has an open special-enrollment window (any approved event). */
    @Transactional(readOnly = true)
    public boolean hasOpenWindow(UUID employeeId) {
        LocalDate today = LocalDate.now();
        return repo.findByTenantIdAndEmployeeIdOrderByEventDateDesc(TENANT, employeeId).stream()
                .anyMatch(e -> e.isWindowOpenOn(today));
    }

    private List<EventResponse> decorate(List<BenefitLifeEvent> rows) {
        if (rows.isEmpty()) return List.of();
        LocalDate today = LocalDate.now();
        Map<UUID, String> names = new HashMap<>();
        return rows.stream().map(e -> EventResponse.from(e,
                names.computeIfAbsent(e.getEmployeeId(), this::employeeName), today)).toList();
    }

    private String employeeName(UUID empId) {
        return employees.findById(empId)
                .map(x -> (x.getFirstName() + " " + x.getLastName()).trim()).orElse(null);
    }

    @Transactional
    public EventResponse report(EventRequest req) {
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        BenefitLifeEvent e = new BenefitLifeEvent();
        e.setTenantId(TENANT);
        e.setEmployeeId(req.employeeId());
        e.setEventType(req.eventType());
        e.setEventDate(req.eventDate());
        e.setWindowDays(req.windowDays() == null ? 30 : req.windowDays());
        e.setNotes(req.notes());
        e.setStatus("PENDING");
        e.setReportedBy(currentRequest.username());
        BenefitLifeEvent saved = repo.save(e);
        EventResponse response = decorate(List.of(saved)).get(0);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "REPORT", null, response);
        return response;
    }

    @Transactional
    public EventResponse review(UUID id, boolean approve, String reviewNotes) {
        BenefitLifeEvent e = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Life event not found: " + id));
        if (!"PENDING".equals(e.getStatus())) {
            throw new BadRequestException("Only PENDING events can be reviewed (current: " + e.getStatus() + ")");
        }
        e.setStatus(approve ? "APPROVED" : "REJECTED");
        e.setReviewedBy(currentRequest.username());
        e.setReviewedAt(OffsetDateTime.now());
        e.setReviewNotes(reviewNotes);
        BenefitLifeEvent saved = repo.save(e);
        EventResponse response = decorate(List.of(saved)).get(0);
        audit.record(MODULE, ENTITY, id.toString(), approve ? "APPROVE" : "REJECT", null, response);
        return response;
    }
}
