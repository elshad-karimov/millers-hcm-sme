package az.millers.hcm.attendance.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily cron that automatically runs the attendance engine for the previous
 * calendar day (PRD §8.4.8 / M197).
 *
 * <p>The engine is idempotent — re-running for a date upserts the summary row,
 * so this scheduler is safe even when triggered multiple times.
 *
 * <p>Default schedule: 01:30 UTC every day, giving turnstile imports from
 * the night before time to settle. Override via
 * {@code hcm.attendance.engine.daily-cron}.
 */
@Component
public class AttendanceEngineScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttendanceEngineScheduler.class);

    private final AttendanceEngine engine;

    public AttendanceEngineScheduler(AttendanceEngine engine) {
        this.engine = engine;
    }

    @Scheduled(cron = "${hcm.attendance.engine.daily-cron:0 30 1 * * *}")
    public void runYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            var result = engine.run(yesterday, yesterday, null);
            log.info("AttendanceEngineScheduler: processed {} employee(s), wrote {} summary row(s) for {}",
                    result.employeesProcessed(), result.summariesWritten(), yesterday);
        } catch (Exception ex) {
            log.error("AttendanceEngineScheduler: failed for {}: {}", yesterday, ex.getMessage(), ex);
        }
    }
}
