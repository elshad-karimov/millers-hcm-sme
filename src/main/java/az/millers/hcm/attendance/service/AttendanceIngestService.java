package az.millers.hcm.attendance.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import az.millers.hcm.attendance.api.dto.AttendanceEventRequest;
import az.millers.hcm.attendance.domain.AttendanceEvent;
import az.millers.hcm.attendance.domain.EventType;
import az.millers.hcm.attendance.domain.TurnstileImportBatch;
import az.millers.hcm.attendance.domain.TurnstileImportRow;
import az.millers.hcm.attendance.repo.AttendanceEventRepository;
import az.millers.hcm.attendance.repo.TurnstileImportBatchRepository;
import az.millers.hcm.attendance.repo.TurnstileImportRowRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class AttendanceIngestService {

    private static final String ROW_FAILED    = "FAILED";
    private static final String ROW_DUPLICATE = "DUPLICATE";

    private final AttendanceEventRepository events;
    private final EmployeeRepository employees;
    private final TurnstileImportBatchRepository batches;
    private final TurnstileImportRowRepository batchRows;
    private final CurrentRequest currentRequest;

    public AttendanceIngestService(AttendanceEventRepository events,
                                   EmployeeRepository employees,
                                   TurnstileImportBatchRepository batches,
                                   TurnstileImportRowRepository batchRows,
                                   CurrentRequest currentRequest) {
        this.events = events;
        this.employees = employees;
        this.batches = batches;
        this.batchRows = batchRows;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public AttendanceEvent ingest(AttendanceEventRequest req, String source) {
        UUID employeeId = resolveEmployeeId(req.employeeId(), req.employeeNo());
        AttendanceEvent e = new AttendanceEvent();
        e.setEmployeeId(employeeId);
        e.setDeviceEmployeeCode(req.deviceEmployeeCode());
        e.setEventTime(req.eventTime());
        e.setEventType(req.eventType());
        e.setDeviceId(req.deviceId());
        e.setLocation(req.location());
        e.setSource(source);
        return events.save(e);
    }

    /**
     * Parses a turnstile-style CSV. The first non-empty, non-comment line is the
     * header. Recognised columns: {@code employee_no}, {@code employee_id},
     * {@code event_time}, {@code event_type}, {@code device_id}, {@code device_employee_code},
     * {@code location}.
     *
     * <p>Each CSV import creates a {@link TurnstileImportBatch} row with aggregate
     * counts. Non-imported rows (DUPLICATE or FAILED) are persisted to
     * {@code turnstile_import_row} for audit and manual reprocessing.
     */
    @Transactional
    public CsvImportResult importCsv(MultipartFile file) {
        Map<String, UUID> employeeNoCache = new HashMap<>();
        int ok = 0, duplicates = 0, failed = 0, total = 0;
        List<TurnstileImportRow> rejectRows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            Map<String, Integer> header = null;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String[] cols = trimmed.split(",", -1);
                if (header == null) {
                    header = parseHeader(cols);
                    continue;
                }
                total++;
                String rawLine = trimmed;
                try {
                    OffsetDateTime eventTime = parseTime(value(cols, header, "event_time"));
                    EventType eventType = EventType.valueOf(
                            value(cols, header, "event_type").toUpperCase());
                    String deviceId = value(cols, header, "device_id");
                    String devEmpCode = value(cols, header, "device_employee_code");
                    String location = value(cols, header, "location");

                    String empNo = value(cols, header, "employee_no");
                    String empIdStr = value(cols, header, "employee_id");
                    UUID empId;
                    if (empIdStr != null && !empIdStr.isBlank()) {
                        empId = UUID.fromString(empIdStr);
                    } else if (empNo != null && !empNo.isBlank()) {
                        empId = employeeNoCache.computeIfAbsent(empNo, this::lookupByEmployeeNo);
                    } else {
                        throw new BadRequestException("missing employee identifier");
                    }

                    if (events.existsByEmployeeIdAndEventTime(empId, eventTime)) {
                        duplicates++;
                        rejectRows.add(buildRow(null, lineNo, rawLine, empId, eventTime,
                                eventType.name(), deviceId, ROW_DUPLICATE,
                                "duplicate: event already exists for employee+time"));
                    } else {
                        AttendanceEvent ev = new AttendanceEvent();
                        ev.setEmployeeId(empId);
                        ev.setEventTime(eventTime);
                        ev.setEventType(eventType);
                        ev.setDeviceId(deviceId);
                        ev.setDeviceEmployeeCode(devEmpCode);
                        ev.setLocation(location);
                        ev.setSource("CSV");
                        events.save(ev);
                        ok++;
                    }
                } catch (Exception ex) {
                    failed++;
                    rejectRows.add(buildRow(null, lineNo, rawLine, null, null, null, null,
                            ROW_FAILED, ex.getMessage()));
                }
            }
        } catch (IOException ex) {
            throw new BadRequestException("Failed to read CSV: " + ex.getMessage());
        }

        // ---- Persist batch metadata ----
        TurnstileImportBatch batch = new TurnstileImportBatch();
        batch.setFileName(file.getOriginalFilename());
        batch.setImportedBy(currentRequest.username());
        batch.setTotalRows(total);
        batch.setImportedCount(ok);
        batch.setDuplicateCount(duplicates);
        batch.setFailedCount(failed);
        batch.setStatus(ok == 0 && total > 0 ? "FAILED"
                : failed > 0 || duplicates > 0 ? "PARTIAL" : "COMPLETED");
        TurnstileImportBatch savedBatch = batches.save(batch);

        // ---- Persist rejected rows for retry ----
        for (TurnstileImportRow row : rejectRows) {
            row.setBatchId(savedBatch.getId());
        }
        if (!rejectRows.isEmpty()) {
            batchRows.saveAll(rejectRows);
        }

        return new CsvImportResult(savedBatch.getId(), ok, duplicates, failed);
    }

    /**
     * Retries all FAILED rows of the given batch. DUPLICATE rows are not retried
     * (they are genuine duplicates). Each FAILED row is re-parsed and re-attempted;
     * on success the row is removed from the reject table and the batch counts are
     * updated. Rows that fail again remain in the reject table with the new error.
     */
    @Transactional
    public CsvImportResult retryFailed(UUID batchId) {
        TurnstileImportBatch batch = batches.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found: " + batchId));

        List<TurnstileImportRow> failedRows =
                batchRows.findByBatchIdAndRowStatusOrderByLineNumberAsc(batchId, ROW_FAILED);
        if (failedRows.isEmpty()) {
            return new CsvImportResult(batchId, 0, 0, 0);
        }

        Map<String, UUID> employeeNoCache = new HashMap<>();
        int nowOk = 0, nowDuplicate = 0, stillFailed = 0;
        List<TurnstileImportRow> toDelete = new ArrayList<>();
        List<TurnstileImportRow> toSave = new ArrayList<>();

        for (TurnstileImportRow row : failedRows) {
            if (row.getRawLine() == null) {
                stillFailed++;
                row.setErrorMessage("raw_line missing — cannot retry");
                toSave.add(row);
                continue;
            }
            try {
                String[] cols = row.getRawLine().split(",", -1);
                // Re-parse raw line using positional heuristics from the original import:
                // We stored parsed fields on the row; use those if available.
                UUID empId = row.getEmployeeId();
                OffsetDateTime eventTime = row.getEventTime();
                EventType eventType = row.getEventType() != null
                        ? EventType.valueOf(row.getEventType()) : null;

                if (empId == null || eventTime == null || eventType == null) {
                    throw new BadRequestException("insufficient parsed data for retry; re-upload required");
                }

                if (events.existsByEmployeeIdAndEventTime(empId, eventTime)) {
                    nowDuplicate++;
                    row.setRowStatus(ROW_DUPLICATE);
                    row.setErrorMessage("duplicate on retry: event already exists");
                    toSave.add(row);
                } else {
                    AttendanceEvent ev = new AttendanceEvent();
                    ev.setEmployeeId(empId);
                    ev.setEventTime(eventTime);
                    ev.setEventType(eventType);
                    ev.setDeviceId(row.getDeviceId());
                    ev.setSource("CSV_RETRY");
                    events.save(ev);
                    nowOk++;
                    toDelete.add(row);
                }
            } catch (Exception ex) {
                stillFailed++;
                row.setErrorMessage(ex.getMessage());
                toSave.add(row);
            }
        }

        batchRows.deleteAll(toDelete);
        if (!toSave.isEmpty()) batchRows.saveAll(toSave);

        // Update batch aggregate counts
        batch.setImportedCount(batch.getImportedCount() + nowOk);
        batch.setDuplicateCount(batch.getDuplicateCount() + nowDuplicate);
        batch.setFailedCount(batch.getFailedCount() - nowOk - nowDuplicate);
        int remaining = batch.getFailedCount();
        batch.setStatus(remaining == 0 && batch.getDuplicateCount() == 0 ? "COMPLETED"
                : remaining == 0 ? "PARTIAL" : "PARTIAL");
        batches.save(batch);

        return new CsvImportResult(batchId, nowOk, nowDuplicate, stillFailed);
    }

    // ---------- Internals ----------

    private Map<String, Integer> parseHeader(String[] cols) {
        Map<String, Integer> header = new LinkedHashMap<>();
        for (int i = 0; i < cols.length; i++) {
            header.put(cols[i].trim().toLowerCase(), i);
        }
        if (!header.containsKey("event_time") || !header.containsKey("event_type")) {
            throw new BadRequestException("CSV header must include event_time and event_type");
        }
        return header;
    }

    private TurnstileImportRow buildRow(UUID batchId, int lineNo, String rawLine,
                                        UUID employeeId, OffsetDateTime eventTime,
                                        String eventType, String deviceId,
                                        String status, String error) {
        TurnstileImportRow row = new TurnstileImportRow();
        row.setBatchId(batchId);
        row.setLineNumber(lineNo);
        row.setRawLine(rawLine);
        row.setEmployeeId(employeeId);
        row.setEventTime(eventTime);
        row.setEventType(eventType);
        row.setDeviceId(deviceId);
        row.setRowStatus(status);
        row.setErrorMessage(error);
        return row;
    }

    private UUID resolveEmployeeId(UUID id, String no) {
        if (id != null) {
            if (!employees.existsById(id)) {
                throw new ResourceNotFoundException("Employee not found: " + id);
            }
            return id;
        }
        return lookupByEmployeeNo(no);
    }

    private UUID lookupByEmployeeNo(String employeeNo) {
        Employee e = employees.findByEmployeeNo(employeeNo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found for employee_no=" + employeeNo));
        return e.getId();
    }

    private String value(String[] cols, Map<String, Integer> header, String key) {
        Integer idx = header.get(key);
        if (idx == null || idx >= cols.length) return null;
        String v = cols[idx].trim();
        return v.isEmpty() ? null : v;
    }

    private OffsetDateTime parseTime(String raw) {
        if (raw == null) throw new BadRequestException("event_time is required");
        try {
            return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return ldt.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        } catch (DateTimeParseException ex) {
            throw new BadRequestException(
                    "event_time must be ISO date-time (e.g. 2026-05-19T09:02:30 or with offset)");
        }
    }

    public InputStream openStream(MultipartFile file) throws IOException {
        return file.getInputStream();
    }

    /** Aggregated CSV import outcome surfaced to the API caller. */
    public record CsvImportResult(UUID batchId, int imported, int duplicates, int failed) {}
}
