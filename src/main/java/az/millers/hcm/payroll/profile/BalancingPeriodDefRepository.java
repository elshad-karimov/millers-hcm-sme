package az.millers.hcm.payroll.profile;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BalancingPeriodDefRepository extends JpaRepository<BalancingPeriodDef, UUID> {

    List<BalancingPeriodDef> findBySchemeCodeOrderByPeriodSeqAsc(String schemeCode);
}
