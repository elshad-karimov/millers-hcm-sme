package az.millers.hcm.timesheet.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.timesheet.api.dto.ApprovalDtos.ApproveRequest;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.BulkApproveRequest;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.BulkApproveResult;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.CorrectionDecision;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.CorrectionView;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.QueueRow;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.RejectRequest;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.ReturnRequest;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.ReviewView;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.service.TimesheetApprovalService;
import az.millers.hcm.timesheet.service.TimesheetCorrectionService;

/**
 * Manager review and decision on their team's timesheets.
 *
 * <p>The role check here is the outer fence; the real boundary is
 * hierarchy scoping inside the service, which narrows every query and every
 * decision to the caller's own reports. A department manager holding the role
 * still cannot reach an employee outside their line.
 *
 * <p>Returns hours and quantities. No endpoint here exposes pay.
 */
@RestController
@RequestMapping("/api/manager/timesheets")
@PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER','HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
public class ManagerTimesheetController {

    private final TimesheetApprovalService approvals;
    private final TimesheetCorrectionService corrections;

    public ManagerTimesheetController(TimesheetApprovalService approvals,
                                      TimesheetCorrectionService corrections) {
        this.approvals = approvals;
        this.corrections = corrections;
    }

    /** The approval queue for a period. Defaults to everything awaiting a decision. */
    @GetMapping
    public List<QueueRow> queue(@RequestParam int year,
                                @RequestParam int month,
                                @RequestParam(required = false) TimesheetStatus status) {
        return approvals.queue(year, month, status);
    }

    @GetMapping("/{id}")
    public ReviewView review(@PathVariable UUID id) {
        return approvals.review(id);
    }

    @PostMapping("/{id}/approve")
    public ReviewView approve(@PathVariable UUID id, @RequestBody(required = false) ApproveRequest req) {
        return approvals.approve(id, req);
    }

    /** Send named days back for correction; unnamed days stay approved. */
    @PostMapping("/{id}/return")
    public ReviewView returnForCorrection(@PathVariable UUID id, @RequestBody ReturnRequest req) {
        return approvals.returnForCorrection(id, req);
    }

    @PostMapping("/{id}/reject")
    public ReviewView reject(@PathVariable UUID id, @RequestBody RejectRequest req) {
        return approvals.reject(id, req);
    }

    /** Approve several clean months. Anything not clean comes back in `skipped`. */
    @PostMapping("/bulk-approve")
    public BulkApproveResult bulkApprove(@RequestBody BulkApproveRequest req) {
        return approvals.bulkApprove(req.timesheetIds(), req.comment());
    }

    // ---- corrections ----

    @GetMapping("/corrections/pending")
    public List<CorrectionView> pendingCorrections() {
        return corrections.pending();
    }

    @PostMapping("/corrections/{id}/decide")
    public CorrectionView decideCorrection(@PathVariable UUID id,
                                           @RequestBody CorrectionDecision decision) {
        return corrections.decide(id, decision);
    }
}
