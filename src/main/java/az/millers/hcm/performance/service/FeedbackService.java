package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.api.dto.FeedbackRequest;
import az.millers.hcm.performance.api.dto.FeedbackResponse;
import az.millers.hcm.performance.domain.CycleStatus;
import az.millers.hcm.performance.domain.Feedback;
import az.millers.hcm.performance.domain.FeedbackStatus;
import az.millers.hcm.performance.domain.FeedbackVisibility;
import az.millers.hcm.performance.domain.ReviewCycle;
import az.millers.hcm.performance.repo.FeedbackRepository;
import az.millers.hcm.performance.repo.ReviewCycleRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class FeedbackService {

    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "Feedback";

    private final FeedbackRepository feedback;
    private final ReviewCycleRepository cycles;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public FeedbackService(FeedbackRepository feedback,
                            ReviewCycleRepository cycles,
                            EmployeeRepository employees,
                            AuditService audit,
                            CurrentRequest currentRequest) {
        this.feedback = feedback;
        this.cycles = cycles;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public Feedback get(UUID id) {
        return feedback.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Feedback> listForSubject(UUID cycleId, UUID subjectEmployeeId) {
        return feedback.findByCycleIdAndSubjectEmployeeIdOrderByCreatedAtDesc(cycleId, subjectEmployeeId);
    }

    @Transactional(readOnly = true)
    public List<Feedback> listForCycle(UUID cycleId) {
        return feedback.findByCycleIdOrderBySubjectEmployeeIdAscCreatedAtAsc(cycleId);
    }

    @Transactional
    public Feedback submit(FeedbackRequest req) {
        ReviewCycle cycle = cycles.findById(req.cycleId())
                .orElseThrow(() -> new BadRequestException("Cycle not found: " + req.cycleId()));
        if (cycle.getStatus() != CycleStatus.OPEN
                && cycle.getStatus() != CycleStatus.CALIBRATING) {
            throw new BadRequestException(
                    "Feedback can only be submitted on OPEN / CALIBRATING cycles (was " + cycle.getStatus() + ")");
        }
        if (!employees.existsById(req.subjectEmployeeId())) {
            throw new BadRequestException("Subject employee not found: " + req.subjectEmployeeId());
        }
        if (req.authorEmployeeId() != null
                && req.authorEmployeeId().equals(req.subjectEmployeeId())
                && req.relationship() != az.millers.hcm.performance.domain.FeedbackRelationship.SELF) {
            throw new BadRequestException(
                    "Self feedback must use the SELF relationship");
        }
        validateRating(req.overallRating());

        Feedback f = new Feedback();
        f.setCycleId(req.cycleId());
        f.setSubjectEmployeeId(req.subjectEmployeeId());
        f.setAuthorEmployeeId(req.authorEmployeeId());
        f.setRelationship(req.relationship());
        f.setVisibility(req.visibility() == null ? FeedbackVisibility.NORMAL : req.visibility());
        f.setOverallRating(req.overallRating());
        f.setStrengths(req.strengths());
        f.setImprovements(req.improvements());
        f.setComments(req.comments());
        if (req.competencies() != null) f.setCompetencies(req.competencies());
        f.setStatus(req.submit() ? FeedbackStatus.SUBMITTED : FeedbackStatus.DRAFT);
        if (req.submit()) f.setSubmittedAt(OffsetDateTime.now());
        f.setCreatedBy(currentRequest.username());
        Feedback saved = feedback.save(f);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                req.submit() ? "SUBMIT" : "DRAFT", null, FeedbackResponse.from(saved));
        return saved;
    }

    @Transactional
    public Feedback submitDraft(UUID id) {
        Feedback f = get(id);
        if (f.getStatus() == FeedbackStatus.SUBMITTED) {
            throw new BadRequestException("Feedback is already submitted");
        }
        FeedbackResponse before = FeedbackResponse.from(f);
        f.setStatus(FeedbackStatus.SUBMITTED);
        f.setSubmittedAt(OffsetDateTime.now());
        Feedback saved = feedback.save(f);
        audit.record(MODULE, ENTITY, id.toString(),
                "SUBMIT", before, FeedbackResponse.from(saved));
        return saved;
    }

    private void validateRating(BigDecimal rating) {
        if (rating == null) return;
        if (rating.signum() < 0 || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new BadRequestException("overallRating must be between 0 and 5");
        }
    }

    // Audit footprint for re-use if/when we expose status changes via API.
    @SuppressWarnings("unused")
    private Map<String, Object> nothing() { return Map.of(); }
}
