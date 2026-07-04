package az.millers.hcm.notifications;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Creates the next month's {@code notification.notification_log} partition on
 * the 25th of every month (M171 / PRD §15.3). Idempotent — if the partition
 * already exists it is a no-op.
 *
 * <p>Mirrors {@link az.millers.hcm.audit.AuditPartitionMaintenance}.
 */
@Component
public class NotificationPartitionMaintenance {

    private static final Logger log = LoggerFactory.getLogger(NotificationPartitionMaintenance.class);
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyy_MM");

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationPartitionMaintenance(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 20 3 25 * *")
    public void ensureNextMonth() {
        LocalDate target = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        ensurePartition(target);
    }

    public void ensurePartition(LocalDate firstOfMonth) {
        LocalDate start = firstOfMonth.withDayOfMonth(1);
        LocalDate next  = start.plusMonths(1);
        String partName = "notification_log_" + start.format(SUFFIX);

        String sql = "CREATE TABLE IF NOT EXISTS notification.\"" + partName + "\""
                + " PARTITION OF notification.notification_log FOR VALUES FROM ('"
                + start + " 00:00:00+00') TO ('" + next + " 00:00:00+00')";
        try {
            jdbc.update(sql, new MapSqlParameterSource());
            log.info("Ensured notification_log partition {} → {}–{}", partName, start, next);
        } catch (Exception e) {
            log.warn("Failed to ensure notification partition {} : {}", partName, e.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${hcm.notification.partition.initial-delay-ms:7000}",
            fixedRateString = "${hcm.notification.partition.refresh-ms:86400000}")
    public void ensureCurrentAndNextMonth() {
        LocalDate today = LocalDate.now();
        ensurePartition(today.withDayOfMonth(1));
        ensurePartition(today.plusMonths(1).withDayOfMonth(1));
    }
}
