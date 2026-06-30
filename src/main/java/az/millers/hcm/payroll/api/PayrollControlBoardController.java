package az.millers.hcm.payroll.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.PayrollControlBoardResponse;
import az.millers.hcm.payroll.service.PayrollControlBoardService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/payroll")
public class PayrollControlBoardController {

    private final PayrollControlBoardService controlBoard;

    public PayrollControlBoardController(PayrollControlBoardService controlBoard) {
        this.controlBoard = controlBoard;
    }

    @GetMapping("/control-board")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public PayrollControlBoardResponse dashboard() {
        return controlBoard.dashboard();
    }

    // NOTE: GET /api/payroll/runs/{id}/pre-flight lives on PayrollRunController (M350);
    // it is run-scoped, so it is not duplicated here.
}
