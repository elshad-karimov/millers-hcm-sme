package az.millers.hcm.timesheet.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.timesheet.domain.TimesheetDay;

public interface TimesheetDayRepository extends JpaRepository<TimesheetDay, UUID> {

    List<TimesheetDay> findByTimesheetIdOrderByWorkDateAsc(UUID timesheetId);

    void deleteByTimesheetId(UUID timesheetId);
}
