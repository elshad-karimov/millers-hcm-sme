package az.millers.hcm.backup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the daily encrypted database backup at 02:00 every night (PRD §14.4 / M53).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupService backupService;

    /** Fires every day at 02:00 AM server time. */
    @Scheduled(cron = "0 0 2 * * *")
    public void dailyBackup() {
        log.info("Starting scheduled daily backup...");
        backupService.triggerBackup("scheduler");
    }
}
