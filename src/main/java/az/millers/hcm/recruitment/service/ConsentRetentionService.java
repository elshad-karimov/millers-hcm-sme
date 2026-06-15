package az.millers.hcm.recruitment.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.RetentionDtos.AnonymizeResult;
import az.millers.hcm.recruitment.api.dto.RetentionDtos.RetentionReport;
import az.millers.hcm.recruitment.api.dto.RetentionDtos.RetentionRow;
import az.millers.hcm.recruitment.api.dto.RetentionDtos.SweepResult;
import az.millers.hcm.recruitment.domain.Application;
import az.millers.hcm.recruitment.domain.ApplicationStatus;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.CandidatePoolStatus;
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.CandidateRepository;

/**
 * M294 — Recruitment PRD §46/§47: candidate consent retention +
 * anonymisation.
 *
 * <p>Each candidate has a retention window — {@code retentionMonths} after
 * the most recent of consent / last-contact / created. Past it, a candidate
 * who never became an employee and has no live application is eligible for
 * anonymisation: its PII is scrubbed in place and PII child rows removed,
 * while the candidate + applications stay for funnel analytics. A daily
 * sweep reports what's due (and auto-anonymises only when explicitly
 * enabled); HR can also anonymise on demand (right-to-erasure).
 */
@Service
public class ConsentRetentionService {

    private static final Logger log = LoggerFactory.getLogger(ConsentRetentionService.class);
    private static final String MODULE = "RECRUITMENT";

    private final CandidateRepository candidates;
    private final ApplicationRepository applications;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final int retentionMonths;
    private final int upcomingWindowDays;
    private final boolean autoAnonymize;

    public ConsentRetentionService(CandidateRepository candidates,
                                    ApplicationRepository applications,
                                    NamedParameterJdbcTemplate jdbc,
                                    AuditService audit,
                                    @Value("${hcm.recruitment.retention.months:24}") int retentionMonths,
                                    @Value("${hcm.recruitment.retention.upcoming-window-days:30}") int upcomingWindowDays,
                                    @Value("${hcm.recruitment.retention.auto-anonymize:false}") boolean autoAnonymize) {
        this.candidates = candidates;
        this.applications = applications;
        this.jdbc = jdbc;
        this.audit = audit;
        this.retentionMonths = retentionMonths;
        this.upcomingWindowDays = upcomingWindowDays;
        this.autoAnonymize = autoAnonymize;
    }

