package az.millers.hcm.leave.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.leave.api.dto.LeavePeriodLockRequest;
import az.millers.hcm.leave.api.dto.LeavePeriodLockResponse;
import az.millers.hcm.leave.domain.LeavePeriodLock;
import az.millers.hcm.leave.repo.LeavePeriodLockRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class LeavePeriodLockService {

    private static final String MODULE = "LEAVE";
    private static final String ENTITY = "LeavePeriodLock";

    private final LeavePeriodLockRepository locks;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public LeavePeriodLockService(LeavePeriodLockRepository locks,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.locks = locks;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<LeavePeriodLockResponse> listAll() {
        return locks.findAllByOrderByPeriodStartDesc().stream().map(LeavePeriodLockResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LeavePeriodLockResponse get(UUID id) {
        return LeavePeriodLockResponse.from(find(id));
    }

    @Transactional
    public LeavePeriodLockResponse create(LeavePeriodLockRequest req) {
        if (req.periodEnd().isBefore(req.periodStart())) {
            throw new BadRequestException("periodEnd must be on or after periodStart");
        }
        LeavePeriodLock lock = new LeavePeriodLock();
        apply(lock, req);
        lock.setCreatedBy(currentRequest.username());
        lock.setLockedBy(currentRequest.username());
        LeavePeriodLock saved = locks.save(lock);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATED", null, saved);
        return LeavePeriodLockResponse.from(saved);
    }

    @Transactional
    public LeavePeriodLockResponse update(UUID id, LeavePeriodLockRequest req) {
        if (req.periodEnd().isBefore(req.periodStart())) {
            throw new BadRequestException("periodEnd must be on or after periodStart");
        }
        LeavePeriodLock lock = find(id);
        Object old = LeavePeriodLockResponse.from(lock);
        apply(lock, req);
        lock.setUpdatedBy(currentRequest.username());
        LeavePeriodLock saved = locks.save(lock);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATED", old, saved);
        return LeavePeriodLockResponse.from(saved);
    }

    @Transactional
    public void deactivate(UUID id) {
        LeavePeriodLock lock = find(id);
        lock.setActive(false);
        lock.setUpdatedBy(currentRequest.username());
        locks.save(lock);
        audit.record(MODULE, ENTITY, id.toString(), "DEACTIVATED", null, null);
    }

    /**
     * Used by LeaveRequestService to block submit/cancel when the request's
     * date range overlaps an active lock for that leave type (or a global lock).
     */
    public List<LeavePeriodLock> activeLocksFor(LocalDate startDate, LocalDate endDate, UUID leaveTypeId) {
        return locks.findActiveOverlapping(startDate, endDate, leaveTypeId);
    }

    private void apply(LeavePeriodLock lock, LeavePeriodLockRequest req) {
        lock.setPeriodStart(req.periodStart());
        lock.setPeriodEnd(req.periodEnd());
        lock.setLeaveTypeId(req.leaveTypeId());
        lock.setReason(req.reason());
        lock.setActive(req.active() != null ? req.active() : true);
    }

    private LeavePeriodLock find(UUID id) {
        return locks.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave period lock not found: " + id));
    }
}
