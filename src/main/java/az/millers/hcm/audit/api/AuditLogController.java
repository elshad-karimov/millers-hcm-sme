package az.millers.hcm.audit.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.audit.api.dto.AuditLogDtos.AuditLogDetail;
import az.millers.hcm.audit.api.dto.AuditLogDtos.AuditLogRow;
import az.millers.hcm.audit.service.AuditLogQueryService;
import az.millers.hcm.audit.service.AuditLogQueryService.Filter;
import az.millers.hcm.common.PageResponse;
import az.millers.hcm.security.SecurityRoles;

/**
 * M114 — Audit-log browser REST surface.
 *
 * <p>All endpoints sit behind {@link SecurityRoles#READ_AUDIT} — SYSTEM_ADMIN,
 * HR_ADMIN, AUDITOR only. HR_SPECIALIST is intentionally excluded so an
 * actor can't browse their own log entries to map what HR_ADMIN saw of
 * their actions.
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize(SecurityRoles.READ_AUDIT)
public class AuditLogController {

    private final AuditLogQueryService service;

    public AuditLogController(AuditLogQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuditLogRow> search(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.search(
                new Filter(from, to, module, entityName, entityId, action, actor),
                page, size);
    }

    @GetMapping("/{id}")
    public AuditLogDetail get(@PathVariable UUID id) {
        return service.getDetail(id);
    }

    /**
     * History for a specific {@code (entityName, entityId)} pair — handy for
     * the "show audit trail" button on any detail page. Capped to the
     * server-side natural ordering (descending by created_at).
     */
    @GetMapping("/entity")
    public List<AuditLogRow> forEntity(
            @RequestParam String entityName,
            @RequestParam String entityId) {
        return service.forEntity(entityName, entityId);
    }

    @GetMapping("/modules")
    public List<String> modules() {
        return service.distinctModules();
    }

    @GetMapping("/entities")
    public List<String> entities(@RequestParam String module) {
        return service.distinctEntities(module);
    }

    @GetMapping("/actions")
    public List<String> actions(@RequestParam String module) {
        return service.distinctActions(module);
    }
}
