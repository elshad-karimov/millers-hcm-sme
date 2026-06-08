package az.millers.hcm.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes dashboard materialized views on a regular schedule (M177 / PRD §15.2).
 *
 * <p>Each view uses {@code CONCURRENTLY} to avoid locking out readers during
 * the refresh. The views must have been populated at least once (e.g. via
 * {@link #refreshAll()} called at startup) for {@code CONCURRENTLY} to work.
 */
@Component
public class DashboardMvRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(DashboardMvRefreshJob.class);

    private static final String[] VIEWS = {
        "reporting.mv_headcount_monthly",
        "reporting.mv_turnover_monthly",
        "reporting.mv_attendance_monthly",
        "reporting.mv_leave_balance_totals",
    };

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardMvRefreshJob(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Nightly full refresh at 02:05 AM. */
    @Scheduled(cron = "0 5 2 * * *")
    public void refreshAll() {
        for (String view : VIEWS) {
            refreshView(view, true);
        }
    }

    /**
     * Warm-up on startup: populate views so CONCURRENTLY is available for
     * subsequent refreshes. Delayed slightly to let the datasource settle.
     */
    @Scheduled(initialDelayString = "${hcm.dashboard.mv.initial-delay-ms:18000}",
               fixedDelay = Long.MAX_VALUE)
    public void warmUp() {
        for (String view : VIEWS) {
            refreshView(view, false);
        }
    }

    /**
     * On-demand refresh — callable from the admin API or after payroll close.
     *
     * @param concurrently {@code true} to use {@code REFRESH MATERIALIZED VIEW
     *   CONCURRENTLY} (requires at least one prior population and a unique index).
     */
    public void refreshView(String qualifiedName, boolean concurrently) {
        String sql = "REFRESH MATERIALIZED VIEW "
                + (concurrently ? "CONCURRENTLY " : "")
                + qualifiedName;
        try {
            jdbc.update(sql, new MapSqlParameterSource());
            log.info("Refreshed materialized view {}{}", qualifiedName,
                    concurrently ? " (CONCURRENTLY)" : "");
        } catch (Exception e) {
            log.warn("Failed to refresh materialized view {}: {}", qualifiedName, e.getMessage());
        }
    }
}
