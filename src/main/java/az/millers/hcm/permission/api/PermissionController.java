package az.millers.hcm.permission.api;

import java.time.Year;
import java.util.List;
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
import az.millers.hcm.permission.api.dto.PermissionBalanceAdjustment;
import az.millers.hcm.permission.api.dto.PermissionBalanceResponse;
import az.millers.hcm.permission.api.dto.PermissionRequestResponse;
import az.millers.hcm.permission.api.dto.PermissionSubmitRequest;
import az.millers.hcm.permission.api.dto.PermissionTypeRequest;
import az.millers.hcm.permission.api.dto.PermissionTypeResponse;
import az.millers.hcm.permission.domain.PermissionRequest;
import az.millers.hcm.permission.domain.PermissionRequestStatus;
import az.millers.hcm.permission.service.PermissionBalanceService;
import az.millers.hcm.permission.service.PermissionRequestService;
import az.millers.hcm.permission.service.PermissionTypeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/permission")
public class PermissionController {

    private final PermissionTypeService typeService;
    private final PermissionBalanceService balanceService;
    private final PermissionRequestService requestService;

    public PermissionController(PermissionTypeService typeService,
                                 PermissionBalanceService balanceService,
                                 PermissionRequestService requestService) {
        this.typeService = typeService;
        this.balanceService = balanceService;
        this.requestService = requestService;
    }

    // ---------- Types ----------

    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    public List<PermissionTypeResponse> listTypes(@RequestParam(defaultValue = "false") boolean activeOnly) {
        var rows = activeOnly ? typeService.listActive() : typeService.list();
        return rows.stream().map(PermissionTypeResponse::from).toList();
    }

    @PostMapping("/types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public PermissionTypeResponse createType(@Valid @RequestBody PermissionTypeRequest req) {
        return PermissionTypeResponse.from(typeService.create(req));
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public PermissionTypeResponse updateType(@PathVariable UUID id,
                                              @Valid @RequestBody PermissionTypeRequest req) {
        return PermissionTypeResponse.from(typeService.update(id, req));
    }

    @GetMapping("/types/{id}")
    @PreAuthorize("isAuthenticated()")
    public PermissionTypeResponse getType(@PathVariable UUID id) {
        return PermissionTypeResponse.from(typeService.get(id));
    }

    // ---------- Balances ----------

    @GetMapping("/balances")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR')")
    public List<PermissionBalanceResponse> listBalances(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) Integer year) {
        int y = year != null ? year : Year.now().getValue();
        var rows = employeeId != null
                ? balanceService.listForEmployee(employeeId, y)
                : balanceService.listForYear(y);
        return rows.stream().map(PermissionBalanceResponse::from).toList();
    }

    @PostMapping("/balances/adjust")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public PermissionBalanceResponse adjustBalance(@Valid @RequestBody PermissionBalanceAdjustment req) {
        return PermissionBalanceResponse.from(balanceService.applyAdjustment(req));
    }

    // ---------- Requests ----------

    @GetMapping("/requests")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')")
    public PageResponse<PermissionRequestResponse> listRequests(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) PermissionRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("permissionDate").descending());
        Page<PermissionRequest> result = requestService.list(employeeId, status, pageable);
        return PageResponse.of(result, PermissionRequestResponse::from);
    }

    @GetMapping("/requests/{id}")
    @PreAuthorize("isAuthenticated()")
    public PermissionRequestResponse getRequest(@PathVariable UUID id) {
        return PermissionRequestResponse.from(requestService.get(id));
    }

    @PostMapping("/requests/submit")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public PermissionRequestResponse submit(@Valid @RequestBody PermissionSubmitRequest req) {
        return PermissionRequestResponse.from(requestService.submit(req));
    }
}
