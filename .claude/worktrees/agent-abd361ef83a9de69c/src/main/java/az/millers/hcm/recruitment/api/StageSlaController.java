package az.millers.hcm.recruitment.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.StageSlaDtos.SlaBreachReport;
import az.millers.hcm.recruitment.api.dto.StageSlaDtos.SlaConfig;
import az.millers.hcm.recruitment.api.dto.StageSlaDtos.SlaConfigUpdate;
import az.millers.hcm.recruitment.service.StageSlaService;
import az.millers.hcm.security.SecurityRoles;

/** M288 — Recruitment PRD §14/§43 stage-SLA REST. */
@RestController
@RequestMapping("/api/recruitment/sla")
public class StageSlaController {

    private final StageSlaService service;

    public StageSlaController(StageSlaService service) {
        this.service = service;
    }

    @GetMapping("/config")
    @PreAuthorize(SecurityRoles.READ_INTERVIEWS)
    public List<SlaConfig> config() {
        return service.listConfig();
    }

    @PutMapping("/config/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public SlaConfig updateConfig(@PathVariable UUID id,
                                   @Valid @RequestBody SlaConfigUpdate req) {
        return service.updateConfig(id, req);
    }

    /** The overdue / due-soon breach report (PRD §43). */
    @GetMapping("/breaches")
    @PreAuthorize(SecurityRoles.READ_INTERVIEWS)
    public SlaBreachReport breaches() {
        return service.breaches();
    }
}
