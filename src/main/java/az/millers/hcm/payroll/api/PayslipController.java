package az.millers.hcm.payroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.service.PayslipPdfService;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.service.EmployeeContextService;

@RestController
@RequestMapping("/api/payroll")
public class PayslipController {

    private final PayslipPdfService payslipService;
    private final PayrollResultRepository resultRepo;
    private final EmployeeContextService employeeContext;

    public PayslipController(PayslipPdfService payslipService,
                             PayrollResultRepository resultRepo,
                             EmployeeContextService employeeContext) {
        this.payslipService = payslipService;
        this.resultRepo = resultRepo;
        this.employeeContext = employeeContext;
    }

    @PostMapping("/runs/{id}/generate-payslips")
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public Map<String, Object> generatePayslips(@PathVariable UUID id) {
        return payslipService.bulkGenerate(id);
    }

    @GetMapping("/runs/{id}/payslips/{employeeId}/download")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public ResponseEntity<byte[]> downloadPayslip(@PathVariable UUID id, @PathVariable UUID employeeId) {
        byte[] pdfBytes = payslipService.download(id, employeeId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payslip.pdf")
                .body(pdfBytes);
    }

    @PostMapping("/runs/{id}/send-payslips")
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public Map<String, Object> sendPayslips(@PathVariable UUID id) {
        return payslipService.sendByEmail(id);
    }

    // Employee self-service endpoints

    @GetMapping("/employees/me/payslips")
    @PreAuthorize("hasRole('" + SecurityRoles.R_EMPLOYEE + "')")
    public List<PayslipListItem> myPayslips() {
        UUID employeeId = employeeContext.currentEmployee().getId();
        List<PayrollResult> results = resultRepo.findByEmployeeIdOrderByCreatedAtDesc(employeeId);

        return results.stream()
                .map(r -> new PayslipListItem(
                        r.getRunId(),
                        r.getPayslipNo(),
                        r.getPeriodStart(),
                        r.getGrossAmount(),
                        r.getNetAmount()
                ))
                .collect(Collectors.toList());
    }

    @GetMapping("/employees/me/payslips/{runId}/download")
    @PreAuthorize("hasRole('" + SecurityRoles.R_EMPLOYEE + "')")
    public ResponseEntity<byte[]> myPayslipDownload(@PathVariable UUID runId) {
        UUID employeeId = employeeContext.currentEmployee().getId();

        // Verify the result belongs to this employee
        PayrollResult result = resultRepo.findByRunIdAndEmployeeId(runId, employeeId)
                .orElseThrow(() -> new az.millers.hcm.common.ResourceNotFoundException("Payslip not found"));

        if (!result.getEmployeeId().equals(employeeId)) {
            throw new az.millers.hcm.common.BadRequestException("Access denied");
        }

        byte[] pdfBytes = payslipService.download(runId, employeeId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payslip_" + result.getPayslipNo() + ".pdf")
                .body(pdfBytes);
    }

    public record PayslipListItem(
            UUID runId,
            String payslipNo,
            java.time.LocalDate periodStart,
            java.math.BigDecimal gross,
            java.math.BigDecimal net
    ) {}
}
