package az.millers.hcm.corehr.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.EmployeeIdentification;
import az.millers.hcm.corehr.domain.IdentificationDocumentType;

public interface EmployeeIdentificationRepository
        extends JpaRepository<EmployeeIdentification, UUID> {

    List<EmployeeIdentification> findByEmployeeIdOrderByDocumentTypeAscIssueDateDesc(UUID employeeId);

    List<EmployeeIdentification> findByEmployeeIdAndDocumentType(
            UUID employeeId, IdentificationDocumentType documentType);

    /**
     * Equality match on expiry_date — covers the
     * {@code ExpiryAlertScheduler.findExpiringOn(date)} hot path. The
     * partial index in V51 makes this fast.
     */
    List<EmployeeIdentification> findByExpiryDate(LocalDate expiryDate);
}
