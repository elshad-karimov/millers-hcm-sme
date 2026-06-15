package az.millers.hcm.recruitment.api;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.CandidateProfile;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.EducationRequest;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.EducationResponse;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.ExperienceRequest;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.ExperienceResponse;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.SkillRequest;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.SkillResponse;
import az.millers.hcm.recruitment.service.CandidateProfileService;
import az.millers.hcm.security.SecurityRoles;

/** M291 — Recruitment PRD §11 structured candidate profile REST. */
@RestController
@RequestMapping("/api/recruitment/candidates")
public class CandidateProfileController {

    private final CandidateProfileService service;

    public CandidateProfileController(CandidateProfileService service) {
        this.service = service;
    }

    @GetMapping("/{candidateId}/profile")
    @PreAuthorize(SecurityRoles.READ_RECRUITMENT)
    public CandidateProfile profile(@PathVariable UUID candidateId) {
        return service.profile(candidateId);
    }

    // ── Education ──────────────────────────────────────────────────────

    @PostMapping("/{candidateId}/education")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public EducationResponse addEducation(@PathVariable UUID candidateId,
                                           @Valid @RequestBody EducationRequest req) {
        return service.addEducation(candidateId, req);
    }

    @PutMapping("/education/{id}")
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public EducationResponse updateEducation(@PathVariable UUID id,
                                              @Valid @RequestBody EducationRequest req) {
        return service.updateEducation(id, req);
    }

    @DeleteMapping("/education/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public void deleteEducation(@PathVariable UUID id) {
        service.deleteEducation(id);
    }

    // ── Experience ─────────────────────────────────────────────────────

    @PostMapping("/{candidateId}/experience")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public ExperienceResponse addExperience(@PathVariable UUID candidateId,
                                             @Valid @RequestBody ExperienceRequest req) {
        return service.addExperience(candidateId, req);
    }

    @PutMapping("/experience/{id}")
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public ExperienceResponse updateExperience(@PathVariable UUID id,
                                                @Valid @RequestBody ExperienceRequest req) {
        return service.updateExperience(id, req);
    }

    @DeleteMapping("/experience/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public void deleteExperience(@PathVariable UUID id) {
        service.deleteExperience(id);
    }

    // ── Skill ──────────────────────────────────────────────────────────

    @PostMapping("/{candidateId}/skills")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public SkillResponse addSkill(@PathVariable UUID candidateId,
                                   @Valid @RequestBody SkillRequest req) {
        return service.addSkill(candidateId, req);
    }

    @PutMapping("/skills/{id}")
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public SkillResponse updateSkill(@PathVariable UUID id,
                                      @Valid @RequestBody SkillRequest req) {
        return service.updateSkill(id, req);
    }

    @DeleteMapping("/skills/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public void deleteSkill(@PathVariable UUID id) {
        service.deleteSkill(id);
    }
}
