package az.millers.hcm.corehr.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.api.dto.DependentRequest;
import az.millers.hcm.corehr.api.dto.DependentResponse;
import az.millers.hcm.corehr.api.dto.EducationRequest;
import az.millers.hcm.corehr.api.dto.EducationResponse;
import az.millers.hcm.corehr.api.dto.WorkExperienceRequest;
import az.millers.hcm.corehr.api.dto.WorkExperienceResponse;
import az.millers.hcm.corehr.domain.VerificationStatus;
import az.millers.hcm.corehr.service.EmployeeDependentService;
import az.millers.hcm.corehr.service.EmployeeEducationService;
import az.millers.hcm.corehr.service.EmployeeWorkExperienceService;

/**
 * REST surface for the three sub-entities introduced in M71 (P2-03/04/05) —
 * dependents, education, work experience. One controller, three URL prefixes
 * under {@code /api/employees/{id}/...}.
 *
 * <p>Same role gate as the M63 personal-details controller: HR_ADMIN /
 * HR_SPECIALIST / DEPARTMENT_MANAGER (scope-limited) / SYSTEM_ADMIN /
 * AUDITOR read; HR_ADMIN / HR_SPECIALIST / SYSTEM_ADMIN write.
 */
@RestController
public class EmployeeProfileTabsController {

    private static final String READ_ROLES =
            "hasAnyRole('HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER','SYSTEM_ADMIN','AUDITOR')";
    private static final String WRITE_ROLES =
            "hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')";

    private final EmployeeDependentService dependents;
    private final EmployeeEducationService education;
    private final EmployeeWorkExperienceService experience;

    public EmployeeProfileTabsController(EmployeeDependentService dependents,
                                          EmployeeEducationService education,
                                          EmployeeWorkExperienceService experience) {
        this.dependents = dependents;
        this.education = education;
        this.experience = experience;
    }

    // ── Dependents ────────────────────────────────────────────────────────────

    @GetMapping("/api/employees/{employeeId}/dependents")
    @PreAuthorize(READ_ROLES)
    public List<DependentResponse> listDependents(
            @PathVariable UUID employeeId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return dependents.listFor(employeeId, activeOnly);
    }

    @PostMapping("/api/employees/{employeeId}/dependents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public DependentResponse createDependent(@PathVariable UUID employeeId,
                                              @RequestBody @Valid DependentRequest req) {
        return dependents.create(employeeId, req);
    }

    @PutMapping("/api/employees/{employeeId}/dependents/{depId}")
    @PreAuthorize(WRITE_ROLES)
    public DependentResponse updateDependent(@PathVariable UUID employeeId,
                                              @PathVariable UUID depId,
                                              @RequestBody @Valid DependentRequest req) {
        return dependents.update(depId, req);
    }

    @DeleteMapping("/api/employees/{employeeId}/dependents/{depId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void deleteDependent(@PathVariable UUID employeeId, @PathVariable UUID depId) {
        dependents.delete(depId);
    }

    // ── Education ─────────────────────────────────────────────────────────────

    @GetMapping("/api/employees/{employeeId}/education")
    @PreAuthorize(READ_ROLES)
    public List<EducationResponse> listEducation(@PathVariable UUID employeeId) {
        return education.listFor(employeeId);
    }

    @PostMapping("/api/employees/{employeeId}/education")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public EducationResponse createEducation(@PathVariable UUID employeeId,
                                              @RequestBody @Valid EducationRequest req) {
        return education.create(employeeId, req);
    }

    @PutMapping("/api/employees/{employeeId}/education/{eduId}")
    @PreAuthorize(WRITE_ROLES)
    public EducationResponse updateEducation(@PathVariable UUID employeeId,
                                              @PathVariable UUID eduId,
                                              @RequestBody @Valid EducationRequest req) {
        return education.update(eduId, req);
    }

    @PostMapping("/api/employees/{employeeId}/education/{eduId}/verify")
    @PreAuthorize(WRITE_ROLES)
    public EducationResponse verifyEducation(@PathVariable UUID employeeId,
                                              @PathVariable UUID eduId,
                                              @RequestParam VerificationStatus status) {
        return education.verify(eduId, status);
    }

    @DeleteMapping("/api/employees/{employeeId}/education/{eduId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void deleteEducation(@PathVariable UUID employeeId, @PathVariable UUID eduId) {
        education.delete(eduId);
    }

    // ── Work experience ───────────────────────────────────────────────────────

    @GetMapping("/api/employees/{employeeId}/work-experience")
    @PreAuthorize(READ_ROLES)
    public List<WorkExperienceResponse> listExperience(@PathVariable UUID employeeId) {
        return experience.listFor(employeeId);
    }

    @PostMapping("/api/employees/{employeeId}/work-experience")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public WorkExperienceResponse createExperience(@PathVariable UUID employeeId,
                                                    @RequestBody @Valid WorkExperienceRequest req) {
        return experience.create(employeeId, req);
    }

    @PutMapping("/api/employees/{employeeId}/work-experience/{expId}")
    @PreAuthorize(WRITE_ROLES)
    public WorkExperienceResponse updateExperience(@PathVariable UUID employeeId,
                                                    @PathVariable UUID expId,
                                                    @RequestBody @Valid WorkExperienceRequest req) {
        return experience.update(expId, req);
    }

    @PostMapping("/api/employees/{employeeId}/work-experience/{expId}/verify")
    @PreAuthorize(WRITE_ROLES)
    public WorkExperienceResponse verifyExperience(@PathVariable UUID employeeId,
                                                    @PathVariable UUID expId,
                                                    @RequestParam VerificationStatus status) {
        return experience.verify(expId, status);
    }

    @DeleteMapping("/api/employees/{employeeId}/work-experience/{expId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void deleteExperience(@PathVariable UUID employeeId, @PathVariable UUID expId) {
        experience.delete(expId);
    }
}
