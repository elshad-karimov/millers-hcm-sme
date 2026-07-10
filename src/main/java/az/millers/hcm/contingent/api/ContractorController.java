package az.millers.hcm.contingent.api;

import az.millers.hcm.contingent.api.dto.ContractorEngagementRequest;
import az.millers.hcm.contingent.api.dto.ConvertToFTERequest;
import az.millers.hcm.contingent.domain.ContractorEngagement;
import az.millers.hcm.contingent.service.ContractorService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contingent/contractors")
public class ContractorController {
    private final ContractorService service;

    public ContractorController(ContractorService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<ContractorEngagement> listEngagements(@RequestParam(required = false) String status) {
        return service.listEngagements(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public ContractorEngagement getEngagement(@PathVariable UUID id) {
        return service.getEngagement(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ContractorEngagement createEngagement(@Valid @RequestBody ContractorEngagementRequest req) {
        ContractorEngagement engagement = new ContractorEngagement();
        engagement.setEmployeeId(req.employeeId());
        engagement.setVendorAgencyId(req.vendorAgencyId());
        engagement.setContractStart(req.contractStart());
        engagement.setContractEnd(req.contractEnd());
        engagement.setRate(req.rate());
        engagement.setRateUnit(req.rateUnit());
        engagement.setPoNumber(req.poNumber());
        engagement.setTenureAlertDays(req.tenureAlertDays() != null ? req.tenureAlertDays() : 30);
        engagement.setStatus(req.status() != null ? req.status() : "ACTIVE");
        engagement.setNotes(req.notes());
        return service.createEngagement(engagement);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ContractorEngagement updateEngagement(@PathVariable UUID id, @Valid @RequestBody ContractorEngagementRequest req) {
        ContractorEngagement updated = new ContractorEngagement();
        updated.setContractStart(req.contractStart());
        updated.setContractEnd(req.contractEnd());
        updated.setRate(req.rate());
        updated.setRateUnit(req.rateUnit());
        updated.setPoNumber(req.poNumber());
        updated.setTenureAlertDays(req.tenureAlertDays() != null ? req.tenureAlertDays() : 30);
        updated.setStatus(req.status() != null ? req.status() : "ACTIVE");
        updated.setNotes(req.notes());
        return service.updateEngagement(id, updated);
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void convertToFTE(@PathVariable UUID id, @Valid @RequestBody ConvertToFTERequest req) {
        service.convertToFTE(id, req.newEmploymentType(), req.effectiveDate());
    }
}
