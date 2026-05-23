package az.millers.hcm.corehr.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.api.dto.HolidayRequest;
import az.millers.hcm.corehr.api.dto.HolidayResponse;
import az.millers.hcm.corehr.service.HolidayService;
import jakarta.validation.Valid;

/**
 * Public-holiday calendar CRUD (PRD 8.4 / 8.5 / 8.9 — M38).
 *
 * <p>Read is open to any authenticated user — the calendar appears in
 * Timesheet rendering, Leave forms, and the My Workspace dashboard.
 * Write paths are HR_ADMIN / SYSTEM_ADMIN only so operators can patch
 * the calendar (e.g. mid-year decree adds a one-off day off).
 */
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService service;

    public HolidayController(HolidayService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<HolidayResponse> list(@RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Integer month,
                                       @RequestParam(required = false) String jurisdiction) {
        return service.list(year, month, jurisdiction).stream()
                .map(HolidayResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public HolidayResponse get(@PathVariable UUID id) {
        return HolidayResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public HolidayResponse create(@Valid @RequestBody HolidayRequest req) {
        return HolidayResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public HolidayResponse update(@PathVariable UUID id, @Valid @RequestBody HolidayRequest req) {
        return HolidayResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
