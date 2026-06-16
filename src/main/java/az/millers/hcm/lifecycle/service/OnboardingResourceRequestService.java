package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.AssetRequest;
import az.millers.hcm.corehr.api.dto.AssetResponse;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.corehr.service.EmployeeAssetService;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.UpdateTaskRequest;
import az.millers.hcm.lifecycle.api.dto.ResourceRequestDtos.FulfillRequest;
import az.millers.hcm.lifecycle.api.dto.ResourceRequestDtos.ResourceRequestResponse;
import az.millers.hcm.lifecycle.domain.ChecklistAssignment;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatus;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;
import az.millers.hcm.lifecycle.domain.ItProvisioningKind;
import az.millers.hcm.lifecycle.domain.OnboardingResourceRequest;
import az.millers.hcm.lifecycle.domain.ResourceRequestCategory;
import az.millers.hcm.lifecycle.domain.ResourceRequestStatus;
import az.millers.hcm.lifecycle.event.ChecklistStartedEvent;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.repo.ChecklistAssignmentRepository;
import az.millers.hcm.lifecycle.repo.ChecklistTaskStatusRepository;
import az.millers.hcm.lifecycle.repo.OnboardingResourceRequestRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Onboarding equipment / workspace provisioning requests (M301 — Phase B.1).
 *
 * <p>Reacts to {@link ChecklistStartedEvent}: each typed equipment/workspace
 * task on a starting onboarding spawns a tracked request. IT / Facilities move
 * it REQUESTED → IN_PROGRESS → FULFILLED (or CANCELLED). Fulfilling an
 * equipment request bridges to the M72/M124 asset module — it creates an
 * {@code EmployeeAsset} (the handover), links it back, and marks the
 * originating checklist task DONE via {@link ChecklistService}.
 *
 * <p>The dependency is one-way ({@code ChecklistService} publishes an event;
 * this service consumes it and calls back into {@code ChecklistService}) so
 * there is no bean cycle.
 */
@Service
public class OnboardingResourceRequestService {

    private static final String MODULE = "LIFECYCLE";
    private static final String ENTITY = "OnboardingResourceRequest";

