package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.EmployeeReward;

public interface EmployeeRewardRepository
        extends JpaRepository<EmployeeReward, UUID> {

    /** Most-recent first — the recognition timeline. */
    List<EmployeeReward> findByEmployeeIdOrderByAwardedAtDesc(UUID employeeId);
}
