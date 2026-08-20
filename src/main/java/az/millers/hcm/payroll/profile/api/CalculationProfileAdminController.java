package az.millers.hcm.payroll.profile.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.profile.CalculationProfile;
import az.millers.hcm.payroll.profile.CalculationProfileAdminService;
import az.millers.hcm.payroll.profile.EmployeeCalculationProfile;
import az.millers.hcm.payroll.profile.EmployeeMewaRule;
import az.millers.hcm.payroll.profile.ExcessAccumulator;
import az.millers.hcm.payroll.profile.ExcessAccumulatorPostingService;
import az.millers.hcm.payroll.profile.ExcessAccumulatorPostingService.PostingResult;
import az.millers.hcm.payroll.timepay.PeriodNormHours;
import jakarta.validation.Valid;

/**
 * Configuring how people are paid, and closing balancing periods.
 *
 * <p>Restricted to payroll and admin roles — managers and employees never
 * configure or see pay rules. Every write requires a stated reason and is audit
 * logged with its old and new value.
 *
 * <p>This controller is deliberately separate from the read-only preview: one
 * is for checking numbers, this one changes them.
 */
@RestController
@RequestMapping("/api/payroll/calculation-profiles/admin")
@PreAuthorize("hasAnyRole('PAYROLL_SPECIALIST','COMPENSATION_MANAGER','SYSTEM_ADMIN')")
public class CalculationProfileAdminController {

    private final CalculationProfileAdminService admin;
    private final ExcessAccumulatorPostingService posting;

    public CalculationProfileAdminController(CalculationProfileAdminService admin,
                                             ExcessAccumulatorPostingService posting) {
        this.admin = admin;
        this.posting = posting;
    }

    // ---------- Profile settings — this is how the open questions get answered ----

    @PutMapping("/profiles/{code}")
    public CalculationProfile updateSettings(
            @PathVariable String code,
            @Valid @RequestBody ProfileAdminDtos.UpdateProfileSettings req) {
        return admin.updateSettings(code, req);
    }

    /** Return one setting to unresolved, which makes the engine refuse again. */
    @DeleteMapping("/profiles/{code}/settings/{setting}")
    public CalculationProfile clearSetting(@PathVariable String code,
                                           @PathVariable String setting,
                                           @RequestParam String reason) {
        return admin.clearSetting(code, setting, reason);
    }

    // ---------- Employee assignment ----------

    @PostMapping("/assignments")
    public EmployeeCalculationProfile assign(
            @Valid @RequestBody ProfileAdminDtos.AssignProfile req) {
        return admin.assign(req);
    }

    @GetMapping("/assignments/{employeeId}")
    public List<EmployeeCalculationProfile> assignments(@PathVariable UUID employeeId) {
        return admin.assignmentsFor(employeeId);
    }

    // ---------- MEWA ----------

    @PostMapping("/mewa")
    public EmployeeMewaRule upsertMewa(@Valid @RequestBody ProfileAdminDtos.UpsertMewaRule req) {
        return admin.upsertMewa(req);
    }

    @GetMapping("/mewa/{employeeId}")
    public List<EmployeeMewaRule> mewa(@PathVariable UUID employeeId) {
        return admin.mewaFor(employeeId);
    }

    // ---------- Norm hours ----------

    @PostMapping("/norm-hours")
    public PeriodNormHours upsertNormHours(
            @Valid @RequestBody ProfileAdminDtos.UpsertNormHours req) {
        return admin.upsertNormHours(req);
    }

    // ---------- The excess accumulator ----------

    /**
     * Re-post a period into the balancing accumulators.
     *
     * <p>Posting happens automatically when the attendance period locks; this is
     * for after a correction, or when the automatic run reported problems. It
     * replaces each month and recomputes every later running balance, and
     * refuses on any period that has already been settled.
     */
    @PostMapping("/accumulator/post/{year}/{month}")
    public PostingResult repost(@PathVariable int year, @PathVariable int month) {
        return posting.post(year, month, "REPOST");
    }

    /**
     * Close a balancing period and record how it was settled.
     *
     * <p>Refuses while the rotation excess multiplier is unresolved — settling
     * without it would guess at someone's pay by a factor of 27%.
     */
    @PostMapping("/accumulator/settle")
    public ExcessAccumulator settle(@Valid @RequestBody ProfileAdminDtos.SettleExcess req) {
        return admin.settle(req);
    }
}
