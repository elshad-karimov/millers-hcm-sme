package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.lifecycle.domain.ContractStatus;
import az.millers.hcm.lifecycle.domain.EmploymentContract;

public record ContractResponse(
        UUID id,
        String contractNo,
        UUID employeeId,
        EmploymentType contractType,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate probationEndDate,
        int noticePeriodDays,
        ContractStatus status,
        OffsetDateTime signedByEmployeeAt,
        OffsetDateTime signedByHrAt,
        boolean hasConfidentiality,
        LocalDate nonCompeteEndDate,
        String notes,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static ContractResponse from(EmploymentContract c) {
        return new ContractResponse(
                c.getId(),
                c.getContractNo(),
                c.getEmployeeId(),
                c.getContractType(),
                c.getStartDate(),
                c.getEndDate(),
                c.getProbationEndDate(),
                c.getNoticePeriodDays(),
                c.getStatus(),
                c.getSignedByEmployeeAt(),
                c.getSignedByHrAt(),
                c.isHasConfidentiality(),
                c.getNonCompeteEndDate(),
                c.getNotes(),
                c.getCreatedAt(),
                c.getCreatedBy(),
                c.getUpdatedAt(),
                c.getUpdatedBy());
    }
}
