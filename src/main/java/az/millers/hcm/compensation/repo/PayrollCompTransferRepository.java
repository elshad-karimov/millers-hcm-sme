package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.PayrollCompTransfer;

public interface PayrollCompTransferRepository extends JpaRepository<PayrollCompTransfer, UUID> {

    List<PayrollCompTransfer> findByTenantIdOrderByTransferredAtDesc(String tenantId);

    List<PayrollCompTransfer> findByTenantIdAndStatusOrderByTransferredAtDesc(String tenantId, String status);

    Optional<PayrollCompTransfer> findBySourceTypeAndSourceId(String sourceType, UUID sourceId);

    List<PayrollCompTransfer> findByTargetRunId(UUID targetRunId);
}
