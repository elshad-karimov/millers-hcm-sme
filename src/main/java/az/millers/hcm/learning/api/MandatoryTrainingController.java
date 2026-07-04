package az.millers.hcm.learning.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.learning.domain.MandatoryTrainingRule;
import az.millers.hcm.learning.service.MandatoryTrainingService;
import az.millers.hcm.security.SecurityRoles;

/** Mandatory / compliance training API (HCM_14 M406). */
@RestController
@RequestMapping("/api/learning/mandatory-training")
public class MandatoryTrainingController {

    private final MandatoryTrainingService service;

    public MandatoryTrainingController(MandatoryTrainingService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<MandatoryTrainingRule> rules() {
        return service.list();
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public MandatoryTrainingRule create(@RequestBody MandatoryTrainingService.RuleRequest req) {
        return service.save(null, req);
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public MandatoryTrainingRule update(@PathVariable UUID id,
                                        @RequestBody MandatoryTrainingService.RuleRequest req) {
        return service.save(id, req);
    }

    /** Manual sweep trigger (the scheduler runs daily at 05:30). */
    @PostMapping("/sweep")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public MandatoryTrainingService.SweepResult sweep() {
        return service.runSweep();
    }

    @GetMapping("/compliance")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<MandatoryTrainingService.RuleCompliance> compliance() {
        return service.compliance();
    }
}
