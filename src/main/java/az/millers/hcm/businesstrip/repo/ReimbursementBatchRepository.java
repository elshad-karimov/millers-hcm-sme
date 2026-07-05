package az.millers.hcm.businesstrip.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.businesstrip.domain.ReimbursementBatch;
import az.millers.hcm.businesstrip.domain.ReimbursementBatchStatus;

public interface ReimbursementBatchRepository extends JpaRepository<ReimbursementBatch, UUID> {

    @Query(value = "SELECT nextval('business_trip.reimbursement_batch_no_seq')", nativeQuery = true)
    long nextBatchNoSequence();

    Optional<ReimbursementBatch> findByIdAndTenantId(UUID id, String tenantId);

    List<ReimbursementBatch> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<ReimbursementBatch> findByTenantIdAndStatusOrderByCreatedAtDesc(
            String tenantId, ReimbursementBatchStatus status);
}
