package az.millers.hcm.corehr.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.domain.AuditLog;
import az.millers.hcm.audit.repo.AuditLogRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.ChangeHistoryDtos.ChangeEvent;
import az.millers.hcm.corehr.api.dto.ChangeHistoryDtos.EmployeeChangeHistory;
import az.millers.hcm.corehr.api.dto.ChangeHistoryDtos.EventCategory;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmployeeEmploymentHistory;
import az.millers.hcm.corehr.domain.EmployeeStatusHistory;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * Per-employee unified change timeline (M117).
 *
 * <p>Three sources fold into one chronological feed:
 * <ul>
 *   <li>{@link EmployeeEmploymentHistory} slices from M62 — position,
 *       department, manager, FTE changes.</li>
 *   <li>{@link EmployeeStatusHistory} slices from M62 — status transitions.</li>
 *   <li>{@link AuditLog} rows where {@code entity_name = "Employee"} —
 *       direct field edits (name, email, phone, national_id, etc.).</li>
 * </ul>
 *
 * <p>Pure-static mappers ({@link #toEmploymentEvent}, {@link #toStatusEvent},
 * {@link #toAuditEvent}, {@link #sortDescending}) are extracted so the
 * one-event-per-row mapping can be pinned by unit tests without spinning
 * up Spring or hitting the DB.
 */
@Service
public class EmployeeChangeHistoryService {

    /** Hard cap on event count returned — keeps response payload bounded. */
    static final int MAX_EVENTS = 500;

    private final EmployeeRepository employees;
    private final EmployeeHistoryService history;
    private final AuditLogRepository auditLogs;
    private final AccessScopeService accessScope;

    public EmployeeChangeHistoryService(EmployeeRepository employees,
                                    EmployeeHistoryService history,
                                    AuditLogRepository auditLogs,
                                    AccessScopeService accessScope) {
        this.employees = employees;
        this.history = history;
        this.auditLogs = auditLogs;
        this.accessScope = accessScope;
    }

    @Transactional(readOnly = true)
    public EmployeeChangeHistory timelineFor(UUID employeeId) {
        if (employeeId == null) {
            throw new BadRequestException("employeeId is required");
        }
        // Scope check — managers and org-unit HR see only what they're
        // allowed to see; unrestricted HR / auditors bypass.
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
        Employee emp = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + employeeId));

        List<ChangeEvent> events = new ArrayList<>();

        // Employment slices.
        for (EmployeeEmploymentHistory slice : history.employmentHistoryFor(employeeId)) {
            events.add(toEmploymentEvent(slice));
        }
        // Status slices.
        for (EmployeeStatusHistory slice : history.statusHistoryFor(employeeId)) {
            events.add(toStatusEvent(slice));
        }
        // Audit log rows for this employee.
        for (AuditLog entry : auditLogs
                .findByEntityNameAndEntityIdOrderByCreatedAtDesc("Employee", employeeId.toString())) {
            events.add(toAuditEvent(entry));
        }

        List<ChangeEvent> sorted = sortDescending(events);
        if (sorted.size() > MAX_EVENTS) {
            sorted = sorted.subList(0, MAX_EVENTS);
        }

        String fullName = emp.getFirstName() + " " + emp.getLastName();
        return new EmployeeChangeHistory(employeeId, fullName, sorted.size(), sorted);
    }

    // ─── Pure-static mappers ─────────────────────────────────────────────

    /** Package-private — pinned by the unit test. */
    static ChangeEvent toEmploymentEvent(EmployeeEmploymentHistory slice) {
        String title = buildEmploymentTitle(slice);
        String summary = buildEmploymentSummary(slice);
        return new ChangeEvent(
                EventCategory.EMPLOYMENT_CHANGE,
                slice.getCreatedAt(),
                slice.getEffectiveFrom(),
                "EMPLOYMENT_SLICE",
                title,
                summary,
                slice.getChangedBy(),
                slice.getSourceModule(),
                slice.getSourceEntity(),
                slice.getSourceId(),
                null, null,
                slice.getId());
    }

    /** Package-private — pinned by the unit test. */
    static ChangeEvent toStatusEvent(EmployeeStatusHistory slice) {
        String statusName = slice.getStatus() == null ? "(unknown)" : slice.getStatus().name();
        String title = "Status → " + statusName;
        String summary = slice.getReason();
        return new ChangeEvent(
                EventCategory.STATUS_CHANGE,
                slice.getCreatedAt(),
                slice.getEffectiveFrom(),
                "STATUS_" + statusName,
                title,
                summary,
                slice.getChangedBy(),
                slice.getSourceModule(),
                slice.getSourceEntity(),
                slice.getSourceId(),
                null, null,
                slice.getId());
    }

    /** Package-private — pinned by the unit test. */
    static ChangeEvent toAuditEvent(AuditLog entry) {
        String action = entry.getAction();
        String title = humanAction(action);
        return new ChangeEvent(
                EventCategory.AUDIT,
                entry.getCreatedAt(),
                null,
                action,
                title,
                null,
                entry.getActor(),
                entry.getModule(),
                entry.getEntityName(),
                entry.getEntityId(),
                entry.getOldValue(),
                entry.getNewValue(),
                entry.getId());
    }

    /**
     * Pinned by the unit test. Sort by {@code eventTime} descending —
     * most recent change at the top. Events without a timestamp sort
     * to the bottom (defensive — a corrupted row mustn't leapfrog real
     * recent changes).
     */
    static List<ChangeEvent> sortDescending(List<ChangeEvent> events) {
        List<ChangeEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(
                ChangeEvent::eventTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return sorted;
    }

    // ─── Title / summary builders ────────────────────────────────────────

    private static String buildEmploymentTitle(EmployeeEmploymentHistory slice) {
        StringBuilder parts = new StringBuilder("Employment slice");
        if (slice.getPositionTitle() != null) {
            parts.append(" — ").append(slice.getPositionTitle());
        } else if (slice.getDepartmentName() != null) {
            parts.append(" — ").append(slice.getDepartmentName());
        }
        return parts.toString();
    }

    private static String buildEmploymentSummary(EmployeeEmploymentHistory slice) {
        StringBuilder out = new StringBuilder();
        if (slice.getDepartmentName() != null) {
            out.append("Department: ").append(slice.getDepartmentName()).append('\n');
        }
        if (slice.getEmploymentType() != null) {
            out.append("Type: ").append(slice.getEmploymentType()).append('\n');
        }
        if (slice.getFtePercent() != null) {
            out.append("FTE: ").append(slice.getFtePercent()).append("%\n");
        }
        if (slice.getCostCentre() != null) {
            out.append("Cost centre: ").append(slice.getCostCentre()).append('\n');
        }
        if (slice.getChangeReason() != null && !slice.getChangeReason().isBlank()) {
            out.append("Reason: ").append(slice.getChangeReason());
        }
        String s = out.toString();
        return s.isEmpty() ? null : s.trim();
    }

    /** Package-private — pinned by the unit test. */
    static String humanAction(String action) {
        if (action == null) return "Change";
        return switch (action) {
            case "CREATE" -> "Created";
            case "UPDATE" -> "Updated";
            case "DELETE" -> "Deleted";
            case "STATUS_CHANGE" -> "Status changed";
            case "REHIRE" -> "Rehired";
            case "TERMINATE" -> "Terminated";
            default -> action.replace('_', ' ').toLowerCase();
        };
    }

    /** Convenience accessor used by the controller tests. */
    @SuppressWarnings("unused")
    Optional<Employee> resolveEmployee(UUID id) {
        return employees.findById(id);
    }

    /** Defensive — kept around for symmetry with the public accessor. */
    @SuppressWarnings("unused")
    private static OffsetDateTime now() {
        return OffsetDateTime.now();
    }
}
