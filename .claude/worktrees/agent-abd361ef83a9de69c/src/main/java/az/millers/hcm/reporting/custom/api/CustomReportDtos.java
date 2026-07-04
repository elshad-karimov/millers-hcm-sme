package az.millers.hcm.reporting.custom.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.reporting.custom.FieldType;
import az.millers.hcm.reporting.custom.FilterOp;

/**
 * M119 — wire DTOs for the custom report builder. Records the SPA POSTs to
 * save / preview a report, plus the run response.
 */
public final class CustomReportDtos {

    private CustomReportDtos() {}

    // ── Spec wire shape ─────────────────────────────────────────────────────

    public record FilterDto(String fieldKey, FilterOp op, List<String> values) {}

    public record SortDto(String fieldKey, String direction) {}

    /**
     * The body of "save" and "preview". For preview we don't need name/desc;
     * the SPA leaves them null.
     */
    public record SaveRequest(
            String name,
            String description,
            String sourceKey,
            List<String> fieldKeys,
            List<FilterDto> filters,
            List<SortDto> sorts,
            Integer rowLimit,
            Boolean shared) {}

    // ── List / detail responses ─────────────────────────────────────────────

    public record CustomReportSummary(
            UUID id,
            String name,
            String description,
            String sourceKey,
            String sourceLabel,
            boolean shared,
            String ownerUser,
            boolean mine,
            OffsetDateTime updatedAt,
            OffsetDateTime lastRunAt,
            Integer lastRunRows) {}

    public record CustomReportDetail(
            UUID id,
            String name,
            String description,
            String sourceKey,
            String sourceLabel,
            List<String> fieldKeys,
            List<FilterDto> filters,
            List<SortDto> sorts,
            int rowLimit,
            boolean shared,
            String ownerUser,
            boolean mine,
            OffsetDateTime updatedAt,
            OffsetDateTime lastRunAt,
            Integer lastRunRows) {}

    // ── Run response ────────────────────────────────────────────────────────

    public record ColumnDto(String key, String label, FieldType type) {}

    public record RunResponse(
            String sourceKey,
            String sourceLabel,
            List<ColumnDto> columns,
            List<List<Object>> rows,
            int rowCount,
            int rowLimit,
            boolean truncated) {}

    // ── Source catalogue (drives the SPA dropdowns) ─────────────────────────

    public record FieldCatalog(
            String key,
            String label,
            FieldType type,
            boolean filterable,
            boolean sortable) {}

    public record SourceCatalog(
            String key,
            String label,
            String scopeMode,    // "EMPLOYEE" or "GLOBAL"
            List<FieldCatalog> fields) {}

    public record OpCatalog(
            FilterOp op,
            int valueCount,
            List<FieldType> compatibleTypes) {}

    public record CatalogResponse(
            List<SourceCatalog> sources,
            List<OpCatalog> ops) {}
}
