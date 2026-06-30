package az.millers.hcm.payroll.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.PayrollControlBoardResponse;
import az.millers.hcm.payroll.api.dto.PayrollPreFlightResponse;
import az.millers.hcm.payroll.service.PayrollControlBoardService;
import az.millers.hcm.payroll.service.PayrollPreFlightService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/payroll")
public class PayrollControlBoardController {

    private final PayrollControlBoardService controlBoard;
    private final PayrollPreFlightService preFlight;

    public PayrollControlBoardController(PayrollControlBoardService controlBoard,
                                         PayrollPreFlightService preFlight) {
        this.controlBoard = controlBoard;
        this.preFlight = preFlight;
    }

    @GetMapping("/control-board")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public PayrollControlBoardResponse dashboard() {
        return controlBoard.dashboard();
    }

    @GetMapping("/runs/{id}/pre-flight")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public PayrollPreFlightResponse preFlightCheck(@PathVariable UUID id) {
        return preFlight.preFlight(id);
    }
}
