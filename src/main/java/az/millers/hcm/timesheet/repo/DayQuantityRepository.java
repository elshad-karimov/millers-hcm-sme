package az.millers.hcm.timesheet.repo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.timesheet.domain.DayQuantity;

public interface DayQuantityRepository extends JpaRepository<DayQuantity, UUID> {

    List<DayQuantity> findByTimesheetDayId(UUID timesheetDayId);

    List<DayQuantity> findByTimesheetDayIdIn(Collection<UUID> timesheetDayIds);

    void deleteByTimesheetDayId(UUID timesheetDayId);
}