    private final OnboardingResourceRequestRepository requests;
    private final ChecklistTaskStatusRepository taskStatuses;
    private final ChecklistAssignmentRepository assignments;
    private final ChecklistService checklistService;
    private final EmployeeAssetService assetService;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public OnboardingResourceRequestService(OnboardingResourceRequestRepository requests,
                                            ChecklistTaskStatusRepository taskStatuses,
                                            ChecklistAssignmentRepository assignments,
                                            ChecklistService checklistService,
                                            EmployeeAssetService assetService,
                                            EmployeeRepository employees,
                                            AuditService audit,
                                            CurrentRequest currentRequest) {
        this.requests = requests;
        this.taskStatuses = taskStatuses;
        this.assignments = assignments;
        this.checklistService = checklistService;
        this.assetService = assetService;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Auto-create on checklist start ────────────────────────────────────────

    @EventListener
    @Transactional
    public void onChecklistStarted(ChecklistStartedEvent e) {
        if (e.flowType() != ChecklistFlowType.ONBOARDING) return;
        for (ChecklistTaskStatus ts : taskStatuses.findByAssignmentIdOrderByStepOrderAsc(e.assignmentId())) {
            ResourceRequestCategory.forTaskType(ts.getTaskType()).ifPresent(cat -> {
                if (requests.existsByTaskStatusId(ts.getId())) return;
                OnboardingResourceRequest r = new OnboardingResourceRequest();
                r.setRequestNo(String.format("RR-%05d", requests.nextNoSequence()));
                r.setEmployeeId(e.employeeId());
                r.setAssignmentId(e.assignmentId());
                r.setTaskStatusId(ts.getId());
                r.setCategory(cat);
                r.setTitle(ts.getTitle());
                r.setDetails(ts.getDescription());
                r.setStatus(ResourceRequestStatus.REQUESTED);
                r.setCreatedBy(currentRequest.username());
                r.setUpdatedBy(currentRequest.username());
                requests.save(r);
                audit.record(MODULE, ENTITY, r.getId().toString(), "AUTO_CREATE", null,
                        Map.of("category", cat.name(), "taskStatusId", ts.getId().toString(),
                                "employeeId", e.employeeId().toString()));
            });
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ResourceRequestResponse> listOpen() {
        return requests.findByStatusInOrderByRequestedAtAsc(
                        List.of(ResourceRequestStatus.REQUESTED, ResourceRequestStatus.IN_PROGRESS))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ResourceRequestResponse> listForEmployee(UUID employeeId) {
        return requests.findByEmployeeIdOrderByRequestedAtDesc(employeeId)
                .stream().map(this::toResponse).toList();
    }

    // ── Transitions ───────────────────────────────────────────────────────────

    @Transactional
    public ResourceRequestResponse updateStatus(UUID id, String status, String details, String provisioningKind) {
        OnboardingResourceRequest r = mustFind(id);
        ResourceRequestStatus next = parseStatus(status);
        if (next == ResourceRequestStatus.FULFILLED) {
            throw new BadRequestException("Use the fulfil endpoint to mark a request FULFILLED");
        }
        if (r.getStatus() == ResourceRequestStatus.FULFILLED) {
            throw new BadRequestException("Request " + r.getRequestNo() + " is already fulfilled");
        }
        r.setStatus(next);
        if (details != null && !details.isBlank()) {
            r.setDetails(appendNote(r.getDetails(), details));
        }
        if (provisioningKind != null && !provisioningKind.isBlank()) {
            r.setProvisioningKind(parseKind(provisioningKind));
        }
        r.setUpdatedBy(currentRequest.username());
        requests.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "STATUS_" + next, null,
                Map.of("status", next.name()));
        return toResponse(r);
    }

    /**
     * Fulfil a request. For equipment/workspace ({@code createAsset=true}) this
     * creates an {@code EmployeeAsset} for the hire and links it; either way the
     * request becomes FULFILLED and the originating checklist task is marked DONE.
     */
    @Transactional
    public ResourceRequestResponse fulfill(UUID id, FulfillRequest req) {
        OnboardingResourceRequest r = mustFind(id);
        if (r.getStatus() == ResourceRequestStatus.FULFILLED) {
            throw new BadRequestException("Request " + r.getRequestNo() + " is already fulfilled");
        }
        if (r.getStatus() == ResourceRequestStatus.CANCELLED) {
            throw new BadRequestException("Cannot fulfil a cancelled request");
        }

        if (req.createAsset()) {
            if (req.assetType() == null || req.assetName() == null || req.assetName().isBlank()) {
                throw new BadRequestException("assetType and assetName are required to create an asset");
            }
            AssetResponse asset = assetService.assign(r.getEmployeeId(), new AssetRequest(
                    req.assetType(), req.assetName(), req.assetIdentifier(),
                    r.getTitle(), req.assignedAt() != null ? req.assignedAt() : LocalDate.now(),
                    null, null, null, req.notes()));
            r.setAssetId(asset.id());
        }
        // M302 — IT/access request-tracking: record what was provisioned.
        if (req.provisioningKind() != null && !req.provisioningKind().isBlank()) {
            r.setProvisioningKind(parseKind(req.provisioningKind()));
        }
        if (req.provisionedRef() != null && !req.provisionedRef().isBlank()) {
            r.setProvisionedRef(req.provisionedRef().trim());
        }
        r.setStatus(ResourceRequestStatus.FULFILLED);
        r.setFulfilledAt(OffsetDateTime.now());
        if (req.notes() != null && !req.notes().isBlank()) {
            r.setDetails(appendNote(r.getDetails(), req.notes()));
        }
        r.setUpdatedBy(currentRequest.username());
        requests.save(r);

        // Close the originating checklist task so the journey reflects it.
        if (r.getTaskStatusId() != null) {
            try {
                checklistService.updateTask(r.getTaskStatusId(), new UpdateTaskRequest(
                        ChecklistTaskStatusValue.DONE, null, null,
                        "Fulfilled by " + r.getRequestNo()));
            } catch (RuntimeException ex) {
                // Task may already be terminal / cancelled — don't fail the fulfilment.
            }
        }
        audit.record(MODULE, ENTITY, id.toString(), "FULFILL", null,
                Map.of("createdAsset", String.valueOf(req.createAsset()),
                        "assetId", r.getAssetId() == null ? "" : r.getAssetId().toString()));
        return toResponse(r);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OnboardingResourceRequest mustFind(UUID id) {
        return requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource request not found: " + id));
    }

    private ResourceRequestStatus parseStatus(String s) {
        if (s == null || s.isBlank()) throw new BadRequestException("status is required");
        try {
            return ResourceRequestStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown status: " + s);
        }
    }

    private static String appendNote(String existing, String note) {
        return (existing == null || existing.isBlank()) ? note : existing + "\n---\n" + note;
    }

    private ItProvisioningKind parseKind(String s) {
        try {
            return ItProvisioningKind.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown provisioning kind: " + s);
        }
    }

    private ResourceRequestResponse toResponse(OnboardingResourceRequest r) {
        String name = null;
        Optional<Employee> emp = employees.findById(r.getEmployeeId());
        if (emp.isPresent()) {
            Employee e = emp.get();
            name = ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                    + (e.getLastName() == null ? "" : e.getLastName())).trim();
        }
        return new ResourceRequestResponse(
                r.getId(), r.getRequestNo(), r.getEmployeeId(), name,
                r.getAssignmentId(), r.getTaskStatusId(),
                r.getCategory().name(), r.getTitle(), r.getDetails(),
                r.getStatus().name(), r.getAssetId(),
                r.getProvisioningKind() == null ? null : r.getProvisioningKind().name(),
                r.getProvisionedRef(),
                r.getRequestedAt(), r.getFulfilledAt());
    }
}
