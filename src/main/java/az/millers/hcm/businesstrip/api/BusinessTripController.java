package az.millers.hcm.businesstrip.api;

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

import az.millers.hcm.businesstrip.api.dto.BusinessTripResponse;
import az.millers.hcm.businesstrip.api.dto.BusinessTripSubmitRequest;
import az.millers.hcm.businesstrip.api.dto.ExpenseReconcileRequest;
import az.millers.hcm.businesstrip.domain.BusinessTripRequest;
import az.millers.hcm.businesstrip.domain.TripStatus;
import az.millers.hcm.businesstrip.service.BusinessTripService;
import az.millers.hcm.common.PageResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/business-trips")
public class BusinessTripController {

    private final BusinessTripService service;

    public BusinessTripController(BusinessTripService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER','DEPARTMENT_MANAGER')")
    public PageResponse<BusinessTripResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("startDate").descending());
        Page<BusinessTripRequest> result = service.list(employeeId, status, pageable);
        return PageResponse.of(result, BusinessTripResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public BusinessTripResponse get(@PathVariable UUID id) {
        return BusinessTripResponse.from(service.get(id));
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public BusinessTripResponse submit(@Valid @RequestBody BusinessTripSubmitRequest req) {
        return BusinessTripResponse.from(service.submit(req));
    }

    @PostMapping("/{id}/reconcile")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN','FINANCE_USER')")
    public BusinessTripResponse reconcile(@PathVariable UUID id,
                                           @Valid @RequestBody ExpenseReconcileRequest req) {
        return BusinessTripResponse.from(service.reconcile(id, req));
    }
}
