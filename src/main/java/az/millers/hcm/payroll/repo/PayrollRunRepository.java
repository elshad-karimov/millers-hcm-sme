package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.payroll.domain.PayrollRun;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

    @Query(value = "SELECT nextval('payroll.run_no_seq')", nativeQuery = true)
    long nextRunNoSequence();

    Optional<PayrollRun> findByPeriodYearAndPeriodMonthAndJurisdiction(
            int year, int month, String jurisdiction);

    List<PayrollRun> findAllByOrderByPeriodYearDescPeriodMonthDesc();
}
