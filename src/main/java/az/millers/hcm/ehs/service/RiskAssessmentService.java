package az.millers.hcm.ehs.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.ehs.domain.RiskAssessment;
import az.millers.hcm.ehs.domain.RiskAssessmentStatus;
import az.millers.hcm.ehs.repo.RiskAssessmentRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M450 — EHS risk assessment service.
 * Risk score = likelihood × impact (persisted, computed in service).
 * Approval by HR/HR_SPECIALIST.
 */
@Service
public class RiskAssessmentService {

    private static final String TENANT = "default";
    private static final String MODULE = "ehs";
    private static final String ENTITY = "RiskAssessment";

    private final RiskAssessmentRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public RiskAssessmentService(RiskAssessmentRepository repo,
                                 AuditService audit,
                                 CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public RiskAssessment create(UUID workLocationId,
                                  UUID orgUnitId,
                                  String jobTask,
                                  String hazard,
                                  Integer likelihood,
                                  Integer impact,
                                  String controlMeasures,
                                  String responsibleUsername,
                                  LocalDate reviewDate) {

        // Validate likelihood and impact (1-5)
        if (likelihood == null || likelihood < 1 || likelihood > 5) {
            throw new IllegalArgumentException("Likelihood must be between 1 and 5");
        }
        if (impact == null || impact < 1 || impact > 5) {
            throw new IllegalArgumentException("Impact must be between 1 and 5");
        }

        int riskScore = likelihood * impact;

        RiskAssessment assessment = new RiskAssessment();
        assessment.setTenantId(TENANT);
        assessment.setWorkLocationId(workLocationId);
        assessment.setOrgUnitId(orgUnitId);
        assessment.setJobTask(jobTask);
        assessment.setHazard(hazard);
        assessment.setLikelihood(likelihood);
        assessment.setImpact(impact);
        assessment.setRiskScore(riskScore);
        assessment.setControlMeasures(controlMeasures);
        assessment.setResponsibleUsername(responsibleUsername);
        assessment.setReviewDate(reviewDate);
        assessment.setStatus(RiskAssessmentStatus.DRAFT);
        assessment.setCreatedBy(currentRequest.username());
        assessment.setUpdatedBy(currentRequest.username());

        RiskAssessment saved = repo.save(assessment);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATED", null, null);

        return saved;
    }

    @Transactional
    public RiskAssessment update(UUID id,
                                  String jobTask,
                                  String hazard,
                                  Integer likelihood,
                                  Integer impact,
                                  String controlMeasures,
                                  String responsibleUsername,
                                  LocalDate reviewDate) {

        RiskAssessment assessment = get(id);

        if (jobTask != null) assessment.setJobTask(jobTask);
        if (hazard != null) assessment.setHazard(hazard);

        boolean recomputeScore = false;
        if (likelihood != null) {
            if (likelihood < 1 || likelihood > 5) {
                throw new IllegalArgumentException("Likelihood must be between 1 and 5");
            }
            assessment.setLikelihood(likelihood);
            recomputeScore = true;
        }
        if (impact != null) {
            if (impact < 1 || impact > 5) {
                throw new IllegalArgumentException("Impact must be between 1 and 5");
            }
            assessment.setImpact(impact);
            recomputeScore = true;
        }

        if (recomputeScore) {
            int riskScore = assessment.getLikelihood() * assessment.getImpact();
            assessment.setRiskScore(riskScore);
        }

        if (controlMeasures != null) assessment.setControlMeasures(controlMeasures);
        if (responsibleUsername != null) assessment.setResponsibleUsername(responsibleUsername);
        if (reviewDate != null) assessment.setReviewDate(reviewDate);

        assessment.setUpdatedBy(currentRequest.username());
        assessment.setUpdatedAt(OffsetDateTime.now());

        RiskAssessment updated = repo.save(assessment);

        audit.record(MODULE, ENTITY, id.toString(), "UPDATED", null, null);

        return updated;
    }

    @Transactional
    public void approve(UUID id) {
        RiskAssessment assessment = get(id);

        RiskAssessmentStatus oldStatus = assessment.getStatus();

        assessment.setStatus(RiskAssessmentStatus.APPROVED);
        assessment.setApprovedBy(currentRequest.username());
        assessment.setApprovedAt(OffsetDateTime.now());
        assessment.setUpdatedBy(currentRequest.username());
        assessment.setUpdatedAt(OffsetDateTime.now());

        repo.save(assessment);

        audit.record(MODULE, ENTITY, id.toString(), "APPROVED",
                Map.of("status", oldStatus),
                Map.of("status", RiskAssessmentStatus.APPROVED));
    }

    @Transactional(readOnly = true)
    public RiskAssessment get(UUID id) {
        RiskAssessment assessment = repo.findByIdAndTenantId(id, TENANT)
                .orElseThrow(() -> new ResourceNotFoundException("Risk assessment not found: " + id));

        // Tenant post-check
        if (!TENANT.equals(assessment.getTenantId())) {
            throw new ResourceNotFoundException("Risk assessment not found: " + id);
        }

        return assessment;
    }

    @Transactional(readOnly = true)
    public List<RiskAssessment> list(RiskAssessmentStatus statusFilter, Integer minRiskScore) {
        if (statusFilter != null) {
            return repo.findByTenantIdAndStatusOrderByRiskScoreDesc(TENANT, statusFilter);
        } else if (minRiskScore != null) {
            return repo.findByTenantIdAndRiskScoreGreaterThanEqualOrderByRiskScoreDesc(TENANT, minRiskScore);
        } else {
            return repo.findByTenantIdOrderByRiskScoreDesc(TENANT);
        }
    }
}
