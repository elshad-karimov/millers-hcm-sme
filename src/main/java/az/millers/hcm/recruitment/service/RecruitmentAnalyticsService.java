package az.millers.hcm.recruitment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.DateWindow;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.FunnelReport;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.FunnelRow;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.SourceReport;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.SourceRow;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.StaleCandidateRow;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.StaleReport;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.StaleSummary;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.TimeToHireReport;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.TimeToHireRow;
import az.millers.hcm.recruitment.domain.Application;
import az.millers.hcm.recruitment.domain.ApplicationEvent;
import az.millers.hcm.recruitment.domain.ApplicationStage;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.repo.ApplicationEventRepository;
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.CandidateRepository;

/**
 * Recruitment funnel + source + stale-pool analytics (M88).
 *
 * <p>Done in-memory because the recruitment dataset is small (≤ 10⁵ rows by
 * PRD § 15). A larger tenant would push the funnel + median computation
 * into native SQL with window functions, but the public DTO shape stays.
 */
@Service
public class RecruitmentAnalyticsService {

    /** Ordered pipeline used for the funnel — terminal states sit outside. */
    private static final List<ApplicationStage> FUNNEL_STAGES = List.of(
            ApplicationStage.CV_SCREENING,
            ApplicationStage.HR_INTERVIEW,
            ApplicationStage.TECHNICAL_INTERVIEW,
            ApplicationStage.FINAL_INTERVIEW,
            ApplicationStage.OFFER,
            ApplicationStage.HIRED);

    private final ApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final CandidateRepository candidates;

    public RecruitmentAnalyticsService(ApplicationRepository applications,
                                        ApplicationEventRepository events,
                                        CandidateRepository candidates) {
        this.applications = applications;
        this.events = events;
        this.candidates = candidates;
    }

    // ── Funnel ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FunnelReport funnel(LocalDate from, LocalDate to) {
        DateWindow window = DateWindow.ofOrDefault(from, to, java.time.Period.ofYears(1));
        LocalDate windowFrom = window.from();
        LocalDate windowTo = window.to();
        OffsetDateTime fromTs = window.fromTs();
        OffsetDateTime toTs = window.toTsExclusive();

        List<Application> apps = applications.findAll().stream()
                .filter(a -> !a.getCreatedAt().isBefore(fromTs)
                        && a.getCreatedAt().isBefore(toTs))
                .toList();

        // "How many applications EVER reached this stage" — count distinct
        // applications whose history includes a transition INTO the stage,
        // or whose current stage equals/passes it.
        Map<ApplicationStage, Long> reached = new LinkedHashMap<>();
        for (ApplicationStage s : FUNNEL_STAGES) reached.put(s, 0L);

        // For each app, walk its events and mark every stage it touched.
        for (Application a : apps) {
            List<ApplicationEvent> evs = events.findAll().stream()
                    .filter(e -> e.getApplicationId().equals(a.getId()))
                    .toList();
            // Seed with the current stage so an app that never transitioned
            // (e.g. CV_SCREENING with no events) still counts at its stage.
            var stagesTouched = new java.util.HashSet<ApplicationStage>();
            stagesTouched.add(a.getCurrentStage());
            for (ApplicationEvent e : evs) {
                if (e.getToStage() != null) stagesTouched.add(e.getToStage());
                if (e.getFromStage() != null) stagesTouched.add(e.getFromStage());
            }
            for (ApplicationStage s : FUNNEL_STAGES) {
                if (passedOrAt(stagesTouched, s)) {
                    reached.merge(s, 1L, Long::sum);
                }
            }
        }

        List<FunnelRow> rows = new ArrayList<>();
        long prior = -1;
        for (ApplicationStage s : FUNNEL_STAGES) {
            long n = reached.getOrDefault(s, 0L);
            BigDecimal conv = null;
            if (prior > 0) {
                conv = BigDecimal.valueOf(n * 100.0 / prior)
                        .setScale(2, RoundingMode.HALF_UP);
            }
            rows.add(new FunnelRow(s.name(), n, conv));
            prior = n;
        }

        long hired = apps.stream()
                .filter(a -> a.getCurrentStage() == ApplicationStage.HIRED).count();
        long rejected = apps.stream()
                .filter(a -> a.getCurrentStage() == ApplicationStage.REJECTED).count();
        long withdrawn = apps.stream()
                .filter(a -> a.getCurrentStage() == ApplicationStage.WITHDRAWN).count();

