package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.payroll.domain.AnnualTaxCertificate;

public interface AnnualTaxCertificateRepository extends JpaRepository<AnnualTaxCertificate, UUID> {

    List<AnnualTaxCertificate> findByYear(int year);

    Optional<AnnualTaxCertificate> findByEmployeeIdAndYear(UUID employeeId, int year);

    List<AnnualTaxCertificate> findByEmployeeId(UUID employeeId);
}
