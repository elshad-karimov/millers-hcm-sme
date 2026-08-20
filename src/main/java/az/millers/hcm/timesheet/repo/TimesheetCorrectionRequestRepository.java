package az.millers.hcm.timesheet.repo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.timesheet.domain.CorrectionStatus;
import az.millers.hcm.timesheet.domain.TimesheetCorrectionRequest;

public interface TimesheetCorrectionRequestRepository
        extends JpaRepository<TimesheetCorrectionRequest, UUID> {

    List<TimesheetCorrectionRequest> findByTimesheetIdOrderByRequestedAtDesc(UUID timesheetId);

    List<TimesheetCorrectionRequest> findByEmployeeIdOrderByRequestedAtDesc(UUID employeeId);

    List<TimesheetCorrectionRequest> findByStatusOrderByRequestedAtAsc(CorrectionStatus status);

    List<TimesheetCorrectionRequest> findByStatusAndEmployeeIdInOrderByRequestedAtAsc(
            CorrectionStatus status, Collection<UUID> employeeIds);
}
