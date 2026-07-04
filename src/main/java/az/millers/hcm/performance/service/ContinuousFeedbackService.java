package az.millers.hcm.performance.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.domain.ContinuousFeedback;
import az.millers.hcm.performance.domain.PerfCheckIn;
import az.millers.hcm.performance.repo.ContinuousFeedbackRepository;
import az.millers.hcm.performance.repo.PerfCheckInRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * HCM_12 M400 — continuous feedback (§22.1) + check-ins (§22.2).
 * MANAGER_PRIVATE notes are hidden from callers without an HR/manager role.
 */
@Service
public class ContinuousFeedbackService {

    private static final String TENANT = "default";
    private static final String MODULE = "PERFORMANCE";

    public record FeedbackNoteRequest(UUID employeeId, UUID authorEmployeeId, String kind,
                                      String visibility, String message, String tags) {}

    public record CheckInRequest(UUID employeeId, UUID managerId, LocalDate meetingDate,
                                 String kind, String discussionNotes, String actionItems,
                                 LocalDate followUpDate) {}

    private final ContinuousFeedbackRepository feedback;
    private final PerfCheckInRepository checkIns;
    private final EmployeeRepository employees;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ContinuousFeedbackService(ContinuousFeedbackRepository feedback,
                                     PerfCheckInRepository checkIns,
                                     EmployeeRepository employees,
                                     AccessScopeService accessScope,
                                     AuditService audit,
                                     CurrentRequest currentRequest) {
        this.feedback = feedback;
        this.checkIns = checkIns;
        this.employees = employees;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    private boolean hasManagerRole() {
        return currentRequest.hasRole("HR_ADMIN") || currentRequest.hasRole("HR_SPECIALIST")
                || currentRequest.hasRole("SYSTEM_ADMIN") || currentRequest.hasRole("DEPARTMENT_MANAGER");
    }

    // ── §22.1 continuous feedback ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContinuousFeedback> listFeedback(UUID employeeId) {
        requireAccessible(employeeId);
        List<ContinuousFeedback> rows =
                feedback.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TENANT, employeeId);
        // §22.1 — private manager notes stay manager/HR-only.
        if (!hasManagerRole()) {
            rows = rows.stream().filter(f -> !"MANAGER_PRIVATE".equals(f.getVisibility())).toList();
        }
        return rows;
    }

    @Transactional
    public ContinuousFeedback give(FeedbackNoteRequest req) {
        if (req.message() == null || req.message().isBlank()) {
            throw new BadRequestException("A feedback message is required");
        }
        if (req.kind() != null && !Set.of("PRAISE", "IMPROVEMENT", "NOTE", "REQUEST").contains(req.kind())) {
            throw new BadRequestException("Unknown feedback kind: " + req.kind());
        }
        if (req.visibility() != null
                && !Set.of("EMPLOYEE_VISIBLE", "MANAGER_PRIVATE").contains(req.visibility())) {
            throw new BadRequestException("Unknown visibility: " + req.visibility());
        }
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        requireAccessible(req.employeeId());
        if ("MANAGER_PRIVATE".equals(req.visibility()) && !hasManagerRole()) {
            throw new AccessDeniedException("Private manager notes require a manager/HR role");
        }
        ContinuousFeedback f = new ContinuousFeedback();
        f.setTenantId(TENANT);
        f.setEmployeeId(req.employeeId());
        f.setAuthorEmployeeId(req.authorEmployeeId());
        f.setKind(req.kind() == null ? "PRAISE" : req.kind());
        f.setVisibility(req.visibility() == null ? "EMPLOYEE_VISIBLE" : req.visibility());
        f.setMessage(req.message().trim());
        f.setTags(req.tags());
        f.setCreatedBy(currentRequest.username());
        ContinuousFeedback saved = feedback.save(f);
        audit.record(MODULE, "ContinuousFeedback", saved.getId().toString(), "GIVE",
                null, Map.of("kind", saved.getKind(), "visibility", saved.getVisibility()));
        return saved;
    }

    // ── §22.2 check-ins ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PerfCheckIn> listCheckIns(UUID employeeId) {
        requireAccessible(employeeId);
        return checkIns.findByTenantIdAndEmployeeIdOrderByMeetingDateDesc(TENANT, employeeId);
    }

    @Transactional
    public PerfCheckIn createCheckIn(CheckInRequest req) {
        if (req.meetingDate() == null) {
            throw new BadRequestException("A meeting date is required");
        }
        if (req.kind() != null && !Set.of("ONE_TO_ONE", "MONTHLY", "QUARTERLY").contains(req.kind())) {
            throw new BadRequestException("Unknown check-in kind: " + req.kind());
        }
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        requireAccessible(req.employeeId());
        PerfCheckIn c = new PerfCheckIn();
        c.setTenantId(TENANT);
        c.setEmployeeId(req.employeeId());
        c.setManagerId(req.managerId());
        c.setMeetingDate(req.meetingDate());
        c.setKind(req.kind() == null ? "ONE_TO_ONE" : req.kind());
        c.setDiscussionNotes(req.discussionNotes());
        c.setActionItems(req.actionItems());
        c.setFollowUpDate(req.followUpDate());
        c.setCreatedBy(currentRequest.username());
        PerfCheckIn saved = checkIns.save(c);
        audit.record(MODULE, "PerfCheckIn", saved.getId().toString(), "CREATE",
                null, Map.of("employeeId", req.employeeId().toString(),
                        "kind", saved.getKind(), "date", req.meetingDate().toString()));
        return saved;
    }

    /** §22.2 — employee acknowledgement (one-shot). */
    @Transactional
    public PerfCheckIn acknowledge(UUID checkInId) {
        PerfCheckIn c = checkIns.findById(checkInId)
                .orElseThrow(() -> new ResourceNotFoundException("Check-in not found: " + checkInId));
        requireAccessible(c.getEmployeeId());
        if (c.getAcknowledgedAt() != null) {
            throw new BadRequestException("This check-in is already acknowledged");
        }
        c.setAcknowledgedAt(OffsetDateTime.now());
        audit.record(MODULE, "PerfCheckIn", checkInId.toString(), "ACKNOWLEDGE", null, null);
        return checkIns.save(c);
    }

    private void requireAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new AccessDeniedException("Employee is outside your access scope");
        }
    }
}
