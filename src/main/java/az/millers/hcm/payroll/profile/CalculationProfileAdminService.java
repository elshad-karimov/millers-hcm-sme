package az.millers.hcm.payroll.profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.profile.api.ProfileAdminDtos.AssignProfile;
import az.millers.hcm.payroll.profile.api.ProfileAdminDtos.SettleExcess;
import az.millers.hcm.payroll.profile.api.ProfileAdminDtos.UpdateProfileSettings;
import az.millers.hcm.payroll.profile.api.ProfileAdminDtos.UpsertMewaRule;
import az.millers.hcm.payroll.profile.api.ProfileAdminDtos.UpsertNormHours;
import az.millers.hcm.payroll.timepay.PeriodNormHours;
import az.millers.hcm.payroll.timepay.PeriodNormHoursRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Configuring how people are paid.
 *
 * <p>Everything this service writes changes someone's salary, so all of it is
 * effective-dated, requires a stated reason, and is audit logged with the old
 * and new value (global rules 6, 10, 11). Nothing here is a silent update.
 *
 * <h2>Answering the open questions is a configuration change, not a deploy</h2>
 * {@link #updateSettings} is how BLOCKERS Q1, Q2 and Q6 get answered. Until they
 * are, the calculator refuses the affected amounts rather than guessing — so
 * this service is the difference between an engine that is stuck and one that is
 * waiting.
 */
@Service
public class CalculationProfileAdminService {

    private static final String MODULE = "PAYROLL";

    private final CalculationProfileRepository profiles;
    private final EmployeeCalculationProfileRepository assignments;
    private final EmployeeMewaRuleRepository mewaRules;
    private final PeriodNormHoursRepository normHours;
    private final EmployeeRepository employees;
    private final EmployeeCompensationRepository compensations;
    private final ExcessAccumulatorService accumulator;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public CalculationProfileAdminService(CalculationProfileRepository profiles,
                                          EmployeeCalculationProfileRepository assignments,
                                          EmployeeMewaRuleRepository mewaRules,
                                          PeriodNormHoursRepository normHours,
                                          EmployeeRepository employees,
                                          EmployeeCompensationRepository compensations,
                                          ExcessAccumulatorService accumulator,
                                          AuditService audit,
                                          CurrentRequest currentRequest) {
        this.profiles = profiles;
        this.assignments = assignments;
        this.mewaRules = mewaRules;
        this.normHours = normHours;
        this.employees = employees;
        this.compensations = compensations;
        this.accumulator = accumulator;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ---------- Settlement ----------

    /**
     * Close a balancing period, pricing the accumulated hours at the employee's
     * rate for the payroll month that carries the payment.
     *
     * <p>Everything it needs is looked up rather than passed in, so a settlement
     * cannot be made against a hand-typed rate. It refuses on three counts: no
     * profile, no multiplier (BLOCKERS Q2), or no norm hours for the paying
     * month — each of which would otherwise mean guessing at someone's pay.
     */
    @Transactional
    public ExcessAccumulator settle(SettleExcess req) {
        LocalDate payPeriodStart = LocalDate.of(req.paidInYear(), req.paidInMonth(), 1);

        CalculationProfile profile = assignments
                .findByEmployeeIdOrderByEffectiveFromDesc(req.employeeId()).stream()
                .filter(a -> a.coversPeriodStart(payPeriodStart))
                .findFirst()
                .map(EmployeeCalculationProfile::getProfileCode)
                .flatMap(profiles::findByCode)
                .orElseThrow(() -> new BadRequestException(
                        "This employee has no calculation profile effective on "
                                + payPeriodStart + ", so there is no rule to settle under."));

        if (!profile.settlesExcessOverBalancingPeriod()) {
            throw new BadRequestException("Profile " + profile.getCode() + " does not use "
                    + "balancing-period settlement, so it has no accumulated hours to close.");
        }
        if (profile.getExcessMultiplier() == null) {
            throw new BadRequestException(
                    "The rotation excess multiplier is not configured on profile "
                            + profile.getCode() + ". It is either 3.50 (2 x 1.75) or 2.75 "
                            + "(2 + 0.75) and the source material does not say which — "
                            + "BLOCKERS Q2. Settling without it would guess at someone's pay "
                            + "by 27%.");
        }

        BigDecimal norm = normHours
                .findByPeriodYearAndPeriodMonth(req.paidInYear(), req.paidInMonth())
                .map(PeriodNormHours::getNormHours)
                .orElseThrow(() -> new BadRequestException("Norm working hours are not "
                        + "configured for " + req.paidInYear() + "-" + req.paidInMonth()
                        + ", and the hourly rate divides by them."));

        BigDecimal base = compensations.findActiveOn(req.employeeId(), payPeriodStart)
                .map(EmployeeCompensation::getMonthlyBaseSalary)
                .orElseThrow(() -> new BadRequestException("This employee has no effective "
                        + "compensation record on " + payPeriodStart + ", so no rate can be "
                        + "derived."));

        BigDecimal hourlyRate = base.divide(norm, 10, java.math.RoundingMode.HALF_UP);

        return accumulator.settle(req.employeeId(), req.periodYear(), req.periodSeq(),
                profile.getExcessMultiplier(), hourlyRate, currentRequest.username(),
                req.paidInYear(), req.paidInMonth(), req.note());
    }

    // ---------- Profile settings ----------

    @Transactional
    public CalculationProfile updateSettings(String code, UpdateProfileSettings req) {
        CalculationProfile p = profiles.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No calculation profile with code '" + code + "'."));

        Map<String, Object> before = snapshot(p);

        if (req.excessMultiplier() != null) {
            if (CalculationProfile.EXCESS_NONE.equals(p.getExcessMethod())) {
                throw new BadRequestException("Profile " + code + " does not settle excess, so "
                        + "an excess multiplier on it would never be used. Set the excess "
                        + "method first if that is the intent.");
            }
            p.setExcessMultiplier(req.excessMultiplier());
        }
        if (req.nightHoursSeparateFromBase() != null) {
            p.setNightHoursSeparateFromBase(req.nightHoursSeparateFromBase());
        }
        if (req.accumulatorCategories() != null) {
            p.setAccumulatorCategories(normaliseCategories(req.accumulatorCategories()));
        }
        if (req.derivedOffshoreDeductsSick() != null) {
            p.setDerivedOffshoreDeductsSick(req.derivedOffshoreDeductsSick());
        }
        if (req.offshoreMultiplier() != null) {
            if (CalculationProfile.OFFSHORE_NONE.equals(p.getOffshoreSalaryMode())) {
                throw new BadRequestException("Profile " + code + " has no offshore component, "
                        + "so an offshore multiplier on it would never be used.");
            }
            p.setOffshoreMultiplier(req.offshoreMultiplier());
        }

        CalculationProfile saved = profiles.save(p);
        Map<String, Object> after = snapshot(saved);
        after.put("reason", req.reason());
        audit.record(MODULE, "CalculationProfile", code, "PROFILE_SETTINGS_UPDATE", before, after);
        return saved;
    }

    /**
     * Put a value back to unresolved.
     *
     * <p>Separate from {@link #updateSettings} because null there means "leave
     * alone". Un-answering a question has to be deliberate: it turns a paying
     * calculation back into a refusing one.
     */
    @Transactional
    public CalculationProfile clearSetting(String code, String setting, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException(
                    "A reason is required to return a setting to unresolved — it stops the "
                            + "engine paying amounts it was paying yesterday.");
        }
        CalculationProfile p = profiles.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No calculation profile with code '" + code + "'."));
        Map<String, Object> before = snapshot(p);

        switch (setting) {
            case "excessMultiplier" -> p.setExcessMultiplier(null);
            case "nightHoursSeparateFromBase" -> p.setNightHoursSeparateFromBase(null);
            case "accumulatorCategories" -> p.setAccumulatorCategories(null);
            default -> throw new BadRequestException("'" + setting + "' is not a clearable "
                    + "setting. Clearable: excessMultiplier, nightHoursSeparateFromBase, "
                    + "accumulatorCategories.");
        }

        CalculationProfile saved = profiles.save(p);
        audit.record(MODULE, "CalculationProfile", code, "PROFILE_SETTING_CLEARED", before,
                Map.of("setting", setting, "reason", reason));
        return saved;
    }

    // ---------- Employee assignment ----------

    @Transactional
    public EmployeeCalculationProfile assign(AssignProfile req) {
        if (!employees.existsById(req.employeeId())) {
            throw new ResourceNotFoundException("No employee with that id.");
        }
        profiles.findByCode(req.profileCode()).orElseThrow(() -> new BadRequestException(
                "No calculation profile with code '" + req.profileCode() + "'."));
        if (req.effectiveTo() != null && req.effectiveTo().isBefore(req.effectiveFrom())) {
            throw new BadRequestException("The end date is before the start date.");
        }

        List<EmployeeCalculationProfile> existing =
                assignments.findByEmployeeIdOrderByEffectiveFromDesc(req.employeeId());

        // Overlaps are the failure mode that silently changes someone's pay: two
        // open assignments and the answer depends on row order. Close the
        // previous one instead of rejecting, since a profile change is normal.
        for (EmployeeCalculationProfile prior : existing) {
            if (prior.getEffectiveTo() == null
                    && !prior.getEffectiveFrom().isAfter(req.effectiveFrom())) {
                prior.setEffectiveTo(req.effectiveFrom().minusDays(1));
                assignments.save(prior);
            } else if (prior.getEffectiveTo() == null
                    || !prior.getEffectiveTo().isBefore(req.effectiveFrom())) {
                if (!prior.getEffectiveFrom().isAfter(
                        req.effectiveTo() == null ? LocalDate.MAX : req.effectiveTo())) {
                    throw new BadRequestException(
                            "That range overlaps an assignment from " + prior.getEffectiveFrom()
                                    + " to " + (prior.getEffectiveTo() == null ? "open"
                                    : prior.getEffectiveTo()) + ". Two profiles covering one "
                                    + "month would make pay depend on row order.");
                }
            }
        }

        EmployeeCalculationProfile a = new EmployeeCalculationProfile();
        a.setEmployeeId(req.employeeId());
        a.setProfileCode(req.profileCode());
        a.setEffectiveFrom(req.effectiveFrom());
        a.setEffectiveTo(req.effectiveTo());
        a.setReason(req.reason());
        a.setCreatedBy(currentRequest.username());
        EmployeeCalculationProfile saved = assignments.save(a);

        audit.record(MODULE, "EmployeeCalculationProfile", req.employeeId().toString(),
                "PROFILE_ASSIGNED", null,
                Map.of("profileCode", req.profileCode(),
                        "effectiveFrom", req.effectiveFrom().toString(),
                        "reason", req.reason()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EmployeeCalculationProfile> assignmentsFor(UUID employeeId) {
        return assignments.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
    }

    // ---------- MEWA ----------

    @Transactional
    public EmployeeMewaRule upsertMewa(UpsertMewaRule req) {
        if (!employees.existsById(req.employeeId())) {
            throw new ResourceNotFoundException("No employee with that id.");
        }
        if (req.effectiveTo() != null && req.effectiveTo().isBefore(req.effectiveFrom())) {
            throw new BadRequestException("The end date is before the start date.");
        }

        for (EmployeeMewaRule prior : mewaRules.findByEmployeeIdOrderByEffectiveFromDesc(
                req.employeeId())) {
            if (prior.getEffectiveTo() == null
                    && !prior.getEffectiveFrom().isAfter(req.effectiveFrom())) {
                prior.setEffectiveTo(req.effectiveFrom().minusDays(1));
                mewaRules.save(prior);
            }
        }

        EmployeeMewaRule r = new EmployeeMewaRule();
        r.setEmployeeId(req.employeeId());
        r.setBasis(EmployeeMewaRule.BASIS_ONSHORE_EARNING);
        r.setRate(req.rate());
        r.setEffectiveFrom(req.effectiveFrom());
        r.setEffectiveTo(req.effectiveTo());
        r.setReason(req.reason());
        r.setCreatedBy(currentRequest.username());
        EmployeeMewaRule saved = mewaRules.save(r);

        audit.record(MODULE, "EmployeeMewaRule", req.employeeId().toString(), "MEWA_SET", null,
                Map.of("rate", req.rate().toPlainString(),
                        "effectiveFrom", req.effectiveFrom().toString(),
                        "reason", req.reason()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EmployeeMewaRule> mewaFor(UUID employeeId) {
        return mewaRules.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
    }

    // ---------- Norm hours ----------

    @Transactional
    public PeriodNormHours upsertNormHours(UpsertNormHours req) {
        PeriodNormHours existing = normHours
                .findByPeriodYearAndPeriodMonth(req.year(), req.month()).orElse(null);

        BigDecimal old = existing == null ? null : existing.getNormHours();
        PeriodNormHours row = existing == null ? new PeriodNormHours() : existing;
        row.setPeriodYear(req.year());
        row.setPeriodMonth(req.month());
        row.setNormHours(req.normHours());
        PeriodNormHours saved = normHours.save(row);

        // Changing the norm changes every hourly rate in that month.
        audit.record(MODULE, "PeriodNormHours", req.year() + "-" + req.month(),
                old == null ? "NORM_HOURS_SET" : "NORM_HOURS_CHANGED",
                old == null ? null : Map.of("normHours", old.toPlainString()),
                Map.of("normHours", req.normHours().toPlainString()));
        return saved;
    }

    // ---------- Internals ----------

    private static Map<String, Object> snapshot(CalculationProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("offshoreSalaryMode", p.getOffshoreSalaryMode());
        m.put("offshoreMultiplier", str(p.getOffshoreMultiplier()));
        m.put("excessMethod", p.getExcessMethod());
        m.put("excessMultiplier", str(p.getExcessMultiplier()));
        m.put("nightHoursSeparateFromBase", p.getNightHoursSeparateFromBase());
        m.put("accumulatorCategories", p.getAccumulatorCategories());
        m.put("derivedOffshoreDeductsSick", p.isDerivedOffshoreDeductsSick());
        return m;
    }

    private static String str(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static String normaliseCategories(String raw) {
        String cleaned = java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(c -> !c.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
        if (cleaned.isEmpty()) {
            throw new BadRequestException("An empty category list would total zero hours for "
                    + "every month. Use the clear endpoint if the intent is to return this to "
                    + "unresolved.");
        }
        return cleaned;
    }
}
