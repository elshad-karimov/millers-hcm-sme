package az.millers.hcm.lifecycle.event;

import java.util.UUID;

import az.millers.hcm.lifecycle.domain.ChecklistFlowType;

/**
 * Published by {@code ChecklistService.autoCompleteIfDone()} (M308) when every
 * required task in an assignment is DONE or SKIPPED and the assignment
 * transitions to COMPLETED.
 *
 * <p>Lets downstream services (notifications, probation bridge) react without
 * the generic checklist engine knowing about them.
 */
public record ChecklistAssignmentCompletedEvent(
        UUID assignmentId,
        UUID employeeId,
        ChecklistFlowType flowType) {}
