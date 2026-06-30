package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.payroll.domain.PayrollResultComponent;

/**
 * M349 — Per-payslip component snapshot repository.
 */
public interface PayrollResultComponentRepository extends JpaRepository<PayrollResultComponent, UUID> {

    List<PayrollResultComponent> findByResultIdOrderByKindDescAmountDesc(UUID resultId);

    void deleteByResultId(UUID resultId);
}
