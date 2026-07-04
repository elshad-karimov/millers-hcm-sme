package az.millers.hcm.lifecycle.event;

import java.util.UUID;

import az.millers.hcm.lifecycle.domain.ChecklistFlowType;

/**
 * Published by {@code ChecklistService.start()} when a checklist assignment is
 * created (M301). Lets onboarding cross-functional wiring react to typed tasks
 * (e.g. spawn equipment/workspace provisioning requests) without the generic
 * checklist engine depending on those downstream services — keeps the
 * dependency one-way and avoids a bean cycle.
 */
public record ChecklistStartedEvent(UUID assignmentId, UUID employeeId, ChecklistFlowType flowType) {}
