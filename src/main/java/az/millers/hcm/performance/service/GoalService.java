package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.api.dto.GoalProgressRequest;
import az.millers.hcm.performance.api.dto.GoalRatingRequest;
import az.millers.hcm.performance.api.dto.GoalRequest;
import az.millers.hcm.performance.api.dto.GoalResponse;
import az.millers.hcm.performance.domain.CycleStatus;
import az.millers.hcm.performance.domain.Goal;
import az.millers.hcm.performance.domain.GoalStatus;
import az.millers.hcm.performance.domain.ReviewCycle;
import az.millers.hcm.performance.repo.GoalRepository;
import az.millers.hcm.performance.repo.ReviewCycleRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class GoalService {

    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "Goal";

    private final GoalRepository goals;
    private final ReviewCycleRepository cycles;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public GoalService(GoalRepository goals,
                       ReviewCycleRepository cycles,
                       EmployeeRepository employees,
                       AuditService audit,
                       CurrentRequest currentRequest) {
        this.goals = goals;
        this.cycles = cycles;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public Goal get(UUID id) {
        return goals.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Goal> listForEmployee(UUID cycleId, UUID employeeId) {
        return goals.findByCycleIdAndEmployeeIdOrderByCreatedAt(cycleId, employeeId);
    }

    @Transactional(readOnly = true)
    public List<Goal> listForCycle(UUID cycleId) {
        return goals.findByCycleIdOrderByEmployeeIdAscCreatedAtAsc(cycleId);
    }

    @Transactional
    public Goal create(GoalRequest req) {
        ReviewCycle cycle = cycles.findById(req.cycleId())
                .orElseThrow(() -> new BadRequestException("Cycle not found: " + req.cycleId()));
        if (cycle.getStatus() == CycleStatus.COMPLETED) {
            throw new BadRequestException("Cannot add goals to a COMPLETED cycle");
        }
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        if (req.parentGoalId() != null && !goals.existsById(req.parentGoalId())) {
            throw new BadRequestException("Parent goal not found: " + req.parentGoalId());
        }
        validateWeight(req.weightPercent());
        validateProgress(req.progressPercent());

        Goal g = new Goal();
        g.setGoalNo(String.format("GOAL-%05d", goals.nextNoSequence()));
        apply(g, req);
        if (g.getStatus() == null) g.setStatus(GoalStatus.DRAFT);
        g.setCreatedBy(currentRequest.username());
        g.setUpdatedBy(currentRequest.username());
        Goal saved = goals.save(g);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, GoalResponse.from(saved));
        return saved;
    }

    @Transactional
    public Goal update(UUID id, GoalRequest req) {
        Goal g = get(id);
        if (g.getStatus() == GoalStatus.ACHIEVED || g.getStatus() == GoalStatus.MISSED) {
            throw new BadRequestException("Cannot edit a finalised goal");
        }
        validateWeight(req.weightPercent());
        validateProgress(req.progressPercent());
        GoalResponse before = GoalResponse.from(g);
        apply(g, req);
        g.setUpdatedBy(currentRequest.username());
        Goal saved = goals.save(g);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, GoalResponse.from(saved));
        return saved;
    }

    @Transactional
    public Goal updateProgress(UUID id, GoalProgressRequest req) {
        Goal g = get(id);
        validateProgress(req.progressPercent());
        GoalResponse before = GoalResponse.from(g);
        g.setProgressPercent(req.progressPercent());
        if (req.status() != null) g.setStatus(req.status());
        g.setUpdatedBy(currentRequest.username());
        Goal saved = goals.save(g);
        audit.record(MODULE, ENTITY, id.toString(), "PROGRESS",
                before, GoalResponse.from(saved));
        return saved;
    }

    @Transactional
    public Goal rate(UUID id, GoalRatingRequest req) {
        Goal g = get(id);
        if (req.rating() == null || req.rating().signum() < 0
                || req.rating().compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new BadRequestException("rating must be between 0 and 5");
        }
        GoalResponse before = GoalResponse.from(g);
        g.setRating(req.rating());
        g.setRatingNote(req.note());
        if (req.finalStatus() != null) {
            g.setStatus(req.finalStatus());
        } else if (g.getStatus() == GoalStatus.ACTIVE || g.getStatus() == GoalStatus.ON_TRACK
                || g.getStatus() == GoalStatus.AT_RISK || g.getStatus() == GoalStatus.BLOCKED) {
            // Default: hi-end rating → ACHIEVED, low-end → MISSED.
            g.setStatus(req.rating().compareTo(BigDecimal.valueOf(3)) >= 0
                    ? GoalStatus.ACHIEVED : GoalStatus.MISSED);
        }
        g.setUpdatedBy(currentRequest.username());
        Goal saved = goals.save(g);
        audit.record(MODULE, ENTITY, id.toString(), "RATE",
                before, GoalResponse.from(saved));
        return saved;
    }

    private void apply(Goal g, GoalRequest req) {
        g.setCycleId(req.cycleId());
        g.setEmployeeId(req.employeeId());
        g.setParentGoalId(req.parentGoalId());
        g.setTitle(req.title());
        g.setDescription(req.description());
        g.setCategory(req.category());
        g.setTargetMetric(req.targetMetric());
        g.setWeightPercent(req.weightPercent() == null ? BigDecimal.ZERO : req.weightPercent());
        g.setProgressPercent(req.progressPercent() == null ? BigDecimal.ZERO : req.progressPercent());
        if (req.status() != null) g.setStatus(req.status());
        g.setDueDate(req.dueDate());
        // M49: optional LMS course link for auto-rating on PASSED enrollment
        g.setSourceCourseId(req.sourceCourseId());
    }

    private void validateWeight(BigDecimal weight) {
        if (weight == null) return;
        if (weight.signum() < 0 || weight.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("weightPercent must be between 0 and 100");
        }
    }

    private void validateProgress(BigDecimal progress) {
        if (progress == null) return;
        if (progress.signum() < 0 || progress.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("progressPercent must be between 0 and 100");
        }
    }
}
