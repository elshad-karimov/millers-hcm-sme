package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.PositionOccupancy;

public interface PositionOccupancyRepository extends JpaRepository<PositionOccupancy, UUID> {

    List<PositionOccupancy> findByPositionIdOrderByStartDateDesc(UUID positionId);
    List<PositionOccupancy> findByEmployeeIdOrderByStartDateDesc(UUID employeeId);
    List<PositionOccupancy> findByPositionIdAndEndDateIsNull(UUID positionId);
    List<PositionOccupancy> findByEmployeeIdAndEndDateIsNull(UUID employeeId);
}
