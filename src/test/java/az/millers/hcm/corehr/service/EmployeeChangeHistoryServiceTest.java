package az.millers.hcm.corehr.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.audit.domain.AuditLog;
import az.millers.hcm.corehr.api.dto.ChangeHistoryDtos.EventCategory;
import az.millers.hcm.corehr.api.dto.ChangeHistoryDtos.ChangeEvent;
import az.millers.hcm.corehr.domain.EmployeeEmploymentHistory;
import az.millers.hcm.corehr.domain.EmployeeStatusHistory;
import az.millers.hcm.corehr.domain.EmploymentStatus;

/**
 * Pure-math pinning for the M117 timeline service.
 *
 * <p>The risky bit is the mapping from three different row shapes
 * (employment slice / status slice / audit row) into one unified
 * {@code ChangeEvent} and the chronological merge. Reversing the sort
 * order or losing a category would push HR review onto the wrong row
 * during a compliance audit.
 */
class EmployeeChangeHistoryServiceTest {

    // ── toEmploymentEvent() ─────────────────────────────────────────────

    @Test
    void employmentEventCategorizedAndDated() {
        EmployeeEmploymentHistory slice = newEmploymentSlice();
        slice.setPositionTitle("Senior Engineer");
        slice.setDepartmentName("Platform");
        slice.setEffectiveFrom(LocalDate.of(2026, 6, 1));
        slice.setCreatedAt(at(2026, 6, 1, 9, 0));
        slice.setChangedBy("alice");

        ChangeEvent event = EmployeeChangeHistoryService.toEmploymentEvent(slice);

        assertThat(event.category()).isEqualTo(EventCategory.EMPLOYMENT_CHANGE);
        assertThat(event.effectiveDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(event.eventTime()).isEqualTo(at(2026, 6, 1, 9, 0));
        assertThat(event.title()).contains("Senior Engineer");
        assertThat(event.summary()).contains("Department: Platform");
        assertThat(event.actor()).isEqualTo("alice");
    }

    @Test
    void employmentEventTitleFallsBackToDepartmentWhenNoPosition() {
        EmployeeEmploymentHistory slice = newEmploymentSlice();
        slice.setDepartmentName("Finance");
        ChangeEvent event = EmployeeChangeHistoryService.toEmploymentEvent(slice);
        assertThat(event.title()).contains("Finance");
    }

    @Test
    void employmentSummaryIncludesChangeReasonWhenPresent() {
        EmployeeEmploymentHistory slice = newEmploymentSlice();
        slice.setDepartmentName("Engineering");
        slice.setChangeReason("Promoted to Staff Engineer");
        ChangeEvent event = EmployeeChangeHistoryService.toEmploymentEvent(slice);
        assertThat(event.summary()).contains("Reason: Promoted to Staff Engineer");
    }

    @Test
    void employmentSummaryNullWhenNoFieldsPopulated() {
        EmployeeEmploymentHistory slice = newEmploymentSlice();
        ChangeEvent event = EmployeeChangeHistoryService.toEmploymentEvent(slice);
        assertThat(event.summary()).isNull();
    }

    // ── toStatusEvent() ─────────────────────────────────────────────────

    @Test
    void statusEventCategorizedWithFromStatus() {
        EmployeeStatusHistory slice = new EmployeeStatusHistory();
        slice.setId(UUID.randomUUID());
        slice.setStatus(EmploymentStatus.ACTIVE);
        slice.setEffectiveFrom(LocalDate.of(2026, 6, 1));
        slice.setCreatedAt(at(2026, 6, 1, 10, 0));
        slice.setReason("Probation completed");
        slice.setChangedBy("hradmin");

        ChangeEvent event = EmployeeChangeHistoryService.toStatusEvent(slice);

        assertThat(event.category()).isEqualTo(EventCategory.STATUS_CHANGE);
        assertThat(event.title()).isEqualTo("Status → ACTIVE");
        assertThat(event.summary()).isEqualTo("Probation completed");
        assertThat(event.actor()).isEqualTo("hradmin");
        assertThat(event.action()).isEqualTo("STATUS_ACTIVE");
    }

    @Test
    void statusEventHandlesNullStatusGracefully() {
        EmployeeStatusHistory slice = new EmployeeStatusHistory();
        slice.setId(UUID.randomUUID());
        slice.setStatus(null);
        slice.setEffectiveFrom(LocalDate.of(2026, 6, 1));
        slice.setCreatedAt(at(2026, 6, 1, 10, 0));
        ChangeEvent event = EmployeeChangeHistoryService.toStatusEvent(slice);
        assertThat(event.title()).isEqualTo("Status → (unknown)");
    }

    // ── toAuditEvent() ──────────────────────────────────────────────────

    @Test
    void auditEventCarriesOldAndNewJson() {
        AuditLog log = newAuditLog("UPDATE");
        log.setOldValue("{\"email\":\"old@x.com\"}");
        log.setNewValue("{\"email\":\"new@x.com\"}");
        ChangeEvent event = EmployeeChangeHistoryService.toAuditEvent(log);

        assertThat(event.category()).isEqualTo(EventCategory.AUDIT);
        assertThat(event.action()).isEqualTo("UPDATE");
        assertThat(event.title()).isEqualTo("Updated");
        assertThat(event.oldValue()).contains("old@x.com");
        assertThat(event.newValue()).contains("new@x.com");
        assertThat(event.effectiveDate()).isNull();   // audit doesn't carry an effective date
    }

    @Test
    void auditEventCreateHasNoOldValue() {
        AuditLog log = newAuditLog("CREATE");
        log.setOldValue(null);
        log.setNewValue("{\"firstName\":\"Ada\"}");
        ChangeEvent event = EmployeeChangeHistoryService.toAuditEvent(log);
        assertThat(event.oldValue()).isNull();
        assertThat(event.newValue()).contains("Ada");
        assertThat(event.title()).isEqualTo("Created");
    }

    @Test
    void auditEventDeleteHasNoNewValue() {
        AuditLog log = newAuditLog("DELETE");
        log.setOldValue("{\"firstName\":\"Ada\"}");
        log.setNewValue(null);
        ChangeEvent event = EmployeeChangeHistoryService.toAuditEvent(log);
        assertThat(event.oldValue()).contains("Ada");
        assertThat(event.newValue()).isNull();
        assertThat(event.title()).isEqualTo("Deleted");
    }

    // ── humanAction() ───────────────────────────────────────────────────

    @Test
    void humanActionMapsKnownActions() {
        assertThat(EmployeeChangeHistoryService.humanAction("CREATE")).isEqualTo("Created");
        assertThat(EmployeeChangeHistoryService.humanAction("UPDATE")).isEqualTo("Updated");
        assertThat(EmployeeChangeHistoryService.humanAction("DELETE")).isEqualTo("Deleted");
        assertThat(EmployeeChangeHistoryService.humanAction("STATUS_CHANGE")).isEqualTo("Status changed");
        assertThat(EmployeeChangeHistoryService.humanAction("REHIRE")).isEqualTo("Rehired");
        assertThat(EmployeeChangeHistoryService.humanAction("TERMINATE")).isEqualTo("Terminated");
    }

    @Test
    void humanActionFallsBackForUnknownAction() {
        assertThat(EmployeeChangeHistoryService.humanAction("ADJUST_OCCUPANCY"))
                .isEqualTo("adjust occupancy");
    }

    @Test
    void humanActionHandlesNull() {
        assertThat(EmployeeChangeHistoryService.humanAction(null)).isEqualTo("Change");
    }

    // ── sortDescending() ────────────────────────────────────────────────

    @Test
    void sortPutsMostRecentFirst() {
        ChangeEvent a = eventAt(at(2026, 1, 1, 0, 0));
        ChangeEvent b = eventAt(at(2026, 6, 1, 0, 0));
        ChangeEvent c = eventAt(at(2026, 3, 1, 0, 0));
        List<ChangeEvent> sorted = EmployeeChangeHistoryService.sortDescending(List.of(a, b, c));
        assertThat(sorted).containsExactly(b, c, a);
    }

    @Test
    void sortIsStableForEmpty() {
        assertThat(EmployeeChangeHistoryService.sortDescending(List.of())).isEmpty();
    }

    @Test
    void sortPutsNullTimesAtTheBottom() {
        // Defensive — a corrupted row mustn't leapfrog real recent changes.
        ChangeEvent withTime = eventAt(at(2026, 1, 1, 0, 0));
        ChangeEvent withoutTime = eventAt(null);
        List<ChangeEvent> sorted = EmployeeChangeHistoryService.sortDescending(
                List.of(withoutTime, withTime));
        assertThat(sorted).containsExactly(withTime, withoutTime);
    }

    @Test
    void sortDoesNotMutateInput() {
        ChangeEvent a = eventAt(at(2026, 1, 1, 0, 0));
        ChangeEvent b = eventAt(at(2026, 6, 1, 0, 0));
        List<ChangeEvent> input = List.of(a, b);
        List<ChangeEvent> sorted = EmployeeChangeHistoryService.sortDescending(input);
        // input still in original order — service returns a new list.
        assertThat(input).containsExactly(a, b);
        assertThat(sorted).containsExactly(b, a);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static EmployeeEmploymentHistory newEmploymentSlice() {
        EmployeeEmploymentHistory slice = new EmployeeEmploymentHistory();
        slice.setId(UUID.randomUUID());
        slice.setEmployeeId(UUID.randomUUID());
        slice.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        slice.setCreatedAt(at(2026, 1, 1, 9, 0));
        return slice;
    }

    private static AuditLog newAuditLog(String action) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setActor("alice");
        log.setModule("CORE_HR");
        log.setEntityName("Employee");
        log.setEntityId(UUID.randomUUID().toString());
        log.setAction(action);
        log.setCreatedAt(at(2026, 6, 1, 14, 30));
        return log;
    }

    private static ChangeEvent eventAt(OffsetDateTime at) {
        return new ChangeEvent(
                EventCategory.AUDIT, at, null, "UPDATE", "title", null,
                "alice", "CORE_HR", "Employee", "id",
                null, null, UUID.randomUUID());
    }

    private static OffsetDateTime at(int y, int m, int d, int h, int min) {
        return OffsetDateTime.of(y, m, d, h, min, 0, 0, ZoneOffset.UTC);
    }
}
