package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import az.millers.hcm.payroll.domain.LoanType;

@Repository
public interface LoanTypeRepository extends JpaRepository<LoanType, UUID> {
    List<LoanType> findByTenantIdAndActiveOrderByName(String tenantId, Boolean active);
    List<LoanType> findByTenantIdOrderByName(String tenantId);
}
