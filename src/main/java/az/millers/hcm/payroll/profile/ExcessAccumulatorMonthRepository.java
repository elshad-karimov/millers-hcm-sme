package az.millers.hcm.payroll.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExcessAccumulatorMonthRepository
        extends JpaRepository<ExcessAccumulatorMonth, UUID> {

    List<ExcessAccumulatorMonth> findByAccumulatorIdOrderByPeriodYearAscPeriodMonthAsc(
            UUID accumulatorId);

    Optional<ExcessAccumulatorMonth> findByAccumulatorIdAndPeriodYearAndPeriodMonth(
            UUID accumulatorId, int periodYear, int periodMonth);
}
