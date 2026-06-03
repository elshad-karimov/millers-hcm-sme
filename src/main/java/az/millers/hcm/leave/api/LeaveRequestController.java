package az.millers.hcm.leave.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.leave.api.dto.LeaveRequestResponse;
import az.millers.hcm.leave.api.dto.LeaveSubmitRequest;
import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.leave.domain.LeaveRequestStatus;
import az.millers.hcm.leave.service.LeaveRequestService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/leave/requests")
public class LeaveRequestController {

    private final LeaveRequestService service;

    public LeaveRequestController(LeaveRequestService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PageResponse<LeaveRequestResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) LeaveRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("startDate").descending());
        Page<LeaveRequest> result = service.list(employeeId, status, pageable);
        return PageResponse.of(result, LeaveRequestResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public LeaveRequestResponse get(@PathVariable UUID id) {
        return LeaveRequestResponse.from(service.get(id));
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public LeaveRequestResponse submit(@Valid @RequestBody LeaveSubmitRequest req) {
        return LeaveRequestResponse.from(service.submit(req));
    }
}
