package az.millers.hcm.recruitment.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.recruitment.api.dto.CandidateRequest;
import az.millers.hcm.recruitment.domain.Application;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.CandidateSource;
import az.millers.hcm.recruitment.domain.JobPosting;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.CandidateRepository;
import az.millers.hcm.recruitment.repo.JobPostingRepository;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * M281 — Recruitment PRD §10: internal career portal.
 *
 * <p>Employees browse INTERNAL-channel postings and apply with one
 * click — their candidate profile is derived from their employee
 * record (no form to fill, PRD "employee profile reuse"). Internal
 * candidates are marked source=INTERNAL, which blocks the HIRED
 * transition (the M281 guard in ApplicationService) — an internal
 * selection ends in a Position Transfer (M260) or Contract Change
 * (M270), never a duplicate employee record.
 */
@Service
public class InternalCareersService {

    private static final String MODULE = "RECRUITMENT";

    public record InternalJob(
            UUID postingId,
            String title,
            String department,
            String location,
            String language,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String currency,
            LocalDate applicationDeadline,
            String description,
            String requirements,
            boolean alreadyApplied) {}

    public record InternalApplyResult(String applicationNo, String message) {}

    private final JobPostingRepository postings;
    private final VacancyRepository vacancies;
    private final CandidateRepository candidates;
    private final ApplicationRepository applications;
    private final CandidateService candidateService;
    private final ApplicationService applicationService;
    private final EmployeeContextService context;
    private final AuditService audit;

    public InternalCareersService(JobPostingRepository postings,
                                   VacancyRepository vacancies,
                                   CandidateRepository candidates,
                                   ApplicationRepository applications,
                                   CandidateService candidateService,
                                   ApplicationService applicationService,
                                   EmployeeContextService context,
                                   AuditService audit) {
        this.postings = postings;
        this.vacancies = vacancies;
        this.candidates = candidates;
        this.applications = applications;
        this.candidateService = candidateService;
        this.applicationService = applicationService;
        this.context = context;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<InternalJob> listLive() {
        Employee me = context.currentEmployee();
        UUID myCandidateId = findMyCandidateId(me);
        return postings.findLiveByChannel(TenantContext.current(), JobPosting.Channel.INTERNAL, LocalDate.now()).stream()
                .map(p -> {
                    Vacancy v = vacancies.findById(p.getVacancyId()).orElse(null);
                    if (v == null) return null;
                    boolean applied = myCandidateId != null
                            && applications.existsByVacancyIdAndCandidateId(v.getId(), myCandidateId);
                    // Internal board shows the salary range regardless of the
                    // public salary_visible flag — employees see grade ranges
                    // in the comp module anyway; hiding them here would only
                    // push people to ask HR.
                    return new InternalJob(
                            p.getId(), p.getTitle(),
                            v.getDepartment(), v.getLocation(), p.getLanguage(),
                            v.getSalaryMin(), v.getSalaryMax(), v.getCurrency(),
                            p.getApplicationDeadline(),
                            p.getDescription(), p.getRequirements(),
                            applied);
                })
                .filter(j -> j != null)
                .toList();
    }

    @Transactional
    public InternalApplyResult apply(UUID postingId) {
        Employee me = context.currentEmployee();

        JobPosting p = postings.findById(postingId)
                .orElseThrow(() -> new ResourceNotFoundException("Posting not found: " + postingId));
        Vacancy v = vacancies.findById(p.getVacancyId()).orElse(null);
        if (v == null || v.isConfidential()
                || p.getChannel() != JobPosting.Channel.INTERNAL
                || !p.getStatus().isLive()
                || (p.getApplicationDeadline() != null
                    && p.getApplicationDeadline().isBefore(LocalDate.now()))) {
            throw new ResourceNotFoundException("Posting not found: " + postingId);
        }

        // Profile reuse (PRD §10) — candidate derived from the employee
        // record; one candidate master per employee email (PRD §12).
        UUID candidateId = findMyCandidateId(me);
        Candidate candidate = candidateId != null
                ? candidates.findById(candidateId).orElseThrow()
                : candidateService.create(new CandidateRequest(
                        me.getFirstName(), me.getLastName(), me.getMiddleName(),
                        me.getEmail(), me.getPhone(),
                        CandidateSource.INTERNAL,
                        null, null, null, null,
                        null,
                        "Internal applicant — " + me.getEmployeeNo()));

        if (applications.existsByVacancyIdAndCandidateId(v.getId(), candidate.getId())) {
            throw new BadRequestException("You have already applied for this position");
        }

        Application a = applicationService.apply(v.getId(), candidate.getId());
        a.setPostingId(p.getId());
        applications.save(a);

        audit.record(MODULE, "Application", a.getId().toString(), "INTERNAL_APPLY",
                null,
                Map.of("postingNo", p.getPostingNo(),
                        "vacancyNo", v.getVacancyNo(),
                        "employeeNo", me.getEmployeeNo()));
        return new InternalApplyResult(a.getApplicationNo(),
                "Application submitted — reference " + a.getApplicationNo());
    }

    /** The candidate row backing this employee, if they ever applied before. */
    private UUID findMyCandidateId(Employee me) {
        if (me.getEmail() == null || me.getEmail().isBlank()) return null;
        return candidates.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(me.getEmail())
                .map(Candidate::getId)
                .orElse(null);
    }
}
