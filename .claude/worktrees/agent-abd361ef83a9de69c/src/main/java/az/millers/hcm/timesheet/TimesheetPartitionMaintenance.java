package az.millers.hcm.timesheet;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Creates next-year partitions for {@code timesheet.timesheet_day} on the 25th
 * of November every year (M173 / PRD §15.3). Yearly partitions are created one
 * month before year-end so there is always a partition ready for new rows.
 * Idempotent — {@code CREATE TABLE IF NOT EXISTS} is a no-op when the partition
 * already exists.
 */
@Component
public class TimesheetPartitionMaintenance {

    private static final Logger log = LoggerFactory.getLogger(TimesheetPartitionMaintenance.class);

    private final NamedParameterJdbcTemplate jdbc;

    public TimesheetPartitionMaintenance(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Runs on 25 November each year to create next year's partition. */
    @Scheduled(cron = "0 40 3 25 11 *")
    public void ensureNextYear() {
        ensureYearPartition(LocalDate.now().getYear() + 1);
    }

    public void ensureYearPartition(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end   = start.plusYears(1);
        String partName = "timesheet_day_" + year;
        String sql = "CREATE TABLE IF NOT EXISTS timesheet.\"" + partName + "\""
                + " PARTITION OF timesheet.timesheet_day FOR VALUES FROM ('"
                + start + "') TO ('" + end + "')";
        try {
            jdbc.update(sql, new MapSqlParameterSource());
            log.info("Ensured timesheet_day partition {} → {}–{}", partName, start, end);
        } catch (Exception e) {
            log.warn("Failed to ensure timesheet_day partition {}: {}", partName, e.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${hcm.timesheet.partition.initial-delay-ms:12000}",
            fixedRateString = "${hcm.timesheet.partition.refresh-ms:86400000}")
    public void ensureCurrentAndNextYear() {
        int year = LocalDate.now().getYear();
        ensureYearPartition(year);
        ensureYearPartition(year + 1);
    }
}