        return new FunnelReport(windowFrom, windowTo,
                apps.size(), hired, rejected, withdrawn, rows);
    }

    private static boolean passedOrAt(java.util.Set<ApplicationStage> touched,
                                       ApplicationStage s) {
        // "Reached" means the app's history actually visited s. Funnel
        // semantics don't require strict ordering because rejections /
        // withdrawals can happen at any step — we only count stages
        // explicitly touched.
        return touched.contains(s);
    }

    // ── Time-to-hire ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TimeToHireReport timeToHire(LocalDate from, LocalDate to) {
        DateWindow window = DateWindow.ofOrDefault(from, to, java.time.Period.ofYears(1));
        LocalDate windowFrom = window.from();
        LocalDate windowTo = window.to();
        OffsetDateTime fromTs = window.fromTs();
        OffsetDateTime toTs = window.toTsExclusive();

        // Hired applications in window.
        List<Application> hiredApps = applications.findAll().stream()
                .filter(a -> a.getCurrentStage() == ApplicationStage.HIRED
                        && !a.getCreatedAt().isBefore(fromTs)
                        && a.getCreatedAt().isBefore(toTs))
                .toList();

        // Overall: createdAt → HIRED event timestamp (or updatedAt fallback).
        List<Long> overallDays = new ArrayList<>();
        for (Application a : hiredApps) {
            OffsetDateTime hiredAt = events.findAll().stream()
                    .filter(e -> e.getApplicationId().equals(a.getId())
                            && e.getToStage() == ApplicationStage.HIRED)
                    .map(ApplicationEvent::getCreatedAt)
                    .min(Comparator.naturalOrder())
                    .orElse(a.getUpdatedAt());
            long days = ChronoUnit.DAYS.between(
                    a.getCreatedAt().toLocalDate(), hiredAt.toLocalDate());
            if (days >= 0) overallDays.add(days);
        }

        BigDecimal avgOverall = avg(overallDays);
        BigDecimal medianOverall = median(overallDays);

        // Per (fromStage → toStage) transition: mean + median time spent.
        Map<String, List<Long>> byTransition = new LinkedHashMap<>();
        for (Application a : hiredApps) {
            List<ApplicationEvent> stageEvs = events.findAll().stream()
                    .filter(e -> e.getApplicationId().equals(a.getId())
                            && e.getFromStage() != null
                            && e.getToStage() != null
                            && e.getFromStage() != e.getToStage())
                    .sorted(Comparator.comparing(ApplicationEvent::getCreatedAt))
                    .toList();
            // The duration of a stage is from the event that LEFT a prior
            // stage to the event that LEFT the current one. We approximate
            // by treating each (from→to) row's createdAt as "time spent in
            // from" since the prior transition.
            OffsetDateTime cursor = a.getCreatedAt();
            for (ApplicationEvent e : stageEvs) {
                long days = ChronoUnit.DAYS.between(
                        cursor.toLocalDate(), e.getCreatedAt().toLocalDate());
                if (days >= 0) {
                    String key = e.getFromStage().name() + " → " + e.getToStage().name();
                    byTransition.computeIfAbsent(key, k -> new ArrayList<>()).add(days);
                }
                cursor = e.getCreatedAt();
            }
        }

        List<TimeToHireRow> rows = new ArrayList<>();
        for (var e : byTransition.entrySet()) {
            String key = e.getKey();
            String[] parts = key.split(" → ", 2);
            rows.add(new TimeToHireRow(
                    parts[0], parts[1],
                    e.getValue().size(),
                    avg(e.getValue()), median(e.getValue())));
        }
        rows.sort(Comparator.comparingLong(TimeToHireRow::transitions).reversed());

        return new TimeToHireReport(windowFrom, windowTo,
                avgOverall, medianOverall, rows);
    }

    // ── Source effectiveness ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SourceReport sourceEffectiveness(LocalDate from, LocalDate to) {
        DateWindow window = DateWindow.ofOrDefault(from, to, java.time.Period.ofYears(1));
        LocalDate windowFrom = window.from();
        LocalDate windowTo = window.to();
        OffsetDateTime fromTs = window.fromTs();
        OffsetDateTime toTs = window.toTsExclusive();

        // Map candidateId → source. One DB hit; we filter applications by
        // the in-window timestamp and group on source.
        Map<java.util.UUID, String> sourceByCandidate = new java.util.HashMap<>();
        for (Candidate c : candidates.findAll()) {
            sourceByCandidate.put(c.getId(),
                    c.getSource() == null ? "UNKNOWN" : c.getSource().name());
        }

        List<Application> apps = applications.findAll().stream()
                .filter(a -> !a.getCreatedAt().isBefore(fromTs)
                        && a.getCreatedAt().isBefore(toTs))
                .toList();

        Map<String, Long> total = new LinkedHashMap<>();
        Map<String, Long> hires = new LinkedHashMap<>();
        Map<String, List<Long>> daysTo = new LinkedHashMap<>();

        for (Application a : apps) {
            String src = sourceByCandidate.getOrDefault(a.getCandidateId(), "UNKNOWN");
            total.merge(src, 1L, Long::sum);
            if (a.getCurrentStage() == ApplicationStage.HIRED) {
                hires.merge(src, 1L, Long::sum);
                OffsetDateTime hiredAt = events.findAll().stream()
                        .filter(e -> e.getApplicationId().equals(a.getId())
                                && e.getToStage() == ApplicationStage.HIRED)
                        .map(ApplicationEvent::getCreatedAt)
                        .min(Comparator.naturalOrder())
                        .orElse(a.getUpdatedAt());
                long days = ChronoUnit.DAYS.between(
                        a.getCreatedAt().toLocalDate(), hiredAt.toLocalDate());
                if (days >= 0) {
                    daysTo.computeIfAbsent(src, k -> new ArrayList<>()).add(days);
                }
            }
        }

        List<SourceRow> rows = new ArrayList<>();
        for (var e : total.entrySet()) {
            String src = e.getKey();
            long n = e.getValue();
            long h = hires.getOrDefault(src, 0L);
            BigDecimal rate = n == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(h * 100.0 / n).setScale(2, RoundingMode.HALF_UP);
            BigDecimal avgD = avg(daysTo.getOrDefault(src, List.of()));
            rows.add(new SourceRow(src, n, h, rate, avgD));
        }
        rows.sort(Comparator.comparingLong(SourceRow::applications).reversed());
        return new SourceReport(windowFrom, windowTo, rows);
    }

    // ── Stale outreach ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public StaleReport stale(int thresholdDays) {
        int days = thresholdDays < 1 ? 30 : Math.min(thresholdDays, 365);
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);
        List<Candidate> all = candidates.findAll();
        List<StaleCandidateRow> rows = new ArrayList<>();
        for (Candidate c : all) {
            if (c.getPoolStatus() == az.millers.hcm.recruitment.domain.CandidatePoolStatus.DO_NOT_CONTACT
                    || c.getPoolStatus() == az.millers.hcm.recruitment.domain.CandidatePoolStatus.ARCHIVED) {
                continue;
            }
            if (c.getLastContactedAt() != null && c.getLastContactedAt().isAfter(cutoff)) {
                continue;
            }
            long since = c.getLastContactedAt() == null
                    ? ChronoUnit.DAYS.between(c.getCreatedAt(), OffsetDateTime.now())
                    : ChronoUnit.DAYS.between(c.getLastContactedAt(), OffsetDateTime.now());
            rows.add(new StaleCandidateRow(
                    c.getId(), c.getCandidateNo(),
                    c.getFirstName() + " " + c.getLastName(),
                    c.getEmail(),
                    c.getPoolStatus().name(),
                    c.getLastContactedAt(),
                    since));
        }
        rows.sort(Comparator.comparingLong(StaleCandidateRow::daysSinceContact).reversed());
        return new StaleReport(days, rows.size(), rows);
    }

    /**
     * Lightweight stale-pool summary (M89) for the home-dashboard tile.
     * Buckets the same population the {@link #stale(int)} report walks so the
     * tile shows the same counts the analytics page does, just aggregated.
     */
    @Transactional(readOnly = true)
    public StaleSummary staleSummary(int thresholdDays) {
        StaleReport report = stale(thresholdDays);
        long b30 = 0, b60 = 0, b90 = 0, never = 0;
        for (StaleCandidateRow r : report.rows()) {
            if (r.lastContactedAt() == null) {
                never++;
                continue;
            }
            long d = r.daysSinceContact();
            if (d >= 90) b90++;
            else if (d >= 60) b60++;
            else if (d >= 30) b30++;
            // < 30 days can't appear because the underlying report already
            // filters by the threshold (default 30) — but if the caller
            // chose a lower threshold the row drops into b30 only when
            // d ≥ 30, so it's never double-counted.
        }
        return new StaleSummary(report.thresholdDays(), report.total(), b30, b60, b90, never);
    }

    // ── Math helpers ─────────────────────────────────────────────────────────

    private static BigDecimal avg(List<Long> values) {
        if (values == null || values.isEmpty()) return null;
        long sum = 0;
        for (long v : values) sum += v;
        return BigDecimal.valueOf((double) sum / values.size())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal median(List<Long> values) {
        if (values == null || values.isEmpty()) return null;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        if (n % 2 == 1) {
            return BigDecimal.valueOf(sorted.get(n / 2));
        }
        long left = sorted.get(n / 2 - 1);
        long right = sorted.get(n / 2);
        return BigDecimal.valueOf((left + right) / 2.0)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
