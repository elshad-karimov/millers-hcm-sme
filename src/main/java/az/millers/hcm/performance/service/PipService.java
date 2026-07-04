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
import az.millers.hcm.performance.domain.PerformancePip;
import az.millers.hcm.performance.domain.PipCheckpoint;
import az.millers.hcm.performance.repo.PerformancePipRepository;
import az.millers.hcm.performance.repo.PipCheckpointRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * HCM_12 M398 — performance improvement plans (PRD §20). Manual creation
 * (gap-check decision), one live PIP per employee, employee acknowledgement,
 * periodic checkpoints, extension, and a controlled outcome — history is never
 * deleted, only closed.
 */
@Service
public class PipService {

    private static final String TENANT = "default";
    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "PerformancePip";
    private static final List<String> LIVE = List.of(
            "DRAFT", "ACTIVE", "ACKNOWLEDGED", "IN_PROGRESS", "EXTENDED");
    private static final Set<String> OUTCOMES = Set.of(
            "IMPROVED", "EXTENDED", "ROLE_CHANGE", "TRAINING_REQUIRED",
            "DISCIPLINARY", "TERMINATION_RECOMMENDED");

    public record PipRequest(UUID employeeId, UUID reviewId, UUID managerOwnerId, String hrOwner,
                             String reason, String issueDescription, String objectives,
                             String successCriteria, String supportActions, String consequences,
                             LocalDate startDate, LocalDate endDate) {}

    public record CheckpointRequest(LocalDate checkpointDate, Integer progressRating,
                                    String managerComments, String employeeComments) {}

    private final PerformancePipRepository pips;
    private final PipCheckpointRepository checkpoints;
    private final EmployeeRepository employees;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PipService(PerformancePipRepository pips,
                      PipCheckpointRepository checkpoints,
                      EmployeeRepository employees,
                      AccessScopeService accessScope,
                      AuditService audit,
                      CurrentRequest currentRequest) {
        this.pips = pips;
        this.checkpoints = checkpoints;
        this.employees = employees;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<PerformancePip> list(UUID employeeId, String status) {
        List<PerformancePip> rows;
        if (employeeId != null) {
            rows = pips.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TENANT, employeeId);
        } else if (status != null) {
            rows = pips.findByTenantIdAndStatusOrderByCreatedAtDesc(TENANT, status);
        } else {
            rows = pips.findByTenantIdOrderByCreatedAtDesc(TENANT);
        }
        // GLOBAL RULE 7/8 — PIPs are sensitive; scope-filter hard.
        return rows.stream().filter(p -> accessScope.isAccessible(p.getEmployeeId())).toList();
    }

    @Transactional(readOnly = true)
    public PerformancePip get(UUID id) {
        return requireAccessible(id);
    }

