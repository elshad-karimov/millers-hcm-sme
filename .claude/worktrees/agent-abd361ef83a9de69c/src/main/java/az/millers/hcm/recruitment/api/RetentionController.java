package az.millers.hcm.recruitment.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.RetentionDtos.AnonymizeResult;
import az.millers.hcm.recruitment.api.dto.RetentionDtos.RetentionReport;
import az.millers.hcm.recruitment.api.dto.RetentionDtos.SweepResult;
import az.millers.hcm.recruitment.service.ConsentRetentionService;
import az.millers.hcm.security.SecurityRoles;

/** M294 — Recruitment PRD §46/§47 consent-retention + anonymisation REST. */
@RestController
@RequestMapping("/api/recruitment")
public class RetentionController {

    private final ConsentRetentionService service;

    public RetentionController(ConsentRetentionService service) {
        this.service = service;
    }

    /** Candidates due now + approaching the retention window. */
    @GetMapping("/retention/report")
    @PreAuthorize(SecurityRoles.READ_RECRUITMENT)
    public RetentionReport report() {
        return service.report();
    }

    /** Anonymise everyone past the window; {@code dryRun=true} just lists them. */
    @PostMapping("/retention/sweep")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public SweepResult sweep(@RequestParam(defaultValue = "true") boolean dryRun) {
        return service.runSweep(dryRun);
    }

    /** On-demand right-to-erasure for one candidate. */
    @PostMapping("/candidates/{id}/anonymize")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AnonymizeResult anonymize(@PathVariable UUID id,
                                      @RequestParam(required = false) String reason) {
        return service.anonymize(id, reason);
    }
}
