package az.millers.hcm.audit;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Creates the next month's {@code audit.audit_log} partition on the 25th
 * of every month (PRD 15.3). Idempotent — if the partition already exists
 * it's a no-op.
 */
@Component
public class AuditPartitionMaintenance {

    private static final Logger log = LoggerFactory.getLogger(AuditPartitionMaintenance.class);
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyy_MM");

    private final NamedParameterJdbcTemplate jdbc;

    public AuditPartitionMaintenance(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Runs at 03:00 on the 25th of each month. Cron is 6-field
     * (second, minute, hour, dom, month, dow).
     */
    @Scheduled(cron = "0 0 3 25 * *")
    public void ensureNextMonth() {
        LocalDate target = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        ensurePartition(target);
    }

    /** Public + idempotent — also called from a startup hook below. */
    public void ensurePartition(LocalDate firstOfMonth) {
        LocalDate start = firstOfMonth.withDayOfMonth(1);
        LocalDate next = start.plusMonths(1);
        String partName = "audit_log_" + start.format(SUFFIX);

        String sql = "CREATE TABLE IF NOT EXISTS audit.\"" + partName + "\""
                + " PARTITION OF audit.audit_log FOR VALUES FROM ('"
                + start + " 00:00:00+00') TO ('" + next + " 00:00:00+00')";
        try {
            jdbc.update(sql, new MapSqlParameterSource());
            log.info("Ensured audit_log partition {} → {}–{}", partName, start, next);
        } catch (Exception e) {
            // Most likely a race against another instance creating the same
            // partition. Log + carry on; the next tick will succeed.
            log.warn("Failed to ensure partition {} ({}): {}",
                    partName, sql, e.getMessage());
        }
    }

    /**
     * One-shot at startup so a brand-new deployment doesn't sit without
     * a current-month partition. Re-runs ensurePartition for "this month"
     * and "next month" — both no-ops if the migration already created them.
     */
    @Scheduled(initialDelayString = "${hcm.audit.partition.initial-delay-ms:5000}",
            fixedRateString = "${hcm.audit.partition.refresh-ms:86400000}")
    public void ensureCurrentAndNextMonth() {
        LocalDate today = LocalDate.now();
        ensurePartition(today.withDayOfMonth(1));
        ensurePartition(today.plusMonths(1).withDayOfMonth(1));
    }
}
