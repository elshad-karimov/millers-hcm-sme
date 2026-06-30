package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.payroll.domain.PayrollResultCostSplit;

public interface PayrollResultCostSplitRepository extends JpaRepository<PayrollResultCostSplit, UUID> {

    List<PayrollResultCostSplit> findByResultId(UUID resultId);

    void deleteByResultId(UUID resultId);
}
