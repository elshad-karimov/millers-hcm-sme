package az.millers.hcm.corehr.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.corehr.domain.AssetEvent;
import az.millers.hcm.corehr.domain.AssetEventType;
import az.millers.hcm.corehr.domain.AssetStatus;
import az.millers.hcm.corehr.repo.AssetEventRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M124 — append-only writer + reader for {@link AssetEvent}. Kept
 * separate from {@link EmployeeAssetService} so the lifecycle service
 * can stay focused on state transitions; this service handles all the
 * row-shape concerns.
 */
@Service
public class AssetEventService {

    private final AssetEventRepository repo;
    private final CurrentRequest currentRequest;

    public AssetEventService(AssetEventRepository repo,
                             CurrentRequest currentRequest) {
        this.repo = repo;
        this.currentRequest = currentRequest;
    }

    /**
     * Persist a new event row. The caller has already validated the
     * transition and applied it to the asset entity; this is the
     * paper-trail.
     */
    @Transactional
    public AssetEvent record(UUID assetId,
                             AssetEventType type,
                             AssetStatus previousStatus,
                             AssetStatus newStatus,
                             UUID previousEmployeeId,
                             UUID newEmployeeId,
                             String condition,
                             String notes) {
        AssetEvent e = new AssetEvent();
        e.setAssetId(assetId);
        e.setEventType(type);
        e.setActor(currentRequest.username());
        e.setPreviousStatus(previousStatus);
        e.setNewStatus(newStatus);
        e.setPreviousEmployeeId(previousEmployeeId);
        e.setNewEmployeeId(newEmployeeId);
        e.setCondition(condition);
        e.setNotes(notes);
        return repo.save(e);
    }

    @Transactional(readOnly = true)
    public List<AssetEvent> historyFor(UUID assetId) {
        return repo.findByAssetIdOrderByOccurredAtDesc(assetId);
    }
}
