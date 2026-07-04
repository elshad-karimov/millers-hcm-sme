package az.millers.hcm.recruitment.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.JobPostingDtos.PostingRequest;
import az.millers.hcm.recruitment.api.dto.JobPostingDtos.PostingResponse;
import az.millers.hcm.recruitment.domain.JobPosting;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.domain.VacancyStatus;
import az.millers.hcm.recruitment.repo.JobPostingRepository;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M278 — Recruitment PRD §8: Job Posting Management.
 *
 * <p>Postings are channel/language-specific advertisements of an
 * approved requisition. Publishing requires the vacancy to be
 * accepting candidates (OPEN / PUBLISHED — the M275 approval gate);
 * the first published posting flips an OPEN vacancy to PUBLISHED so
 * the requisition list shows it's live somewhere.
 */
@Service
public class JobPostingService {

    private static final Logger log = LoggerFactory.getLogger(JobPostingService.class);

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "JobPosting";
    private static final String TENANT = "default";

    private final JobPostingRepository postings;
    private final VacancyRepository vacancies;
    private final VacancyService vacancyService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public JobPostingService(JobPostingRepository postings,
                              VacancyRepository vacancies,
                              VacancyService vacancyService,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.postings = postings;
        this.vacancies = vacancies;
        this.vacancyService = vacancyService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<JobPosting> listForVacancy(UUID vacancyId) {
        // Confidential gate for free: get() 404s for outsiders (M277).
        vacancyService.get(vacancyId);
        return postings.findByTenantIdAndVacancyIdOrderByCreatedAtDesc(TENANT, vacancyId);
    }

    @Transactional
    public JobPosting create(UUID vacancyId, PostingRequest req) {
        Vacancy v = vacancyService.get(vacancyId);
        JobPosting p = new JobPosting();
        p.setPostingNo(String.format("POST-%05d", postings.nextNoSequence()));
        p.setVacancyId(vacancyId);
        p.setChannel(req.channel());
        p.setLanguage(req.language() == null || req.language().isBlank()
                ? "az" : req.language().toLowerCase());
        // Blank posting fields default from the requisition so HR only
        // overrides what differs per channel/language.
        p.setTitle(blankTo(req.title(), v.getTitle()));
        p.setDescription(blankTo(req.description(), v.getDescription()));
        p.setRequirements(blankTo(req.requirements(), v.getRequirements()));
        p.setBenefitsDescription(req.benefitsDescription());
        p.setSalaryVisible(Boolean.TRUE.equals(req.salaryVisible()));
        p.setApplicationDeadline(req.applicationDeadline() != null
                ? req.applicationDeadline() : v.getClosingDate());
        p.setCreatedBy(currentRequest.username());
        p.setUpdatedBy(currentRequest.username());
        JobPosting saved = postings.save(p);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, PostingResponse.from(saved));
        return saved;
    }

