package az.millers.hcm.lifecycle.event;

import java.util.UUID;

/** Published when an OffboardingCase first reaches IN_PROGRESS status. */
public record OffboardingCaseActivatedEvent(UUID caseId, UUID employeeId) {}
