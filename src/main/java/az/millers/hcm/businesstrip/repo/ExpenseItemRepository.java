package az.millers.hcm.businesstrip.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.businesstrip.domain.ExpenseItem;

public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, UUID> {

    List<ExpenseItem> findByClaimIdOrderByItemDateAscCategoryAsc(UUID claimId);

    void deleteByClaimId(UUID claimId);
}
