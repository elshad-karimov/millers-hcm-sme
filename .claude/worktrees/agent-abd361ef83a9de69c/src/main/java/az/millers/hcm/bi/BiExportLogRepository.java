package az.millers.hcm.bi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link BiExportLog} (PRD §17.6 / M55).
 */
public interface BiExportLogRepository extends JpaRepository<BiExportLog, UUID> {

    /** Returns the 50 most recent export log entries, newest first. */
    List<BiExportLog> findTop50ByOrderByExportedAtDesc();
}
