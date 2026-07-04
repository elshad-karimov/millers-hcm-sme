package az.millers.hcm.learning.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.learning.api.dto.SkillInventoryReportDto;
import az.millers.hcm.learning.service.SkillInventoryService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M420: Skill inventory reports.
 */
@RestController
@RequestMapping("/api/reports/skills")
public class SkillInventoryController {

    private final SkillInventoryService service;

    public SkillInventoryController(SkillInventoryService service) {
        this.service = service;
    }

    @GetMapping("/inventory")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<SkillInventoryReportDto.ByDepartmentRow> inventoryByDepartment() {
        return service.inventoryByDepartment();
    }

    @GetMapping("/critical")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<SkillInventoryReportDto.CriticalSkillRow> criticalSkillsCoverage() {
        return service.criticalSkillsCoverage();
    }

    @GetMapping("/certifications")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<SkillInventoryReportDto.CertificationRow> certificationCoverage() {
        return service.certificationCoverage();
    }
}
