package az.millers.hcm.leave.service;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.config.CacheConfig;

import java.math.BigDecimal;
import java.util.List;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.leave.api.dto.LeaveTypeRequest;
import az.millers.hcm.leave.api.dto.LeaveTypeResponse;
import az.millers.hcm.leave.api.dto.SeniorityBracket;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.domain.LeaveUnit;
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
    @Cacheable(CacheConfig.LEAVE_TYPES)
    public List<LeaveType> list() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.LEAVE_TYPES, key = "'active'")
    public List<LeaveType> listActive() {
        return repository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public LeaveType get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found: " + id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.LEAVE_TYPES, allEntries = true)
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
    @CacheEvict(value = CacheConfig.LEAVE_TYPES, allEntries = true)
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
        t.setSeniorityBrackets(validateBrackets(req.seniorityBrackets()));
        t.setCarryForwardExpiryMonths(req.carryForwardExpiryMonths());
        t.setNegativeBalanceAllowed(Boolean.TRUE.equals(req.negativeBalanceAllowed()));
        t.setMaxNegativeDays(req.maxNegativeDays());
        t.setLeaveUnit(req.leaveUnit() != null ? req.leaveUnit() : LeaveUnit.DAYS);
        if (req.hoursPerDay() != null) {
            t.setHoursPerDay(req.hoursPerDay());
        } else if (t.getHoursPerDay() == null) {
            t.setHoursPerDay(java.math.BigDecimal.valueOf(8));
        }
    }

    /**
     * Validates and normalises the seniority bracket schedule (M47).
     * Rules:
     * <ul>
     *   <li>{@code yearsMin} must be ≥ 0</li>
     *   <li>{@code yearsMax} (when present) must be ≥ {@code yearsMin}</li>
     *   <li>{@code annualDays} must be &gt; 0</li>
     *   <li>No two brackets may share the same {@code yearsMin}</li>
     * </ul>
     * Returns {@code null} when the input list is null or empty so that
     * the converter stores a SQL NULL rather than an empty JSON array.
     */
    private List<SeniorityBracket> validateBrackets(List<SeniorityBracket> brackets) {
        if (brackets == null || brackets.isEmpty()) {
            return null;
        }
        long distinctMins = brackets.stream()
                .mapToInt(SeniorityBracket::yearsMin).distinct().count();
        if (distinctMins != brackets.size()) {
            throw new BadRequestException(
                    "Seniority brackets: duplicate yearsMin values are not allowed");
        }
        for (SeniorityBracket b : brackets) {
            if (b.yearsMin() < 0) {
                throw new BadRequestException(
                        "Seniority brackets: yearsMin cannot be negative");
            }
            if (b.yearsMax() != null && b.yearsMax() < b.yearsMin()) {
                throw new BadRequestException(
                        "Seniority brackets: yearsMax must be >= yearsMin (got "
                        + b.yearsMin() + ".." + b.yearsMax() + ")");
            }
            if (b.annualDays() == null || b.annualDays().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException(
                        "Seniority brackets: annualDays must be > 0");
            }
        }
        return brackets;
    }
}
