package az.millers.hcm.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.CandidatePoolStatus;

/**
 * Pins the {@link StalePoolReminderScheduler#isStale} predicate so the staleness
 * boundary stays explicit and so the priority rule (ARCHIVED / DO_NOT_CONTACT
 * always wins over an old lastContactedAt) doesn't silently regress.
 *
 * <p>Reflection-free — {@code isStale} is package-private static on purpose
 * so the test can drive it directly. Mockito-free for the same Java 25
 * reasons that drove M68 / M77 / M88.
 */
class StalePoolReminderSchedulerTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 6, 3, 12, 0, 0, 0, ZoneOffset.UTC);
    /** 30 days before NOW. Used as the staleness cutoff in every test. */
    private static final OffsetDateTime CUTOFF = NOW.minusDays(30);

    private static Candidate candidate(CandidatePoolStatus status, OffsetDateTime touched) {
        Candidate c = new Candidate();
        c.setPoolStatus(status);
        c.setLastContactedAt(touched);
        // createdAt fallback is required so the null-touched branch is exercised
        // realistically — set to NOW so a null lastContactedAt means "fresh".
        c.setCreatedAt(NOW);
        return c;
    }

    @Test
    void freshContactIsNotStale() {
        Candidate c = candidate(CandidatePoolStatus.ACTIVE, NOW.minusDays(10));
        assertThat(StalePoolReminderScheduler.isStale(c, CUTOFF)).isFalse();
    }

    @Test
    void oldContactIsStale() {
        Candidate c = candidate(CandidatePoolStatus.ACTIVE, NOW.minusDays(45));
        assertThat(StalePoolReminderScheduler.isStale(c, CUTOFF)).isTrue();
    }

    @Test
    void doNotContactIsNeverStale() {
        Candidate c = candidate(CandidatePoolStatus.DO_NOT_CONTACT, NOW.minusDays(365));
        assertThat(StalePoolReminderScheduler.isStale(c, CUTOFF)).isFalse();
    }

    @Test
    void archivedIsNeverStale() {
        Candidate c = candidate(CandidatePoolStatus.ARCHIVED, NOW.minusDays(365));
        assertThat(StalePoolReminderScheduler.isStale(c, CUTOFF)).isFalse();
    }

    @Test
    void passiveWithOldContactIsStale() {
        Candidate c = candidate(CandidatePoolStatus.PASSIVE, NOW.minusDays(60));
        assertThat(StalePoolReminderScheduler.isStale(c, CUTOFF)).isTrue();
    }

    @Test
    void neverContactedButOldCandidateIsStale() {
        Candidate c = new Candidate();
        c.setPoolStatus(CandidatePoolStatus.ACTIVE);
        c.setLastContactedAt(null);
        c.setCreatedAt(NOW.minusDays(90));  // old createdAt → uses fallback path
        assertThat(StalePoolReminderScheduler.isStale(c, CUTOFF)).isTrue();
    }

    @Test
    void neverContactedFreshCandidateIsNotStale() {
        Candidate c = new Candidate();
        c.setPoolStatus(CandidatePoolStatus.ACTIVE);
        c.setLastContactedAt(null);
        c.setCreatedAt(NOW.minusDays(3));
        assertThat(StalePoolReminderScheduler.isStale(c, CUTOFF)).isFalse();
    }
}
