package az.millers.hcm.attendance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.attendance.domain.AttendancePayrollSummary;

public interface AttendancePayrollSummaryRepository
        extends JpaRepository<AttendancePayrollSummary, UUID> {

    List<AttendancePayrollSummary> findByPayrollRunIdOrderByEmployeeIdAsc(UUID payrollRunId);

    @Modifying
    @Query("DELETE FROM AttendancePayrollSummary s WHERE s.payrollRunId = :runId")
    void deleteByPayrollRunId(UUID runId);
}
