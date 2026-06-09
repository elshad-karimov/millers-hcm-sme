package az.millers.hcm.reporting.api;

import java.time.LocalDate;
import java.time.Year;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.reporting.api.dto.ContractChangeReportDtos.PendingContractChangesReport;
import az.millers.hcm.reporting.api.dto.ContractChangeReportDtos.PositionChangeHistoryReport;
import az.millers.hcm.reporting.api.dto.ContractChangeReportDtos.SalaryChangeHistoryReport;
import az.millers.hcm.reporting.service.ContractChangeReportService;
import az.millers.hcm.security.SecurityRoles;

/**
 * Contract-change report endpoints (M226 / PRD §8.12.5).
 *
 * <p>Base path: {@code /api/reports/contract-changes}
 * <p>History reports default to the current calendar year when {@code from}
 * / {@code to} are omitted.
 */
@RestController
@RequestMapping("/api/reports/contract-changes")
public class ContractChangeReportController {

    private final ContractChangeReportService service;

    public ContractChangeReportController(ContractChangeReportService service) {
        this.service = service;
    }

    @GetMapping("/salary-history")
    @PreAuthorize(SecurityRoles.READ_PAYROLL_INTERNAL)
    public SalaryChangeHistoryReport salaryHistory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.of(Year.now().getValue(), 1, 1);
        LocalDate t = to   != null ? to   : LocalDate.now();
        return service.salaryHistory(f, t);
    }

    @GetMapping("/position-history")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PositionChangeHistoryReport positionHistory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.of(Year.now().getValue(), 1, 1);
        LocalDate t = to   != null ? to   : LocalDate.now();
        return service.positionHistory(f, t);
    }

    @GetMapping("/pending")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PendingContractChangesReport pending() {
        return service.pending();
    }
}
