package az.millers.hcm.payroll.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.CompensationRequest;
import az.millers.hcm.payroll.api.dto.CompensationResponse;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.service.CompensationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payroll/compensation")
public class CompensationController {

    private final CompensationService service;

    public CompensationController(CompensationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','PAYROLL_SPECIALIST','AUDITOR')")
    public List<CompensationResponse> history(@RequestParam UUID employeeId) {
        return service.historyFor(employeeId).stream()
                .map(CompensationResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public CompensationResponse upsert(@Valid @RequestBody CompensationRequest req) {
        EmployeeCompensation c = new EmployeeCompensation();
        c.setEmployeeId(req.employeeId());
        c.setMonthlyBaseSalary(req.monthlyBaseSalary());
        c.setCurrency(req.currency() == null ? "AZN" : req.currency());
        c.setEffectiveFrom(req.effectiveFrom());
        c.setEffectiveTo(req.effectiveTo());
        c.setReason(req.reason());
        return CompensationResponse.from(service.upsert(c));
    }
}
