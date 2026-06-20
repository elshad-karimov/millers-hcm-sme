package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.OffboardingCase;
import az.millers.hcm.lifecycle.domain.OffboardingCaseStatus;
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
}
