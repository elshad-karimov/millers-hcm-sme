package az.millers.hcm.organization.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.organization.domain.OrgUnitHistory;

public interface OrgUnitHistoryRepository extends JpaRepository<OrgUnitHistory, UUID> {

    /** Per-unit lifecycle, newest first — backs the Org page "History" panel. */
    List<OrgUnitHistory> findByOrgUnitIdOrderByChangedAtDesc(UUID orgUnitId);

    /** Recent activity across a whole version — for the dashboard. */
    List<OrgUnitHistory> findTop100ByVersionIdOrderByChangedAtDesc(UUID versionId);
}
