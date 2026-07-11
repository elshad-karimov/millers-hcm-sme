package az.millers.hcm.payroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.api.dto.AnnualTaxCertificateResponse;
import az.millers.hcm.payroll.domain.AnnualTaxCertificate;
import az.millers.hcm.payroll.service.AnnualTaxCertificateService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/payroll/year-end")
public class YearEndController {

    private final AnnualTaxCertificateService service;
    private final EmployeeRepository employeeRepo;

    public YearEndController(AnnualTaxCertificateService service,
                              EmployeeRepository employeeRepo) {
        this.service = service;
        this.employeeRepo = employeeRepo;
    }

    /**
     * M354: Generate annual payroll summaries for all employees with PAID REGULAR runs in the year.
     */
    @PostMapping("/generate-summary")
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public ResponseEntity<Map<String, String>> generateSummary(@RequestParam int year) {
        service.generateSummary(year);
        return ResponseEntity.ok(Map.of("status", "generated", "year", String.valueOf(year)));
    }

    /**
     * M354: List all annual tax certificates for a year.
     */
    @GetMapping("/certificates")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<AnnualTaxCertificateResponse> listCertificates(@RequestParam int year) {
        List<AnnualTaxCertificate> certs = service.listCertificates(year);

        List<UUID> employeeIds = certs.stream()
                .map(AnnualTaxCertificate::getEmployeeId)
                .distinct()
                .toList();
        Map<UUID, Employee> employees = employeeRepo.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        return certs.stream()
                .map(cert -> {
                    Employee emp = employees.get(cert.getEmployeeId());
                    String empNo = emp != null ? emp.getEmployeeNo() : "Unknown";
                    String empName = emp != null ? emp.getFirstName() + " " + emp.getLastName() : "Unknown";
                    String maskedId = emp != null && emp.getNationalId() != null
                            ? maskNationalId(emp.getNationalId())
                            : "N/A";
                    return AnnualTaxCertificateResponse.from(cert, empNo, empName, maskedId);
                })
                .toList();
    }

    /**
     * M354: Generate PDF certificates for all summaries in the year.
     */
    @PostMapping("/certificates/generate")
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public ResponseEntity<Map<String, String>> generateCertificates(@RequestParam int year) {
        service.generateCertificates(year);
        return ResponseEntity.ok(Map.of("status", "generated", "year", String.valueOf(year)));
    }

    /**
     * M354: Download a certificate PDF.
     */
    @GetMapping("/certificates/{id}/download")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable UUID id) {
        byte[] pdfBytes = service.downloadCertificate(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tax_certificate.pdf\"")
                .body(pdfBytes);
    }

    private String maskNationalId(String nationalId) {
        String m = az.millers.hcm.security.PiiMasking.maskDocumentId(nationalId);
        return m != null ? m : "•••";
    }
}