    @Transactional
    public PerformancePip create(PipRequest req) {
        validate(req);
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        requireEmployeeAccessible(req.employeeId());
        if (pips.existsByEmployeeIdAndStatusIn(req.employeeId(), LIVE)) {
            throw new BadRequestException("The employee already has a live PIP");
        }
        PerformancePip p = new PerformancePip();
        p.setTenantId(TENANT);
        apply(p, req);
        p.setCreatedBy(currentRequest.username());
        PerformancePip saved = pips.save(p);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE",
                null, Map.of("employeeId", req.employeeId().toString(), "reason", req.reason()));
        return saved;
    }

    @Transactional
    public PerformancePip update(UUID id, PipRequest req) {
        PerformancePip p = requireAccessible(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new BadRequestException("Only DRAFT PIPs can be structurally edited");
        }
        validate(req);
        apply(p, req);
        PerformancePip saved = pips.save(p);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", null, req.reason());
        return saved;
    }

    @Transactional
    public PerformancePip activate(UUID id) {
        PerformancePip p = requireAccessible(id);
        assertStatus(p, "DRAFT");
        p.setStatus("ACTIVE");
        audit.record(MODULE, ENTITY, id.toString(), "ACTIVATE", "DRAFT", "ACTIVE");
        return pips.save(p);
    }

    /** §20.1 — employee acknowledgement (one-shot). */
    @Transactional
    public PerformancePip acknowledge(UUID id, String comments) {
        PerformancePip p = requireAccessible(id);
        if (!"ACTIVE".equals(p.getStatus())) {
            throw new BadRequestException("Only an ACTIVE PIP can be acknowledged");
        }
        p.setStatus("ACKNOWLEDGED");
        p.setAcknowledgedAt(OffsetDateTime.now());
        p.setAcknowledgedComments(comments);
        audit.record(MODULE, ENTITY, id.toString(), "ACKNOWLEDGE", "ACTIVE",
                Map.of("comments", String.valueOf(comments)));
        return pips.save(p);
    }

    /** §20.3 — periodic check-in; moves the PIP into IN_PROGRESS. */
    @Transactional
    public PipCheckpoint addCheckpoint(UUID pipId, CheckpointRequest req) {
        PerformancePip p = requireAccessible(pipId);
        if (!LIVE.contains(p.getStatus()) || "DRAFT".equals(p.getStatus())) {
            throw new BadRequestException("Checkpoints require an active PIP (was " + p.getStatus() + ")");
        }
        if (req.progressRating() != null
                && (req.progressRating() < 1 || req.progressRating() > 5)) {
            throw new BadRequestException("progressRating must be 1..5");
        }
        PipCheckpoint c = new PipCheckpoint();
        c.setTenantId(p.getTenantId());
        c.setPipId(pipId);
        c.setCheckpointDate(req.checkpointDate() == null ? LocalDate.now() : req.checkpointDate());
        c.setProgressRating(req.progressRating());
        c.setManagerComments(req.managerComments());
        c.setEmployeeComments(req.employeeComments());
        c.setRecordedBy(currentRequest.username());
        PipCheckpoint saved = checkpoints.save(c);
        if ("ACTIVE".equals(p.getStatus()) || "ACKNOWLEDGED".equals(p.getStatus())) {
            p.setStatus("IN_PROGRESS");
            pips.save(p);
        }
        audit.record(MODULE, "PipCheckpoint", saved.getId().toString(), "CHECKPOINT",
                null, Map.of("pipId", pipId.toString(),
                        "rating", String.valueOf(req.progressRating())));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PipCheckpoint> checkpoints(UUID pipId) {
        requireAccessible(pipId);
        return checkpoints.findByPipIdOrderByCheckpointDateDesc(pipId);
    }

    /** §20.4 — extend the PIP window. */
    @Transactional
    public PerformancePip extend(UUID id, LocalDate newEndDate) {
        PerformancePip p = requireAccessible(id);
        if (!LIVE.contains(p.getStatus()) || "DRAFT".equals(p.getStatus())) {
            throw new BadRequestException("Only an active PIP can be extended");
        }
        if (newEndDate == null || !newEndDate.isAfter(p.getEndDate())) {
            throw new BadRequestException("New end date must be after the current end date");
        }
        String before = p.getEndDate().toString();
        p.setEndDate(newEndDate);
        p.setStatus("EXTENDED");
        audit.record(MODULE, ENTITY, id.toString(), "EXTEND", before, newEndDate.toString());
        return pips.save(p);
    }

    /** §20.4 — close with an outcome; IMPROVED → COMPLETED_SUCCESS, else FAILED. */
    @Transactional
    public PerformancePip close(UUID id, String outcome, String notes) {
        PerformancePip p = requireAccessible(id);
        if (!LIVE.contains(p.getStatus()) || "DRAFT".equals(p.getStatus())) {
            throw new BadRequestException("Only an active PIP can be closed with an outcome");
        }
        if (outcome == null || !OUTCOMES.contains(outcome)) {
            throw new BadRequestException("Outcome must be one of " + OUTCOMES);
        }
        p.setOutcome(outcome);
        p.setOutcomeNotes(notes);
        p.setStatus("IMPROVED".equals(outcome) ? "COMPLETED_SUCCESS" : "FAILED");
        PerformancePip saved = pips.save(p);
        audit.record(MODULE, ENTITY, id.toString(), "CLOSE", null,
                Map.of("outcome", outcome, "notes", String.valueOf(notes)));
        return saved;
    }

    @Transactional
    public PerformancePip cancel(UUID id) {
        PerformancePip p = requireAccessible(id);
        if (!LIVE.contains(p.getStatus())) {
            throw new BadRequestException("Only a live PIP can be cancelled");
        }
        p.setStatus("CANCELLED");
        audit.record(MODULE, ENTITY, id.toString(), "CANCEL", null, "CANCELLED");
        return pips.save(p);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void validate(PipRequest req) {
        if (req.reason() == null || req.reason().isBlank()) {
            throw new BadRequestException("A PIP reason is required");
        }
        if (req.startDate() == null || req.endDate() == null
                || req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("PIP end date must be on/after the start date");
        }
    }

    private static void apply(PerformancePip p, PipRequest req) {
        p.setEmployeeId(req.employeeId());
        p.setReviewId(req.reviewId());
        p.setManagerOwnerId(req.managerOwnerId());
        p.setHrOwner(req.hrOwner());
        p.setReason(req.reason().trim());
        p.setIssueDescription(req.issueDescription());
        p.setObjectives(req.objectives());
        p.setSuccessCriteria(req.successCriteria());
        p.setSupportActions(req.supportActions());
        p.setConsequences(req.consequences());
        p.setStartDate(req.startDate());
        p.setEndDate(req.endDate());
    }

    private static void assertStatus(PerformancePip p, String expected) {
        if (!expected.equals(p.getStatus())) {
            throw new BadRequestException("PIP must be " + expected + " (was " + p.getStatus() + ")");
        }
    }

    private PerformancePip requireAccessible(UUID id) {
        PerformancePip p = pips.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PIP not found: " + id));
        requireEmployeeAccessible(p.getEmployeeId());
        return p;
    }

    private void requireEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new AccessDeniedException("PIP is outside your access scope");
        }
    }
}
