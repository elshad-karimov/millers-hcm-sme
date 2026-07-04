package az.millers.hcm.lifecycle.event;

import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.TerminationReason;

/**
 * Published by {@link az.millers.hcm.lifecycle.service.TerminationService}
 * when a termination is successfully processed (PRD §8.11.6).
 *
 * @param terminationId   the processed termination record
 * @param terminationNo   human-readable number (e.g. TRM-00042)
 * @param employeeId      the terminated employee
 * @param employeeNo      employee number
 * @param employeeName    full name (first + last)
 * @param managerId       direct manager's employee ID (nullable)
 * @param reasonCode      termination reason
 * @param effectiveDate   termination effective date
 */
public record TerminationProcessedEvent(
        UUID terminationId,
        String terminationNo,
        UUID employeeId,
        String employeeNo,
        String employeeName,
        UUID managerId,
        TerminationReason reasonCode,
        LocalDate effectiveDate) {}
