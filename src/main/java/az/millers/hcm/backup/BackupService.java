package az.millers.hcm.backup;

import az.millers.hcm.backup.domain.BackupLog;
import az.millers.hcm.backup.domain.BackupStatus;
import az.millers.hcm.backup.repo.BackupLogRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Core backup engine — pg_dump → gzip → AES-256-GCM → MinIO (PRD §14.4 / M53).
 *
 * <h3>Backup flow</h3>
 * <ol>
 *   <li>Create a {@code RUNNING} log entry.</li>
 *   <li>Fork {@code pg_dump} via {@link ProcessBuilder}.</li>
 *   <li>Gzip the raw SQL dump bytes.</li>
 *   <li>Encrypt with AES-256-GCM (12-byte IV prepended).</li>
 *   <li>Compute SHA-256 checksum of the encrypted payload.</li>
 *   <li>Upload to MinIO bucket {@code hcm-backups}.</li>
 *   <li>Mark log entry {@code SUCCESS}; on any error mark {@code FAILED}.</li>
 * </ol>
 */
@Slf4j
@Service
public class BackupService {

    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int PG_DUMP_TIMEOUT_MINUTES = 5;

    private final MinioClient minio;
    private final String datasourceUrl;
    private final String dbUser;
    private final String dbPassword;
    private final String backupBucket;
    private final String base64Key;
    /** Override the host used for the pg_dump -h flag (useful when running pg_dump inside a container). */
    private final String pgDumpHostOverride;
    /** Override the port used for the pg_dump -p flag (e.g., 5432 when the binary runs inside Docker). */
    private final int    pgDumpPortOverride;
    /** The pg_dump binary (or wrapper script) to invoke; defaults to just {@code pg_dump}. */
    private final String pgDumpCommand;
    private final BackupLogRepository repo;

