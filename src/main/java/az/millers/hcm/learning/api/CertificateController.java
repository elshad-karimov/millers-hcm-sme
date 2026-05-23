package az.millers.hcm.learning.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.learning.api.dto.CertificateResponse;
import az.millers.hcm.learning.repo.CertificateRepository;

@RestController
@RequestMapping("/api/learning/certificates")
public class CertificateController {

    private final CertificateRepository certificates;

    public CertificateController(CertificateRepository certificates) {
        this.certificates = certificates;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CertificateResponse> list(@RequestParam(required = false) UUID employeeId) {
        var rows = employeeId == null
                ? certificates.findAllByOrderByIssuedAtDesc()
                : certificates.findByEmployeeIdOrderByIssuedAtDesc(employeeId);
        return rows.stream().map(CertificateResponse::from).toList();
    }
}
