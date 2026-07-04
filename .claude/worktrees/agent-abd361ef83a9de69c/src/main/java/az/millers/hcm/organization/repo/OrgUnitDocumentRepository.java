package az.millers.hcm.organization.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.organization.domain.OrgUnitDocument;

public interface OrgUnitDocumentRepository extends JpaRepository<OrgUnitDocument, UUID> {

    List<OrgUnitDocument> findByOrgUnitIdOrderByCreatedAtDesc(UUID orgUnitId);

    /** Expiry scanner — called once per day per alert-window delta by ExpiryAlertScheduler. */
    List<OrgUnitDocument> findByExpiryDate(LocalDate expiryDate);
}
