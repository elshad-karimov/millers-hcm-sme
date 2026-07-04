package az.millers.hcm.leave.api;

import java.time.Year;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.service.LeaveYearEndService;
import az.millers.hcm.leave.service.LeaveYearEndService.RolloverResult;

/**
 * Admin trigger for the year-end leave carry-forward rollover (M178 / PRD §8.5.2).
 *
 * <p>The rollover also runs automatically at 01:30 on 1 January each year.
 * This endpoint allows HR Admins to re-run or dry-run it manually for
 * verification or off-cycle corrections.
 */
@RestController
@RequestMapping("/api/leave/year-end-rollover")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')")
public class LeaveYearEndController {

    private final LeaveYearEndService svc;

    public LeaveYearEndController(LeaveYearEndService svc) {
        this.svc = svc;
    }

    /**
     * @param closingYear the year whose balances are rolled over (default: previous year)
     * @param dryRun      if {@code true} computes but does not persist
     */
    @PostMapping
    public RolloverResult rollover(
            @RequestParam(required = false) Integer closingYear,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        int year = closingYear != null ? closingYear : Year.now().getValue() - 1;
        return svc.rollover(year, dryRun);
    }
}
