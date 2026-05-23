package az.millers.hcm.staffing.api;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.staffing.api.dto.PositionRequest;
import az.millers.hcm.staffing.api.dto.PositionResponse;
import az.millers.hcm.staffing.api.dto.VacancyStateChangeRequest;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionStatus;
import az.millers.hcm.staffing.domain.VacancyState;
import az.millers.hcm.staffing.service.StaffingService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private final StaffingService service;

    public PositionController(StaffingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER','FINANCE_USER')")
    public PageResponse<PositionResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID orgUnitId,
            @RequestParam(required = false) VacancyState vacancyState,
            @RequestParam(required = false) PositionStatus status) {

        var pageable = PageRequest.of(page, size, Sort.by("code").ascending());
        Page<Position> result = service.list(search, orgUnitId, vacancyState, status, pageable);
        return PageResponse.of(result, PositionResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER','FINANCE_USER')")
    public PositionResponse get(@PathVariable UUID id) {
        return PositionResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public PositionResponse create(@Valid @RequestBody PositionRequest req) {
        return PositionResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public PositionResponse update(@PathVariable UUID id, @Valid @RequestBody PositionRequest req) {
        return PositionResponse.from(service.update(id, req));
    }

    @PostMapping("/{id}/vacancy-state")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public PositionResponse changeVacancyState(@PathVariable UUID id,
                                                @Valid @RequestBody VacancyStateChangeRequest req) {
        return PositionResponse.from(service.changeVacancyState(id, req.newState(), req.reason()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public PositionResponse close(@PathVariable UUID id,
                                   @RequestBody(required = false) az.millers.hcm.organization.api.dto.StatusTransitionRequest req) {
        return PositionResponse.from(service.close(id, req == null ? null : req.reason()));
    }
}
