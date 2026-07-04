package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.leave.domain.LeaveEntitlementRule;
import az.millers.hcm.leave.repo.LeaveEntitlementRuleRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M339: Leave entitlement rules engine.
 *
 * <p>HR can define per-type rules keyed on employment_type + tenure-months window.
 * The accrual engine calls {@link #resolve} at highest priority before seniority
 * brackets, so this layer gives HR the most specific annual entitlement override.
 *
 * <p>Resolution: rules are sorted priority DESC; the first matching rule wins.
 * A rule matches when its employment_type is null or equals the employee's type
 * AND the employee's completed tenure months falls within [minTenureMonths, maxTenureMonths].
 */
@Service
public class LeaveEntitlementRuleService {

    private static final String MODULE = "LEAVE";
    private static final String ENTITY = "LeaveEntitlementRule";

    private final LeaveEntitlementRuleRepository rules;
    private final LeaveTypeRepository leaveTypes;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public LeaveEntitlementRuleService(LeaveEntitlementRuleRepository rules,
                                       LeaveTypeRepository leaveTypes,
                                       AuditService audit,
                                       CurrentRequest currentRequest) {
        this.rules = rules;
        this.leaveTypes = leaveTypes;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<LeaveEntitlementRule> list(UUID leaveTypeId) {
        return rules.findByLeaveTypeIdOrderByPriorityDescCreatedAtAsc(leaveTypeId);
    }

    @Transactional
    public LeaveEntitlementRule create(UUID leaveTypeId, LeaveEntitlementRule req) {
        if (!leaveTypes.existsById(leaveTypeId)) {
            throw new BadRequestException("Leave type not found: " + leaveTypeId);
        }
        req.setLeaveTypeId(leaveTypeId);
        req.setCreatedBy(currentRequest.username());
        req.setUpdatedBy(currentRequest.username());
        LeaveEntitlementRule saved = rules.save(req);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public LeaveEntitlementRule update(UUID id, LeaveEntitlementRule req) {
        LeaveEntitlementRule existing = rules.findById(id)
                .orElseThrow(() -> new BadRequestException("Entitlement rule not found: " + id));
        existing.setEmploymentType(req.getEmploymentType());
        existing.setMinTenureMonths(req.getMinTenureMonths());
        existing.setMaxTenureMonths(req.getMaxTenureMonths());
        existing.setAnnualEntitlementDays(req.getAnnualEntitlementDays());
        existing.setPriority(req.getPriority());
        existing.setUpdatedBy(currentRequest.username());
        LeaveEntitlementRule saved = rules.save(existing);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void toggleActive(UUID id) {
        LeaveEntitlementRule rule = rules.findById(id)
                .orElseThrow(() -> new BadRequestException("Entitlement rule not found: " + id));
        rule.setActive(!rule.isActive());
        rule.setUpdatedBy(currentRequest.username());
        rules.save(rule);
        audit.record(MODULE, ENTITY, id.toString(),
                rule.isActive() ? "ACTIVATE" : "DEACTIVATE", null, null);
    }

    /**
     * Resolves the annual entitlement for an employee against the leave type's active rules.
     * Returns the first matching rule's annual_entitlement_days (priority DESC), or empty if none match.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> resolve(UUID leaveTypeId,
                                        EmploymentType employmentType,
                                        int tenureMonths) {
        return rules.findByLeaveTypeIdAndActiveTrueOrderByPriorityDesc(leaveTypeId)
                .stream()
                .filter(r -> r.matches(employmentType, tenureMonths))
                .map(LeaveEntitlementRule::getAnnualEntitlementDays)
                .findFirst();
    }
}
