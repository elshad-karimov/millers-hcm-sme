package az.millers.hcm.lifecycle.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.AssetStatus;
import az.millers.hcm.corehr.domain.EmployeeAsset;
import az.millers.hcm.corehr.repo.EmployeeAssetRepository;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.AssetReturnResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.AssetReturnUpdateRequest;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.ClearanceSignOffRequest;
import az.millers.hcm.lifecycle.domain.AssetReturnStatus;
import az.millers.hcm.lifecycle.domain.ClearanceDepartment;
import az.millers.hcm.lifecycle.domain.ClearanceStatus;
import az.millers.hcm.lifecycle.domain.OffboardingAssetReturn;
import az.millers.hcm.lifecycle.event.OffboardingCaseActivatedEvent;
import az.millers.hcm.lifecycle.repo.OffboardingAssetReturnRepository;

@Service
public class OffboardingAssetReturnService {

    private static final Logger log = LoggerFactory.getLogger(OffboardingAssetReturnService.class);

    private static final List<AssetReturnStatus> TERMINAL_STATUSES = List.of(
            AssetReturnStatus.RETURNED, AssetReturnStatus.RETURNED_DAMAGED,
            AssetReturnStatus.DEDUCTION_APPROVED, AssetReturnStatus.WRITTEN_OFF,
            AssetReturnStatus.WAIVED);

    private final OffboardingAssetReturnRepository repo;
    private final EmployeeAssetRepository assetRepo;
    private final OffboardingClearanceService clearanceService;

    public OffboardingAssetReturnService(OffboardingAssetReturnRepository repo,
                                          EmployeeAssetRepository assetRepo,
                                          OffboardingClearanceService clearanceService) {
        this.repo = repo;
        this.assetRepo = assetRepo;
        this.clearanceService = clearanceService;
    }

    @EventListener
    @Transactional
    public void onCaseActivated(OffboardingCaseActivatedEvent event) {
        try {
            List<EmployeeAsset> assigned = assetRepo.findByEmployeeIdAndStatusOrderByAssignedAtDesc(
                    event.employeeId(), AssetStatus.ASSIGNED);
            for (EmployeeAsset asset : assigned) {
                repo.findByCaseIdAndEmployeeAssetId(event.caseId(), asset.getId()).ifPresentOrElse(
                        existing -> {},
                        () -> {
                            OffboardingAssetReturn row = new OffboardingAssetReturn();
                            row.setCaseId(event.caseId());
                            row.setEmployeeAssetId(asset.getId());
                            row.setAssetType(asset.getAssetType().name());
                            row.setAssetName(asset.getAssetName());
                            row.setAssetIdentifier(asset.getAssetIdentifier());
                            row.setReturnStatus(AssetReturnStatus.PENDING_RETURN);
                            repo.save(row);
                        });
            }
            log.info("Seeded {} asset return rows for case {}", assigned.size(), event.caseId());
        } catch (Exception e) {
            log.warn("Failed to seed asset return rows for case {}: {}", event.caseId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AssetReturnResponse> getReturns(UUID caseId) {
        return repo.findByCaseIdOrderByAssetType(caseId).stream().map(AssetReturnResponse::from).toList();
    }

    @Transactional
    public AssetReturnResponse updateReturn(UUID returnId, AssetReturnUpdateRequest request) {
        OffboardingAssetReturn row = repo.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset return not found: " + returnId));
        if (request.returnStatus() != null) row.setReturnStatus(request.returnStatus());
        if (request.returnDate() != null) row.setReturnDate(request.returnDate());
        if (request.conditionAtReturn() != null) row.setConditionAtReturn(request.conditionAtReturn());
        row.setDeductionAmount(request.deductionAmount());
        if (request.notes() != null) row.setNotes(request.notes());
        AssetReturnResponse resp = AssetReturnResponse.from(repo.save(row));
        tryAutoUpdateAssetClearance(row.getCaseId());
        return resp;
    }

    private void tryAutoUpdateAssetClearance(UUID caseId) {
        boolean allResolved = !repo.existsByCaseIdAndReturnStatusNotIn(caseId, TERMINAL_STATUSES);
        if (!allResolved) return;
        boolean anyDeduction = repo.findByCaseIdOrderByAssetType(caseId).stream()
                .anyMatch(r -> r.getDeductionAmount() != null && r.getDeductionAmount().signum() > 0);
        ClearanceStatus cs = anyDeduction ? ClearanceStatus.CLEARED_WITH_DEDUCTION : ClearanceStatus.CLEARED;
        try {
            clearanceService.signOff(caseId, ClearanceDepartment.ASSET,
                    new ClearanceSignOffRequest(cs, null, "Auto-cleared: all assets resolved"));
        } catch (Exception e) {
            log.warn("Could not auto-update ASSET clearance for case {}: {}", caseId, e.getMessage());
        }
    }
}
