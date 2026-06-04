package az.millers.hcm.businesstrip.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.businesstrip.domain.ClaimStatus;
import az.millers.hcm.businesstrip.domain.ExpenseClaim;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID> {

    List<ExpenseClaim> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<ExpenseClaim> findByTripIdOrderByCreatedAtDesc(UUID tripId);

    List<ExpenseClaim> findByStatusOrderByCreatedAtDesc(ClaimStatus status);

    Optional<ExpenseClaim> findByClaimNo(String claimNo);
}
