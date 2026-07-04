package az.millers.hcm.attendance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.WorkSchedule;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, UUID> {

    Optional<WorkSchedule> findByCode(String code);

    boolean existsByCode(String code);

    List<WorkSchedule> findByActiveTrueOrderByCodeAsc();
}
