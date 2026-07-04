package az.millers.hcm.recruitment.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.recruitment.domain.Application;
import az.millers.hcm.recruitment.domain.ApplicationStage;
import az.millers.hcm.recruitment.domain.ApplicationStatus;

public record ApplicationResponse(
        UUID id,
        String applicationNo,
        UUID vacancyId,
        UUID candidateId,
        ApplicationStage currentStage,
        ApplicationStatus status,
        UUID createdEmployeeId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy) {

    public static ApplicationResponse from(Application a) {
        return new ApplicationResponse(
                a.getId(), a.getApplicationNo(),
                a.getVacancyId(), a.getCandidateId(),
                a.getCurrentStage(), a.getStatus(),
                a.getCreatedEmployeeId(),
                a.getCreatedAt(), a.getUpdatedAt(), a.getCreatedBy());
    }
}
