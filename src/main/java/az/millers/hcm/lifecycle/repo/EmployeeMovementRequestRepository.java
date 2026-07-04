package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import az.millers.hcm.lifecycle.domain.EmployeeMovementRequest;

@Repository
public interface EmployeeMovementRequestRepository extends JpaRepository<EmployeeMovementRequest, UUID> {

    List<EmployeeMovementRequest> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
