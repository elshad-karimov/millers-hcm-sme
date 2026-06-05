package az.millers.hcm.corehr.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.AssetRequest;
import az.millers.hcm.corehr.api.dto.AssetResponse;
import az.millers.hcm.corehr.api.dto.AssetReturnRequest;
import az.millers.hcm.corehr.domain.AssetEventType;
import az.millers.hcm.corehr.domain.AssetStatus;
import az.millers.hcm.corehr.domain.EmployeeAsset;
import az.millers.hcm.corehr.repo.EmployeeAssetRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * Manages assets issued to employees (M72 / P2-09). Two lifecycle endpoints
 * — assign (create) and return (close out with final status) — plus the
 * standard read / update / delete.
 */
@Service
public class EmployeeAssetService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeAsset";

    private final EmployeeAssetRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;
    /** M124 — append-only paper trail per asset. */
    private final AssetEventService events;

    public EmployeeAssetService(EmployeeAssetRepository repository,
                                 EmployeeRepository employees,
                                 AuditService audit,
                                 AccessScopeService accessScope,
                                 CurrentRequest currentRequest,
                                 AssetEventService events) {
        this.repository = repository;
        this.employees = employees;
        this.audit = audit;
        this.accessScope = accessScope;
        this.currentRequest = currentRequest;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> listFor(UUID employeeId, boolean assignedOnly) {
        ensureEmployeeAccessible(employeeId);
        List<EmployeeAsset> rows = assignedOnly
                ? repository.findByEmployeeIdAndStatusOrderByAssignedAtDesc(employeeId, AssetStatus.ASSIGNED)
                : repository.findByEmployeeIdOrderByAssignedAtDesc(employeeId);
        return rows.stream().map(AssetResponse::from).toList();
    }

    @Transactional
    public AssetResponse assign(UUID employeeId, AssetRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        EmployeeAsset a = new EmployeeAsset();
        a.setEmployeeId(employeeId);
        a.setAssetType(req.assetType());
        a.setAssetName(req.assetName());
        a.setAssetIdentifier(req.assetIdentifier());
        a.setDescription(req.description());
        a.setStatus(AssetStatus.ASSIGNED);
        a.setAssignedAt(req.assignedAt());
        a.setExpectedReturnDate(req.expectedReturnDate());
        a.setConditionAtAssignment(req.conditionAtAssignment());
        a.setCustodyFormUrl(req.custodyFormUrl());
        a.setNotes(req.notes());
        a.setCreatedBy(currentRequest.username());
        a.setUpdatedBy(currentRequest.username());
        EmployeeAsset saved = repository.save(a);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "ASSIGN", null, AssetResponse.from(saved));
        // M124 — log the initial ASSIGN event on the new asset's timeline.
        events.record(saved.getId(), AssetEventType.ASSIGN,
                null, AssetStatus.ASSIGNED, null, employeeId,
                req.conditionAtAssignment(), null);
        return AssetResponse.from(saved);
    }

    @Transactional
    public AssetResponse update(UUID id, AssetRequest req) {
        EmployeeAsset a = loadOrThrow(id);
        AssetResponse before = AssetResponse.from(a);
        a.setAssetType(req.assetType());
        a.setAssetName(req.assetName());
        a.setAssetIdentifier(req.assetIdentifier());
        a.setDescription(req.description());
        a.setAssignedAt(req.assignedAt());
        a.setExpectedReturnDate(req.expectedReturnDate());
        a.setConditionAtAssignment(req.conditionAtAssignment());
        a.setCustodyFormUrl(req.custodyFormUrl());
        a.setNotes(req.notes());
        a.setUpdatedBy(currentRequest.username());
        EmployeeAsset saved = repository.save(a);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, AssetResponse.from(saved));
        return AssetResponse.from(saved);
    }

    /**
     * Close out an assignment — RETURNED / LOST / DAMAGED / WRITTEN_OFF. The
     * DB CHECK in V58 enforces "non-ASSIGNED rows must have returned_at"; the
     * status validation lives here so the API surfaces a friendly 400.
     */
    @Transactional
    public AssetResponse close(UUID id, AssetReturnRequest req) {
        EmployeeAsset a = loadOrThrow(id);
        // M124 — formal state-machine gate replaces the ad-hoc "already
        // closed" check. The state machine knows that RETURNED can move
        // to ASSIGNED (reissue) or WRITTEN_OFF, and that ASSIGNED can
        // move to RETURNED/LOST/DAMAGED/WRITTEN_OFF.
        AssetStateMachine.requireTransition(a.getStatus(), req.status());
        if (req.returnedAt().isBefore(a.getAssignedAt())) {
            throw new BadRequestException("returnedAt cannot be before assignedAt");
        }
        AssetResponse before = AssetResponse.from(a);
        AssetStatus prior = a.getStatus();
        UUID priorHolder = a.getEmployeeId();
        a.setStatus(req.status());
        a.setReturnedAt(req.returnedAt());
        a.setConditionAtReturn(req.conditionAtReturn());
        a.setReturnAcceptedBy(currentRequest.username());
        if (req.notes() != null && !req.notes().isBlank()) {
            String prefix = a.getNotes() == null ? "" : a.getNotes() + "\n---\n";
            a.setNotes(prefix + req.notes());
        }
        a.setUpdatedBy(currentRequest.username());
        EmployeeAsset saved = repository.save(a);
        audit.record(MODULE, ENTITY, id.toString(),
                "CLOSE_" + req.status(), before,
                Map.of("returnedAt", req.returnedAt().toString(),
                        "condition", req.conditionAtReturn() == null ? "" : req.conditionAtReturn()));
        // M124 — append-only timeline entry.
        events.record(id,
                AssetStateMachine.eventTypeFor(prior, req.status()),
                prior, req.status(), priorHolder, null,
                req.conditionAtReturn(), req.notes());
        return AssetResponse.from(saved);
    }

    /**
     * M124 — atomically close out the current assignment and open a new
     * one to a different employee. Captures the transfer as a single
     * REASSIGN event so the timeline reads as one move, not two
     * half-events. Asset must currently be either ASSIGNED (handed back
     * implicitly in this call) or RETURNED.
     */
    @Transactional
    public AssetResponse reissue(UUID id,
                                  UUID newEmployeeId,
                                  java.time.LocalDate effectiveAt,
                                  String conditionAtReturn,
                                  String conditionAtAssignment,
                                  String notes) {
        if (newEmployeeId == null) {
            throw new BadRequestException("newEmployeeId is required");
        }
        if (!employees.existsById(newEmployeeId)) {
            throw new BadRequestException("Employee not found: " + newEmployeeId);
        }
        EmployeeAsset a = loadOrThrow(id);
        AssetStatus prior = a.getStatus();
        UUID priorHolder = a.getEmployeeId();
        // Allowed source states: ASSIGNED (implicit return) and RETURNED.
        if (prior != AssetStatus.ASSIGNED && prior != AssetStatus.RETURNED) {
            throw new BadRequestException(
                    "Reissue requires the asset to be ASSIGNED or RETURNED (was "
                            + prior + ")");
        }
        if (priorHolder != null && priorHolder.equals(newEmployeeId)) {
            throw new BadRequestException(
                    "Cannot reissue to the same employee who currently holds it");
        }
        AssetResponse before = AssetResponse.from(a);
        // Apply: holder + status + dates + conditions.
        a.setEmployeeId(newEmployeeId);
        a.setStatus(AssetStatus.ASSIGNED);
        a.setAssignedAt(effectiveAt);
        a.setReturnedAt(null);
        a.setConditionAtReturn(conditionAtReturn);
        a.setConditionAtAssignment(conditionAtAssignment);
        if (notes != null && !notes.isBlank()) {
            String prefix = a.getNotes() == null ? "" : a.getNotes() + "\n---\n";
            a.setNotes(prefix + notes);
        }
        a.setUpdatedBy(currentRequest.username());
        EmployeeAsset saved = repository.save(a);
        audit.record(MODULE, ENTITY, id.toString(), "REASSIGN", before,
                AssetResponse.from(saved));
        events.record(id, AssetEventType.REASSIGN,
                prior, AssetStatus.ASSIGNED, priorHolder, newEmployeeId,
                conditionAtAssignment, notes);
        return AssetResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeAsset a = loadOrThrow(id);
        AssetResponse before = AssetResponse.from(a);
        repository.delete(a);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    private EmployeeAsset loadOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Asset not found: " + id));
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }
}