    @Transactional
    public JobPosting update(UUID id, PostingRequest req) {
        JobPosting p = get(id);
        if (p.getStatus() != JobPosting.Status.DRAFT
                && p.getStatus() != JobPosting.Status.PAUSED) {
            throw new BadRequestException(
                    "Only DRAFT or PAUSED postings can be edited (current: "
                            + p.getStatus() + ")");
        }
        PostingResponse before = PostingResponse.from(p);
        p.setChannel(req.channel());
        if (req.language() != null && !req.language().isBlank()) {
            p.setLanguage(req.language().toLowerCase());
        }
        if (req.title() != null && !req.title().isBlank()) p.setTitle(req.title());
        p.setDescription(req.description());
        p.setRequirements(req.requirements());
        p.setBenefitsDescription(req.benefitsDescription());
        if (req.salaryVisible() != null) p.setSalaryVisible(req.salaryVisible());
        p.setApplicationDeadline(req.applicationDeadline());
        p.setUpdatedBy(currentRequest.username());
        JobPosting saved = postings.save(p);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, PostingResponse.from(saved));
        return saved;
    }

    @Transactional
    public JobPosting publish(UUID id) {
        JobPosting p = get(id);
        if (p.getStatus() != JobPosting.Status.DRAFT
                && p.getStatus() != JobPosting.Status.PAUSED) {
            throw new BadRequestException(
                    "Only DRAFT or PAUSED postings can be published (current: "
                            + p.getStatus() + ")");
        }
        Vacancy v = vacancies.findById(p.getVacancyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vacancy not found: " + p.getVacancyId()));
        // M275 gate — the requisition must have passed approval and be
        // accepting candidates before any channel goes live.
        if (!v.getStatus().isAccepting()) {
            throw new BadRequestException(
                    "Requisition " + v.getVacancyNo() + " is " + v.getStatus()
                            + " — it must be OPEN before postings can go live");
        }
        if (p.getApplicationDeadline() != null
                && p.getApplicationDeadline().isBefore(LocalDate.now())) {
            throw new BadRequestException("Application deadline is in the past");
        }
        p.setStatus(JobPosting.Status.PUBLISHED);
        p.setPublishedAt(OffsetDateTime.now());
        p.setPublishedBy(currentRequest.username());
        p.setUpdatedBy(currentRequest.username());
        JobPosting saved = postings.save(p);
        // First live posting flips OPEN → PUBLISHED on the requisition.
        if (v.getStatus() == VacancyStatus.OPEN) {
            vacancyService.changeStatus(v.getId(), VacancyStatus.PUBLISHED,
                    "First posting published: " + saved.getPostingNo());
        }
        audit.record(MODULE, ENTITY, id.toString(), "PUBLISH",
                Map.of("status", "DRAFT"),
                Map.of("status", saved.getStatus().name(),
                        "channel", saved.getChannel().name(),
                        "language", saved.getLanguage()));
        return saved;
    }

    @Transactional
    public JobPosting pause(UUID id) {
        return transitionFromPublished(id, JobPosting.Status.PAUSED, "PAUSE");
    }

    @Transactional
    public JobPosting close(UUID id) {
        JobPosting p = get(id);
        if (p.getStatus() == JobPosting.Status.CLOSED) {
            throw new BadRequestException("Posting already CLOSED");
        }
        JobPosting.Status old = p.getStatus();
        p.setStatus(JobPosting.Status.CLOSED);
        p.setUpdatedBy(currentRequest.username());
        JobPosting saved = postings.save(p);
        audit.record(MODULE, ENTITY, id.toString(), "CLOSE",
                Map.of("status", old.name()),
                Map.of("status", saved.getStatus().name()));
        return saved;
    }

    private JobPosting transitionFromPublished(UUID id, JobPosting.Status target, String action) {
        JobPosting p = get(id);
        if (p.getStatus() != JobPosting.Status.PUBLISHED) {
            throw new BadRequestException(
                    "Only PUBLISHED postings can be " + target + " (current: "
                            + p.getStatus() + ")");
        }
        p.setStatus(target);
        p.setUpdatedBy(currentRequest.username());
        JobPosting saved = postings.save(p);
        audit.record(MODULE, ENTITY, id.toString(), action,
                Map.of("status", JobPosting.Status.PUBLISHED.name()),
                Map.of("status", target.name()));
        return saved;
    }

    private JobPosting get(UUID id) {
        return postings.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Posting not found: " + id));
    }

    /**
     * M278 — daily expiry sweep at 00:30: PUBLISHED postings whose
     * application deadline has passed become EXPIRED. Mirrors the
     * M68 ExpiryAlertScheduler timing convention.
     */
    @Scheduled(cron = "0 30 0 * * *")
    @Transactional
    public void expireOverduePostings() {
        List<JobPosting> overdue = postings.findExpired(TENANT, LocalDate.now());
        for (JobPosting p : overdue) {
            p.setStatus(JobPosting.Status.EXPIRED);
            p.setUpdatedBy("system");
            postings.save(p);
            audit.record(MODULE, ENTITY, p.getId().toString(), "EXPIRE",
                    Map.of("status", JobPosting.Status.PUBLISHED.name()),
                    Map.of("status", JobPosting.Status.EXPIRED.name(),
                            "deadline", p.getApplicationDeadline().toString()));
        }
        if (!overdue.isEmpty()) {
            log.info("JobPostingService: expired {} overdue posting(s)", overdue.size());
        }
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
