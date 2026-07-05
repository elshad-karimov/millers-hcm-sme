package az.millers.hcm.businesstrip.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.businesstrip.domain.ReimbursementBatchItem;

public interface ReimbursementBatchItemRepository extends JpaRepository<ReimbursementBatchItem, UUID> {

    List<ReimbursementBatchItem> findByBatchId(UUID batchId);

    boolean existsByExpenseClaimId(UUID expenseClaimId);

    @Query("""
        SELECT i FROM ReimbursementBatchItem i
        JOIN ReimbursementBatch b ON i.batchId = b.id
        WHERE i.expenseClaimId = :claimId
          AND b.status IN ('DRAFT', 'APPROVED')
    """)
    List<ReimbursementBatchItem> findLiveItemsByClaimId(@Param("claimId") UUID claimId);
}
