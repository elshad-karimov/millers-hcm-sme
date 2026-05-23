package az.millers.hcm.leave.api;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.service.LeaveAccrualService;
import az.millers.hcm.leave.service.LeaveAccrualService.AccrualResult;

/**
 * Admin trigger for the monthly leave-accrual walker (PRD 8.5.2 —
 * milestone 34). The walker also runs on its own via {@code @Scheduled}
 * at 02:00 on the 1st of every month; this endpoint exists for
 * off-cycle / verification runs.
 *
 * <p>Idempotent — if the walker has already credited a given period
 * for a given (employee, type) the audit-log marker short-circuits the
 * second pass, so re-runs are safe.
 */
@RestController
@RequestMapping("/api/leave/accruals")
public class LeaveAccrualController {

    private final LeaveAccrualService service;

    public LeaveAccrualController(LeaveAccrualService service) {
        this.service = service;
    }

    /**
     * {@code POST /api/leave/accruals/run-now?year=&month=&dryRun=}.
     * All three params are optional — defaults are "this year, this
     * month, dryRun=false". HR_ADMIN / SYSTEM_ADMIN only.
     */
    @PostMapping("/run-now")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public AccrualResult runNow(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false, defaultValue = "false") boolean dryRun) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        return service.accrueForMonth(y, m, dryRun);
    }
}
