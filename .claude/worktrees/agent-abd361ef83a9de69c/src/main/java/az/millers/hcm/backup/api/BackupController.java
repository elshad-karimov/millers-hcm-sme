package az.millers.hcm.backup.api;

import az.millers.hcm.backup.BackupService;
import az.millers.hcm.backup.repo.BackupLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for database backup management (PRD §14.4 / M53).
 *
 * <p>All endpoints require the {@code SYSTEM_ADMIN} role.
 *
 * <pre>
 *   GET  /api/admin/backups               — last 20 backup log entries
 *   POST /api/admin/backups/trigger       — trigger an on-demand backup (sync, returns 201)
 *   POST /api/admin/backups/{id}/verify   — verify a SUCCESS backup (sync, returns 200)
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/backups")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;
    private final BackupLogRepository backupLogRepository;

    /** Returns the 20 most recent backup log entries (newest first). */
    @GetMapping
    public List<BackupResponse> listBackups() {
        return backupLogRepository.findTop20ByOrderByStartedAtDesc()
                .stream()
                .map(BackupResponse::from)
                .toList();
    }

    /**
     * Triggers an on-demand backup synchronously.
     *
     * <p>The backup runs in the calling thread so the admin receives the final
     * result (SUCCESS or FAILED) in the response rather than polling.
     *
     * @return 201 Created with the resulting {@link BackupResponse}
     */
    @PostMapping("/trigger")
    @ResponseStatus(HttpStatus.CREATED)
    public BackupResponse triggerBackup() {
        return BackupResponse.from(backupService.triggerBackup("admin"));
    }

    /**
     * Verifies a SUCCESS backup by downloading, decrypting, and inspecting the
     * pg_dump header stored in MinIO.
     *
     * @param id  UUID of the backup_log entry to verify
     * @return the updated {@link BackupResponse} with status {@code VERIFIED}
     */
    @PostMapping("/{id}/verify")
    public BackupResponse verifyBackup(@PathVariable UUID id) {
        return BackupResponse.from(backupService.verifyBackup(id));
    }
}
