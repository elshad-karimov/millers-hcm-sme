package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.ChangeReason;

public interface ChangeReasonRepository extends JpaRepository<ChangeReason, UUID> {

    List<ChangeReason> findByTenantIdAndIsActiveTrue(String tenantId);

    Optional<ChangeReason> findByTenantIdAndCode(String tenantId, String code);
}
