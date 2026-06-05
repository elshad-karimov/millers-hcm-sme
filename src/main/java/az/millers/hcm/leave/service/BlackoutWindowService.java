package az.millers.hcm.leave.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.api.BlackoutDtos.BlackoutRequest;
import az.millers.hcm.leave.api.BlackoutDtos.BlackoutResponse;
import az.millers.hcm.leave.api.BlackoutDtos.PreviewMatch;
import az.millers.hcm.leave.api.BlackoutDtos.PreviewRequest;
import az.millers.hcm.leave.api.BlackoutDtos.PreviewResponse;
import az.millers.hcm.leave.domain.BlackoutScope;
import az.millers.hcm.leave.domain.BlackoutSeverity;
import az.millers.hcm.leave.domain.BlackoutWindow;
import az.millers.hcm.leave.repo.BlackoutWindowRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M123 — orchestrates the blackout window CRUD + the applicability
 * lookup used by both {@link LeaveRequestService} on submit and by the
 * preview endpoint that powers the leave-form conflict banner.
 *
 * <p>Org-unit ancestor walk uses a recursive CTE against
 * {@code organization.org_unit} so an employee at a leaf unit picks up
 * blackouts scoped to any ancestor.
 */
@Service
public class BlackoutWindowService {

    private final BlackoutWindowRepository windows;
    private final EmployeeRepository employees;
    private final LeaveTypeRepository leaveTypes;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public BlackoutWindowService(BlackoutWindowRepository windows,
                                 EmployeeRepository employees,
                                 LeaveTypeRepository leaveTypes,
                                 NamedParameterJdbcTemplate jdbc,
                                 AuditService audit,
                                 CurrentRequest currentRequest) {
        this.windows = windows;
        this.employees = employees;
        this.leaveTypes = leaveTypes;
        this.jdbc = jdbc;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── CRUD ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BlackoutResponse> listAll() {
        return windows.findAllByOrderByStartDateDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BlackoutResponse get(UUID id) {
        return windows.findById(id).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Blackout not found: " + id));
    }

    @Transactional
    public BlackoutResponse create(BlackoutRequest req) {
        validate(req);
        BlackoutWindow w = new BlackoutWindow();
        apply(w, req);
        w.setCreatedBy(currentRequest.username());
        BlackoutWindow saved = windows.save(w);
        audit.record("leave_mgmt", "BlackoutWindow", saved.getId().toString(),
                "CREATE", null, snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public BlackoutResponse update(UUID id, BlackoutRequest req) {
        validate(req);
        BlackoutWindow w = windows.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blackout not found: " + id));
        Map<String, Object> before = snapshot(w);
        apply(w, req);
        w.setUpdatedBy(currentRequest.username());
        BlackoutWindow saved = windows.save(w);
        audit.record("leave_mgmt", "BlackoutWindow", saved.getId().toString(),
                "UPDATE", before, snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        BlackoutWindow w = windows.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blackout not found: " + id));
        Map<String, Object> before = snapshot(w);
        windows.delete(w);
        audit.record("leave_mgmt", "BlackoutWindow", id.toString(),
                "DELETE", before, null);
    }

    // ── Applicability — used by LeaveRequestService + preview ──────────────

    /**
     * Compute blackout matches for a candidate request. Returns the
     * subset of active overlapping windows that actually apply to this
     * (employee, leave type, date range). Empty list = no conflict.
     */
    @Transactional(readOnly = true)
    public List<BlackoutWindow> applicableFor(UUID employeeId,
                                              UUID leaveTypeId,
                                              LocalDate from,
                                              LocalDate to) {
        List<BlackoutWindow> overlapping = windows.findActiveOverlapping(from, to);
        if (overlapping.isEmpty()) return List.of();
        Set<UUID> orgChain = ancestorOrgUnits(employeeId);
        return BlackoutChecker.findApplicable(overlapping, orgChain, leaveTypeId, from, to);
    }

    /** REST preview surface — same calc, packaged for the SPA. */
    @Transactional(readOnly = true)
    public PreviewResponse preview(PreviewRequest req) {
        if (req.employeeId() == null || req.leaveTypeId() == null
                || req.startDate() == null || req.endDate() == null) {
            throw new BadRequestException("employeeId, leaveTypeId, startDate, endDate are required");
        }
        if (req.startDate().isAfter(req.endDate())) {
            throw new BadRequestException("startDate must be on or before endDate");
        }
        List<BlackoutWindow> hits = applicableFor(
                req.employeeId(), req.leaveTypeId(), req.startDate(), req.endDate());
        BlackoutSeverity worst = BlackoutChecker.worstSeverity(hits);
        String blockMsg = worst == BlackoutSeverity.BLOCK
                ? BlackoutChecker.formatBlockMessage(hits)
                : null;
        List<PreviewMatch> matches = new ArrayList<>(hits.size());
        for (BlackoutWindow w : hits) {
            matches.add(new PreviewMatch(
                    w.getId(), w.getName(), w.getScope(), w.getSeverity(),
                    w.getStartDate(), w.getEndDate(), w.getReason()));
        }
        return new PreviewResponse(worst, blockMsg, matches);
    }

    // ── Internals ──────────────────────────────────────────────────────────

    /**
     * Set of org-unit ids the employee inherits from. Walks the
     * {@code organization.org_unit} tree via recursive CTE so blackouts
     * scoped to a parent unit still apply to a leaf-unit employee.
     * Empty set if the employee has no unit or no longer exists.
     */
    private Set<UUID> ancestorOrgUnits(UUID employeeId) {
        Optional<Employee> emp = employees.findById(employeeId);
        if (emp.isEmpty() || emp.get().getOrgUnitId() == null) return Set.of();
        UUID start = emp.get().getOrgUnitId();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "WITH RECURSIVE chain AS ("
                + "  SELECT id, parent_id FROM organization.org_unit WHERE id = :id"
                + "  UNION ALL"
                + "  SELECT u.id, u.parent_id"
                + "    FROM organization.org_unit u"
                + "    JOIN chain c ON c.parent_id = u.id"
                + ") SELECT id::text AS id FROM chain",
                new MapSqlParameterSource("id", start));
        Set<UUID> out = new HashSet<>();
        for (Map<String, Object> r : rows) out.add(UUID.fromString((String) r.get("id")));
        return out;
    }

    private void validate(BlackoutRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new BadRequestException("name is required");
        }
        if (req.scope() == null) {
            throw new BadRequestException("scope is required");
        }
        if (req.startDate() == null || req.endDate() == null) {
            throw new BadRequestException("startDate and endDate are required");
        }
        if (req.startDate().isAfter(req.endDate())) {
            throw new BadRequestException("startDate must be on or before endDate");
        }
        switch (req.scope()) {
            case GLOBAL -> {
                if (req.orgUnitId() != null || req.leaveTypeId() != null) {
                    throw new BadRequestException("GLOBAL scope must not carry orgUnitId or leaveTypeId");
                }
            }
            case ORG_UNIT -> {
                if (req.orgUnitId() == null || req.leaveTypeId() != null) {
                    throw new BadRequestException("ORG_UNIT scope requires orgUnitId and no leaveTypeId");
                }
            }
            case LEAVE_TYPE -> {
                if (req.leaveTypeId() == null || req.orgUnitId() != null) {
                    throw new BadRequestException("LEAVE_TYPE scope requires leaveTypeId and no orgUnitId");
                }
                leaveTypes.findById(req.leaveTypeId())
                        .orElseThrow(() -> new BadRequestException("Unknown leaveTypeId"));
            }
        }
    }

    private void apply(BlackoutWindow w, BlackoutRequest req) {
        w.setName(req.name().trim());
        w.setDescription(req.description());
        w.setScope(req.scope());
        w.setOrgUnitId(req.orgUnitId());
        w.setLeaveTypeId(req.leaveTypeId());
        w.setStartDate(req.startDate());
        w.setEndDate(req.endDate());
        w.setSeverity(req.severity() == null ? BlackoutSeverity.BLOCK : req.severity());
        w.setReason(req.reason());
        w.setActive(req.active() == null ? true : req.active());
    }

    private BlackoutResponse toResponse(BlackoutWindow w) {
        String unitName = null;
        if (w.getOrgUnitId() != null) {
            List<String> names = jdbc.queryForList(
                    "SELECT name FROM organization.org_unit WHERE id = :id",
                    new MapSqlParameterSource("id", w.getOrgUnitId()), String.class);
            if (!names.isEmpty()) unitName = names.get(0);
        }
        String typeCode = null;
        if (w.getLeaveTypeId() != null) {
            typeCode = leaveTypes.findById(w.getLeaveTypeId())
                    .map(t -> t.getCode()).orElse(null);
        }
        return new BlackoutResponse(
                w.getId(), w.getName(), w.getDescription(), w.getScope(),
                w.getOrgUnitId(), unitName,
                w.getLeaveTypeId(), typeCode,
                w.getStartDate(), w.getEndDate(),
                w.getSeverity(), w.getReason(), w.isActive(),
                w.getCreatedAt(), w.getCreatedBy(),
                w.getUpdatedAt(), w.getUpdatedBy());
    }

    private static Map<String, Object> snapshot(BlackoutWindow w) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", w.getName());
        m.put("scope", w.getScope());
        m.put("orgUnitId", w.getOrgUnitId());
        m.put("leaveTypeId", w.getLeaveTypeId());
        m.put("startDate", w.getStartDate());
        m.put("endDate", w.getEndDate());
        m.put("severity", w.getSeverity());
        m.put("active", w.isActive());
        m.put("reason", w.getReason());
        return m;
    }
}
