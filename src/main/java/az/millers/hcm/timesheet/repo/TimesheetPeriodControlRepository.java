package az.millers.hcm.timesheet.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.timesheet.domain.TimesheetPeriodControl;

public interface TimesheetPeriodControlRepository
        extends JpaRepository<TimesheetPeriodControl, UUID> {

    Optional<TimesheetPeriodControl> findByPeriodYearAndPeriodMonth(int year, int month);
}
