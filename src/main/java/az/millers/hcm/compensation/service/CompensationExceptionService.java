package az.millers.hcm.compensation.service;

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
import az.millers.hcm.security.CurrentRequest;

/**
 * M362 — Compensation exceptions registry.
 * Tracks out-of-band salary changes, budget overruns, threshold violations.
 */
@Service
public class CompensationExceptionService {

    private static final String MODULE = "compensation";
    private static final String ENTITY = "CompensationException";
    private static final String TENANT = "default";

    private final CompensationExceptionRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public CompensationExceptionService(CompensationExceptionRepository repo,
                                         AuditService audit,
                                         CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
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
        exc.setTenantId(TENANT);
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
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CompensationExceptionDto> list(CompensationExceptionStatus status,
                                                 CompensationExceptionType exceptionType) {
        List<CompensationException> rows;
        if (status != null) {
            rows = repo.findByTenantIdAndStatusOrderByRaisedAtDesc(TENANT, status);
        } else if (exceptionType != null) {
            rows = repo.findByTenantIdAndExceptionTypeOrderByRaisedAtDesc(TENANT, exceptionType);
        } else {
            rows = repo.findByTenantIdOrderByRaisedAtDesc(TENANT);
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
