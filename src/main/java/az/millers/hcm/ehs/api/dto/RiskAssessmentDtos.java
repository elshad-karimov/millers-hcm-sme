package az.millers.hcm.ehs.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.ehs.domain.RiskAssessment;
import az.millers.hcm.ehs.domain.RiskAssessmentStatus;
import az.millers.hcm.ehs.domain.RiskBand;

public class RiskAssessmentDtos {

    public record CreateRiskAssessmentRequest(
            UUID workLocationId,
            UUID orgUnitId,
            String jobTask,
            String hazard,
            Integer likelihood,
            Integer impact,
            String controlMeasures,
            String responsibleUsername,
            LocalDate reviewDate
    ) {}

    public record UpdateRiskAssessmentRequest(
            String jobTask,
            String hazard,
            Integer likelihood,
            Integer impact,
            String controlMeasures,
            String responsibleUsername,
            LocalDate reviewDate
    ) {}

    public record RiskAssessmentResponse(
            UUID id,
            String tenantId,
            UUID workLocationId,
            UUID orgUnitId,
            String jobTask,
            String hazard,
            Integer likelihood,
            Integer impact,
            Integer riskScore,
            RiskBand riskBand,
            String controlMeasures,
            String responsibleUsername,
            LocalDate reviewDate,
            RiskAssessmentStatus status,
            String approvedBy,
            OffsetDateTime approvedAt,
            String createdBy,
            OffsetDateTime createdAt,
            String updatedBy,
            OffsetDateTime updatedAt
    ) {
        public static RiskAssessmentResponse from(RiskAssessment assessment) {
            return new RiskAssessmentResponse(
                    assessment.getId(),
                    assessment.getTenantId(),
                    assessment.getWorkLocationId(),
                    assessment.getOrgUnitId(),
                    assessment.getJobTask(),
                    assessment.getHazard(),
                    assessment.getLikelihood(),
                    assessment.getImpact(),
                    assessment.getRiskScore(),
                    RiskBand.fromScore(assessment.getRiskScore()),
                    assessment.getControlMeasures(),
                    assessment.getResponsibleUsername(),
                    assessment.getReviewDate(),
                    assessment.getStatus(),
                    assessment.getApprovedBy(),
                    assessment.getApprovedAt(),
                    assessment.getCreatedBy(),
                    assessment.getCreatedAt(),
                    assessment.getUpdatedBy(),
                    assessment.getUpdatedAt()
            );
        }
    }
}
