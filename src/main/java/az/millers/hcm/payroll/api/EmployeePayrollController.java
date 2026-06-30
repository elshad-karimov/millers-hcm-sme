package az.millers.hcm.payroll.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.payroll.domain.AnnualTaxCertificate;
import az.millers.hcm.payroll.service.AnnualTaxCertificateService;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * M354: Employee self-service endpoints for payroll data.
 */
@RestController
@RequestMapping("/api/payroll/employees/me")
public class EmployeePayrollController {

    private final AnnualTaxCertificateService certificateService;
    private final EmployeeContextService employeeContext;

    public EmployeePayrollController(AnnualTaxCertificateService certificateService,
                                      EmployeeContextService employeeContext) {
        this.certificateService = certificateService;
        this.employeeContext = employeeContext;
    }

    /**
     * M354: Employee downloads own annual tax certificate.
     * GET /api/payroll/employees/me/year-end/certificate?year=2026
     */
    @GetMapping("/year-end/certificate")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<byte[]> yearEndCertificate(@RequestParam int year) {
        Employee emp = employeeContext.currentEmployee();
        AnnualTaxCertificate cert = certificateService.employeeCertificate(emp.getId(), year);
        byte[] pdfBytes = certificateService.downloadCertificate(cert.getId());

        String filename = String.format("Tax_Certificate_%d_%s.pdf", year, emp.getEmployeeNo());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }
}
