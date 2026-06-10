package az.millers.hcm.staffing.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.PositionImpactReport;
import az.millers.hcm.staffing.service.PositionImpactService;

/** M272 — PRD §43 Position Impact Analysis REST. */
@RestController
@RequestMapping("/api/positions/{id}/impact")
public class PositionImpactController {

    private final PositionImpactService service;

    public PositionImpactController(PositionImpactService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public PositionImpactReport impact(@PathVariable UUID id) {
        return service.compute(id);
    }
}
