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
import az.millers.hcm.attendance.repo.AttendanceEventRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;

@Service
public class AttendanceIngestService {

    private final AttendanceEventRepository events;
    private final EmployeeRepository employees;

    public AttendanceIngestService(AttendanceEventRepository events, EmployeeRepository employees) {
        this.events = events;
        this.employees = employees;
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
     */
    @Transactional
    public CsvImportResult importCsv(MultipartFile file) {
        Map<String, UUID> employeeNoCache = new HashMap<>();
        int ok = 0;
        List<String> errors = new java.util.ArrayList<>();

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
                    header = new LinkedHashMap<>();
                    for (int i = 0; i < cols.length; i++) {
                        header.put(cols[i].trim().toLowerCase(), i);
                    }
                    if (!header.containsKey("event_time") || !header.containsKey("event_type")) {
                        throw new BadRequestException(
                                "CSV header must include event_time and event_type");
                    }
                    continue;
                }
                try {
                    AttendanceEvent ev = new AttendanceEvent();
                    ev.setEventTime(parseTime(value(cols, header, "event_time")));
                    ev.setEventType(EventType.valueOf(value(cols, header, "event_type").toUpperCase()));
                    ev.setDeviceId(value(cols, header, "device_id"));
                    ev.setDeviceEmployeeCode(value(cols, header, "device_employee_code"));
                    ev.setLocation(value(cols, header, "location"));
                    ev.setSource("CSV");

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
                    ev.setEmployeeId(empId);
                    events.save(ev);
                    ok++;
                } catch (Exception ex) {
                    errors.add("line " + lineNo + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new BadRequestException("Failed to read CSV: " + ex.getMessage());
        }
        return new CsvImportResult(ok, errors);
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

    /** Aggregated CSV import outcome surfaced to the API caller. */
    public record CsvImportResult(int imported, List<String> errors) {
    }

    public InputStream openStream(MultipartFile file) throws IOException {
        return file.getInputStream();
    }
}
