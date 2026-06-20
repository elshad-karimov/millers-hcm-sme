package az.millers.hcm.lifecycle.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.AssetReturnStatus;
import az.millers.hcm.lifecycle.domain.HandoverStatus;
import az.millers.hcm.lifecycle.domain.ItAccessStatus;
import az.millers.hcm.lifecycle.domain.OffboardingHandoverTask;
import az.millers.hcm.lifecycle.domain.OffboardingItAccess;
import az.millers.hcm.lifecycle.domain.ClearanceDepartment;
import az.millers.hcm.lifecycle.domain.ClearanceStatus;
import az.millers.hcm.lifecycle.domain.OffboardingAssetReturn;
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
            Boolean replacementRequired,
            String replacementNotes,
            OffsetDateTime vacancyTriggeredAt,
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
                    c.getNotes(), c.getReplacementRequired(), c.getReplacementNotes(),
                    c.getVacancyTriggeredAt(), c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedAt());
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

    public record AssetReturnResponse(
            UUID id, UUID caseId, UUID employeeAssetId,
            String assetType, String assetName, String assetIdentifier,
            AssetReturnStatus returnStatus, LocalDate returnDate,
            String conditionAtReturn, BigDecimal deductionAmount,
            String notes, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        public static AssetReturnResponse from(OffboardingAssetReturn r) {
            return new AssetReturnResponse(
                    r.getId(), r.getCaseId(), r.getEmployeeAssetId(),
                    r.getAssetType(), r.getAssetName(), r.getAssetIdentifier(),
                    r.getReturnStatus(), r.getReturnDate(),
                    r.getConditionAtReturn(), r.getDeductionAmount(),
                    r.getNotes(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    public record AssetReturnUpdateRequest(
            AssetReturnStatus returnStatus,
            LocalDate returnDate,
            String conditionAtReturn,
            BigDecimal deductionAmount,
            String notes
    ) {}

    public record ItAccessResponse(
            UUID id, UUID caseId, String accessType, String displayLabel,
            ItAccessStatus accessStatus, LocalDate removalDate,
            String handledBy, String reference, String notes,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        public static ItAccessResponse from(OffboardingItAccess a) {
            return new ItAccessResponse(
                    a.getId(), a.getCaseId(), a.getAccessType(), a.getDisplayLabel(),
                    a.getAccessStatus(), a.getRemovalDate(),
                    a.getHandledBy(), a.getReference(), a.getNotes(),
                    a.getCreatedAt(), a.getUpdatedAt());
        }
    }

    public record ItAccessUpdateRequest(
            ItAccessStatus accessStatus,
            LocalDate removalDate,
            String handledBy,
            String reference,
            String notes
    ) {}

    public record HandoverTaskResponse(
            UUID id, UUID caseId, String title, String category,
            String description, String handoverTo,
            HandoverStatus handoverStatus, LocalDate dueDate,
            OffsetDateTime completedAt, String notes,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        public static HandoverTaskResponse from(OffboardingHandoverTask t) {
            return new HandoverTaskResponse(
                    t.getId(), t.getCaseId(), t.getTitle(), t.getCategory(),
                    t.getDescription(), t.getHandoverTo(),
                    t.getHandoverStatus(), t.getDueDate(),
                    t.getCompletedAt(), t.getNotes(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    public record HandoverTaskCreateRequest(
            String title, String category, String description,
            String handoverTo, LocalDate dueDate, String notes
    ) {}

    public record HandoverTaskUpdateRequest(
            HandoverStatus handoverStatus, String handoverTo,
            LocalDate dueDate, String notes
    ) {}

    public record ReplacementUpdateRequest(
            Boolean replacementRequired,
            String replacementNotes
    ) {}
}
