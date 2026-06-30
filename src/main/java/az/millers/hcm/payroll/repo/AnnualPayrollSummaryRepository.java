package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.payroll.domain.AnnualPayrollSummary;

public interface AnnualPayrollSummaryRepository extends JpaRepository<AnnualPayrollSummary, UUID> {

    List<AnnualPayrollSummary> findByYear(int year);

    Optional<AnnualPayrollSummary> findByEmployeeIdAndYear(UUID employeeId, int year);

    List<AnnualPayrollSummary> findByEmployeeId(UUID employeeId);
}
