package az.millers.hcm.organization.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.organization.domain.OrgUnitHistory;
import az.millers.hcm.organization.domain.OrgUnitHistory.ChangeKind;
import az.millers.hcm.organization.repo.OrgUnitHistoryRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Records append-only per-unit history (M81). The {@code before / after}
 * snapshots are JSONB blobs the SPA renders inline on the Org page.
 *
 * <p>Designed for low-friction integration — the existing
 * {@code OrgStructureService} calls {@link #record(UUID, UUID, ChangeKind,
 * Object, Object, String)} from the same transaction as the mutation
 * itself, so a rollback also rolls back the history row.
 */
@Service
public class OrgUnitHistoryService {

    private final OrgUnitHistoryRepository repo;
    private final CurrentRequest currentRequest;

    public OrgUnitHistoryService(OrgUnitHistoryRepository repo,
                                  CurrentRequest currentRequest) {
        this.repo = repo;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public OrgUnitHistory record(UUID orgUnitId, UUID versionId, ChangeKind kind,
                                  Object before, Object after, String reason) {
        OrgUnitHistory h = new OrgUnitHistory();
        h.setOrgUnitId(orgUnitId);
        h.setVersionId(versionId);
        h.setChangeKind(kind);
        h.setBeforeValue(before);
        h.setAfterValue(after);
        h.setChangeReason(reason);
        h.setChangedBy(currentRequest.username());
        return repo.save(h);
    }

    @Transactional(readOnly = true)
    public List<OrgUnitHistory> historyOf(UUID orgUnitId) {
        return repo.findByOrgUnitIdOrderByChangedAtDesc(orgUnitId);
    }

    @Transactional(readOnly = true)
    public List<OrgUnitHistory> recentInVersion(UUID versionId) {
        return repo.findTop100ByVersionIdOrderByChangedAtDesc(versionId);
    }
}
