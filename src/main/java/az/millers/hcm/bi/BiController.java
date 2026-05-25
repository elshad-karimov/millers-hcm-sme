package az.millers.hcm.bi;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BI export endpoints — OData v4 feed and bulk CSV download (PRD §17.6 / M55).
 *
 * <h2>Supported entities</h2>
 * <ul>
 *   <li>{@code employees}</li>
 *   <li>{@code payroll_runs}</li>
 *   <li>{@code leave_balances}</li>
 *   <li>{@code attendance_summary}</li>
 *   <li>{@code headcount_trend}</li>
 * </ul>
 *
 * <h2>Access control</h2>
 * Both endpoints require {@code SYSTEM_ADMIN} or {@code AUDITOR} role.
 *
 * <h2>OData format</h2>
 * Minimal OData v4 JSON: {@code {"@odata.context":"...","value":[...]}}
 * Power BI's OData connector consumes this without any additional library.
 */
@RestController
@RequestMapping("/api/bi")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','AUDITOR')")
public class BiController {

    private static final Set<String> VALID_ENTITIES = Set.of(
            "employees", "payroll_runs", "leave_balances",
            "attendance_summary", "headcount_trend");

    private final BiExportService service;

    public BiController(BiExportService service) {
        this.service = service;
    }

    // =========================================================================
    //  OData v4 feed
    // =========================================================================

    /**
     * Minimal OData v4 JSON feed consumable by Power BI's OData connector.
     *
     * <p>Example: {@code GET /api/bi/odata/employees}
     *
     * @param entity one of the 5 supported entity names
     * @return OData v4 wrapper {@code {"@odata.context":"...","value":[...]}}
     */
    @GetMapping(value = "/odata/{entity}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ODataResponse odata(@PathVariable String entity) {
        validateEntity(entity);
        List<Map<String, Object>> rows = fetchRows(entity);
        service.logExport(entity, "ODATA", rows.size());
        String context = "$metadata#" + entity;
        return new ODataResponse(context, rows);
    }

    // =========================================================================
    //  CSV bulk export
    // =========================================================================

    /**
     * Streams a UTF-8 CSV file suitable for Excel "Get Data → From Web" and
     * generic ETL tools.
     *
     * <p>Example: {@code GET /api/bi/export/employees.csv}
     *
     * @param entity one of the 5 supported entity names (without .csv suffix)
     */
    @GetMapping("/export/{entity}.csv")
    public void exportCsv(@PathVariable String entity, HttpServletResponse response)
            throws IOException {
        validateEntity(entity);
        List<Map<String, Object>> rows = fetchRows(entity);

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + entity + ".csv\"");

        PrintWriter writer = response.getWriter();
        // UTF-8 BOM (U+FEFF) — improves Excel auto-detection of the encoding
        writer.print('﻿');

        if (!rows.isEmpty()) {
            // Header row
            List<String> headers = List.copyOf(rows.get(0).keySet());
            writer.println(String.join(",", headers));

            // Data rows
            for (Map<String, Object> row : rows) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < headers.size(); i++) {
                    if (i > 0) sb.append(',');
                    Object value = row.get(headers.get(i));
                    sb.append(csvEscape(value));
                }
                writer.println(sb);
            }
        }
        writer.flush();

        service.logExport(entity, "CSV", rows.size());
    }

    // =========================================================================
    //  Export log
    // =========================================================================

    /**
     * Returns the 50 most recent BI export log entries (newest first).
     * Used by the frontend BiExportPage to show recent activity.
     */
    @GetMapping("/export-log")
    public List<BiExportLog> exportLog() {
        return service.getRecentLog();
    }

    // =========================================================================
    //  Internal helpers
    // =========================================================================

    private List<Map<String, Object>> fetchRows(String entity) {
        return switch (entity) {
            case "employees"          -> service.queryEmployees();
            case "payroll_runs"       -> service.queryPayrollRuns();
            case "leave_balances"     -> service.queryLeaveBalances();
            case "attendance_summary" -> service.queryAttendanceSummary();
            case "headcount_trend"    -> service.queryHeadcountTrend();
            default -> throw new IllegalArgumentException("Unknown entity: " + entity);
        };
    }

    private static void validateEntity(String entity) {
        if (!VALID_ENTITIES.contains(entity)) {
            throw new EntityNotFoundException("Unknown BI entity: " + entity);
        }
    }

    /**
     * Escapes a cell value for CSV: wraps in quotes if it contains
     * a comma, double-quote, or newline; doubles any embedded double-quotes.
     */
    private static String csvEscape(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    // =========================================================================
    //  Inner types
    // =========================================================================

    /**
     * Minimal OData v4 response wrapper.
     * The {@code @odata.context} field satisfies Power BI's parser.
     */
    public record ODataResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("@odata.context") String odataContext,
            List<Map<String, Object>> value) {
    }

    /** Thrown when an unknown entity name is requested — maps to HTTP 404. */
    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    static class EntityNotFoundException extends RuntimeException {
        EntityNotFoundException(String msg) { super(msg); }
    }
}
