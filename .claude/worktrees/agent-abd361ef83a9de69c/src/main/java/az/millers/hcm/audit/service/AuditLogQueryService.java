package az.millers.hcm.audit.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.api.dto.AuditLogDtos.AuditLogDetail;
import az.millers.hcm.audit.api.dto.AuditLogDtos.AuditLogRow;
import az.millers.hcm.audit.domain.AuditLog;
import az.millers.hcm.audit.repo.AuditLogRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.PageResponse;
import az.millers.hcm.common.ResourceNotFoundException;

/**
 * Read-side service for the M114 audit-log browser.
 *
 * <p>Pure search + fetch — no mutations. The audit log is append-only by
 * design (PRD 14.5) so the only verbs here are GET. Caller-supplied
 * filter values are normalised through {@link Filter#normalise(Filter)}
 * (pure-static, unit-tested) before being handed to the repository so
 * we never pass an empty string downstream where a {@code null} is
 * expected.
 */
@Service
public class AuditLogQueryService {

    /** Hard upper bound on page size. */
    static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository repository;

    public AuditLogQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Audit-log filter set. All fields are nullable — null means
     * "don't filter on this column". An empty string is silently
     * treated as null (the UI hands us back blanks).
     */
    public record Filter(
            OffsetDateTime from,
            OffsetDateTime to,
            String module,
            String entityName,
            String entityId,
            String action,
            String actor) {

        /** Package-private — pinned by the unit test. */
        public static Filter normalise(Filter raw) {
            if (raw == null) return new Filter(null, null, null, null, null, null, null);
            return new Filter(
                    raw.from,
                    raw.to,
                    blankToNull(raw.module),
                    blankToNull(raw.entityName),
                    blankToNull(raw.entityId),
                    blankToNull(raw.action),
                    blankToNull(raw.actor));
        }

        private static String blankToNull(String s) {
            if (s == null) return null;
            String trimmed = s.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogRow> search(Filter raw, int page, int size) {
        validatePaging(page, size);
        Filter f = Filter.normalise(raw);
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
            throw new BadRequestException("'to' must be on or after 'from'");
        }
        Page<AuditLog> result = repository.search(
                f.from(), f.to(),
                f.module(), f.entityName(), f.entityId(),
                f.action(), f.actor(),
                PageRequest.of(page, size));
        return PageResponse.of(result, AuditLogRow::from);
    }

    @Transactional(readOnly = true)
    public AuditLogDetail getDetail(UUID id) {
        return repository.findById(id)
                .map(AuditLogDetail::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Audit log entry not found: " + id));
    }

    /** Returns the rows that document the lifecycle of one entity. */
    @Transactional(readOnly = true)
    public List<AuditLogRow> forEntity(String entityName, String entityId) {
        if (entityName == null || entityName.isBlank()
                || entityId == null || entityId.isBlank()) {
            throw new BadRequestException("entityName and entityId are required");
        }
        return repository
                .findByEntityNameAndEntityIdOrderByCreatedAtDesc(entityName, entityId)
                .stream().map(AuditLogRow::from).toList();
    }

    @Transactional(readOnly = true)
    public List<String> distinctModules() {
        return repository.distinctModules();
    }

    @Transactional(readOnly = true)
    public List<String> distinctEntities(String module) {
        if (module == null || module.isBlank()) return List.of();
        return repository.distinctEntitiesIn(module.trim());
    }

    @Transactional(readOnly = true)
    public List<String> distinctActions(String module) {
        if (module == null || module.isBlank()) return List.of();
        return repository.distinctActionsIn(module.trim());
    }

    /** Package-private — pinned by the unit test. */
    static void validatePaging(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be >= 0");
        }
        if (size < 1) {
            throw new BadRequestException("size must be >= 1");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException(
                    "size must be <= " + MAX_PAGE_SIZE);
        }
    }

    @SuppressWarnings("unused")
    private static OffsetDateTime utc(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 0, 0, 0, 0, ZoneOffset.UTC);
    }
}
