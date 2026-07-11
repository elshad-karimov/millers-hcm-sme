package az.millers.hcm.lifecycle.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.AssetEventType;
import az.millers.hcm.corehr.domain.AssetStatus;
import az.millers.hcm.corehr.domain.EmployeeAsset;
import az.millers.hcm.corehr.repo.EmployeeAssetRepository;
import az.millers.hcm.corehr.service.AssetEventService;
import az.millers.hcm.lifecycle.domain.AssetTransfer;
import az.millers.hcm.lifecycle.domain.AssetTransferStatus;
import az.millers.hcm.lifecycle.repo.AssetTransferRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

/**
 * M457 — Asset transfer service (simple HR-approve flow → reassign asset + event history).
 */
@Service
public class AssetTransferService {
    private static final String TENANT = "default";
    private static final String MODULE = "lifecycle";
    private static final String ENTITY = "AssetTransfer";

    private final AssetTransferRepository repository;
    private final EmployeeAssetRepository assetRepo;
    private final AssetEventService eventService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final NamedParameterJdbcTemplate jdbc;

    public AssetTransferService(AssetTransferRepository repository, EmployeeAssetRepository assetRepo,
                                AssetEventService eventService, AuditService audit, CurrentRequest currentRequest,
                                NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.assetRepo = assetRepo;
        this.eventService = eventService;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AssetTransfer> listPending() {
        return repository.findByTenantIdAndStatusOrderByRequestedAtDesc(TENANT, AssetTransferStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<AssetTransfer> listAll() {
        return repository.findByTenantIdOrderByRequestedAtDesc(TENANT);
    }

    @Transactional(readOnly = true)
    public AssetTransfer get(UUID id) {
        return repository.findById(id)
                .filter(t -> TENANT.equals(t.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));
    }

    @Transactional
    public AssetTransfer request(UUID assetId, UUID fromEmployeeId, UUID toEmployeeId, String reason) {
        EmployeeAsset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new BadRequestException("Asset not found"));
        if (!asset.getEmployeeId().equals(fromEmployeeId)) {
            throw new BadRequestException("Asset not currently assigned to from_employee");
        }
        if (asset.getStatus() != AssetStatus.ASSIGNED) {
            throw new BadRequestException("Asset must be ASSIGNED to transfer");
        }

        String transferNo = BusinessNumbers.format("AT", 5, jdbc.queryForObject("SELECT nextval('lifecycle.asset_transfer_no_seq')", Map.of(), Long.class));

        AssetTransfer transfer = new AssetTransfer();
        transfer.setTenantId(TENANT);
        transfer.setTransferNo(transferNo);
        transfer.setAssetId(assetId);
        transfer.setFromEmployeeId(fromEmployeeId);
        transfer.setToEmployeeId(toEmployeeId);
        transfer.setReason(reason);
        transfer.setStatus(AssetTransferStatus.PENDING);
        transfer.setRequestedBy(currentRequest.username());
        transfer.setCreatedBy(currentRequest.username());
        transfer.setUpdatedBy(currentRequest.username());
        AssetTransfer saved = repository.save(transfer);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "REQUEST", null, saved);
        return saved;
    }

    @Transactional
    public AssetTransfer approve(UUID id) {
        AssetTransfer transfer = get(id);
        if (transfer.getStatus() != AssetTransferStatus.PENDING) {
            throw new BadRequestException("Transfer not pending");
        }

        // Block self-approval
        String currentUsername = currentRequest.username();
        if (currentUsername != null && currentUsername.equals(transfer.getRequestedBy())) {
            throw new BadRequestException("Cannot approve your own asset transfer request");
        }

        transfer.setStatus(AssetTransferStatus.APPROVED);
        transfer.setApprovedBy(currentRequest.username());
        transfer.setApprovedAt(OffsetDateTime.now());
        transfer.setUpdatedBy(currentRequest.username());
        AssetTransfer saved = repository.save(transfer);
        audit.record(MODULE, ENTITY, id.toString(), "APPROVE", null, Map.of("approvedBy", currentRequest.username()));
        return saved;
    }

    @Transactional
    public AssetTransfer complete(UUID id) {
        AssetTransfer transfer = get(id);
        if (transfer.getStatus() != AssetTransferStatus.APPROVED) {
            throw new BadRequestException("Transfer must be approved before completion");
        }

        // Reassign asset via existing EmployeeAssetService.reissue pattern
        EmployeeAsset asset = assetRepo.findById(transfer.getAssetId())
                .orElseThrow(() -> new BadRequestException("Asset not found"));
        asset.setEmployeeId(transfer.getToEmployeeId());
        asset.setStatus(AssetStatus.ASSIGNED);
        asset.setAssignedAt(java.time.LocalDate.now());
        asset.setReturnedAt(null);
        asset.setUpdatedBy(currentRequest.username());
        assetRepo.save(asset);

        // Event history on both sides
        eventService.record(asset.getId(), AssetEventType.REASSIGN, AssetStatus.ASSIGNED, AssetStatus.ASSIGNED,
                transfer.getFromEmployeeId(), transfer.getToEmployeeId(), null, transfer.getReason());

        transfer.setStatus(AssetTransferStatus.COMPLETED);
        transfer.setCompletedAt(OffsetDateTime.now());
        transfer.setUpdatedBy(currentRequest.username());
        AssetTransfer saved = repository.save(transfer);
        audit.record(MODULE, ENTITY, id.toString(), "COMPLETE", null, Map.of("completedAt", OffsetDateTime.now()));
        return saved;
    }

    @Transactional
    public AssetTransfer reject(UUID id, String reason) {
        AssetTransfer transfer = get(id);
        if (transfer.getStatus() != AssetTransferStatus.PENDING) {
            throw new BadRequestException("Only pending transfers can be rejected");
        }
        transfer.setStatus(AssetTransferStatus.REJECTED);
        transfer.setNotes(reason);
        transfer.setUpdatedBy(currentRequest.username());
        AssetTransfer saved = repository.save(transfer);
        audit.record(MODULE, ENTITY, id.toString(), "REJECT", null, Map.of("reason", reason));
        return saved;
    }
}
