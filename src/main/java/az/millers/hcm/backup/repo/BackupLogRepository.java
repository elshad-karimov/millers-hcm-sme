package az.millers.hcm.backup.repo;

import az.millers.hcm.backup.domain.BackupLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BackupLogRepository extends JpaRepository<BackupLog, UUID> {

    /** Returns the 20 most recent backup log entries, newest first. */
    List<BackupLog> findTop20ByOrderByStartedAtDesc();
}
