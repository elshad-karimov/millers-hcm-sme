package az.millers.hcm.timesheet.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.timesheet.domain.TimeCategory;

public interface TimeCategoryRepository extends JpaRepository<TimeCategory, UUID> {

    List<TimeCategory> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<TimeCategory> findByCode(String code);
}