    // ── Reporting ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RetentionReport report() {
        LocalDate today = LocalDate.now();
        List<RetentionRow> due = new ArrayList<>();
        List<RetentionRow> upcoming = new ArrayList<>();
        for (Candidate c : candidates.findByMergedIntoIdIsNullAndAnonymizedAtIsNull()) {
            if (!eligibleForRetention(c)) continue; // hired or live application → keep
            LocalDate anchor = anchorDate(c);
            LocalDate retention = anchor.plusMonths(retentionMonths);
            long days = ChronoUnit.DAYS.between(today, retention);
            RetentionRow row = new RetentionRow(c.getId(), c.getCandidateNo(),
                    fullName(c), c.getEmail(), anchor, retention, days, days < 0);
            if (days <= 0) due.add(row);
            else if (days <= upcomingWindowDays) upcoming.add(row);
        }
        due.sort((a, b) -> Long.compare(a.daysUntilDue(), b.daysUntilDue()));
        upcoming.sort((a, b) -> Long.compare(a.daysUntilDue(), b.daysUntilDue()));
        return new RetentionReport(retentionMonths, due.size(), upcoming.size(), due, upcoming);
    }

    /** The candidates the sweep would anonymise right now. */
    @Transactional(readOnly = true)
    public List<Candidate> dueNow() {
        LocalDate today = LocalDate.now();
        List<Candidate> out = new ArrayList<>();
        for (Candidate c : candidates.findByMergedIntoIdIsNullAndAnonymizedAtIsNull()) {
            if (eligibleForRetention(c)
                    && !anchorDate(c).plusMonths(retentionMonths).isAfter(today)) {
                out.add(c);
            }
        }
        return out;
    }

    private boolean eligibleForRetention(Candidate c) {
        // Keep anyone who became an employee or still has a live application.
        for (Application a : applications.findByCandidateIdOrderByCreatedAtDesc(c.getId())) {
            if (a.getStatus() == ApplicationStatus.HIRED
                    || a.getStatus() == ApplicationStatus.IN_PROGRESS) {
                return false;
            }
        }
        return true;
    }

    private LocalDate anchorDate(Candidate c) {
        OffsetDateTime anchor = c.getCreatedAt();
        if (c.getConsentAt() != null && c.getConsentAt().isAfter(anchor)) anchor = c.getConsentAt();
        if (c.getLastContactedAt() != null && c.getLastContactedAt().isAfter(anchor)) {
            anchor = c.getLastContactedAt();
        }
        return anchor.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    }

    // ── Anonymisation ──────────────────────────────────────────────────

    /**
     * Scrub a candidate's PII in place and delete its PII child rows. Used
     * by both the sweep and on-demand right-to-erasure. Idempotent — an
     * already-anonymised candidate is returned untouched.
     */
    @Transactional
    public AnonymizeResult anonymize(UUID candidateId, String reason) {
        Candidate c = candidates.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + candidateId));
        if (c.getAnonymizedAt() != null) {
            return new AnonymizeResult(candidateId, 0, 0, 0, 0, 0);
        }
        if (c.getMergedIntoId() != null) {
            throw new BadRequestException("Candidate has been merged — anonymise the surviving master");
        }

        var p = new MapSqlParameterSource("id", candidateId);
        int edu = jdbc.update("DELETE FROM recruitment.candidate_education WHERE candidate_id = :id", p);
        int exp = jdbc.update("DELETE FROM recruitment.candidate_experience WHERE candidate_id = :id", p);
        int skills = jdbc.update("DELETE FROM recruitment.candidate_skill WHERE candidate_id = :id", p);
        int notes = jdbc.update("DELETE FROM recruitment.candidate_note WHERE candidate_id = :id", p);
        int docs = jdbc.update("DELETE FROM recruitment.candidate_document WHERE candidate_id = :id", p);

        // Scrub the master record — keep the row + candidate_no for funnel
        // analytics; null out everything that identifies a person.
        c.setFirstName("Anonymised");
        c.setLastName(c.getCandidateNo());
        c.setMiddleName(null);
        c.setEmail(null);
        c.setPhone(null);
        c.setCvUrl(null);
        c.setSkills(null);
        c.setNotes(null);
        c.setPoolStatus(CandidatePoolStatus.DO_NOT_CONTACT);
        c.setAnonymizedAt(OffsetDateTime.now());
        candidates.save(c);

        audit.record(MODULE, "Candidate", candidateId.toString(), "ANONYMIZE", null,
                Map.of("reason", reason == null ? "retention" : reason,
                        "educationDeleted", edu, "experienceDeleted", exp,
                        "skillsDeleted", skills, "notesDeleted", notes, "documentsDeleted", docs));
        log.info("Anonymised candidate {} ({})", c.getCandidateNo(), reason);
        return new AnonymizeResult(candidateId, edu, exp, skills, notes, docs);
    }

    /** Anonymise everyone past the window; {@code dryRun} just lists them. */
    @Transactional
    public SweepResult runSweep(boolean dryRun) {
        List<Candidate> due = dueNow();
        List<String> nos = new ArrayList<>();
        for (Candidate c : due) {
            nos.add(c.getCandidateNo());
            if (!dryRun) anonymize(c.getId(), "retention sweep");
        }
        return new SweepResult(dryRun, due.size(), nos);
    }

    /**
     * M294 — daily 02:30 retention sweep. Auto-anonymises only when
     * {@code hcm.recruitment.retention.auto-anonymize=true}; otherwise just
     * audits the due count so HR can action it (safe default).
     */
    @Scheduled(cron = "${hcm.recruitment.retention.cron:0 30 2 * * *}")
    @Transactional
    public void dailySweep() {
        List<Candidate> due = dueNow();
        if (due.isEmpty()) return;
        if (autoAnonymize) {
            for (Candidate c : due) anonymize(c.getId(), "retention sweep");
            log.info("Retention sweep anonymised {} candidate(s)", due.size());
        } else {
            audit.record(MODULE, "RetentionSweep", "daily", "DUE_REVIEW", null,
                    Map.of("dueCount", due.size()));
            log.info("Retention sweep: {} candidate(s) past the {}-month window (auto-anonymise off)",
                    due.size(), retentionMonths);
        }
    }

    private static String fullName(Candidate c) {
        return ((c.getFirstName() == null ? "" : c.getFirstName()) + " "
                + (c.getLastName() == null ? "" : c.getLastName())).trim();
    }
}
