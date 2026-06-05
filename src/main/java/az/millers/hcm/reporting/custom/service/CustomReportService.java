package az.millers.hcm.reporting.custom.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.reporting.custom.CustomReportSource;
import az.millers.hcm.reporting.custom.CustomReportSpec;
import az.millers.hcm.reporting.custom.CustomReportSqlBuilder;
import az.millers.hcm.reporting.custom.FieldSpec;
import az.millers.hcm.reporting.custom.FilterOp;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.ColumnDto;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.CustomReportDetail;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.CustomReportSummary;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.FilterDto;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.RunResponse;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.SaveRequest;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.SortDto;
import az.millers.hcm.reporting.custom.domain.CustomReport;
import az.millers.hcm.reporting.custom.repo.CustomReportRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * M119 — orchestrates the custom report builder lifecycle:
 * <ul>
 *   <li>CRUD on saved definitions (name-uniqueness per owner, scoped list),</li>
 *   <li>validation via {@link CustomReportSqlBuilder#validate}
 *       — same code path used for preview and persisted runs,</li>
 *   <li>execution via {@link NamedParameterJdbcTemplate}, with ABAC
 *       narrowing through {@link AccessScopeService}.</li>
 * </ul>
 *
 * <p>Audit-trail: every save/update/delete records an entry under module
 * {@code reporting}. Runs are intentionally NOT audited per-row — that
 * would dwarf legitimate audit volume — but the spec evolution is.
 */
@Service
public class CustomReportService {

    private final CustomReportRepository repo;
    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentRequest currentRequest;
    private final AccessScopeService accessScope;
    private final AuditService audit;

    public CustomReportService(CustomReportRepository repo,
                               NamedParameterJdbcTemplate jdbc,
                               CurrentRequest currentRequest,
                               AccessScopeService accessScope,
                               AuditService audit) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.currentRequest = currentRequest;
        this.accessScope = accessScope;
        this.audit = audit;
    }

    // ── CRUD ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CustomReportSummary> listVisible() {
        String me = currentRequest.username();
        return repo.findVisibleTo(me).stream().map(r -> toSummary(r, me)).toList();
    }

    @Transactional(readOnly = true)
    public CustomReportDetail getDetail(UUID id) {
        String me = currentRequest.username();
        CustomReport r = findOrThrow(id);
        if (!canSee(r, me)) {
            throw new ResourceNotFoundException("Report not found: " + id);
        }
        return toDetail(r, me);
    }

    @Transactional
    public CustomReportDetail save(SaveRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new BadRequestException("Name is required");
        }
        String me = currentRequest.username();
        CustomReportSpec spec = materialise(req);

        // Upsert on (owner, name) — saving the same name overwrites.
        CustomReport existing = repo
                .findByOwnerUserAndNameIgnoreCase(me, req.name().trim())
                .orElseGet(() -> {
                    CustomReport fresh = new CustomReport();
                    fresh.setOwnerUser(me);
                    return fresh;
                });
        boolean isNew = existing.getId() == null;

        Map<String, Object> before = isNew ? null : snapshot(existing);
        existing.setName(req.name().trim());
        existing.setDescription(req.description());
        existing.setSourceKey(spec.source().name());
        existing.setFieldsJson(jsonFields(spec.fieldKeys()));
        existing.setFiltersJson(jsonFilters(spec.filters()));
        existing.setSortJson(jsonSorts(spec.sorts()));
        existing.setRowLimit(spec.rowLimit());
        existing.setShared(Boolean.TRUE.equals(req.shared()));

        CustomReport saved = repo.save(existing);
        audit.record("reporting", "CustomReport", saved.getId().toString(),
                isNew ? "CREATE" : "UPDATE",
                before, snapshot(saved));
        return toDetail(saved, me);
    }

    @Transactional
    public void delete(UUID id) {
        String me = currentRequest.username();
        CustomReport r = findOrThrow(id);
        if (!r.getOwnerUser().equals(me)) {
            throw new BadRequestException("Only the owner can delete a saved report");
        }
        Map<String, Object> before = snapshot(r);
        repo.delete(r);
        audit.record("reporting", "CustomReport", id.toString(), "DELETE", before, null);
    }

    // ── Execution ──────────────────────────────────────────────────────────

    /** Run a saved definition. Updates last_run_at + last_run_rows. */
    @Transactional
    public RunResponse runSaved(UUID id) {
        String me = currentRequest.username();
        CustomReport r = findOrThrow(id);
        if (!canSee(r, me)) {
            throw new ResourceNotFoundException("Report not found: " + id);
        }
        CustomReportSpec spec = specFromEntity(r);
        RunResponse resp = execute(spec);
        r.setLastRunAt(OffsetDateTime.now());
        r.setLastRunRows(resp.rowCount());
        return resp;
    }

    /** Execute an in-flight builder spec without persisting it. */
    @Transactional(readOnly = true)
    public RunResponse runPreview(SaveRequest req) {
        return execute(materialise(req));
    }

    /** Shared between runSaved and runPreview — same validation, same SQL. */
    private RunResponse execute(CustomReportSpec spec) {
        CustomReportSqlBuilder.Built built = CustomReportSqlBuilder.build(
                spec, accessScope.scopeOrNullForCurrentUser());
        List<Map<String, Object>> rawRows = jdbc.queryForList(built.sql(), built.params());

        List<ColumnDto> columns = built.columns().stream()
                .map(c -> new ColumnDto(c.key(), c.label(), c.type()))
                .toList();
        List<List<Object>> rows = new ArrayList<>(rawRows.size());
        for (Map<String, Object> row : rawRows) {
            List<Object> ordered = new ArrayList<>(built.columns().size());
            for (FieldSpec c : built.columns()) {
                ordered.add(row.get(c.key()));
            }
            rows.add(ordered);
        }
        int limit = CustomReportSqlBuilder.clampLimit(spec.rowLimit());
        return new RunResponse(
                spec.source().name(),
                spec.source().label(),
                columns,
                rows,
                rows.size(),
                limit,
                rows.size() >= limit);
    }

    // ── Spec ↔ JSON ↔ Entity glue ──────────────────────────────────────────

    /** Wire DTO → validated spec. Source lookup is the only place that throws. */
    static CustomReportSpec materialise(SaveRequest req) {
        CustomReportSource source = CustomReportSource.findByKey(req.sourceKey())
                .orElseThrow(() -> new BadRequestException("Unknown source: " + req.sourceKey()));

        List<CustomReportSpec.Filter> filters = (req.filters() == null ? List.<FilterDto>of() : req.filters())
                .stream()
                .map(f -> new CustomReportSpec.Filter(
                        f.fieldKey(),
                        f.op(),
                        f.values() == null ? List.of() : f.values()))
                .toList();
        List<CustomReportSpec.Sort> sorts = (req.sorts() == null ? List.<SortDto>of() : req.sorts())
                .stream()
                .map(s -> new CustomReportSpec.Sort(
                        s.fieldKey(),
                        CustomReportSpec.Sort.Direction.valueOf(s.direction().toUpperCase())))
                .toList();

        int limit = req.rowLimit() == null ? CustomReportSqlBuilder.DEFAULT_ROW_LIMIT : req.rowLimit();
        CustomReportSpec spec = new CustomReportSpec(
                source,
                req.fieldKeys() == null ? List.of() : req.fieldKeys(),
                filters,
                sorts,
                limit);
        CustomReportSqlBuilder.validate(spec);
        return spec;
    }

    /** Reconstruct a spec from the persisted JSONB blobs (already trusted). */
    private CustomReportSpec specFromEntity(CustomReport r) {
        CustomReportSource source = CustomReportSource.findByKey(r.getSourceKey())
                .orElseThrow(() -> new BadRequestException(
                        "Source no longer available: " + r.getSourceKey()));
        List<String> keys = (r.getFieldsJson() == null ? List.<Map<String, Object>>of() : r.getFieldsJson())
                .stream()
                .map(m -> (String) m.get("key"))
                .toList();
        List<CustomReportSpec.Filter> filters = (r.getFiltersJson() == null ? List.<Map<String, Object>>of() : r.getFiltersJson())
                .stream()
                .map(m -> new CustomReportSpec.Filter(
                        (String) m.get("fieldKey"),
                        FilterOp.valueOf((String) m.get("op")),
                        toStringList(m.get("values"))))
                .toList();
        List<CustomReportSpec.Sort> sorts = (r.getSortJson() == null ? List.<Map<String, Object>>of() : r.getSortJson())
                .stream()
                .map(m -> new CustomReportSpec.Sort(
                        (String) m.get("fieldKey"),
                        CustomReportSpec.Sort.Direction.valueOf(
                                ((String) m.get("direction")).toUpperCase())))
                .toList();
        return new CustomReportSpec(source, keys, filters, sorts, r.getRowLimit());
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) out.add(o == null ? null : o.toString());
            return out;
        }
        return List.of(value.toString());
    }

    private static List<Map<String, Object>> jsonFields(List<String> keys) {
        List<Map<String, Object>> out = new ArrayList<>(keys.size());
        for (String k : keys) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", k);
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> jsonFilters(List<CustomReportSpec.Filter> filters) {
        List<Map<String, Object>> out = new ArrayList<>(filters.size());
        for (CustomReportSpec.Filter f : filters) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fieldKey", f.fieldKey());
            m.put("op", f.op().name());
            m.put("values", f.values());
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> jsonSorts(List<CustomReportSpec.Sort> sorts) {
        List<Map<String, Object>> out = new ArrayList<>(sorts.size());
        for (CustomReportSpec.Sort s : sorts) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fieldKey", s.fieldKey());
            m.put("direction", s.direction().name());
            out.add(m);
        }
        return out;
    }

    // ── Mapping to wire ─────────────────────────────────────────────────────

    private CustomReportSummary toSummary(CustomReport r, String me) {
        CustomReportSource src = CustomReportSource.findByKey(r.getSourceKey()).orElse(null);
        return new CustomReportSummary(
                r.getId(),
                r.getName(),
                r.getDescription(),
                r.getSourceKey(),
                src == null ? r.getSourceKey() : src.label(),
                r.isShared(),
                r.getOwnerUser(),
                r.getOwnerUser().equals(me),
                r.getUpdatedAt(),
                r.getLastRunAt(),
                r.getLastRunRows());
    }

    private CustomReportDetail toDetail(CustomReport r, String me) {
        CustomReportSource src = CustomReportSource.findByKey(r.getSourceKey()).orElse(null);
        List<String> keys = (r.getFieldsJson() == null ? List.<Map<String, Object>>of() : r.getFieldsJson())
                .stream()
                .map(m -> (String) m.get("key"))
                .toList();
        List<FilterDto> filters = (r.getFiltersJson() == null ? List.<Map<String, Object>>of() : r.getFiltersJson())
                .stream()
                .map(m -> new FilterDto(
                        (String) m.get("fieldKey"),
                        FilterOp.valueOf((String) m.get("op")),
                        toStringList(m.get("values"))))
                .toList();
        List<SortDto> sorts = (r.getSortJson() == null ? List.<Map<String, Object>>of() : r.getSortJson())
                .stream()
                .map(m -> new SortDto(
                        (String) m.get("fieldKey"),
                        (String) m.get("direction")))
                .toList();
        return new CustomReportDetail(
                r.getId(),
                r.getName(),
                r.getDescription(),
                r.getSourceKey(),
                src == null ? r.getSourceKey() : src.label(),
                keys,
                filters,
                sorts,
                r.getRowLimit(),
                r.isShared(),
                r.getOwnerUser(),
                r.getOwnerUser().equals(me),
                r.getUpdatedAt(),
                r.getLastRunAt(),
                r.getLastRunRows());
    }

    private static Map<String, Object> snapshot(CustomReport r) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", r.getName());
        m.put("description", r.getDescription());
        m.put("sourceKey", r.getSourceKey());
        m.put("fields", r.getFieldsJson());
        m.put("filters", r.getFiltersJson());
        m.put("sorts", r.getSortJson());
        m.put("rowLimit", r.getRowLimit());
        m.put("shared", r.isShared());
        return m;
    }

    private CustomReport findOrThrow(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
    }

    private boolean canSee(CustomReport r, String me) {
        return r.isShared() || r.getOwnerUser().equals(me);
    }
}
