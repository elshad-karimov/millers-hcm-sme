package az.millers.hcm.reporting.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.domain.AuditLog;
import az.millers.hcm.audit.repo.AuditLogRepository;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.ActivityFeed;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.ActivityRow;

/**
 * Global recent-activity feed (M80 / P2-32). Reads from {@code audit.audit_log}
 * with optional module / entity / actor filters. The system writes one audit
 * row per state-changing operation across every module, so this feed is the
 * unified narrative of "what's happening".
 *
 * <p>Scope-restriction is intentionally <em>not</em> applied here — the feed
 * is gated by role at the controller layer (HR_ADMIN + AUDITOR), neither of
 * which is scope-limited. A future revision can wire the M27
 * {@code AccessScopeService} in if managers ever get access.
 */
@Service
public class ActivityFeedService {

    private static final int DEFAULT_LIMIT = 100;

    private final AuditLogRepository auditLogs;

    public ActivityFeedService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Transactional(readOnly = true)
    public ActivityFeed recent(String module, String entityName, String actor, Integer limit) {
        int cap = limit == null || limit < 1 || limit > 500 ? DEFAULT_LIMIT : limit;
        List<AuditLog> rows = auditLogs.findRecent(module, entityName, actor, PageRequest.of(0, cap));
        var mapped = rows.stream()
                .map(r -> new ActivityRow(
                        r.getCreatedAt(),
                        r.getActor(),
                        r.getModule(),
                        r.getEntityName(),
                        r.getEntityId(),
                        r.getAction(),
                        summarise(r)))
                .toList();
        return new ActivityFeed(cap, mapped);
    }

    /** Compact human-readable line for the activity feed UI. */
    private static String summarise(AuditLog r) {
        String entity = r.getEntityName() == null ? "?" : r.getEntityName();
        String id = r.getEntityId() == null ? "" : r.getEntityId();
        String shortId = id.length() > 8 ? id.substring(0, 8) + "…" : id;
        return r.getAction() + " " + entity + (shortId.isBlank() ? "" : " #" + shortId);
    }
}
