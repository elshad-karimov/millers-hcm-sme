package az.millers.hcm.employeerelations.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.employeerelations.domain.ErCase;
import az.millers.hcm.employeerelations.domain.ErCaseStatus;

/**
 * M445 — ER case repository.
 */
public interface ErCaseRepository extends JpaRepository<ErCase, UUID> {

    Optional<ErCase> findByIdAndTenantId(UUID id, String tenantId);

    List<ErCase> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<ErCase> findByTenantIdAndStatusInOrderByCreatedAtDesc(String tenantId, List<ErCaseStatus> statuses);

    List<ErCase> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);
}
