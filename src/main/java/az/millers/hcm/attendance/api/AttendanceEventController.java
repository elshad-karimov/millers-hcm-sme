package az.millers.hcm.attendance.api;

import az.millers.hcm.security.SecurityRoles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import az.millers.hcm.attendance.api.dto.AttendanceEventRequest;
import az.millers.hcm.attendance.api.dto.AttendanceEventResponse;
import az.millers.hcm.attendance.repo.AttendanceEventRepository;
import az.millers.hcm.attendance.service.AttendanceIngestService;
import az.millers.hcm.common.PageResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/attendance/events")
public class AttendanceEventController {

    private final AttendanceIngestService ingest;
    private final AttendanceEventRepository events;

    public AttendanceEventController(AttendanceIngestService ingest, AttendanceEventRepository events) {
        this.ingest = ingest;
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST')")
    public AttendanceEventResponse ingest(@Valid @RequestBody AttendanceEventRequest req) {
        return AttendanceEventResponse.from(ingest.ingest(req, "REST"));
    }

    @PostMapping("/csv")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST')")
    public AttendanceIngestService.CsvImportResult importCsv(@RequestPart("file") MultipartFile file) {
        return ingest.importCsv(file);
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PageResponse<AttendanceEventResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        OffsetDateTime from = fromDate.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime to = toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        var pageable = PageRequest.of(page, size, Sort.by("eventTime").descending());
        Page<az.millers.hcm.attendance.domain.AttendanceEvent> result = employeeId != null
                ? events.findByEmployeeIdAndEventTimeBetween(employeeId, from, to, pageable)
                : events.findByEventTimeBetween(from, to, pageable);
        return PageResponse.of(result, AttendanceEventResponse::from);
    }
}
