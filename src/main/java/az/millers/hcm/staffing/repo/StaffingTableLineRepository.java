package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.StaffingTableLine;

public interface StaffingTableLineRepository extends JpaRepository<StaffingTableLine, UUID> {

    List<StaffingTableLine> findByStaffingTableIdOrderByLineNoAsc(UUID staffingTableId);

    void deleteByStaffingTableId(UUID staffingTableId);
}
