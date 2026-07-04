package az.millers.hcm.lifecycle.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.TerminationReason;
import az.millers.hcm.lifecycle.domain.TerminationRequest;
import az.millers.hcm.lifecycle.domain.TerminationStatus;

public record TerminationResponse(
        UUID id,
        String terminationNo,
        UUID employeeId,
        TerminationReason reasonCode,
        String reasonDetail,
        LocalDate noticeDate,
        LocalDate lastWorkingDate,
        LocalDate effectiveDate,
        TerminationStatus status,
        UUID workflowInstanceId,
        BigDecimal unusedLeaveDays,
        BigDecimal unusedLeavePayout,
        BigDecimal severanceAmount,
        BigDecimal finalSettlementAmount,
        String currency,
        Map<String, Object> payoutCalcDetails,
        boolean clearanceIt,
        boolean clearanceHr,
        boolean clearanceFinance,
        boolean clearanceAssets,
        String attachmentUrls,
        String note,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime processedAt,
        String processedBy) {

    public static TerminationResponse from(TerminationRequest t) {
        return new TerminationResponse(
                t.getId(),
                t.getTerminationNo(),
                t.getEmployeeId(),
                t.getReasonCode(),
                t.getReasonDetail(),
                t.getNoticeDate(),
                t.getLastWorkingDate(),
                t.getEffectiveDate(),
                t.getStatus(),
                t.getWorkflowInstanceId(),
                t.getUnusedLeaveDays(),
                t.getUnusedLeavePayout(),
                t.getSeveranceAmount(),
                t.getFinalSettlementAmount(),
                t.getCurrency(),
                t.getPayoutCalcDetails(),
                t.isClearanceIt(),
                t.isClearanceHr(),
                t.isClearanceFinance(),
                t.isClearanceAssets(),
                t.getAttachmentUrls(),
                t.getNote(),
                t.getCreatedAt(),
                t.getCreatedBy(),
                t.getProcessedAt(),
                t.getProcessedBy());
    }
}
