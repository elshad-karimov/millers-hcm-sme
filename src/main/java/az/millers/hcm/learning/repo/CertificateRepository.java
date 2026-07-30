package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.learning.domain.Certificate;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('learning.certificate_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Optional<Certificate> findByEnrollmentId(UUID enrollmentId);

    List<Certificate> findByEmployeeIdOrderByIssuedAtDesc(UUID employeeId);

    List<Certificate> findAllByOrderByIssuedAtDesc();
}
