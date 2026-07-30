package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.AgencyInvoice;
import az.millers.hcm.recruitment.domain.InvoiceStatus;

public interface AgencyInvoiceRepository extends JpaRepository<AgencyInvoice, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('recruitment.agency_invoice_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<AgencyInvoice> findAllByOrderByCreatedAtDesc();

    List<AgencyInvoice> findByStatusOrderByCreatedAtDesc(InvoiceStatus status);

    List<AgencyInvoice> findByAgencyIdOrderByCreatedAtDesc(UUID agencyId);

    boolean existsBySubmissionId(UUID submissionId);
}
