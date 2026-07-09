package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import az.millers.hcm.payroll.domain.LoanRequest;
import az.millers.hcm.payroll.domain.LoanRequestStatus;

@Repository
public interface LoanRequestRepository extends JpaRepository<LoanRequest, UUID> {
    List<LoanRequest> findByTenantIdAndStatusOrderByRequestedAtDesc(String tenantId, LoanRequestStatus status);
    List<LoanRequest> findByTenantIdOrderByRequestedAtDesc(String tenantId);
    List<LoanRequest> findByEmployeeIdOrderByRequestedAtDesc(UUID employeeId);
    boolean existsByEmployeeIdAndStatusIn(UUID employeeId, List<LoanRequestStatus> statuses);
}
