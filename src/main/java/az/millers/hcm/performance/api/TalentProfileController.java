package az.millers.hcm.performance.api;
import az.millers.hcm.common.tenant.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.domain.CareerInterest;
import az.millers.hcm.performance.domain.TalentProfile;
import az.millers.hcm.performance.repo.CareerInterestRepository;
import az.millers.hcm.performance.repo.TalentProfileRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * HCM_15 M409 — talent profile (HR/executive-ONLY: HiPo, retention risk —
 * the employee NEVER sees these) + employee-editable career interests.
 * Thin CRUD kept in the controller.
 */
@RestController
@RequestMapping("/api/talent")
public class TalentProfileController {

    private static final List<String> RISKS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public record ProfileRequest(Boolean hipoFlag, String hipoSource, String retentionRisk,
                                 String riskReason, String retentionActionPlan,
                                 String mobilityWillingness, String relocationPreference,
                                 String careerAspirations, String notes) {}

    public record InterestRequest(String targetRole, String targetDepartment,
                                  String targetLocation, String timeline, String note) {}

    private final TalentProfileRepository profiles;
    private final CareerInterestRepository interests;
    private final EmployeeRepository employees;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public TalentProfileController(TalentProfileRepository profiles,
                                   CareerInterestRepository interests,
                                   EmployeeRepository employees,
                                   AccessScopeService accessScope,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.profiles = profiles;
        this.interests = interests;
        this.employees = employees;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Talent profile — HR-only surface (§15.17 confidentiality) ──────────

    @GetMapping("/profiles")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    public List<TalentProfile> profiles(@RequestParam(required = false) Boolean hipoOnly,
                                        @RequestParam(required = false) String retentionRisk) {
        if (Boolean.TRUE.equals(hipoOnly)) {
            return profiles.findByTenantIdAndHipoFlagTrueOrderByUpdatedAtDesc(TenantContext.current());
        }
        if (retentionRisk != null) {
            return profiles.findByTenantIdAndRetentionRiskOrderByUpdatedAtDesc(TenantContext.current(), retentionRisk);
        }
        return profiles.findByTenantIdOrderByUpdatedAtDesc(TenantContext.current());
    }

    @GetMapping("/profiles/employees/{employeeId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    public TalentProfile profileOf(@PathVariable UUID employeeId) {
        return profiles.findByTenantIdAndEmployeeId(TenantContext.current(), employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("No talent profile for employee"));
    }

    /** Upsert — one profile per employee. */
    @PutMapping("/profiles/employees/{employeeId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public TalentProfile upsert(@PathVariable UUID employeeId, @RequestBody ProfileRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        if (req.retentionRisk() != null && !RISKS.contains(req.retentionRisk())) {
            throw new BadRequestException("retentionRisk must be one of " + RISKS);
        }
        TalentProfile p = profiles.findByTenantIdAndEmployeeId(TenantContext.current(), employeeId)
                .orElseGet(() -> {
                    TalentProfile n = new TalentProfile();
                    n.setTenantId(TenantContext.current());
                    n.setEmployeeId(employeeId);
                    return n;
                });
        if (req.hipoFlag() != null) {
            p.setHipoFlag(req.hipoFlag());
            p.setHipoSource(req.hipoFlag() ? (req.hipoSource() == null ? "MANUAL" : req.hipoSource()) : null);
        }
        p.setRetentionRisk(req.retentionRisk());
        p.setRiskReason(req.riskReason());
        p.setRetentionActionPlan(req.retentionActionPlan());
        p.setMobilityWillingness(req.mobilityWillingness());
        p.setRelocationPreference(req.relocationPreference());
        p.setCareerAspirations(req.careerAspirations());
        p.setNotes(req.notes());
        p.setUpdatedBy(currentRequest.username());
        TalentProfile saved = profiles.save(p);
        audit.record("TALENT", "TalentProfile", saved.getId().toString(), "UPSERT",
                null, Map.of("employeeId", employeeId.toString(),
                        "hipo", String.valueOf(saved.isHipoFlag()),
                        "risk", String.valueOf(saved.getRetentionRisk())));
        return saved;
    }

    // ── Career interests — employee-editable, hierarchy-scoped reads ───────

    @GetMapping("/interests")
    @PreAuthorize("isAuthenticated()")
    public List<CareerInterest> interests(@RequestParam UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new AccessDeniedException("Employee is outside your access scope");
        }
        return interests.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TenantContext.current(), employeeId);
    }

    @PostMapping("/interests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public CareerInterest addInterest(@RequestParam UUID employeeId,
                                      @RequestBody InterestRequest req) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new AccessDeniedException("Employee is outside your access scope");
        }
        if (req.targetRole() == null || req.targetRole().isBlank()) {
            throw new BadRequestException("A target role is required");
        }
        CareerInterest i = new CareerInterest();
        i.setTenantId(TenantContext.current());
        i.setEmployeeId(employeeId);
        i.setTargetRole(req.targetRole().trim());
        i.setTargetDepartment(req.targetDepartment());
        i.setTargetLocation(req.targetLocation());
        i.setTimeline(req.timeline());
        i.setNote(req.note());
        CareerInterest saved = interests.save(i);
        audit.record("TALENT", "CareerInterest", saved.getId().toString(), "CREATE",
                null, req.targetRole().trim());
        return saved;
    }

    @DeleteMapping("/interests/{id}")
    @PreAuthorize("isAuthenticated()")
    public void removeInterest(@PathVariable UUID id) {
        CareerInterest i = interests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interest not found: " + id));
        if (!accessScope.isAccessible(i.getEmployeeId())) {
            throw new AccessDeniedException("Interest is outside your access scope");
        }
        interests.delete(i);
        audit.record("TALENT", "CareerInterest", id.toString(), "DELETE", null, i.getTargetRole());
    }
}
