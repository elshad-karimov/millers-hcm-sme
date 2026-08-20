package az.millers.hcm.payroll.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExcessAccumulatorRepository extends JpaRepository<ExcessAccumulator, UUID> {

    Optional<ExcessAccumulator> findByEmployeeIdAndPeriodYearAndPeriodSeq(
            UUID employeeId, int periodYear, int periodSeq);

    List<ExcessAccumulator> findByEmployeeIdOrderByPeriodYearDescPeriodSeqDesc(UUID employeeId);
}
