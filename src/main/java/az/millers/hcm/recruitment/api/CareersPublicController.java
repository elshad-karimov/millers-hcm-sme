package az.millers.hcm.recruitment.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.domain.JobPosting;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.repo.JobPostingRepository;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.recruitment.service.PublicCareersApplyService;
import az.millers.hcm.security.CurrentRequest;

/**
 * M279 — Recruitment PRD §9: public career portal API.
 *
 * <p>ANONYMOUS endpoints (permitAll in SecurityConfig, same pattern as
 * the M122 pre-boarding and M139 letter-verify public APIs). Serves
 * ONLY live EXTERNAL postings of non-confidential requisitions, and
 * only public-safe fields — no recruiter/hiring-manager IDs, no cost
 * centre, salary range only when the posting opts in (PRD §8 salary
 * visibility control).
 */
@RestController
@RequestMapping("/api/public/careers")
public class CareersPublicController {

    private final JobPostingRepository postings;
    private final VacancyRepository vacancies;
    private final PublicCareersApplyService applyService;
    private final CurrentRequest currentRequest;

    public CareersPublicController(JobPostingRepository postings,
                                    VacancyRepository vacancies,
                                    PublicCareersApplyService applyService,
                                    CurrentRequest currentRequest) {
        this.postings = postings;
        this.vacancies = vacancies;
        this.applyService = applyService;
        this.currentRequest = currentRequest;
    }

    /** M280 — anonymous application submission (PRD §9). */
    @PostMapping("/jobs/{id}/apply")
    public PublicCareersApplyService.PublicApplyResult apply(
            @PathVariable UUID id,
            @RequestBody PublicCareersApplyService.PublicApplyRequest req) {
        return applyService.apply(id, req, currentRequest.ipAddress());
    }

    /**
     * M282 — anonymous status tracking. The candidate sees only their
     * own application (the token is the credential) and only a
     * candidate-friendly summary — internal pipeline stages collapse
     * to four public phases so the company's process detail and any
     * other candidates' data stay invisible (PRD §55: "Candidate —
     * view own application only").
     */
    public record TrackingStatus(
            String applicationNo,
            String jobTitle,
            LocalDate submittedOn,
            String status) {}

    @GetMapping("/track/{token}")
    @Transactional(readOnly = true)
    public TrackingStatus track(@PathVariable String token) {
        var a = applyService.findByTrackingToken(token);
        Vacancy v = vacancies.findById(a.getVacancyId()).orElse(null);
        String title = v == null ? "—" : v.getTitle();
        if (a.getPostingId() != null) {
            title = postings.findById(a.getPostingId())
                    .map(JobPosting::getTitle)
                    .orElse(title);
        }
        return new TrackingStatus(
                a.getApplicationNo(),
                title,
                a.getCreatedAt() == null ? null : a.getCreatedAt().toLocalDate(),
                publicStage(a));
    }

    /** Collapse the internal pipeline to candidate-safe phases. */
    private static String publicStage(az.millers.hcm.recruitment.domain.Application a) {
        return switch (a.getCurrentStage()) {
            case CV_SCREENING -> "Under review";
            case HR_INTERVIEW, TECHNICAL_INTERVIEW, FINAL_INTERVIEW -> "Interview stage";
            case OFFER -> "Offer stage";
            case HIRED -> "Hired — welcome aboard!";
            case REJECTED -> "Process closed";
            case WITHDRAWN -> "Withdrawn";
        };
    }

    /** Public job card — the list view. */
    public record PublicJob(
            UUID id,
            String title,
            String department,
            String location,
            String language,
            String employmentType,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String currency,
            LocalDate applicationDeadline,
            LocalDate publishedOn) {}

    /** Public job detail — adds the long-form fields. */
    public record PublicJobDetail(
            UUID id,
            String title,
            String department,
            String location,
            String language,
            String employmentType,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String currency,
            LocalDate applicationDeadline,
            LocalDate publishedOn,
            String description,
            String requirements,
            String benefits) {}

    @GetMapping("/jobs")
    @Transactional(readOnly = true)
    public List<PublicJob> jobs(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) String location,
                                 @RequestParam(required = false) String lang) {
        return postings.findLiveByChannel(JobPosting.Channel.EXTERNAL, LocalDate.now()).stream()
                .map(p -> toCard(p, vacancies.findById(p.getVacancyId()).orElse(null)))
                .filter(j -> j != null)
                .filter(j -> q == null || q.isBlank()
                        || j.title().toLowerCase().contains(q.toLowerCase()))
                .filter(j -> location == null || location.isBlank()
                        || (j.location() != null
                            && j.location().toLowerCase().contains(location.toLowerCase())))
                .filter(j -> lang == null || lang.isBlank()
                        || j.language().equalsIgnoreCase(lang))
                .toList();
    }

    @GetMapping("/jobs/{id}")
    @Transactional(readOnly = true)
    public PublicJobDetail job(@PathVariable UUID id) {
        JobPosting p = postings.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + id));
        Vacancy v = vacancies.findById(p.getVacancyId()).orElse(null);
        // Re-validate every liveness condition on direct access — a
        // stale link must not expose paused/closed/confidential jobs.
        if (v == null || v.isConfidential()
                || p.getChannel() != JobPosting.Channel.EXTERNAL
                || !p.getStatus().isLive()
                || (p.getApplicationDeadline() != null
                    && p.getApplicationDeadline().isBefore(LocalDate.now()))) {
            throw new ResourceNotFoundException("Job not found: " + id);
        }
        return new PublicJobDetail(
                p.getId(), p.getTitle(),
                v.getDepartment(), v.getLocation(),
                p.getLanguage(), v.getEmploymentType(),
                p.isSalaryVisible() ? v.getSalaryMin() : null,
                p.isSalaryVisible() ? v.getSalaryMax() : null,
                p.isSalaryVisible() ? v.getCurrency() : null,
                p.getApplicationDeadline(),
                p.getPublishedAt() == null ? null : p.getPublishedAt().toLocalDate(),
                p.getDescription(), p.getRequirements(), p.getBenefitsDescription());
    }

    private PublicJob toCard(JobPosting p, Vacancy v) {
        if (v == null) return null;
        return new PublicJob(
                p.getId(), p.getTitle(),
                v.getDepartment(), v.getLocation(),
                p.getLanguage(), v.getEmploymentType(),
                p.isSalaryVisible() ? v.getSalaryMin() : null,
                p.isSalaryVisible() ? v.getSalaryMax() : null,
                p.isSalaryVisible() ? v.getCurrency() : null,
                p.getApplicationDeadline(),
                p.getPublishedAt() == null ? null : p.getPublishedAt().toLocalDate());
    }
}
