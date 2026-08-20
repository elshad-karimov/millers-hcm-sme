package az.millers.hcm.corehr.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import az.millers.hcm.corehr.domain.EmployeeStatusHistory;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeEmploymentHistoryRepository;
import az.millers.hcm.corehr.repo.EmployeeStatusHistoryRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Pins the ORDER of statements when a status slice is superseded.
 *
 * These tables carry a partial unique index — one open row per employee — and
 * Hibernate orders a flush as inserts, then updates, then deletes. So closing
 * the prior slice and inserting the new one in the same flush sends the INSERT
 * first, while the prior row is still open, and Postgres rejects it:
 *
 *   duplicate key value violates unique constraint
 *   "uq_emp_status_hist_one_open_per_employee"
 *
 * which reached a user as an unexplained failure on "Change employment status".
 * The fix is a flush between the two, and since nothing about the Java reads as
 * order-dependent it is exactly the kind of thing a later edit would drop —
 * hence a test on the ordering itself rather than on the outcome.
 */
class EmployeeHistorySliceOrderTest {

    private final EmployeeEmploymentHistoryRepository employmentHistory =
            mock(EmployeeEmploymentHistoryRepository.class);
    private final EmployeeStatusHistoryRepository statusHistory =
            mock(EmployeeStatusHistoryRepository.class);
    private final CurrentRequest currentRequest = mock(CurrentRequest.class);

    private final EmployeeHistoryService service =
            new EmployeeHistoryService(employmentHistory, statusHistory, currentRequest);

    private static final UUID EMPLOYEE = UUID.randomUUID();

    @Test
    @DisplayName("the prior slice is closed AND flushed before the new one is written")
    void closesAndFlushesBeforeInserting() {
        EmployeeStatusHistory open = sliceFrom(LocalDate.of(2024, 2, 8));
        when(statusHistory.findOpenForEmployee(EMPLOYEE)).thenReturn(Optional.of(open));

        service.recordStatusSlice(EMPLOYEE, EmploymentStatus.ACTIVE,
                LocalDate.of(2026, 8, 20), "probation ended", "CORE_HR", "Employee", null);

        InOrder order = inOrder(statusHistory);
        order.verify(statusHistory).save(open);   // close the prior row
        order.verify(statusHistory).flush();      // ...and get it into the DB
        order.verify(statusHistory).save(any());  // only then insert the new one
    }

    @Test
    @DisplayName("a same-day re-transition deletes and flushes before the new one is written")
    void deletesAndFlushesBeforeInserting() {
        LocalDate sameDay = LocalDate.of(2026, 8, 20);
        EmployeeStatusHistory open = sliceFrom(sameDay);
        when(statusHistory.findOpenForEmployee(EMPLOYEE)).thenReturn(Optional.of(open));

        service.recordStatusSlice(EMPLOYEE, EmploymentStatus.ACTIVE,
                sameDay, "corrected same day", "CORE_HR", "Employee", null);

        // Deletes are flushed LAST of all by Hibernate, so this branch needs the
        // flush just as much as the close branch does.
        InOrder order = inOrder(statusHistory);
        order.verify(statusHistory).delete(open);
        order.verify(statusHistory).flush();
        order.verify(statusHistory).save(any());
    }

    @Test
    @DisplayName("with no prior open slice there is nothing to flush")
    void firstSliceNeedsNoFlush() {
        when(statusHistory.findOpenForEmployee(EMPLOYEE)).thenReturn(Optional.empty());

        service.recordStatusSlice(EMPLOYEE, EmploymentStatus.ON_PROBATION,
                LocalDate.of(2026, 8, 20), "Hired", "CORE_HR", "Employee", null);

        InOrder order = inOrder(statusHistory);
        order.verify(statusHistory).save(any());
        order.verifyNoMoreInteractions();
    }

    private static EmployeeStatusHistory sliceFrom(LocalDate from) {
        EmployeeStatusHistory slice = new EmployeeStatusHistory();
        slice.setEmployeeId(EMPLOYEE);
        slice.setStatus(EmploymentStatus.ON_PROBATION);
        slice.setEffectiveFrom(from);
        slice.setEffectiveTo(null);
        return slice;
    }
}
