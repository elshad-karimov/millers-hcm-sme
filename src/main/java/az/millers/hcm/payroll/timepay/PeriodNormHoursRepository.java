package az.millers.hcm.payroll.timepay;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodNormHoursRepository extends JpaRepository<PeriodNormHours, UUID> {

    Optional<PeriodNormHours> findByPeriodYearAndPeriodMonth(int year, int month);
}
