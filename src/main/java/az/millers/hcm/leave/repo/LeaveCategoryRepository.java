package az.millers.hcm.leave.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.leave.domain.LeaveCategory;

public interface LeaveCategoryRepository extends JpaRepository<LeaveCategory, UUID> {

    List<LeaveCategory> findByActiveTrueOrderByCodeAsc();

    Optional<LeaveCategory> findByCode(String code);
}
