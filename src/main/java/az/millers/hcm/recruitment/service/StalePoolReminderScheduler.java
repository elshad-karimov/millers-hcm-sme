package az.millers.hcm.recruitment.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import az.millers.hcm.audit.AuditIdempotency;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.CandidatePoolStatus;
import az.millers.hcm.recruitment.repo.CandidateRepository;

/**
 * Daily nudge for recruiters about stale talent-pool candidates (M89).
 *
 * <p>For every {@link Candidate} whose {@code last_contacted_at} is older
 * than {@code hcm.recruitment.stale.threshold-days} (default 30) and whose
 * pool status is still ACTIVE or PASSIVE, this scheduler bundles the
 * findings by {@code createdBy} (the candidate's recruiter) and sends one
 * digest notification per recipient. If a candidate has no recorded creator,
 * it falls back to the configured house-account ({@code hcm.recruitment.stale.fallback-recipient}).
 *
 * <p>Mirrors the ExpiryAlertScheduler idempotency pattern: every dispatched
 * digest journals an audit row keyed on {@code (today, recipient)} so the
 * scan is safe to re-run on the same day.
 *
 * <p>Runs at 07:00 Europe/Baku daily by default — chained after the 06:00
 * expiry scheduler so an HR specialist sees both digests together.
 */
