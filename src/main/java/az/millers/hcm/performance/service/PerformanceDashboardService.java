package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.domain.ReviewStatus;
import az.millers.hcm.performance.repo.PerformanceReviewRepository;
import az.millers.hcm.performance.repo.ReviewCycleRepository;

/**
 * HCM_12 M401 — HR performance dashboard (PRD §27.1). One read model per cycle:
 * review-status funnel, pending acknowledgements, rating distribution, high/low
 * performers, live PIP + development-plan counts.
 */
@Service
public class PerformanceDashboardService {

    public record PerformerEntry(UUID reviewId, UUID employeeId, String employeeName,
                                 BigDecimal finalRating, String finalBand) {}

    public record DashboardResponse(
            UUID cycleId, String cycleName, long totalReviews,
            Map<String, Long> byStatus,
            long pendingAcknowledgement, long disputed,
            Map<String, Long> ratingDistribution,
            List<PerformerEntry> highPerformers, List<PerformerEntry> lowPerformers,
            long activePips, long activeDevPlans, long openAppeals,
            long goalPlansPendingApproval) {}

    private final PerformanceReviewRepository reviews;
    private final ReviewCycleRepository cycles;
    private final NamedParameterJdbcTemplate jdbc;

    public PerformanceDashboardService(PerformanceReviewRepository reviews,
                                       ReviewCycleRepository cycles,
                                       NamedParameterJdbcTemplate jdbc) {
        this.reviews = reviews;
        this.cycles = cycles;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public DashboardResponse overview(UUID cycleId) {
        var cycle = cycles.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle not found: " + cycleId));
        List<PerformanceReview> rows = reviews.findByCycleIdOrderByCreatedAtDesc(cycleId);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ReviewStatus s : ReviewStatus.values()) byStatus.put(s.name(), 0L);
        long pendingAck = 0, disputed = 0;
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (PerformanceReview r : rows) {
            byStatus.merge(r.getStatus().name(), 1L, Long::sum);
            if (r.getFinalRating() != null && r.getAcknowledgedAt() == null) pendingAck++;
            if (r.isAcknowledgementDisputed()) disputed++;
            String band = r.getFinalBand() == null ? "Unrated" : r.getFinalBand();
            distribution.merge(band, 1L, Long::sum);
        }
        byStatus.values().removeIf(v -> v == 0);

        // High/low performers by final rating (names via the non-encrypted projection).
        List<PerformanceReview> rated = rows.stream()
                .filter(r -> r.getFinalRating() != null)
                .sorted((a, b) -> b.getFinalRating().compareTo(a.getFinalRating()))
                .toList();
        Map<UUID, String> names = new LinkedHashMap<>();
        if (!rated.isEmpty()) {
            jdbc.queryForList(
                    "SELECT id::text AS id, first_name || ' ' || last_name AS full_name "
                            + "FROM core_hr.employee WHERE id IN (:ids)",
                    new MapSqlParameterSource("ids",
                            rated.stream().map(PerformanceReview::getEmployeeId).distinct().toList()))
                    .forEach(row -> names.put(UUID.fromString((String) row.get("id")),
                            (String) row.get("full_name")));
        }
        List<PerformerEntry> high = rated.stream().limit(5)
                .map(r -> new PerformerEntry(r.getId(), r.getEmployeeId(),
                        names.get(r.getEmployeeId()), r.getFinalRating(), r.getFinalBand()))
                .toList();
        List<PerformerEntry> low = rated.reversed().stream().limit(5)
                .map(r -> new PerformerEntry(r.getId(), r.getEmployeeId(),
                        names.get(r.getEmployeeId()), r.getFinalRating(), r.getFinalBand()))
                .toList();

        long activePips = countScalar(
                "SELECT count(*) FROM performance.performance_pip "
                        + "WHERE status IN ('ACTIVE','ACKNOWLEDGED','IN_PROGRESS','EXTENDED')");
        long activeDevPlans = countScalar(
                "SELECT count(*) FROM performance.development_plan "
                        + "WHERE status IN ('ACTIVE','IN_PROGRESS')");
        long openAppeals = countScalar(
                "SELECT count(*) FROM performance.performance_appeal "
                        + "WHERE status IN ('SUBMITTED','UNDER_REVIEW','RETURNED')");
        long goalPlansPending = jdbc.queryForObject(
                "SELECT count(DISTINCT employee_id) FROM performance.goal "
                        + "WHERE cycle_id = :cycleId AND approval_status = 'PENDING_APPROVAL'",
                new MapSqlParameterSource("cycleId", cycleId), Long.class);

        return new DashboardResponse(cycleId, cycle.getName(), rows.size(), byStatus,
                pendingAck, disputed, distribution, high, low,
                activePips, activeDevPlans, openAppeals, goalPlansPending);
    }

    private long countScalar(String sql) {
        Long v = jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        return v == null ? 0 : v;
    }
}
