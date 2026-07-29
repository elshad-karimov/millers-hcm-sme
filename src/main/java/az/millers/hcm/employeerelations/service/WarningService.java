package az.millers.hcm.employeerelations.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.employeerelations.domain.WarningLevel;
import az.millers.hcm.employeerelations.domain.WarningRecord;
import az.millers.hcm.employeerelations.repo.WarningRecordRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * M446 — Warning service.
 */
@Service
public class WarningService {

    private static final String MODULE = "employee-relations";
    private static final String ENTITY = "WarningRecord";

    private final WarningRecordRepository repo;
    private final AccessScopeService accessScope;
    private final EmployeeContextService employeeContext;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final NotificationService notifications;

    public WarningService(WarningRecordRepository repo,
                         AccessScopeService accessScope,
                         EmployeeContextService employeeContext,
                         NamedParameterJdbcTemplate jdbc,
                         AuditService audit,
                         CurrentRequest currentRequest,
                         NotificationService notifications) {
        this.repo = repo;
        this.accessScope = accessScope;
        this.employeeContext = employeeContext;
        this.jdbc = jdbc;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.notifications = notifications;
    }

    @Transactional
    public WarningRecord issue(UUID employeeId,
                              WarningLevel level,
                              String reason,
                              UUID disciplinaryActionId,
                              UUID attachmentId) {
        // HR only can issue warnings
        WarningRecord warning = new WarningRecord();
        warning.setTenantId(TenantContext.current());
        warning.setEmployeeId(employeeId);
        warning.setWarningLevel(level);
        warning.setReason(reason);
        warning.setDisciplinaryActionId(disciplinaryActionId);
        warning.setAttachmentId(attachmentId);
        warning.setIssuedAt(OffsetDateTime.now());
        warning.setCreatedBy(currentRequest.username());

        // Calculate expiry
        Integer expiryMonths = level.getDefaultExpiryMonths();
        if (expiryMonths != null) {
            warning.setExpiresAt(OffsetDateTime.now().plusMonths(expiryMonths));
        }
        // FINAL warnings have null expiresAt (indefinite)

        WarningRecord saved = repo.save(warning);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "ISSUED", null, null);

        // Notify employee (via username lookup if available)
        jdbc.query(
                "SELECT username FROM core_hr.employee WHERE id = :id AND tenant_id = :tenantId AND username IS NOT NULL",
                Map.of("id", employeeId, "tenantId", TenantContext.current()),
                rs -> {
                    if (rs.next()) {
                        String username = rs.getString("username");
                        notifications.notifyAll(NotificationCategory.TRANSACTIONAL,
                                username, "Warning Issued",
                                "A " + level + " warning has been issued. Please review and acknowledge.",
                                MODULE, ENTITY, saved.getId().toString());
                    }
                });

        return saved;
    }

    @Transactional
    public WarningRecord acknowledge(UUID id) {
        WarningRecord warning = repo.findByIdAndTenantId(id, TenantContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Warning not found: " + id));

        // Only the employee themselves can acknowledge their own warning
        UUID currentEmployeeId = employeeContext.currentEmployee().getId();
        if (!warning.getEmployeeId().equals(currentEmployeeId)) {
            throw new BadRequestException("You can only acknowledge your own warnings");
        }

        if (warning.getIsAcknowledged()) {
            throw new BadRequestException("Warning already acknowledged");
        }

        warning.setIsAcknowledged(true);
        warning.setAcknowledgedAt(OffsetDateTime.now());

        WarningRecord updated = repo.save(warning);

        audit.record(MODULE, ENTITY, id.toString(), "ACKNOWLEDGED", null, null);

        return updated;
    }

    @Transactional(readOnly = true)
    public List<WarningRecord> listByEmployee(UUID employeeId) {
        // Access controlled via @PreAuthorize in controller
        // HR_PLUS_MANAGERS already includes hierarchy filtering
        return repo.findByTenantIdAndEmployeeIdOrderByIssuedAtDesc(TenantContext.current(), employeeId);
    }

    @Transactional(readOnly = true)
    public List<WarningRecord> activeWarnings(UUID employeeId) {
        // Access controlled via @PreAuthorize in controller
        return repo.findActiveWarnings(TenantContext.current(), employeeId, OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<WarningRecord> myWarnings() {
        UUID currentEmployeeId = employeeContext.currentEmployee().getId();
        return repo.findByTenantIdAndEmployeeIdOrderByIssuedAtDesc(TenantContext.current(), currentEmployeeId);
    }
}