@Component
public class StalePoolReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(StalePoolReminderScheduler.class);

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "TalentPoolReminder";
    private static final String AUDIT_ACTION = "STALE_REMINDER_SENT";

    /** How many candidate names to inline in the digest body. */
    private static final int MAX_NAMES_IN_BODY = 5;

    private final CandidateRepository candidates;
    private final NotificationService notifications;
    private final AuditService audit;
    private final AuditIdempotency auditIdempotency;
    private final int thresholdDays;
    private final String fallbackRecipient;

    public StalePoolReminderScheduler(CandidateRepository candidates,
                                      NotificationService notifications,
                                      AuditService audit,
                                      AuditIdempotency auditIdempotency,
                                      @Value("${hcm.recruitment.stale.threshold-days:30}") int thresholdDays,
                                      @Value("${hcm.recruitment.stale.fallback-recipient:hr.admin}") String fallbackRecipient) {
        this.candidates = candidates;
        this.notifications = notifications;
        this.audit = audit;
        this.auditIdempotency = auditIdempotency;
        this.thresholdDays = thresholdDays < 1 ? 30 : thresholdDays;
        this.fallbackRecipient = fallbackRecipient == null || fallbackRecipient.isBlank()
                ? "hr.admin" : fallbackRecipient;
    }

    /** Result envelope returned by the manual / admin scan trigger. */
    public record ScanSummary(
            LocalDate today,
            int candidatesConsidered,
            int recipientsNotified,
            int recipientsSkippedAsDuplicate) {}

    /** Daily walker — 07:00 every day in the deployment-local TZ. */
    @Scheduled(cron = "${hcm.recruitment.stale.cron:0 0 7 * * *}")
    public void scanAll() {
        ScanSummary summary = scanFor(LocalDate.now());
        if (summary.recipientsNotified() > 0 || summary.recipientsSkippedAsDuplicate() > 0) {
            log.info("StalePoolReminderScheduler scan complete: {} considered, {} notified, {} skipped (duplicate)",
                    summary.candidatesConsidered(), summary.recipientsNotified(), summary.recipientsSkippedAsDuplicate());
        }
    }

    /** Entry point for the admin trigger and JUnit tests. */
    public ScanSummary scanFor(LocalDate today) {
        OffsetDateTime cutoff = today.atStartOfDay().toInstant(ZoneOffset.UTC)
                .atOffset(ZoneOffset.UTC).minusDays(thresholdDays);

        // Bucket stale candidates by recipient username so each recruiter gets
        // ONE digest, not N notifications.
        Map<String, List<Candidate>> byRecipient = new LinkedHashMap<>();
        int considered = 0;
        for (Candidate c : candidates.findAll()) {
            if (!isStale(c, cutoff)) continue;
            considered++;
            String recipient = recipientFor(c);
            byRecipient.computeIfAbsent(recipient, r -> new ArrayList<>()).add(c);
        }

        int notified = 0, skipped = 0;
        for (Map.Entry<String, List<Candidate>> e : byRecipient.entrySet()) {
            String recipient = e.getKey();
            if (alreadyNotified(recipient, today)) {
                skipped++;
                continue;
            }
            dispatchDigest(recipient, e.getValue(), today);
            notified++;
        }
        return new ScanSummary(today, considered, notified, skipped);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    static boolean isStale(Candidate c, OffsetDateTime cutoff) {
        if (c.getPoolStatus() == CandidatePoolStatus.DO_NOT_CONTACT
                || c.getPoolStatus() == CandidatePoolStatus.ARCHIVED) {
            return false;
        }
        OffsetDateTime touched = c.getLastContactedAt() != null
                ? c.getLastContactedAt() : c.getCreatedAt();
        // touched can still be null on hand-loaded test rows; treat as stale.
        return touched == null || touched.isBefore(cutoff);
    }

    private String recipientFor(Candidate c) {
        String createdBy = c.getCreatedBy();
        return (createdBy == null || createdBy.isBlank()) ? fallbackRecipient : createdBy;
    }

    private boolean alreadyNotified(String recipient, LocalDate today) {
        return auditIdempotency.hasMarker(ENTITY, recipient, AUDIT_ACTION,
                Map.of("day", today.toString(), "recipient", recipient));
    }

    private void dispatchDigest(String recipient, List<Candidate> stale, LocalDate today) {
        String title = stale.size() == 1
                ? "1 stale candidate needs follow-up"
                : stale.size() + " stale candidates need follow-up";

        StringBuilder body = new StringBuilder();
        body.append("These talent-pool candidates haven't been contacted in ≥ ")
                .append(thresholdDays).append(" days:\n");
        int shown = 0;
        for (Candidate c : stale) {
            if (shown >= MAX_NAMES_IN_BODY) break;
            body.append("  • ")
                    .append(c.getCandidateNo()).append(" — ")
                    .append(c.getFirstName() == null ? "" : c.getFirstName()).append(' ')
                    .append(c.getLastName() == null ? "" : c.getLastName())
                    .append(" (").append(daysSince(c)).append(" d)\n");
            shown++;
        }
        if (stale.size() > MAX_NAMES_IN_BODY) {
            body.append("  …and ").append(stale.size() - MAX_NAMES_IN_BODY).append(" more.\n");
        }
        body.append("Review at /recruitment/analytics.");

        // M115 — stale-pool reminders are mutable; recruiters can opt out per channel.
        notifications.notifyAll(
                az.millers.hcm.notifications.domain.NotificationCategory.STALE_POOL_REMINDER,
                recipient, title, body.toString(),
                MODULE, ENTITY, recipient);

        // Idempotency marker — what alreadyNotified() looks for.
        audit.record(MODULE, ENTITY, recipient, AUDIT_ACTION, null,
                Map.of(
                        "day", today.toString(),
                        "recipient", recipient,
                        "candidateCount", stale.size(),
                        "thresholdDays", thresholdDays));
    }

    private static long daysSince(Candidate c) {
        OffsetDateTime touched = c.getLastContactedAt() != null
                ? c.getLastContactedAt() : c.getCreatedAt();
        if (touched == null) return -1;
        return ChronoUnit.DAYS.between(touched, OffsetDateTime.now());
    }

    /** Test accessors. */
    int getThresholdDays() {
        return thresholdDays;
    }

    String getFallbackRecipient() {
        return fallbackRecipient;
    }

    /** Exposed as a courtesy for an admin trigger; not used internally. */
    public Map<String, Integer> debugBuckets() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(thresholdDays);
        Map<String, Integer> counts = new HashMap<>();
        for (Candidate c : candidates.findAll()) {
            if (!isStale(c, cutoff)) continue;
            counts.merge(recipientFor(c), 1, Integer::sum);
        }
        return counts;
    }
}
