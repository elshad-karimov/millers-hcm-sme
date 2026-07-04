package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import az.millers.hcm.common.expiry.ExpiryAlertSource;
import az.millers.hcm.lifecycle.domain.ProbationReview;
import az.millers.hcm.lifecycle.repo.ProbationReviewRepository;

/**
 * Sixth concrete {@code ExpiryAlertSource} (M73 / P2-02). Plugs
 * {@link ProbationReview#getScheduledDate()} into the M61 daily scheduler so
 * HR + managers get reminders at {90, 60, 30, 14, 7, 0} days before each
 * scheduled milestone.
 *
 * <p>Distinct from {@code EmploymentContractProbationSource} (M64) — that one
 * fires on {@code probation_end_date}, the legal probation deadline. This
 * source fires on the formal REVIEW dates, which lead up to the deadline.
 * Both are valid: the contract source warns "probation ending soon" globally,
 * this source nudges the named reviewer to actually conduct the review.
 *
 * <p>SCHEDULED-only filter via {@code findScheduledOn} — COMPLETED and
 * CANCELLED rows are silently skipped so a re-run on the same date doesn't
 * re-alert reviewers who've already finished.
 */
@Component
public class ProbationReviewExpirySource implements ExpiryAlertSource {

    private final ProbationReviewRepository repository;

    public ProbationReviewExpirySource(ProbationReviewRepository repository) {
        this.repository = repository;
    }

    @Override public String moduleName() { return "LIFECYCLE"; }

    @Override public String entityName() { return "ProbationReview"; }

    @Override
    public List<? extends ProbationReview> findExpiringOn(LocalDate date) {
        return repository.findScheduledOn(date);
    }
}