    public BackupService(
            // Optional, exactly as AttachmentService already treats it: the
            // MinioClient bean only exists when hcm.storage.minio.enabled=true.
            // Requiring it here made that flag unusable — the whole application
            // refused to start with storage disabled, which is the supported
            // configuration for an SME box that has no object store.
            @Autowired(required = false) MinioClient minio,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String dbUser,
            @Value("${spring.datasource.password}") String dbPassword,
            @Value("${hcm.backup.bucket:hcm-backups}") String backupBucket,
            @Value("${hcm.security.encryption.data-key}") String base64Key,
            @Value("${hcm.backup.pg-dump-command:pg_dump}") String pgDumpCommand,
            @Value("${hcm.backup.pg-dump-host-override:}") String pgDumpHostOverride,
            @Value("${hcm.backup.pg-dump-port-override:0}") int pgDumpPortOverride,
            BackupLogRepository repo) {

        this.minio = minio;
        this.datasourceUrl = datasourceUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.backupBucket = backupBucket;
        this.base64Key = base64Key;
        this.pgDumpCommand = pgDumpCommand;
        this.pgDumpHostOverride = pgDumpHostOverride;
        this.pgDumpPortOverride = pgDumpPortOverride;
        this.repo = repo;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes a full database backup and persists the result to backup_log.
     *
     * @param triggeredBy  display name of the caller ("scheduler" or username)
     * @return the final {@link BackupLog} entry (SUCCESS or FAILED)
     */
    /**
     * Backups need somewhere to put the dump. Fail loudly and early rather than
     * NPE deep inside a running backup.
     */
    private void requireStorage() {
        if (minio == null) {
            throw new BadRequestException(
                    "Object storage is disabled (hcm.storage.minio.enabled=false), "
                            + "so backups cannot run. Enable MinIO to use this feature.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BackupLog triggerBackup(String triggeredBy) {
        requireStorage();
        BackupLog entry = new BackupLog();
        entry.setStartedAt(OffsetDateTime.now());
        entry.setStatus(BackupStatus.RUNNING);
        entry.setTriggeredBy(triggeredBy);
        entry = repo.save(entry);

        try {
            runBackup(entry);
        } catch (Exception ex) {
            log.error("Backup failed: {}", ex.getMessage(), ex);
            entry.setStatus(BackupStatus.FAILED);
            entry.setErrorMessage(truncate(ex.getMessage(), 2000));
            entry.setFinishedAt(OffsetDateTime.now());
        }

        return repo.save(entry);
    }

    /**
     * Verifies an existing SUCCESS backup by downloading, decrypting, and
     * inspecting the pg_dump header.
     *
     * @param id  UUID of the {@link BackupLog} entry to verify
     * @return the updated entry with status {@code VERIFIED}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BackupLog verifyBackup(UUID id) {
        requireStorage();
        BackupLog entry = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Backup log not found: " + id));

        if (entry.getStatus() != BackupStatus.SUCCESS) {
            throw new BadRequestException(
                    "Only SUCCESS backups can be verified; current status: " + entry.getStatus());
        }

        try {
            // Download encrypted payload from MinIO
            byte[] payload;
            try (InputStream is = minio.getObject(
                    GetObjectArgs.builder()
                            .bucket(backupBucket)
                            .object(entry.getObjectKey())
                            .build())) {
                payload = is.readAllBytes();
            }

            // Verify checksum
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String actualChecksum = HexFormat.of().formatHex(md.digest(payload));
            if (!actualChecksum.equals(entry.getChecksumSha256())) {
                throw new IllegalStateException(
                        "Checksum mismatch — expected " + entry.getChecksumSha256()
                                + " but got " + actualChecksum);
            }

            // Decrypt
            byte[] decrypted = aesGcmDecrypt(payload);

            // Decompress first 256 bytes
            byte[] header = new byte[256];
            int read;
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(decrypted))) {
                read = gz.readNBytes(header, 0, header.length);
            }

            // Verify pg_dump header
            String headerStr = new String(header, 0, read);
            if (!headerStr.contains("PostgreSQL database dump")) {
                throw new IllegalStateException(
                        "Decrypted content does not look like a pg_dump — header: "
                                + headerStr.substring(0, Math.min(100, headerStr.length())));
            }

            entry.setStatus(BackupStatus.VERIFIED);
            entry.setFinishedAt(OffsetDateTime.now());
            log.info("Backup {} verified successfully", id);

        } catch (Exception ex) {
            log.error("Backup verification failed for {}: {}", id, ex.getMessage(), ex);
            throw new BadRequestException("Verification failed: " + ex.getMessage());
        }

        return repo.save(entry);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void runBackup(BackupLog entry) throws Exception {
        // Parse JDBC URL → host, port, dbname
        // e.g. jdbc:postgresql://localhost:5433/hcm
        URI uri = new URI(datasourceUrl.replace("jdbc:", ""));
        String host   = (pgDumpHostOverride != null && !pgDumpHostOverride.isBlank())
                ? pgDumpHostOverride : uri.getHost();
        int    port   = pgDumpPortOverride > 0
                ? pgDumpPortOverride : (uri.getPort() > 0 ? uri.getPort() : 5432);
        String dbName = uri.getPath().replaceFirst("^/", "");

        log.info("Starting pg_dump for database '{}' at {}:{} (command={})", dbName, host, port, pgDumpCommand);

        // Run pg_dump
        byte[] dumpBytes = runPgDump(host, port, dbName);
        log.debug("pg_dump completed: {} raw bytes", dumpBytes.length);

        // Gzip
        byte[] compressed = gzip(dumpBytes);
        log.debug("Compressed to {} bytes", compressed.length);

        // Encrypt (AES-256-GCM)
        byte[] payload = aesGcmEncrypt(compressed);
        log.debug("Encrypted to {} bytes", payload.length);

        // SHA-256 checksum of the encrypted payload
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String checksum = HexFormat.of().formatHex(md.digest(payload));

        // Ensure bucket exists
        ensureBucket();

        // Object key: backups/hcm_yyyyMMdd_HHmmss.sql.gz.enc
        String objectKey = "backups/hcm_"
                + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
                + ".sql.gz.enc";

        minio.putObject(PutObjectArgs.builder()
                .bucket(backupBucket)
                .object(objectKey)
                .stream(new ByteArrayInputStream(payload), payload.length, -1)
                .contentType("application/octet-stream")
                .build());

        log.info("Backup uploaded to MinIO: {}/{} ({} bytes, sha256={})",
                backupBucket, objectKey, payload.length, checksum);

        entry.setStatus(BackupStatus.SUCCESS);
        entry.setObjectKey(objectKey);
        entry.setSizeBytes((long) payload.length);
        entry.setChecksumSha256(checksum);
        entry.setFinishedAt(OffsetDateTime.now());
    }

    private byte[] runPgDump(String host, int port, String dbName) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                pgDumpCommand, "-h", host, "-p", String.valueOf(port), "-U", dbUser,
                "-F", "p",           // plain-text SQL format
                "--no-password",
                dbName);
        pb.environment().put("PGPASSWORD", dbPassword);
        pb.redirectErrorStream(false);

        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new IOException(
                    "pg_dump not found in PATH — ensure pg_dump is installed and on the JVM process PATH: "
                            + e.getMessage(), e);
        }

        // Read stdout (the dump) and stderr concurrently to avoid deadlock
        byte[] dumpBytes;
        byte[] stderrBytes;
        try (InputStream stdout = proc.getInputStream();
             InputStream stderr = proc.getErrorStream()) {

            // Read stderr on a separate thread
            final byte[][] stderrHolder = {new byte[0]};
            Thread stderrReader = Thread.ofVirtual().start(() -> {
                try {
                    stderrHolder[0] = stderr.readAllBytes();
                } catch (IOException ignored) { }
            });

            dumpBytes = stdout.readAllBytes();
            stderrReader.join(TimeUnit.MINUTES.toMillis(PG_DUMP_TIMEOUT_MINUTES));
            stderrBytes = stderrHolder[0];
        }

        boolean finished = proc.waitFor(PG_DUMP_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            proc.destroyForcibly();
            throw new RuntimeException("pg_dump timed out after " + PG_DUMP_TIMEOUT_MINUTES + " minutes");
        }

        int exitCode = proc.exitValue();
        if (exitCode != 0) {
            String errMsg = stderrBytes != null ? new String(stderrBytes).trim() : "(no stderr)";
            throw new RuntimeException("pg_dump exited with code " + exitCode + ": " + errMsg);
        }

        return dumpBytes;
    }

    private byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(data);
        }
        return out.toByteArray();
    }

    private byte[] aesGcmEncrypt(byte[] plaintext) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plaintext);

        // Wire format: [12-byte IV][ciphertext + 16-byte GCM auth tag]
        byte[] payload = new byte[IV_BYTES + encrypted.length];
        System.arraycopy(iv,        0, payload, 0,        IV_BYTES);
        System.arraycopy(encrypted, 0, payload, IV_BYTES, encrypted.length);
        return payload;
    }

    private byte[] aesGcmDecrypt(byte[] payload) throws Exception {
        if (payload.length < IV_BYTES + 16) {
            throw new IllegalStateException("Payload too short for AES-GCM decryption");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(payload, 0, iv, 0, IV_BYTES);

        byte[] ciphertext = new byte[payload.length - IV_BYTES];
        System.arraycopy(payload, IV_BYTES, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    private void ensureBucket() throws Exception {
        boolean exists = minio.bucketExists(
                BucketExistsArgs.builder().bucket(backupBucket).build());
        if (!exists) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(backupBucket).build());
            log.info("MinIO bucket '{}' created", backupBucket);
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
