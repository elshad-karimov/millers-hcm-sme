package az.millers.hcm.learning.api;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.learning.domain.Instructor;
import az.millers.hcm.learning.domain.TrainingCost;
import az.millers.hcm.learning.domain.TrainingFeedback;
import az.millers.hcm.learning.repo.InstructorRepository;
import az.millers.hcm.learning.repo.TrainingAttendanceRepository;
import az.millers.hcm.learning.repo.TrainingCostRepository;
import az.millers.hcm.learning.repo.TrainingFeedbackRepository;
import az.millers.hcm.learning.repo.TrainingSessionRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * HCM_14 M407 (cost tracking §19) + M408 (feedback §20). Thin CRUD kept in the
 * controller; feedback re-aggregates the instructor's rating on submit.
 */
@RestController
@RequestMapping("/api/learning")
public class TrainingCostFeedbackController {

    public record CostRequest(UUID courseId, UUID sessionId, String costType, String description,
                              BigDecimal amount, LocalDate incurredOn) {}

    public record FeedbackRequest(UUID sessionId, UUID courseId, UUID employeeId,
                                  Integer overallRating, Integer contentRating,
                                  Integer instructorRating, String comment, Boolean anonymous) {}


    private final TrainingCostRepository costs;
    private final TrainingFeedbackRepository feedback;
    private final TrainingSessionRepository sessions;
    private final TrainingAttendanceRepository attendance;
    private final InstructorRepository instructors;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final AccessScopeService accessScope;

    public TrainingCostFeedbackController(TrainingCostRepository costs,
                                          TrainingFeedbackRepository feedback,
                                          TrainingSessionRepository sessions,
                                          TrainingAttendanceRepository attendance,
                                          InstructorRepository instructors,
                                          AuditService audit,
                                          CurrentRequest currentRequest,
                                          AccessScopeService accessScope) {
        this.costs = costs;
        this.feedback = feedback;
        this.sessions = sessions;
        this.attendance = attendance;
        this.instructors = instructors;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.accessScope = accessScope;
    }

    // ── M407 costs ──────────────────────────────────────────────────────────

    @GetMapping("/training-costs")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<TrainingCost> listCosts(@RequestParam(required = false) UUID courseId,
                                        @RequestParam(required = false) UUID sessionId) {
        if (sessionId != null) return costs.findBySessionIdOrderByCreatedAtDesc(sessionId);
        if (courseId != null) return costs.findByTenantIdAndCourseIdOrderByCreatedAtDesc(TenantContext.current(), courseId);
        return costs.findByTenantIdOrderByCreatedAtDesc(TenantContext.current());
    }

    @PostMapping("/training-costs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingCost addCost(@RequestBody CostRequest req) {
        if (req.courseId() == null && req.sessionId() == null) {
            throw new BadRequestException("A cost line needs a course or a session");
        }
        if (req.amount() == null || req.amount().signum() < 0) {
            throw new BadRequestException("A non-negative amount is required");
        }
        TrainingCost c = new TrainingCost();
        c.setTenantId(TenantContext.current());
        c.setCourseId(req.courseId());
        c.setSessionId(req.sessionId());
        if (req.costType() != null) c.setCostType(req.costType());
        c.setDescription(req.description());
        c.setAmount(req.amount());
        c.setIncurredOn(req.incurredOn());
        c.setCreatedBy(currentRequest.username());
        TrainingCost saved = costs.save(c);
        audit.record("LEARNING", "TrainingCost", saved.getId().toString(), "CREATE",
                null, Map.of("type", saved.getCostType(), "amount", req.amount().toPlainString()));
        return saved;
    }

    // ── M408 feedback ───────────────────────────────────────────────────────

    /** HR view; anonymous rows have the employee hidden client-side by omission here. */
    @GetMapping("/training-feedback")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<TrainingFeedback> listFeedback(@RequestParam(required = false) UUID sessionId,
                                               @RequestParam(required = false) UUID courseId) {
        List<TrainingFeedback> rows = sessionId != null
                ? feedback.findBySessionIdOrderByCreatedAtDesc(sessionId)
                : feedback.findByTenantIdAndCourseIdOrderByCreatedAtDesc(TenantContext.current(), courseId);
        // §20 — anonymity: strip the author on anonymous rows before returning.
        rows.forEach(f -> { if (f.isAnonymous()) f.setEmployeeId(new UUID(0, 0)); });
        return rows;
    }

    @PostMapping("/training-feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public TrainingFeedback submitFeedback(@RequestBody FeedbackRequest req) {
        if (req.sessionId() == null && req.courseId() == null) {
            throw new BadRequestException("Feedback needs a session or a course");
        }
        if (req.overallRating() == null || req.overallRating() < 1 || req.overallRating() > 5) {
            throw new BadRequestException("overallRating must be 1..5");
        }
        // Scope guard: prevent cross-hierarchy feedback forgery
        if (!accessScope.isAccessible(req.employeeId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Employee is outside your access scope");
        }
        if (req.sessionId() != null) {
            if (!attendance.existsBySessionIdAndEmployeeId(req.sessionId(), req.employeeId())) {
                throw new BadRequestException("Only session participants can leave feedback");
            }
            if (feedback.existsBySessionIdAndEmployeeId(req.sessionId(), req.employeeId())) {
                throw new BadRequestException("Feedback already submitted for this session");
            }
        }
        TrainingFeedback f = new TrainingFeedback();
        f.setTenantId(TenantContext.current());
        f.setSessionId(req.sessionId());
        f.setCourseId(req.courseId());
        f.setEmployeeId(req.employeeId());
        f.setOverallRating(req.overallRating());
        f.setContentRating(req.contentRating());
        f.setInstructorRating(req.instructorRating());
        f.setComment(req.comment());
        f.setAnonymous(req.anonymous() == null ? true : req.anonymous());
        TrainingFeedback saved = feedback.save(f);
        audit.record("LEARNING", "TrainingFeedback", saved.getId().toString(), "SUBMIT",
                null, Map.of("overall", String.valueOf(req.overallRating())));
        refreshInstructorRating(req.sessionId());
        return saved;
    }

    /** Roll the average instructor rating of the session's feedback onto the instructor. */
    private void refreshInstructorRating(UUID sessionId) {
        if (sessionId == null) return;
        sessions.findById(sessionId).ifPresent(s -> {
            if (s.getInstructorId() == null) return;
            List<TrainingFeedback> rows = feedback.findBySessionIdOrderByCreatedAtDesc(sessionId);
            double avg = rows.stream()
                    .filter(f -> f.getInstructorRating() != null)
                    .mapToInt(TrainingFeedback::getInstructorRating)
                    .average().orElse(0);
            if (avg > 0) {
                Instructor i = instructors.findById(s.getInstructorId()).orElse(null);
                if (i != null) {
                    i.setRating(BigDecimal.valueOf(avg).setScale(2, java.math.RoundingMode.HALF_UP));
                    instructors.save(i);
                }
            }
        });
    }
}
