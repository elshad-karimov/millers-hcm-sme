package az.millers.hcm.payroll;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Creates next-month partitions for {@code payroll.payroll_result} on the 25th
 * of every month (M175 / PRD §15.3). Idempotent.
 *
 * <p>Payroll results are partitioned per payroll period (one partition per
 * calendar month). The scheduler ensures the next month's child partition
 * exists before the payroll team opens the period.
 */
@Component
public class PayrollResultPartitionMaintenance {

    private static final Logger log = LoggerFactory.getLogger(PayrollResultPartitionMaintenance.class);
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyy_MM");

    private final NamedParameterJdbcTemplate jdbc;

    public PayrollResultPartitionMaintenance(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 50 3 25 * *")
    public void ensureNextMonth() {
        ensurePartition(LocalDate.now().plusMonths(1).withDayOfMonth(1));
    }

    public void ensurePartition(LocalDate firstOfMonth) {
        LocalDate start = firstOfMonth.withDayOfMonth(1);
        LocalDate next  = start.plusMonths(1);
        String partName = "payroll_result_" + start.format(SUFFIX);
        String sql = "CREATE TABLE IF NOT EXISTS payroll.\"" + partName + "\""
                + " PARTITION OF payroll.payroll_result FOR VALUES FROM ('"
                + start + "') TO ('" + next + "')";
        try {
            jdbc.update(sql, new MapSqlParameterSource());
            log.info("Ensured payroll_result partition {} → {}–{}", partName, start, next);
        } catch (Exception e) {
            log.warn("Failed to ensure payroll_result partition {}: {}", partName, e.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${hcm.payroll.partition.initial-delay-ms:15000}",
            fixedRateString = "${hcm.payroll.partition.refresh-ms:86400000}")
    public void ensureCurrentAndNextMonth() {
        LocalDate today = LocalDate.now();
        ensurePartition(today.withDayOfMonth(1));
        ensurePartition(today.plusMonths(1).withDayOfMonth(1));
    }
}
