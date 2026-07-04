package az.millers.hcm.leave.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.api.dto.LeaveDelegationRequest;
import az.millers.hcm.leave.api.dto.LeaveDelegationResponse;
import az.millers.hcm.leave.domain.DelegationStatus;
import az.millers.hcm.leave.domain.LeaveDelegation;
import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.leave.repo.LeaveDelegationRepository;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class LeaveDelegationService {

    private static final String MODULE = "LEAVE";
    private static final String ENTITY = "LeaveDelegation";

    private final LeaveDelegationRepository delegations;
    private final LeaveRequestRepository leaveRequests;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public LeaveDelegationService(LeaveDelegationRepository delegations,
                                   LeaveRequestRepository leaveRequests,
                                   EmployeeRepository employees,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.delegations = delegations;
        this.leaveRequests = leaveRequests;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    /** List all delegations for a leave request. */
    @Transactional(readOnly = true)
    public List<LeaveDelegationResponse> listForRequest(UUID leaveRequestId) {
        return delegations.findByLeaveRequestId(leaveRequestId).stream()
                .map(d -> enrich(d))
                .toList();
    }

    /** List PENDING delegations for the current delegate (self-service inbox). */
    @Transactional(readOnly = true)
    public List<LeaveDelegationResponse> listPendingForDelegate(UUID delegateId) {
        return delegations.findByDelegateIdAndStatus(delegateId, DelegationStatus.PENDING).stream()
                .map(d -> enrich(d))
                .toList();
    }

    /** Employee submits a coverage delegation for their own leave request. */
    @Transactional
    public LeaveDelegationResponse create(UUID leaveRequestId, LeaveDelegationRequest req) {
        LeaveRequest lr = leaveRequests.findById(leaveRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found: " + leaveRequestId));

        if (req.delegateId().equals(lr.getEmployeeId())) {
            throw new BadRequestException("Delegate cannot be the same as the leave requester");
        }

        // Idempotent — if already exists, return it
        delegations.findByLeaveRequestIdAndDelegateId(leaveRequestId, req.delegateId())
                .ifPresent(d -> {
                    throw new BadRequestException("Delegation to this employee already exists for this request");
                });

        LeaveDelegation d = new LeaveDelegation();
        d.setLeaveRequestId(leaveRequestId);
        d.setDelegatorId(lr.getEmployeeId());
        d.setDelegateId(req.delegateId());
        d.setDelegationScope(req.delegationScope());
        d.setCreatedBy(currentRequest.username());
        LeaveDelegation saved = delegations.save(d);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATED",
                null, Map.of("delegate", req.delegateId().toString(),
                        "request", leaveRequestId.toString()));
        return enrich(saved);
    }

    /** Delegate accepts the coverage. */
    @Transactional
    public LeaveDelegationResponse accept(UUID delegationId, String notes) {
        LeaveDelegation d = getOrThrow(delegationId);
        assertPending(d);
        d.setStatus(DelegationStatus.ACCEPTED);
        d.setDelegateNotes(notes);
        d.setRespondedAt(OffsetDateTime.now());
        audit.record(MODULE, ENTITY, delegationId.toString(), "ACCEPTED", null, Map.of());
        return enrich(delegations.save(d));
    }

    /** Delegate declines the coverage. */
    @Transactional
    public LeaveDelegationResponse decline(UUID delegationId, String notes) {
        LeaveDelegation d = getOrThrow(delegationId);
        assertPending(d);
        d.setStatus(DelegationStatus.DECLINED);
        d.setDelegateNotes(notes);
        d.setRespondedAt(OffsetDateTime.now());
        audit.record(MODULE, ENTITY, delegationId.toString(), "DECLINED", null, Map.of());
        return enrich(delegations.save(d));
    }

    /** Requester revokes the delegation (e.g., leave was cancelled). */
    @Transactional
    public LeaveDelegationResponse revoke(UUID delegationId) {
        LeaveDelegation d = getOrThrow(delegationId);
        if (d.getStatus() == DelegationStatus.REVOKED) {
            throw new BadRequestException("Delegation is already revoked");
        }
        d.setStatus(DelegationStatus.REVOKED);
        d.setRespondedAt(OffsetDateTime.now());
        audit.record(MODULE, ENTITY, delegationId.toString(), "REVOKED", null, Map.of());
        return enrich(delegations.save(d));
    }

    private LeaveDelegation getOrThrow(UUID id) {
        return delegations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delegation not found: " + id));
    }

    private static void assertPending(LeaveDelegation d) {
        if (d.getStatus() != DelegationStatus.PENDING) {
            throw new BadRequestException("Delegation is no longer PENDING (current: " + d.getStatus() + ")");
        }
    }

    private LeaveDelegationResponse enrich(LeaveDelegation d) {
        String delegatorName = resolveName(d.getDelegatorId());
        String delegateName = resolveName(d.getDelegateId());
        return LeaveDelegationResponse.of(d, delegatorName, delegateName);
    }

    private String resolveName(UUID employeeId) {
        if (employeeId == null) return null;
        return employees.findById(employeeId)
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .orElse("Unknown");
    }
}
