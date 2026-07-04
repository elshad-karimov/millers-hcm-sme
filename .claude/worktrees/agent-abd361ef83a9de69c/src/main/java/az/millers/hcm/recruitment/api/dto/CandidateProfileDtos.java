package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import az.millers.hcm.recruitment.domain.CandidateEducation;
import az.millers.hcm.recruitment.domain.CandidateExperience;
import az.millers.hcm.recruitment.domain.CandidateSkill;
import az.millers.hcm.recruitment.domain.SkillProficiency;

/** M291 — Recruitment PRD §11 structured candidate profile DTOs. */
public final class CandidateProfileDtos {

    private CandidateProfileDtos() {}

    // ── Education ──────────────────────────────────────────────────────

    public record EducationRequest(
            int ordinal,
            @NotBlank @Size(max = 200) String institution,
            @Size(max = 120) String degree,
            @Size(max = 160) String fieldOfStudy,
            Integer startYear,
            Integer endYear,
            @Size(max = 60) String grade) {}

    public record EducationResponse(
            UUID id, int ordinal, String institution, String degree,
            String fieldOfStudy, Integer startYear, Integer endYear, String grade) {

        public static EducationResponse from(CandidateEducation e) {
            return new EducationResponse(e.getId(), e.getOrdinal(), e.getInstitution(),
                    e.getDegree(), e.getFieldOfStudy(), e.getStartYear(), e.getEndYear(),
                    e.getGrade());
        }
    }

    // ── Experience ─────────────────────────────────────────────────────

    public record ExperienceRequest(
            int ordinal,
            @NotBlank @Size(max = 200) String company,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 160) String location,
            LocalDate startDate,
            LocalDate endDate,
            boolean current,
            String description) {}

    public record ExperienceResponse(
            UUID id, int ordinal, String company, String title, String location,
            LocalDate startDate, LocalDate endDate, boolean current, String description) {

        public static ExperienceResponse from(CandidateExperience x) {
            return new ExperienceResponse(x.getId(), x.getOrdinal(), x.getCompany(),
                    x.getTitle(), x.getLocation(), x.getStartDate(), x.getEndDate(),
                    x.isCurrent(), x.getDescription());
        }
    }

    // ── Skill ──────────────────────────────────────────────────────────

    public record SkillRequest(
            int ordinal,
            @NotBlank @Size(max = 100) String name,
            SkillProficiency proficiency,
            BigDecimal yearsExperience) {}

    public record SkillResponse(
            UUID id, int ordinal, String name,
            SkillProficiency proficiency, BigDecimal yearsExperience) {

        public static SkillResponse from(CandidateSkill s) {
            return new SkillResponse(s.getId(), s.getOrdinal(), s.getName(),
                    s.getProficiency(), s.getYearsExperience());
        }
    }

    /** Composite — the whole structured profile in one round-trip. */
    public record CandidateProfile(
            List<EducationResponse> education,
            List<ExperienceResponse> experience,
            List<SkillResponse> skills) {}
}
