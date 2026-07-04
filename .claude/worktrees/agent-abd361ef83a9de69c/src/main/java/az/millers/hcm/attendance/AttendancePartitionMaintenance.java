package az.millers.hcm.attendance;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Creates next-month partitions for {@code attendance.attendance_event} and
 * {@code attendance.daily_summary} on the 25th of every month
 * (M171/M173 / PRD §15.3). Idempotent — existing partitions are a no-op.
 *
 * <p>Mirrors {@link az.millers.hcm.audit.AuditPartitionMaintenance}.
 */
@Component
public class AttendancePartitionMaintenance {

    private static final Logger log = LoggerFactory.getLogger(AttendancePartitionMaintenance.class);
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyy_MM");

    private final NamedParameterJdbcTemplate jdbc;

    public AttendancePartitionMaintenance(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 10 3 25 * *")
    public void ensureNextMonth() {
        LocalDate target = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        ensureEventPartition(target);
        ensureSummaryPartition(target);
    }

    public void ensureEventPartition(LocalDate firstOfMonth) {
        LocalDate start = firstOfMonth.withDayOfMonth(1);
        LocalDate next  = start.plusMonths(1);
        String partName = "attendance_event_" + start.format(SUFFIX);
        String sql = "CREATE TABLE IF NOT EXISTS attendance.\"" + partName + "\""
                + " PARTITION OF attendance.attendance_event FOR VALUES FROM ('"
                + start + " 00:00:00+00') TO ('" + next + " 00:00:00+00')";
        try {
            jdbc.update(sql, new MapSqlParameterSource());
            log.info("Ensured attendance_event partition {} → {}–{}", partName, start, next);
        } catch (Exception e) {
            log.warn("Failed to ensure attendance_event partition {}: {}", partName, e.getMessage());
        }
    }

    public void ensureSummaryPartition(LocalDate firstOfMonth) {
        LocalDate start = firstOfMonth.withDayOfMonth(1);
        LocalDate next  = start.plusMonths(1);
        String partName = "daily_summary_" + start.format(SUFFIX);
        String sql = "CREATE TABLE IF NOT EXISTS attendance.\"" + partName + "\""
                + " PARTITION OF attendance.daily_summary FOR VALUES FROM ('"
                + start + "') TO ('" + next + "')";
        try {
            jdbc.update(sql, new MapSqlParameterSource());
            log.info("Ensured daily_summary partition {} → {}–{}", partName, start, next);
        } catch (Exception e) {
            log.warn("Failed to ensure daily_summary partition {}: {}", partName, e.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${hcm.attendance.partition.initial-delay-ms:6000}",
            fixedRateString = "${hcm.attendance.partition.refresh-ms:86400000}")
    public void ensureCurrentAndNextMonth() {
        LocalDate today = LocalDate.now();
        ensureEventPartition(today.withDayOfMonth(1));
        ensureEventPartition(today.plusMonths(1).withDayOfMonth(1));
        ensureSummaryPartition(today.withDayOfMonth(1));
        ensureSummaryPartition(today.plusMonths(1).withDayOfMonth(1));
    }
}
