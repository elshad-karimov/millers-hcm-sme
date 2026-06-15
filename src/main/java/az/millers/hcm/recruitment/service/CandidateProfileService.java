package az.millers.hcm.recruitment.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.CandidateProfile;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.EducationRequest;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.EducationResponse;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.ExperienceRequest;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.ExperienceResponse;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.SkillRequest;
import az.millers.hcm.recruitment.api.dto.CandidateProfileDtos.SkillResponse;
import az.millers.hcm.recruitment.domain.CandidateEducation;
import az.millers.hcm.recruitment.domain.CandidateExperience;
import az.millers.hcm.recruitment.domain.CandidateSkill;
import az.millers.hcm.recruitment.repo.CandidateEducationRepository;
import az.millers.hcm.recruitment.repo.CandidateExperienceRepository;
import az.millers.hcm.recruitment.repo.CandidateRepository;
import az.millers.hcm.recruitment.repo.CandidateSkillRepository;

/**
 * M291 — Recruitment PRD §11: structured candidate profile (education,
 * work experience, rated skills). Plain per-candidate child-row CRUD;
 * each mutation is audited under the owning candidate so the M294
 * anonymisation / retention flow can sweep it later.
 */
@Service
public class CandidateProfileService {

    private static final String MODULE = "RECRUITMENT";

    private final CandidateRepository candidates;
    private final CandidateEducationRepository education;
    private final CandidateExperienceRepository experience;
    private final CandidateSkillRepository skills;
    private final AuditService audit;

    public CandidateProfileService(CandidateRepository candidates,
                                    CandidateEducationRepository education,
                                    CandidateExperienceRepository experience,
                                    CandidateSkillRepository skills,
                                    AuditService audit) {
        this.candidates = candidates;
        this.education = education;
        this.experience = experience;
        this.skills = skills;
        this.audit = audit;
    }

    private void requireCandidate(UUID candidateId) {
        if (!candidates.existsById(candidateId)) {
            throw new ResourceNotFoundException("Candidate not found: " + candidateId);
        }
    }

    @Transactional(readOnly = true)
    public CandidateProfile profile(UUID candidateId) {
        requireCandidate(candidateId);
        return new CandidateProfile(
                education.findByCandidateIdOrderByOrdinalAscCreatedAtAsc(candidateId)
                        .stream().map(EducationResponse::from).toList(),
                experience.findByCandidateIdOrderByOrdinalAscCreatedAtAsc(candidateId)
                        .stream().map(ExperienceResponse::from).toList(),
                skills.findByCandidateIdOrderByOrdinalAscCreatedAtAsc(candidateId)
                        .stream().map(SkillResponse::from).toList());
    }

    // ── Education ──────────────────────────────────────────────────────

    @Transactional
    public EducationResponse addEducation(UUID candidateId, EducationRequest req) {
        requireCandidate(candidateId);
        CandidateEducation e = new CandidateEducation();
        e.setCandidateId(candidateId);
        applyEducation(e, req);
        CandidateEducation saved = education.save(e);
        audit.record(MODULE, "CandidateEducation", saved.getId().toString(), "CREATE",
                null, Map.of("candidateId", candidateId.toString(), "institution", saved.getInstitution()));
        return EducationResponse.from(saved);
    }

    @Transactional
    public EducationResponse updateEducation(UUID id, EducationRequest req) {
        CandidateEducation e = education.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Education not found: " + id));
        applyEducation(e, req);
        return EducationResponse.from(education.save(e));
    }

    @Transactional
    public void deleteEducation(UUID id) {
        education.deleteById(id);
        audit.record(MODULE, "CandidateEducation", id.toString(), "DELETE", null, null);
    }

    private void applyEducation(CandidateEducation e, EducationRequest req) {
        if (req.startYear() != null && req.endYear() != null && req.endYear() < req.startYear()) {
            throw new BadRequestException("End year cannot be before start year");
        }
        e.setOrdinal(req.ordinal());
        e.setInstitution(req.institution());
        e.setDegree(req.degree());
        e.setFieldOfStudy(req.fieldOfStudy());
        e.setStartYear(req.startYear());
        e.setEndYear(req.endYear());
        e.setGrade(req.grade());
    }

    // ── Experience ─────────────────────────────────────────────────────

    @Transactional
    public ExperienceResponse addExperience(UUID candidateId, ExperienceRequest req) {
        requireCandidate(candidateId);
        CandidateExperience x = new CandidateExperience();
        x.setCandidateId(candidateId);
        applyExperience(x, req);
        CandidateExperience saved = experience.save(x);
        audit.record(MODULE, "CandidateExperience", saved.getId().toString(), "CREATE",
                null, Map.of("candidateId", candidateId.toString(),
                        "company", saved.getCompany(), "title", saved.getTitle()));
        return ExperienceResponse.from(saved);
    }

    @Transactional
    public ExperienceResponse updateExperience(UUID id, ExperienceRequest req) {
        CandidateExperience x = experience.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Experience not found: " + id));
        applyExperience(x, req);
        return ExperienceResponse.from(experience.save(x));
    }

    @Transactional
    public void deleteExperience(UUID id) {
        experience.deleteById(id);
        audit.record(MODULE, "CandidateExperience", id.toString(), "DELETE", null, null);
    }

    private void applyExperience(CandidateExperience x, ExperienceRequest req) {
        if (req.startDate() != null && req.endDate() != null
                && req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }
        x.setOrdinal(req.ordinal());
        x.setCompany(req.company());
        x.setTitle(req.title());
        x.setLocation(req.location());
        x.setStartDate(req.startDate());
        // A current role has no end date.
        x.setCurrent(req.current());
        x.setEndDate(req.current() ? null : req.endDate());
        x.setDescription(req.description());
    }

    // ── Skill ──────────────────────────────────────────────────────────

    @Transactional
    public SkillResponse addSkill(UUID candidateId, SkillRequest req) {
        requireCandidate(candidateId);
        skills.findByCandidateIdAndNameIgnoreCase(candidateId, req.name().trim())
                .ifPresent(s -> { throw new BadRequestException(
                        "Skill already listed: " + s.getName()); });
        CandidateSkill s = new CandidateSkill();
        s.setCandidateId(candidateId);
        applySkill(s, req);
        CandidateSkill saved = skills.save(s);
        audit.record(MODULE, "CandidateSkill", saved.getId().toString(), "CREATE",
                null, Map.of("candidateId", candidateId.toString(), "name", saved.getName()));
        return SkillResponse.from(saved);
    }

    @Transactional
    public SkillResponse updateSkill(UUID id, SkillRequest req) {
        CandidateSkill s = skills.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found: " + id));
        // Guard a rename onto another existing skill of the same candidate.
        skills.findByCandidateIdAndNameIgnoreCase(s.getCandidateId(), req.name().trim())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new BadRequestException(
                        "Skill already listed: " + other.getName()); });
        applySkill(s, req);
        return SkillResponse.from(skills.save(s));
    }

    @Transactional
    public void deleteSkill(UUID id) {
        skills.deleteById(id);
        audit.record(MODULE, "CandidateSkill", id.toString(), "DELETE", null, null);
    }

    private void applySkill(CandidateSkill s, SkillRequest req) {
        s.setOrdinal(req.ordinal());
        s.setName(req.name().trim());
        s.setProficiency(req.proficiency());
        s.setYearsExperience(req.yearsExperience());
    }
}
