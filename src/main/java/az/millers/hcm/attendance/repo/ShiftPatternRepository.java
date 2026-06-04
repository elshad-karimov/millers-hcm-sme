package az.millers.hcm.attendance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.ShiftPattern;

public interface ShiftPatternRepository extends JpaRepository<ShiftPattern, UUID> {

    boolean existsByCode(String code);

    List<ShiftPattern> findAllByOrderByNameAsc();

    List<ShiftPattern> findByActiveTrueOrderByNameAsc();
}
