package az.millers.hcm.lifecycle.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.ClearanceDepartment;
import az.millers.hcm.lifecycle.domain.ClearanceStatus;
import az.millers.hcm.lifecycle.domain.OffboardingCase;
import az.millers.hcm.lifecycle.domain.OffboardingCaseStatus;
import az.millers.hcm.lifecycle.domain.OffboardingClearance;
import az.millers.hcm.lifecycle.domain.OffboardingSource;

public final class OffboardingDtos {
    private OffboardingDtos() {}

    public record OffboardingCaseResponse(
            UUID id,
            String caseNo,
            UUID employeeId,
            OffboardingSource source,
            UUID resignationId,
            UUID terminationId,
            String exitReason,
            OffboardingCaseStatus caseStatus,
            String caseOwner,
            LocalDate lastWorkingDate,
            LocalDate accessRemovalDate,
            LocalDate settlementDate,
            UUID checklistAssignmentId,
            String notes,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static OffboardingCaseResponse from(OffboardingCase c) {
            return new OffboardingCaseResponse(
                    c.getId(), c.getCaseNo(), c.getEmployeeId(), c.getSource(),
                    c.getResignationId(), c.getTerminationId(), c.getExitReason(),
                    c.getCaseStatus(), c.getCaseOwner(), c.getLastWorkingDate(),
                    c.getAccessRemovalDate(), c.getSettlementDate(), c.getChecklistAssignmentId(),
                    c.getNotes(), c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record OffboardingOverviewResponse(
            long activeCases,
            long leavingThisWeek,
            long leavingThisMonth,
            long totalCases,
            List<OffboardingCaseResponse> recentCases
    ) {}

    public record ClearanceResponse(
            UUID id,
            UUID caseId,
            ClearanceDepartment department,
            ClearanceStatus status,
            String clearedBy,
            OffsetDateTime clearedAt,
            BigDecimal deductionAmount,
            String notes,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static ClearanceResponse from(OffboardingClearance c) {
            return new ClearanceResponse(
                    c.getId(), c.getCaseId(), c.getDepartment(), c.getStatus(),
                    c.getClearedBy(), c.getClearedAt(), c.getDeductionAmount(),
                    c.getNotes(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record ClearanceSignOffRequest(
            ClearanceStatus status,
            BigDecimal deductionAmount,
            String notes
    ) {}
}
