package az.millers.hcm.performance.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.performance.repo.EmployeePoolMemberRepository;
import az.millers.hcm.performance.repo.EmployeeTalentPoolRepository;
import az.millers.hcm.performance.repo.TalentReviewCycleRepository;
import az.millers.hcm.performance.repo.TalentReviewRepository;

/**
 * HCM_15 M412 — talent analytics (PRD §15.9). Read-only reporting over
 * talent_review (9-box distribution, HiPo count, retention-risk breakdown)
 * and talent pools (coverage = pool count + member count per pool).
 */
@Service
public class TalentAnalyticsService {

    private static final String TENANT = "default";

    public record NineBoxCell(int performanceBox, int potentialBox, long count) {}

    public record RetentionBreakdown(String risk, long count) {}

    public record PoolCoverage(String poolCode, String poolName, long memberCount) {}

    public record AnalyticsReport(long totalReviews, long hipoCount, double hipoPercent,
                                  List<NineBoxCell> nineBoxDistribution,
                                  List<RetentionBreakdown> retentionRiskBreakdown,
                                  List<PoolCoverage> poolCoverage) {}

    private final TalentReviewCycleRepository cycles;
    private final TalentReviewRepository reviews;
    private final EmployeeTalentPoolRepository pools;
    private final EmployeePoolMemberRepository members;
    private final NamedParameterJdbcTemplate jdbc;

    public TalentAnalyticsService(TalentReviewCycleRepository cycles,
                                  TalentReviewRepository reviews,
                                  EmployeeTalentPoolRepository pools,
                                  EmployeePoolMemberRepository members,
                                  NamedParameterJdbcTemplate jdbc) {
        this.cycles = cycles;
        this.reviews = reviews;
        this.pools = pools;
        this.members = members;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public AnalyticsReport report(UUID cycleId) {
        if (!cycles.existsById(cycleId)) {
            throw new ResourceNotFoundException("Cycle not found: " + cycleId);
        }

        List<Map<String, Object>> reviewRows = reviews.findByCycleIdOrderByDecidedAtDesc(cycleId)
                .stream()
                .map(r -> Map.<String, Object>of(
                        "performanceBox", r.getPerformanceBox() != null ? r.getPerformanceBox() : 0,
                        "potentialBox", r.getPotentialBox() != null ? r.getPotentialBox() : 0,
                        "hipoDecision", r.isHipoDecision(),
                        "retentionRisk", r.getRetentionRisk() != null ? r.getRetentionRisk() : "NONE"))
                .toList();

        long totalReviews = reviewRows.size();
        long hipoCount = reviewRows.stream().filter(r -> (Boolean) r.get("hipoDecision")).count();
        double hipoPercent = totalReviews > 0 ? (hipoCount * 100.0 / totalReviews) : 0.0;

        // 9-box distribution: group by (performanceBox, potentialBox) where both != 0.
        Map<String, Long> nineBoxCounts = new HashMap<>();
        for (Map<String, Object> r : reviewRows) {
            int perf = (Integer) r.get("performanceBox");
            int pot = (Integer) r.get("potentialBox");
            if (perf > 0 && pot > 0) {
                String key = perf + "-" + pot;
                nineBoxCounts.put(key, nineBoxCounts.getOrDefault(key, 0L) + 1);
            }
        }
        List<NineBoxCell> nineBoxDistribution = nineBoxCounts.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("-");
                    return new NineBoxCell(Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]), e.getValue());
                })
                .toList();

        // Retention risk breakdown.
        Map<String, Long> riskCounts = new HashMap<>();
        for (Map<String, Object> r : reviewRows) {
            String risk = (String) r.get("retentionRisk");
            if (!"NONE".equals(risk)) {
                riskCounts.put(risk, riskCounts.getOrDefault(risk, 0L) + 1);
            }
        }
        List<RetentionBreakdown> retentionRiskBreakdown = riskCounts.entrySet().stream()
                .map(e -> new RetentionBreakdown(e.getKey(), e.getValue()))
                .toList();

        // Pool coverage: active pools + member count.
        List<PoolCoverage> poolCoverage = pools.findByTenantIdAndActiveTrueOrderByCodeAsc(TENANT)
                .stream()
                .map(pool -> {
                    long count = members.findByPoolIdOrderByAddedAtDesc(pool.getId()).size();
                    return new PoolCoverage(pool.getCode(), pool.getName(), count);
                })
                .toList();

        return new AnalyticsReport(totalReviews, hipoCount, hipoPercent,
                nineBoxDistribution, retentionRiskBreakdown, poolCoverage);
    }
}
