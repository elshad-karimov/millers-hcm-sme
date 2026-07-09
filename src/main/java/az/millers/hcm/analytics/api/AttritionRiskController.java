package az.millers.hcm.analytics.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.analytics.domain.AttritionRisk;
import az.millers.hcm.analytics.service.AttritionRiskService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M476 — Attrition risk endpoints (HR_ADMIN only).
 * NEVER exposed to employees/managers.
 */
@RestController
@RequestMapping("/api/analytics/attrition-risk")
public class AttritionRiskController {

    private final AttritionRiskService service;

    public AttritionRiskController(AttritionRiskService service) {
        this.service = service;
    }

    /**
     * List all attrition risk scores (HR_ADMIN only, ordered by score desc).
     */
    @GetMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public List<AttritionRisk> listAll() {
        return service.listAll();
    }

    /**
     * Manual recompute of all attrition risk scores (HR_ADMIN only).
     */
    @PostMapping("/recompute")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void recompute() {
        service.recomputeAll();
    }
}
