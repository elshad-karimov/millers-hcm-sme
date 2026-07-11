package az.millers.hcm.recruitment.service;

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
import az.millers.hcm.recruitment.api.dto.AssessmentDtos.AssessmentRequest;
import az.millers.hcm.recruitment.api.dto.AssessmentDtos.AssessmentResponse;
import az.millers.hcm.recruitment.api.dto.AssessmentDtos.AssessmentUpdate;
import az.millers.hcm.recruitment.domain.Assessment;
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.AssessmentRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

/**
 * M287 — Recruitment PRD §22: assessment & test management. Assign a
 * test to an application, record score + pass/fail. A blocks-hire
 * FAILED assessment stops {@code ApplicationService.hire} (§22/§70).
 */
@Service
public class AssessmentService {

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "Assessment";

    private final AssessmentRepository assessments;
    private final ApplicationRepository applications;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public AssessmentService(AssessmentRepository assessments,
                              ApplicationRepository applications,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.assessments = assessments;
        this.applications = applications;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> listForApplication(UUID applicationId) {
        return assessments.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
                .map(AssessmentResponse::from)
                .toList();
    }

    @Transactional
    public AssessmentResponse create(UUID applicationId, AssessmentRequest req) {
        if (!applications.existsById(applicationId)) {
            throw new ResourceNotFoundException("Application not found: " + applicationId);
        }
        if (req.maxScore() != null && req.passingScore() != null
                && req.passingScore().compareTo(req.maxScore()) > 0) {
            throw new BadRequestException("passingScore cannot exceed maxScore");
        }
        Assessment a = new Assessment();
        a.setAssessmentNo(BusinessNumbers.format("ASM", 5, assessments.nextNoSequence()));
        a.setApplicationId(applicationId);
        a.setAssessmentType(req.assessmentType());
        a.setName(req.name());
        a.setProvider(req.provider());
        a.setMaxScore(req.maxScore());
        a.setPassingScore(req.passingScore());
        a.setValidUntil(req.validUntil());
        a.setBlocksHire(req.blocksHire() != null && req.blocksHire());
        a.setStatus(Assessment.Status.ASSIGNED);
        a.setCreatedBy(currentRequest.username());
        a.setUpdatedBy(currentRequest.username());
        Assessment saved = assessments.save(a);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null,
                Map.of("applicationId", applicationId.toString(),
                        "type", saved.getAssessmentType().name(),
                        "blocksHire", saved.isBlocksHire()));
        return AssessmentResponse.from(saved);
    }

    @Transactional
    public AssessmentResponse update(UUID id, AssessmentUpdate req) {
        Assessment a = assessments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + id));
        if (a.getStatus() == Assessment.Status.CANCELLED) {
            throw new BadRequestException("Assessment is cancelled");
        }
        Assessment.Status old = a.getStatus();
        a.setStatus(req.status());
        if (req.score() != null) a.setScore(req.score());
        if (req.notes() != null) a.setNotes(req.notes());
        if (req.attachmentId() != null) a.setAttachmentId(req.attachmentId());

        // Auto-derive PASS/FAIL from score vs passing_score when a
        // result wasn't given explicitly and we have both numbers.
        Assessment.Result result = req.result();
        if (result == null && req.score() != null && a.getPassingScore() != null) {
            result = req.score().compareTo(a.getPassingScore()) >= 0
                    ? Assessment.Result.PASS : Assessment.Result.FAIL;
        }
        if (result != null) a.setResult(result);

        if ((req.status() == Assessment.Status.COMPLETED) && a.getCompletedAt() == null) {
            a.setCompletedAt(OffsetDateTime.now());
        }
        a.setUpdatedBy(currentRequest.username());
        Assessment saved = assessments.save(a);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE",
                Map.of("status", old.name()),
                Map.of("status", saved.getStatus().name(),
                        "score", saved.getScore() == null ? "" : saved.getScore().toPlainString(),
                        "result", saved.getResult() == null ? "" : saved.getResult().name()));
        return AssessmentResponse.from(saved);
    }

    /** M287 — PRD §22 hire gate: a blocks-hire FAILED assessment stops the hire. */
    @Transactional(readOnly = true)
    public void assertNoBlockingFailures(UUID applicationId) {
        List<Assessment> failed = assessments.findBlockingFailures(applicationId);
        if (!failed.isEmpty()) {
            String names = failed.stream()
                    .map(Assessment::getName)
                    .reduce((x, y) -> x + ", " + y).orElse("");
            throw new BadRequestException(
                    "Cannot hire — required assessment(s) FAILED: " + names
                    + ". Resolve or waive before hiring.");
        }
    }
}
