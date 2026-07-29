package az.millers.hcm.integration.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.integration.api.dto.IntegrationConfigListDto;
import az.millers.hcm.integration.domain.IntegrationConfig;
import az.millers.hcm.integration.domain.IntegrationDirection;
import az.millers.hcm.integration.domain.IntegrationLog;
import az.millers.hcm.integration.domain.IntegrationStatus;
import az.millers.hcm.integration.domain.IntegrationType;
import az.millers.hcm.integration.repo.IntegrationConfigRepository;
import az.millers.hcm.integration.repo.IntegrationLogRepository;

/**
 * M492 — Integration registry service. CRUD for integration configs + public
 * seam for recording execution logs. 90-day purge job.
 */
@Service
@Transactional
public class IntegrationRegistryService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationRegistryService.class);

    private final IntegrationConfigRepository configRepo;
    private final IntegrationLogRepository logRepo;
    private final AuditService audit;

    public IntegrationRegistryService(IntegrationConfigRepository configRepo,
                                       IntegrationLogRepository logRepo,
                                       AuditService audit) {
        this.configRepo = configRepo;
        this.logRepo = logRepo;
        this.audit = audit;
    }

    // ── Config CRUD ────────────────────────────────────────────────────────────

    public IntegrationConfig create(String code, String name, IntegrationDirection direction,
                                     IntegrationType type, String endpointUrl,
                                     String credentialsRef, String notes, String createdBy) {
        // Check uniqueness
        configRepo.findByCodeAndTenantId(code, TenantContext.current()).ifPresent(existing -> {
            throw new IllegalArgumentException("Integration code already exists: " + code);
        });

        IntegrationConfig config = new IntegrationConfig();
        config.setTenantId(TenantContext.current());
        config.setCode(code);
        config.setName(name);
        config.setDirection(direction);
        config.setType(type);
        config.setEndpointUrl(endpointUrl);
        config.setCredentialsRef(credentialsRef);
        config.setNotes(notes);
        config.setCreatedBy(createdBy);

        IntegrationConfig saved = configRepo.save(config);

        audit.record("INTEGRATION", "IntegrationConfig", saved.getId().toString(),
                    "CREATED", null, saved);

        log.info("Created integration config {} ({})", code, name);
        return saved;
    }

    public IntegrationConfig update(UUID id, String name, IntegrationDirection direction,
                                     IntegrationType type, String endpointUrl,
                                     String credentialsRef, boolean enabled, String notes,
                                     String updatedBy) {
        IntegrationConfig config = configRepo.findByIdAndTenantId(id, TenantContext.current())
            .orElseThrow(() -> new ResourceNotFoundException("Integration config not found: " + id));

        IntegrationConfig before = clone(config);

        config.setName(name);
        config.setDirection(direction);
        config.setType(type);
        config.setEndpointUrl(endpointUrl);
        config.setCredentialsRef(credentialsRef);
        config.setEnabled(enabled);
        config.setNotes(notes);
        config.setUpdatedBy(updatedBy);

        IntegrationConfig saved = configRepo.save(config);

        audit.record("INTEGRATION", "IntegrationConfig", saved.getId().toString(),
                    "UPDATED", before, saved);

        log.info("Updated integration config {}", config.getCode());
        return saved;
    }

    public void delete(UUID id, String deletedBy) {
        IntegrationConfig config = configRepo.findByIdAndTenantId(id, TenantContext.current())
            .orElseThrow(() -> new ResourceNotFoundException("Integration config not found: " + id));

        configRepo.delete(config);

        audit.record("INTEGRATION", "IntegrationConfig", id.toString(),
                    "DELETED", config, null);

        log.info("Deleted integration config {} by {}", config.getCode(), deletedBy);
    }

    public List<IntegrationConfigListDto> listAll() {
        return configRepo.findByTenantIdOrderByNameAsc(TenantContext.current()).stream()
            .map(IntegrationConfigListDto::from)
            .collect(Collectors.toList());
    }

    public List<IntegrationConfigListDto> listEnabled() {
        return configRepo.findByTenantIdAndEnabledOrderByNameAsc(TenantContext.current(), true).stream()
            .map(IntegrationConfigListDto::from)
            .collect(Collectors.toList());
    }

    public IntegrationConfig get(UUID id) {
        return configRepo.findByIdAndTenantId(id, TenantContext.current())
            .orElseThrow(() -> new ResourceNotFoundException("Integration config not found: " + id));
    }

    public IntegrationConfig getByCode(String code) {
        return configRepo.findByCodeAndTenantId(code, TenantContext.current())
            .orElseThrow(() -> new ResourceNotFoundException("Integration config not found: " + code));
    }

    // ── Execution log seam ─────────────────────────────────────────────────────

    /**
     * Public seam for other services to record integration execution.
     * Non-fatal — swallows exceptions so integration log failures don't break
     * the caller's transaction.
     */
    public void recordRun(String code, IntegrationStatus status, int recordsProcessed, String errorMessage) {
        try {
            IntegrationConfig config = configRepo.findByCodeAndTenantId(code, TenantContext.current())
                .orElse(null);
            if (config == null) {
                log.warn("recordRun: integration config not found for code '{}'", code);
                return;
            }

            // Update config last run
            config.setLastRunAt(OffsetDateTime.now());
            config.setLastStatus(status.name());
            configRepo.save(config);

            // Create log entry
            IntegrationLog logEntry = new IntegrationLog();
            logEntry.setTenantId(TenantContext.current());
            logEntry.setConfigId(config.getId());
            logEntry.setStatus(status);
            logEntry.setRecordsProcessed(recordsProcessed);
            logEntry.setErrorMessage(errorMessage);
            logRepo.save(logEntry);

            log.debug("Recorded integration run: {} → {} ({} records)", code, status, recordsProcessed);
        } catch (Exception ex) {
            log.error("Failed to record integration run for {}: {}", code, ex.getMessage(), ex);
        }
    }

    /**
     * Get recent logs for a specific config.
     */
    public List<IntegrationLog> getLogsForConfig(UUID configId, int limit) {
        Pageable page = PageRequest.of(0, limit);
        return logRepo.findByConfigIdOrderByRunAtDesc(configId, page);
    }

    /**
     * Get recent failed logs across all integrations.
     */
    public List<IntegrationLog> getRecentFailures(int limit) {
        Pageable page = PageRequest.of(0, limit);
        return logRepo.findByTenantIdAndStatusOrderByRunAtDesc(TenantContext.current(), IntegrationStatus.FAILED, page);
    }

    // ── Daily purge job ────────────────────────────────────────────────────────

    /**
     * Daily job to purge integration logs older than 90 days.
     * Runs at 02:00 AM daily.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeOldLogs() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(90);
        int deleted = logRepo.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Purged {} integration log entries older than {}", deleted, cutoff.toLocalDate());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private IntegrationConfig clone(IntegrationConfig config) {
        IntegrationConfig copy = new IntegrationConfig();
        copy.setId(config.getId());
        copy.setCode(config.getCode());
        copy.setName(config.getName());
        copy.setDirection(config.getDirection());
        copy.setType(config.getType());
        copy.setEndpointUrl(config.getEndpointUrl());
        copy.setCredentialsRef(config.getCredentialsRef());
        copy.setEnabled(config.isEnabled());
        copy.setNotes(config.getNotes());
        return copy;
    }
}
