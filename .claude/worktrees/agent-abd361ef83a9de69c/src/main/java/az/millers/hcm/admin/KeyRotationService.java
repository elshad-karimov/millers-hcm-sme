package az.millers.hcm.admin;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.security.crypto.AesGcmEncryptor;

/**
 * AES-256-GCM key rotation service (M48 — PRD 14.3).
 *
 * <p>Iterates every encrypted column that may contain {@code enc:v1:…} rows,
 * decrypts with the v1 key, re-encrypts with the v2 key (writing
 * {@code enc:v2:…}), and updates the row in-place via raw JDBC — bypassing
 * JPA to avoid dirty-tracking interference with the
 * {@code EncryptedStringConverter}.
 *
 * <h3>Encrypted columns</h3>
 * <ul>
 *   <li>{@code core_hr.employee.national_id}</li>
 *   <li>{@code payroll.bank_account.iban}</li>
 *   <li>{@code payroll.bank_account.account_number}</li>
 *   <li>{@code reporting.report_schedule.webhook_url}</li>
 *   <li>{@code reporting.report_run.webhook_target}</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <ol>
 *   <li>Deploy with {@code HCM_SECURITY_DATA_KEY_V2} set to the new key.
 *   <li>Call {@code POST /api/admin/key-rotation/run} (SYSTEM_ADMIN only).
 *   <li>Verify {@code rotated} count; {@code failed} must be 0.
 *   <li>In the next deploy: move v2 → v1, unset v2 property.
 * </ol>
 */
@Service
public class KeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(KeyRotationService.class);

    private final AesGcmEncryptor encryptor;
    private final JdbcTemplate    jdbc;

    public KeyRotationService(AesGcmEncryptor encryptor, JdbcTemplate jdbc) {
        this.encryptor = encryptor;
        this.jdbc      = jdbc;
    }

    /**
     * Rotates all {@code enc:v1:} column values to {@code enc:v2:}.
     *
     * @param dryRun when {@code true}, counts candidates but issues no UPDATE.
     * @throws IllegalStateException if the v2 key is not configured.
     */
    @Transactional
    public KeyRotationResult rotate(boolean dryRun) {
        if (!encryptor.hasV2Key()) {
            throw new IllegalStateException(
                    "Key rotation requires hcm.security.encryption.data-key-v2 to be configured");
        }

        StringBuilder details = new StringBuilder();
        int totalRotated = 0;
        int totalSkipped = 0;
        int totalFailed  = 0;

        // ── employee.national_id ────────────────────────────────────────────
        ColumnResult empNid = rotateColumn(
                "core_hr.employee", "id", "national_id", dryRun);
        details.append("core_hr.employee.national_id: ").append(empNid).append("\n");
        totalRotated += empNid.rotated();
        totalSkipped += empNid.skipped();
        totalFailed  += empNid.failed();

        // ── bank_account.iban ───────────────────────────────────────────────
        ColumnResult bankIban = rotateColumn(
                "payroll.bank_account", "id", "iban", dryRun);
        details.append("payroll.bank_account.iban: ").append(bankIban).append("\n");
        totalRotated += bankIban.rotated();
        totalSkipped += bankIban.skipped();
        totalFailed  += bankIban.failed();

        // ── bank_account.account_number ─────────────────────────────────────
        ColumnResult bankAcct = rotateColumn(
                "payroll.bank_account", "id", "account_number", dryRun);
        details.append("payroll.bank_account.account_number: ").append(bankAcct).append("\n");
        totalRotated += bankAcct.rotated();
        totalSkipped += bankAcct.skipped();
        totalFailed  += bankAcct.failed();

        // ── report_schedule.webhook_url ─────────────────────────────────────
        ColumnResult schedUrl = rotateColumn(
                "reporting.report_schedule", "id", "webhook_url", dryRun);
        details.append("reporting.report_schedule.webhook_url: ").append(schedUrl).append("\n");
        totalRotated += schedUrl.rotated();
        totalSkipped += schedUrl.skipped();
        totalFailed  += schedUrl.failed();

        // ── report_run.webhook_target ────────────────────────────────────────
        ColumnResult runTarget = rotateColumn(
                "reporting.report_run", "id", "webhook_target", dryRun);
        details.append("reporting.report_run.webhook_target: ").append(runTarget);
        totalRotated += runTarget.rotated();
        totalSkipped += runTarget.skipped();
        totalFailed  += runTarget.failed();

        log.info("Key rotation {} — rotated={} skipped={} failed={}\n{}",
                dryRun ? "(dry-run)" : "complete",
                totalRotated, totalSkipped, totalFailed, details);

        return new KeyRotationResult(dryRun, totalRotated, totalSkipped, totalFailed,
                details.toString().trim());
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private ColumnResult rotateColumn(
            String table, String idCol, String valueCol, boolean dryRun) {

        String sql = String.format(
                "SELECT %s, %s FROM %s WHERE %s LIKE 'enc:v1:%%'",
                idCol, valueCol, table, valueCol);

        List<Object[]> rows = jdbc.query(sql,
                (rs, i) -> new Object[]{ rs.getObject(1), rs.getString(2) });

        int rotated = 0, skipped = 0, failed = 0;

        for (Object[] row : rows) {
            Object id       = row[0];
            String wireV1   = (String) row[1];

            if (!encryptor.isV1Encrypted(wireV1)) {
                // Already on v2 or legacy plaintext — skip.
                skipped++;
                continue;
            }
            try {
                String plaintext = encryptor.decrypt(wireV1);
                String wireV2    = encryptor.encrypt(plaintext);

                if (!dryRun) {
                    String upd = String.format(
                            "UPDATE %s SET %s = ? WHERE %s = ?",
                            table, valueCol, idCol);
                    jdbc.update(upd, wireV2, id);
                }
                rotated++;
            } catch (Exception ex) {
                log.error("Failed to rotate {}.{}[{}]: {}", table, valueCol, id, ex.getMessage());
                failed++;
            }
        }

        return new ColumnResult(rotated, skipped, failed);
    }

    /** Per-column rotation counts. */
    private record ColumnResult(int rotated, int skipped, int failed) {
        @Override
        public String toString() {
            return "rotated=" + rotated + " skipped=" + skipped + " failed=" + failed;
        }
    }
}
