package az.millers.hcm.leave.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.leave.api.dto.LeaveTypeRequest;
import az.millers.hcm.leave.api.dto.LeaveTypeResponse;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class LeaveTypeService {

    private static final String MODULE = "LEAVE";

    private final LeaveTypeRepository repository;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public LeaveTypeService(LeaveTypeRepository repository, AuditService audit,
                             CurrentRequest currentRequest) {
        this.repository = repository;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<LeaveType> list() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<LeaveType> listActive() {
        return repository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public LeaveType get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found: " + id));
    }

    @Transactional
    public LeaveType create(LeaveTypeRequest req) {
        if (repository.existsByCode(req.code())) {
            throw new BadRequestException("Leave type code already exists: " + req.code());
        }
        LeaveType t = new LeaveType();
        applyRequest(t, req);
        t.setCreatedBy(currentRequest.username());
        t.setUpdatedBy(currentRequest.username());
        LeaveType saved = repository.save(t);
        audit.record(MODULE, "LeaveType", saved.getId().toString(),
                "CREATE", null, LeaveTypeResponse.from(saved));
        return saved;
    }

    @Transactional
    public LeaveType update(UUID id, LeaveTypeRequest req) {
        LeaveType t = get(id);
        if (!t.getCode().equals(req.code()) && repository.existsByCode(req.code())) {
            throw new BadRequestException("Leave type code already exists: " + req.code());
        }
        LeaveTypeResponse before = LeaveTypeResponse.from(t);
        applyRequest(t, req);
        t.setUpdatedBy(currentRequest.username());
        LeaveType saved = repository.save(t);
        audit.record(MODULE, "LeaveType", id.toString(),
                "UPDATE", before, LeaveTypeResponse.from(saved));
        return saved;
    }

    private void applyRequest(LeaveType t, LeaveTypeRequest req) {
        t.setCode(req.code());
        t.setName(req.name());
        t.setDescription(req.description());
        t.setPaid(req.paid() == null ? true : req.paid());
        t.setRequiresAttachment(Boolean.TRUE.equals(req.requiresAttachment()));
        t.setRequiresReplacement(Boolean.TRUE.equals(req.requiresReplacement()));
        t.setDefaultAnnualEntitlementDays(req.defaultAnnualEntitlementDays());
        t.setCarryForwardLimitDays(req.carryForwardLimitDays());
        t.setMaxConsecutiveDays(req.maxConsecutiveDays());
        t.setExcludeWeekends(Boolean.TRUE.equals(req.excludeWeekends()));
        t.setExcludeHolidays(Boolean.TRUE.equals(req.excludeHolidays()));
        t.setActive(req.active() == null ? true : req.active());
        t.setAccruesMonthly(Boolean.TRUE.equals(req.accruesMonthly()));
        t.setMonthlyAccrualDays(req.monthlyAccrualDays());
    }
}
