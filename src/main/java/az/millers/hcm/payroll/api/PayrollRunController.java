package az.millers.hcm.payroll.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.AddBonusRequest;
import az.millers.hcm.payroll.api.dto.CreateRunRequest;
import az.millers.hcm.payroll.api.dto.PayrollAllowanceResponse;
import az.millers.hcm.payroll.api.dto.PayrollResultResponse;
import az.millers.hcm.payroll.api.dto.PayrollRunResponse;
import az.millers.hcm.payroll.domain.BankFileFormat;
import az.millers.hcm.payroll.service.BankFileService;
import az.millers.hcm.payroll.service.PayrollRunService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payroll/runs")
public class PayrollRunController {

    private final PayrollRunService service;
    private final BankFileService bankFile;

    public PayrollRunController(PayrollRunService service, BankFileService bankFile) {
        this.service = service;
        this.bankFile = bankFile;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<PayrollRunResponse> list() {
        return service.list().stream().map(PayrollRunResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public PayrollRunResponse get(@PathVariable UUID id) {
        return PayrollRunResponse.from(service.get(id));
    }

    @GetMapping("/{id}/results")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<PayrollResultResponse> results(@PathVariable UUID id) {
        return service.resultsOf(id).stream().map(PayrollResultResponse::from).toList();
    }

    /** M41: allowance breakdown for one employee on a run. */
    @GetMapping("/{id}/results/{employeeId}/allowances")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<PayrollAllowanceResponse> allowances(@PathVariable UUID id,
                                                     @PathVariable UUID employeeId) {
        return service.allowancesOf(id, employeeId).stream()
                .map(PayrollAllowanceResponse::from)
                .toList();
    }

    /** M41: full per-run allowance snapshot. */
    @GetMapping("/{id}/allowances")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<PayrollAllowanceResponse> allowancesForRun(@PathVariable UUID id) {
        return service.allowancesOfRun(id).stream()
                .map(PayrollAllowanceResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public PayrollRunResponse create(@Valid @RequestBody CreateRunRequest req) {
        return PayrollRunResponse.from(service.create(req));
    }

    @PostMapping("/{id}/calculate")
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public PayrollRunResponse calculate(@PathVariable UUID id) {
        return PayrollRunResponse.from(service.calculate(id));
    }

    @PostMapping("/{id}/bonuses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public void addBonus(@PathVariable UUID id, @Valid @RequestBody AddBonusRequest req) {
        service.addBonus(id, req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN','PAYROLL_SPECIALIST')")
    public PayrollRunResponse submit(@PathVariable UUID id) {
        return PayrollRunResponse.from(service.submit(id));
    }

    @PostMapping("/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN','FINANCE_USER')")
    public PayrollRunResponse markPaid(@PathVariable UUID id) {
        return PayrollRunResponse.from(service.markPaid(id));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PayrollRunResponse close(@PathVariable UUID id) {
        return PayrollRunResponse.from(service.close(id));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN')")
    public PayrollRunResponse reopen(@PathVariable UUID id) {
        return PayrollRunResponse.from(service.reopen(id, null));
    }

    @GetMapping("/{id}/bank-file")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN','PAYROLL_SPECIALIST','FINANCE_USER')")
    public ResponseEntity<String> bankFile(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "CSV") BankFileFormat format) {
        String content = bankFile.renderBankFile(id, format);
        String ext = format == BankFileFormat.RESPUBLIKA ? "txt" : "csv";
        String contentType = format == BankFileFormat.RESPUBLIKA
                ? "text/plain; charset=utf-8"
                : "text/csv; charset=utf-8";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=payroll-bank-" + format.name().toLowerCase()
                                + "-" + id + "." + ext)
                .body(content);
    }
}
