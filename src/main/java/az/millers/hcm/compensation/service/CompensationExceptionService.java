package az.millers.hcm.compensation.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.api.dto.CompensationExceptionDto;
import az.millers.hcm.compensation.domain.CompensationException;
import az.millers.hcm.compensation.domain.CompensationExceptionSeverity;
import az.millers.hcm.compensation.domain.CompensationExceptionSource;
import az.millers.hcm.compensation.domain.CompensationExceptionStatus;
import az.millers.hcm.compensation.domain.CompensationExceptionType;
import az.millers.hcm.compensation.repo.CompensationExceptionRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.security.CurrentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * M362 — Compensation exceptions registry.
 * Tracks out-of-band salary changes, budget overruns, threshold violations.
 * M371 — adds notification on exception raised (non-fatal).
 */
@Service
public class CompensationExceptionService {

    private static final Logger log = LoggerFactory.getLogger(CompensationExceptionService.class);

    private static final String MODULE = "compensation";
    private static final String ENTITY = "CompensationException";

    private final CompensationExceptionRepository repo;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final NotificationService notifications;

    public CompensationExceptionService(CompensationExceptionRepository repo,
                                         EmployeeRepository employees,
                                         AuditService audit,
                                         CurrentRequest currentRequest,
                                         NotificationService notifications) {
        this.repo = repo;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.notifications = notifications;
    }

    /**
     * Raise an exception for the given source and type.
     * Idempotent — if an exception already exists for (sourceType, sourceId, exceptionType), do nothing.
     */
    @Transactional
    public CompensationException raise(UUID employeeId,
                                        CompensationExceptionSource sourceType,
                                        UUID sourceId,
                                        CompensationExceptionType exceptionType,
                                        CompensationExceptionSeverity severity,
                                        String reason) {
        // Idempotent: check if already exists
        if (repo.findBySourceTypeAndSourceIdAndExceptionType(sourceType, sourceId, exceptionType).isPresent()) {
            return repo.findBySourceTypeAndSourceIdAndExceptionType(sourceType, sourceId, exceptionType).get();
        }

        CompensationException exc = new CompensationException();
        exc.setTenantId(TenantContext.current());
        exc.setEmployeeId(employeeId);
        exc.setSourceType(sourceType);
        exc.setSourceId(sourceId);
        exc.setExceptionType(exceptionType);
        exc.setSeverity(severity != null ? severity : CompensationExceptionSeverity.MEDIUM);
        exc.setStatus(CompensationExceptionStatus.OPEN);
        exc.setReason(reason);

        CompensationException saved = repo.save(exc);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "RAISED", null, CompensationExceptionDto.from(saved));

        // M371 notification: notify manager on exception raised (non-fatal)
        try {
            Employee employee = employees.findById(employeeId).orElse(null);
            if (employee != null && employee.getManagerId() != null) {
                Employee manager = employees.findById(employee.getManagerId()).orElse(null);
                if (manager != null && manager.getUsername() != null) {
                    notifications.notifyAll(
                            NotificationCategory.TRANSACTIONAL,
                            manager.getUsername(),
                            "Compensation Exception Raised",
                            "A compensation exception (" + exceptionType.name() + ") was raised for "
                                    + employee.getFirstName() + " " + employee.getLastName() + ": " + reason,
                            MODULE, ENTITY, saved.getId().toString()
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send notification for compensation exception {}: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<CompensationExceptionDto> list(CompensationExceptionStatus status,
                                                 CompensationExceptionType exceptionType) {
        List<CompensationException> rows;
        if (status != null) {
            rows = repo.findByTenantIdAndStatusOrderByRaisedAtDesc(TenantContext.current(), status);
        } else if (exceptionType != null) {
            rows = repo.findByTenantIdAndExceptionTypeOrderByRaisedAtDesc(TenantContext.current(), exceptionType);
        } else {
            rows = repo.findByTenantIdOrderByRaisedAtDesc(TenantContext.current());
        }
        return rows.stream().map(CompensationExceptionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public CompensationException get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compensation exception not found: " + id));
    }

    @Transactional
    public CompensationException resolve(UUID id, CompensationExceptionStatus newStatus, String note) {
        if (newStatus == CompensationExceptionStatus.OPEN) {
            throw new BadRequestException("Cannot resolve to OPEN status");
        }
        CompensationException exc = get(id);
        CompensationExceptionDto before = CompensationExceptionDto.from(exc);

        exc.setStatus(newStatus);
        exc.setResolvedBy(currentRequest.username());
        exc.setResolvedAt(OffsetDateTime.now());

        CompensationException saved = repo.save(exc);
        audit.record(MODULE, ENTITY, id.toString(),
                newStatus.name(), before, CompensationExceptionDto.from(saved));
        return saved;
    }
}
