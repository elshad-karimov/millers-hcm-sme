package az.millers.hcm.permission.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.permission.api.dto.PermissionRequestResponse;
import az.millers.hcm.permission.api.dto.PermissionSubmitRequest;
import az.millers.hcm.permission.domain.PermissionRequest;
import az.millers.hcm.permission.domain.PermissionRequestStatus;
import az.millers.hcm.permission.domain.PermissionType;
import az.millers.hcm.permission.repo.PermissionRequestRepository;
import az.millers.hcm.permission.repo.PermissionTypeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;

@Service
public class PermissionRequestService {

    public static final String WORKFLOW_DEFINITION = "PERMISSION_APPROVAL";
    private static final String MODULE = "PERMISSION";
    private static final String ENTITY = "PermissionRequest";

    private final PermissionRequestRepository requests;
    private final PermissionTypeRepository types;
    private final PermissionBalanceService balances;
    private final EmployeeRepository employees;
    private final WorkflowService workflowService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final AccessScopeService accessScope;

    public PermissionRequestService(PermissionRequestRepository requests,
                                     PermissionTypeRepository types,
                                     PermissionBalanceService balances,
                                     EmployeeRepository employees,
                                     WorkflowService workflowService,
                                     AuditService audit,
                                     CurrentRequest currentRequest,
                                     AccessScopeService accessScope) {
        this.requests = requests;
        this.types = types;
        this.balances = balances;
        this.employees = employees;
        this.workflowService = workflowService;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.accessScope = accessScope;
    }

    @Transactional(readOnly = true)
    public PermissionRequest get(UUID id) {
        PermissionRequest r = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission request not found: " + id));
        // ABAC: hide rows the caller isn't scoped to behind a 404 (PRD 14.9).
        if (!accessScope.isAccessible(r.getEmployeeId())) {
            throw new ResourceNotFoundException("Permission request not found: " + id);
        }
        return r;
    }

    @Transactional(readOnly = true)
    public Page<PermissionRequest> list(UUID employeeId, PermissionRequestStatus status, Pageable pageable) {
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope == null) {
            if (employeeId != null) return requests.findByEmployeeIdOrderByPermissionDateDesc(employeeId, pageable);
            if (status != null) return requests.findByStatusOrderByPermissionDateDesc(status, pageable);
            return requests.findAllByOrderByPermissionDateDesc(pageable);
        }
        if (scope.isEmpty()) return Page.empty(pageable);
        if (employeeId != null) {
            if (!scope.contains(employeeId)) return Page.empty(pageable);
            return requests.findByEmployeeIdOrderByPermissionDateDesc(employeeId, pageable);
        }
        if (status != null) {
            return requests.findByEmployeeIdInAndStatusOrderByPermissionDateDesc(scope, status, pageable);
        }
        return requests.findByEmployeeIdInOrderByPermissionDateDesc(scope, pageable);
    }

    @Transactional
    public PermissionRequest submit(PermissionSubmitRequest req) {
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        PermissionType type = types.findById(req.permissionTypeId())
                .orElseThrow(() -> new BadRequestException(
                        "Permission type not found: " + req.permissionTypeId()));
        if (!type.isActive()) {
            throw new BadRequestException("Permission type is inactive: " + type.getCode());
        }
        BigDecimal hours = resolveDurationHours(req);
        if (type.isRequiresAttachment()
                && (req.attachmentUrl() == null || req.attachmentUrl().isBlank())) {
            throw new BadRequestException("This permission type requires an attachment");
        }

        PermissionRequest r = new PermissionRequest();
        r.setRequestNo(String.format("PR-%05d", requests.nextRequestNoSequence()));
        r.setEmployeeId(req.employeeId());
        r.setPermissionTypeId(req.permissionTypeId());
        r.setPermissionDate(req.permissionDate());
        r.setStartTime(req.startTime());
        r.setEndTime(req.endTime());
        r.setDurationHours(hours);
        r.setReason(req.reason());
        r.setAttachmentUrl(req.attachmentUrl());
        r.setStatus(PermissionRequestStatus.PENDING);
        r.setCreatedBy(currentRequest.username());
        PermissionRequest saved = requests.save(r);

        boolean enforceLimit = type.getAnnualLimitHours() != null
                && type.getAnnualLimitHours().signum() > 0;
        balances.reserve(req.employeeId(), req.permissionTypeId(),
                req.permissionDate().getYear(), hours, enforceLimit);

        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                saved.getId().toString(),
                type.getName() + " — " + saved.getRequestNo()
                        + " (" + hours.stripTrailingZeros().toPlainString() + "h on "
                        + saved.getPermissionDate() + ")",
                Map.of(
                        "permissionType", type.getCode(),
                        "durationHours", hours.toPlainString(),
                        "date", saved.getPermissionDate().toString())));
        saved.setWorkflowInstanceId(instance.getId());
        saved = requests.save(saved);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "SUBMIT", null, PermissionRequestResponse.from(saved));
        return saved;
    }

    @Transactional
    public PermissionRequest onApproved(UUID requestId, String comment) {
        PermissionRequest r = get(requestId);
        if (r.getStatus() != PermissionRequestStatus.PENDING) return r;
        balances.commit(r.getEmployeeId(), r.getPermissionTypeId(),
                r.getPermissionDate().getYear(), r.getDurationHours());
        r.setStatus(PermissionRequestStatus.APPROVED);
        PermissionRequest saved = requests.save(r);
        audit.record(MODULE, ENTITY, requestId.toString(), "APPROVED", null,
                Map.of("comment", comment == null ? "" : comment,
                        "hours", r.getDurationHours().toPlainString()));
        return saved;
    }

    @Transactional
    public PermissionRequest onRejected(UUID requestId, String comment) {
        PermissionRequest r = get(requestId);
        if (r.getStatus() != PermissionRequestStatus.PENDING) return r;
        balances.release(r.getEmployeeId(), r.getPermissionTypeId(),
                r.getPermissionDate().getYear(), r.getDurationHours());
        r.setStatus(PermissionRequestStatus.REJECTED);
        PermissionRequest saved = requests.save(r);
        audit.record(MODULE, ENTITY, requestId.toString(), "REJECTED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public PermissionRequest onCancelled(UUID requestId, String comment) {
        PermissionRequest r = get(requestId);
        if (r.getStatus() != PermissionRequestStatus.PENDING) return r;
        balances.release(r.getEmployeeId(), r.getPermissionTypeId(),
                r.getPermissionDate().getYear(), r.getDurationHours());
        r.setStatus(PermissionRequestStatus.CANCELLED);
        PermissionRequest saved = requests.save(r);
        audit.record(MODULE, ENTITY, requestId.toString(), "CANCELLED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    private BigDecimal resolveDurationHours(PermissionSubmitRequest req) {
        if (req.durationHours() != null && req.durationHours().signum() > 0) {
            return req.durationHours();
        }
        Duration d = Duration.between(req.startTime(), req.endTime());
        BigDecimal minutes = BigDecimal.valueOf(d.toMinutes());
        return minutes.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
