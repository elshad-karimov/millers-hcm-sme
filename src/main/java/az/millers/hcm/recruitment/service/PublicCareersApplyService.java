package az.millers.hcm.recruitment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
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

/**
 * M280 — Recruitment PRD §9: anonymous application submission from the
 * public career portal.
 *
 * <p>All the public-endpoint concerns live here in one place:
 * <ul>
 *   <li><b>Liveness</b> — same re-validation as the M279 detail
 *       endpoint: EXTERNAL + PUBLISHED + deadline + non-confidential,
 *       all failures mapped to 404.</li>
 *   <li><b>Spam</b> — honeypot field ("website") that humans never see;
 *       bots that fill it get a fake success and nothing is stored.
 *       Plus a per-IP fixed-window throttle (5 applications/hour).</li>
 *   <li><b>Consent</b> — PRD §46: explicit consent is mandatory;
 *       timestamp + text version stored on the candidate.</li>
 *   <li><b>Dedup</b> — PRD §12: one candidate master per email; a
 *       repeat applicant reuses their existing profile, and a repeat
 *       application to the same requisition is rejected politely.</li>
 * </ul>
 *
 * <p>Candidate creation reuses {@code CandidateService.create} and the
 * pipeline entry reuses {@code ApplicationService.apply} — the portal
 * path produces exactly the same records as an HR-entered application.
 */
@Service
public class PublicCareersApplyService {

    private static final Logger log = LoggerFactory.getLogger(PublicCareersApplyService.class);

    private static final String MODULE = "RECRUITMENT";
    /** Bump when the portal consent text changes (PRD §46 consent version). */
    public static final String CONSENT_VERSION = "v1-2026";

    private static final int MAX_PER_WINDOW = 5;
    private static final long WINDOW_MILLIS = 60 * 60 * 1000L;

    public record PublicApplyRequest(
            String firstName,
            String lastName,
            String email,
            String phone,
            BigDecimal expectedSalary,
            String coverNote,
            boolean consent,
            /** Honeypot — rendered invisible on the page; humans leave it blank. */
            String website) {}

    public record PublicApplyResult(String applicationNo, String message,
                                    /** M282 — anonymous status-tracking credential. */
                                    String trackingToken) {}

    private final JobPostingRepository postings;
    private final VacancyRepository vacancies;
    private final CandidateRepository candidates;
    private final ApplicationRepository applications;
    private final CandidateService candidateService;
    private final ApplicationService applicationService;
    private final AuditService audit;

    /** ip → (windowStartMillis, count). Fixed-window; resets per window. */
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();
    private final AtomicInteger honeypotHits = new AtomicInteger();

    public PublicCareersApplyService(JobPostingRepository postings,
                                      VacancyRepository vacancies,
                                      CandidateRepository candidates,
                                      ApplicationRepository applications,
                                      CandidateService candidateService,
                                      ApplicationService applicationService,
                                      AuditService audit) {
        this.postings = postings;
        this.vacancies = vacancies;
        this.candidates = candidates;
        this.applications = applications;
        this.candidateService = candidateService;
        this.applicationService = applicationService;
        this.audit = audit;
    }

    @Transactional
    public PublicApplyResult apply(UUID postingId, PublicApplyRequest req, String ip) {
        // Honeypot first — fake success, store nothing, log the hit.
        if (req.website() != null && !req.website().isBlank()) {
            log.info("Careers honeypot hit #{} from {}", honeypotHits.incrementAndGet(), ip);
            // Fake token too — indistinguishable from a real success.
            return new PublicApplyResult("APP-00000", "Application received", newToken());
        }
        throttle(ip);

        if (!req.consent()) {
            throw new BadRequestException(
                    "Privacy consent is required to submit an application");
        }
        requireText(req.firstName(), "First name");
        requireText(req.lastName(), "Last name");
        requireText(req.email(), "Email");

        // Same liveness rules as the public detail endpoint — 404 on any
        // failure so closed/confidential jobs stay invisible.
        JobPosting p = postings.findById(postingId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + postingId));
        Vacancy v = vacancies.findById(p.getVacancyId()).orElse(null);
        if (v == null || v.isConfidential()
                || p.getChannel() != JobPosting.Channel.EXTERNAL
                || !p.getStatus().isLive()
                || (p.getApplicationDeadline() != null
                    && p.getApplicationDeadline().isBefore(LocalDate.now()))) {
            throw new ResourceNotFoundException("Job not found: " + postingId);
        }

        // PRD §12 — one candidate master per email.
        Candidate candidate = candidates
                .findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(req.email().trim())
                .orElseGet(() -> candidateService.create(new CandidateRequest(
                        req.firstName().trim(), req.lastName().trim(), null,
                        req.email().trim(), req.phone(),
                        CandidateSource.WEBSITE,
                        null, null,
                        req.expectedSalary(), null,
                        null,
                        req.coverNote())));

        // PRD §70 — one application per candidate per requisition.
        if (applications.existsByVacancyIdAndCandidateId(v.getId(), candidate.getId())) {
            throw new BadRequestException(
                    "You have already applied for this position — our team has your application");
        }

        // PRD §46 — consent evidence (refreshed on every application so
        // returning candidates re-accept the current text version).
        candidate.setConsentAt(OffsetDateTime.now());
        candidate.setConsentVersion(CONSENT_VERSION);
        candidates.save(candidate);

        Application a = applicationService.apply(v.getId(), candidate.getId());
        a.setPostingId(p.getId()); // PRD §44 — channel attribution
        a.setTrackingToken(newToken()); // M282 — anonymous status tracking
        applications.save(a);

        audit.record(MODULE, "Application", a.getId().toString(), "PUBLIC_APPLY",
                null,
                Map.of("postingNo", p.getPostingNo(),
                        "vacancyNo", v.getVacancyNo(),
                        "candidateNo", candidate.getCandidateNo(),
                        "sourceIp", ip == null ? "" : ip,
                        "consentVersion", CONSENT_VERSION));
        return new PublicApplyResult(a.getApplicationNo(),
                "Application received — reference " + a.getApplicationNo(),
                a.getTrackingToken());
    }

    /** M282 — tracking lookup; unknown token → 404 (token is the credential). */
    @Transactional(readOnly = true)
    public Application findByTrackingToken(String token) {
        if (token == null || token.length() < 16) {
            throw new ResourceNotFoundException("Application not found");
        }
        return applications.findByTrackingToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
    }

    /** 32-hex-char SecureRandom token (128 bits) — the tracking credential. */
    private static String newToken() {
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void throttle(String ip) {
        String key = ip == null ? "unknown" : ip;
        long now = System.currentTimeMillis();
        long[] w = windows.compute(key, (k, cur) ->
                cur == null || now - cur[0] > WINDOW_MILLIS
                        ? new long[]{now, 1}
                        : new long[]{cur[0], cur[1] + 1});
        if (w[1] > MAX_PER_WINDOW) {
            throw new BadRequestException(
                    "Too many applications from this address — please try again later");
        }
        // Opportunistic cleanup so the map doesn't grow unbounded.
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue()[0] > WINDOW_MILLIS);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
    }
}
