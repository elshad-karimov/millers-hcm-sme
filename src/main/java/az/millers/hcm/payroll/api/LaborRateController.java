package az.millers.hcm.payroll.api;

import az.millers.hcm.payroll.api.dto.LaborRateRequest;
import az.millers.hcm.payroll.domain.LaborRate;
import az.millers.hcm.payroll.service.LaborRateService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * M485: Labor rate endpoints (Finance/HR confidential).
 */
@RestController
@RequestMapping("/api/payroll/labor-rates")
public class LaborRateController {

    private final LaborRateService service;

    public LaborRateController(LaborRateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<LaborRate> listRates() {
        return service.listRates();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public LaborRate getRate(@PathVariable UUID id) {
        return service.getRate(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public LaborRate createRate(@Valid @RequestBody LaborRateRequest req) {
        LaborRate rate = new LaborRate();
        rate.setGradeId(req.gradeId());
        rate.setPositionId(req.positionId());
        rate.setHourlyRate(req.hourlyRate());
        rate.setEffectiveFrom(req.effectiveFrom());
        rate.setEffectiveTo(req.effectiveTo());
        return service.createRate(rate);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public LaborRate updateRate(@PathVariable UUID id, @Valid @RequestBody LaborRateRequest req) {
        LaborRate updated = new LaborRate();
        updated.setHourlyRate(req.hourlyRate());
        updated.setEffectiveFrom(req.effectiveFrom());
        updated.setEffectiveTo(req.effectiveTo());
        return service.updateRate(id, updated);
    }
}
