package az.millers.hcm.workflow;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Creates next-month partitions for {@code workflow.workflow_action} on
 * the 25th of every month (M173 / PRD §15.3). Idempotent.
 */
@Component
public class WorkflowPartitionMaintenance {

    private static final Logger log = LoggerFactory.getLogger(WorkflowPartitionMaintenance.class);
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyy_MM");

    private final NamedParameterJdbcTemplate jdbc;

    public WorkflowPartitionMaintenance(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 30 3 25 * *")
    public void ensureNextMonth() {
        ensurePartition(OffsetDateTime.now().plusMonths(1).withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0));
    }

    public void ensurePartition(OffsetDateTime firstOfMonth) {
        OffsetDateTime start = firstOfMonth.withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime next  = start.plusMonths(1);
        String partName = "workflow_action_" + start.format(SUFFIX);
        String sql = "CREATE TABLE IF NOT EXISTS workflow.\"" + partName + "\""
                + " PARTITION OF workflow.workflow_action FOR VALUES FROM ('"
                + start.toInstant() + "') TO ('" + next.toInstant() + "')";
        try {
            jdbc.update(sql, new MapSqlParameterSource());
            log.info("Ensured workflow_action partition {} → {}–{}", partName, start, next);
        } catch (Exception e) {
            log.warn("Failed to ensure workflow_action partition {}: {}", partName, e.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${hcm.workflow.partition.initial-delay-ms:9000}",
            fixedRateString = "${hcm.workflow.partition.refresh-ms:86400000}")
    public void ensureCurrentAndNextMonth() {
        OffsetDateTime now = OffsetDateTime.now();
        ensurePartition(now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0));
        ensurePartition(now.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0));
    }
}
