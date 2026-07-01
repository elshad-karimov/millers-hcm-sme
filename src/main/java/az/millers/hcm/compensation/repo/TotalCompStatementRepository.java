package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.TotalCompStatement;

public interface TotalCompStatementRepository extends JpaRepository<TotalCompStatement, UUID> {

    Optional<TotalCompStatement> findByTenantIdAndEmployeeIdAndYear(String tenantId, UUID employeeId, int year);

    List<TotalCompStatement> findByTenantIdAndYearOrderByEmployeeIdAsc(String tenantId, int year);

    List<TotalCompStatement> findByEmployeeIdAndStatusOrderByYearDesc(UUID employeeId, String status);
}
