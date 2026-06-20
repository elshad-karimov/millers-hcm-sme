package az.millers.hcm.lifecycle.event;

import java.util.UUID;

/** Published after an offboarding settlement transitions to APPROVED. */
public record OffboardingSettlementApprovedEvent(UUID caseId, UUID employeeId, String settlementNo) {}
