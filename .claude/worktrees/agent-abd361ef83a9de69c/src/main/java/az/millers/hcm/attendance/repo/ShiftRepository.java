package az.millers.hcm.attendance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.Shift;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    boolean existsByCode(String code);

    List<Shift> findAllByOrderByNameAsc();

    List<Shift> findByActiveTrueOrderByNameAsc();
}
